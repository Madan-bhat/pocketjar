package com.mchost.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.mchost.data.ServerConfig
import com.mchost.service.IServerHost
import com.mchost.service.ServerHostService
import com.mchost.service.ServerHostState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/**
 * UI-process wrapper — starts the :mcserver service and tails logs locally.
 */
class RemoteServerProcess(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var exitCode: Int? = null
        private set

    @Volatile
    var heapUsedMb: Int = 0
        private set

    @Volatile
    var heapMaxMb: Int = 0
        private set

    var onExitListener: ((Int?) -> Unit)? = null

    private var host: IServerHost? = null
    private var tailJob: Job? = null
    private var pollJob: Job? = null
    private var lastLogPos: Long = 0
    private var lastJvmLogPos: Long = 0
    private var bound = false
    private var startTimeMs = 0L
    private var sawJvmRunning = false
    private var sessionId = 0
    private var stopStreak = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            host = IServerHost.Stub.asInterface(service)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            host = null
            bound = false
            if (isRunning && sawJvmRunning) {
                completeExit(sessionId, readExitCode())
            }
        }
    }

    /** True if the UI-side session thinks a server is active. */
    fun isSessionActive(): Boolean = isRunning

    /** True when the remote JVM host reports it is running (requires an active binder). */
    fun isHostRunning(): Boolean = queryHostRunning()

    /**
     * Clear a stale local session when [isRunning] is true but the remote host is gone.
     * Returns true only if the remote JVM host is actually running.
     */
    fun reconcileRunningState(): Boolean {
        if (!isRunning) return false
        if (queryHostRunning()) return true
        forceReset()
        return false
    }

    fun start(serverDir: File, config: ServerConfig, cores: Int): Result<Unit> {
        if (isRunning && queryHostRunning()) {
            return Result.failure(IllegalStateException("Server already running"))
        }
        if (isRunning) forceReset()

        val session = ++sessionId

        val intent = Intent(context, ServerHostService::class.java).apply {
            action = ServerHostService.ACTION_START
            putExtra(ServerHostService.EXTRA_SERVER_DIR, serverDir.absolutePath)
            putExtra(ServerHostService.EXTRA_CONFIG_JSON, Gson().toJson(config))
            putExtra(ServerHostService.EXTRA_CORES, cores)
        }
        context.startService(intent)
        context.bindService(
            Intent(context, ServerHostService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        isRunning = true
        exitCode = null
        lastLogPos = 0
        lastJvmLogPos = 0
        heapMaxMb = config.memoryMb
        startTimeMs = System.currentTimeMillis()
        sawJvmRunning = false
        stopStreak = 0

        LogBus.emit(com.mchost.data.LogLevel.INFO, "Starting server in ${serverDir.absolutePath}")
        startTail(serverDir, session)
        startPoll(serverDir, config.memoryMb, session)
        return Result.success(Unit)
    }

    fun forceReset() {
        resetConnection()
    }

    fun sendCommand(command: String) {
        try {
            if (!isRunning) {
                LogBus.emit(com.mchost.data.LogLevel.WARN, "Cannot send command — server is not running")
                return
            }
            host?.sendCommand(command)
                ?: LogBus.emit(com.mchost.data.LogLevel.WARN, "Server console not ready yet — try again in a moment")
        } catch (e: Exception) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "sendCommand failed: ${e.message}")
        }
        LogBus.emit(com.mchost.data.LogLevel.INFO, "> $command")
    }

    fun stopGracefully() {
        if (!isRunning) return
        sendCommand("stop")
        context.startService(
            Intent(context, ServerHostService::class.java).apply {
                action = ServerHostService.ACTION_STOP
            },
        )
    }

    private fun startTail(serverDir: File, session: Int) {
        val logFile = File(serverDir, "logs/latest.log")
        val jvmLog = File(serverDir, "logs/jvm.log")
        tailJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning && session == sessionId) {
                lastLogPos = tailFileAt(logFile, lastLogPos)
                lastJvmLogPos = tailFileAt(jvmLog, lastJvmLogPos)
                delay(250)
            }
        }
    }

    private fun tailFileAt(logFile: File, pos: Long): Long {
        if (!logFile.exists()) return pos
        var readFrom = pos
        try {
            RandomAccessFile(logFile, "r").use { raf ->
                if (readFrom > raf.length()) readFrom = 0
                raf.seek(readFrom)
                var line = raf.readLine()
                var linesThisTick = 0
                while (line != null && linesThisTick < 40) {
                    LogBus.emit(parseLogLevel(line), line)
                    line = raf.readLine()
                    linesThisTick++
                }
                return raf.filePointer
            }
        } catch (_: Exception) {
            return readFrom
        }
    }

    private fun startPoll(serverDir: File, maxMb: Int, session: Int) {
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning && session == sessionId) {
                heapMaxMb = maxMb
                heapUsedMb = (maxMb * 0.35).toInt()
                val hostRunning = queryHostRunning()
                if (hostRunning) {
                    sawJvmRunning = true
                    stopStreak = 0
                } else if (sawJvmRunning) {
                    stopStreak++
                }

                val elapsed = System.currentTimeMillis() - startTimeMs
                val startupTimeout = !sawJvmRunning && elapsed > 180_000
                val confirmedStop = sawJvmRunning && !hostRunning && stopStreak >= 2

                if (confirmedStop || startupTimeout) {
                    val code = readExitCode()
                    if (code == 0 && !File(serverDir, "logs/latest.log").exists()) {
                        LogBus.emit(
                            com.mchost.data.LogLevel.WARN,
                            "JVM exited 0 but no server log — check logs/jvm.log in Files",
                        )
                    }
                    completeExit(session, code)
                }
                delay(1000)
            }
        }
    }

    private fun queryHostRunning(): Boolean {
        return try {
            host?.isRunning == true
        } catch (_: Exception) {
            false
        }
    }

    private fun readExitCode(): Int? {
        return try {
            host?.exitCode()?.takeIf { it != -999 }
        } catch (_: Exception) {
            readStateFile()?.exitCode
        }
    }

    private fun completeExit(session: Int, code: Int?) {
        if (session != sessionId || !isRunning) return
        exitCode = code
        isRunning = false
        stopTail()
        unbind()
        onExitListener?.invoke(exitCode)
    }

    private fun readStateFile(): ServerHostState? {
        return try {
            val f = context.getFileStreamPath(ServerHostService.STATE_FILE)
            if (!f.exists()) return null
            Gson().fromJson(f.readText(), ServerHostState::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLogLevel(line: String) = when {
        line.contains("ERROR", ignoreCase = true) -> com.mchost.data.LogLevel.ERROR
        line.contains("WARN", ignoreCase = true) -> com.mchost.data.LogLevel.WARN
        line.contains("DEBUG", ignoreCase = true) -> com.mchost.data.LogLevel.DEBUG
        else -> com.mchost.data.LogLevel.INFO
    }

    private fun stopTail() {
        tailJob?.cancel()
        pollJob?.cancel()
    }

    private fun unbind() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: Exception) {
            }
            bound = false
        }
        host = null
    }

    private fun resetConnection() {
        stopTail()
        unbind()
        isRunning = false
        sawJvmRunning = false
        stopStreak = 0
        exitCode = null
    }
}
