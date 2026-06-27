package com.mchost.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mchost.data.NetworkPrefs
import com.mchost.data.Server
import com.mchost.data.ServerConfig
import com.mchost.data.ServerStatus
import com.mchost.runtime.LogBus
import com.mchost.runtime.RemoteServerProcess
import com.mchost.runtime.RuntimeManager
import com.mchost.runtime.ServerJarDownloader
import com.mchost.runtime.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ServerManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val storage: StorageManager,
    private val runtimeManager: RuntimeManager,
    private val jarDownloader: ServerJarDownloader,
    private val networkManager: NetworkManager,
    private val onServerUpdate: suspend (Server) -> Unit,
) {
    val process = RemoteServerProcess(context, scope)

    private val _runtimeProgress = MutableStateFlow<String?>(null)
    val runtimeProgress: StateFlow<String?> = _runtimeProgress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var runningServer: Server? = null
    private var runningConfig: ServerConfig? = null
    private var startGeneration = 0

    init {
        process.onExitListener = { code ->
            val generation = startGeneration
            scope.launch {
                handleProcessExit(code, generation)
            }
        }
    }

    suspend fun startServer(server: Server, config: ServerConfig, networkPrefs: NetworkPrefs) {
        if (process.reconcileRunningState()) {
            _lastError.value = "A server is already running"
            return
        }
        if (process.isRunning) {
            process.forceReset()
        }

        _lastError.value = null
        startGeneration++
        val generation = startGeneration
        onServerUpdate(server.copy(status = ServerStatus.STARTING))

        startForegroundService()

        val serverDir = storage.resolveServerDir(server.id, server.name)
        val cores = withContext(Dispatchers.IO) {
            Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        }

        val launchResult = withContext(Dispatchers.IO) {
            process.start(serverDir, config, cores)
        }
        if (launchResult.isFailure) {
            _lastError.value = launchResult.exceptionOrNull()?.message
            onServerUpdate(server.copy(status = ServerStatus.STOPPED))
            process.forceReset()
            stopForegroundService()
            return
        }
        if (generation != startGeneration) return

        runningServer = server
        runningConfig = config

        val becameRunning = withContext(Dispatchers.IO) {
            waitForJvmRunning(timeoutMs = 120_000)
        }
        if (generation != startGeneration) return

        if (!becameRunning) {
            _lastError.value = "Server failed to start — check Console and logs/jvm.log"
            process.forceReset()
            onServerUpdate(server.copy(status = ServerStatus.STOPPED))
            stopForegroundService()
            runningServer = null
            runningConfig = null
            return
        }

        onServerUpdate(
            server.copy(
                status = ServerStatus.RUNNING,
                startedAt = System.currentTimeMillis(),
                maxPlayers = config.maxPlayers,
            )
        )

        scope.launch {
            networkManager.applyForwarding(networkPrefs.forwardingMethod, config.serverPort, networkPrefs)
        }
    }

    suspend fun stopServer(server: Server, config: ServerConfig) {
        startGeneration++
        onServerUpdate(server.copy(status = ServerStatus.STOPPING))
        process.stopGracefully()
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(120_000) {
                while (process.isRunning) delay(500)
            }
        }
        if (process.isRunning) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "Server still running — forcing stop")
            process.forceReset()
        }
        finishStop(server, config)
    }

    private suspend fun handleProcessExit(code: Int?, generation: Int) {
        if (generation != startGeneration) return
        val server = runningServer ?: return
        val config = runningConfig ?: return
        if (code != null && code != 0) {
            _lastError.value = "Server exited with code $code"
        }
        finishStop(server, config)
    }

    private suspend fun finishStop(server: Server, config: ServerConfig) {
        if (process.isRunning) {
            process.forceReset()
        }
        withContext(Dispatchers.IO) {
            networkManager.clearForwarding(config.serverPort)
        }
        runningServer = null
        runningConfig = null
        onServerUpdate(server.copy(status = ServerStatus.STOPPED, startedAt = null))
        stopForegroundService()
    }

    private suspend fun waitForJvmRunning(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (process.isHostRunning()) return true
            if (!process.isSessionActive()) return false
            delay(500)
        }
        return process.isHostRunning()
    }

    private fun startForegroundService() {
        val intent = Intent(context, MCHostForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundService() {
        context.stopService(Intent(context, MCHostForegroundService::class.java))
    }

    fun maxMemoryMb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024) / 2).toInt().coerceIn(512, 4096)
    }
}
