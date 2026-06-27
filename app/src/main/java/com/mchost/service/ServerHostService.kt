package com.mchost.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.mchost.R
import com.mchost.data.ServerConfig
import com.mchost.runtime.LogBus
import com.mchost.runtime.RuntimeManager
import com.mchost.runtime.ServerJarDownloader
import com.mchost.runtime.ServerJvmRunner
import com.mchost.runtime.StorageManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Isolated process (:mcserver) — hosts the in-process JVM away from Compose/HWUI.
 */
class ServerHostService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage by lazy { StorageManager(this) }
    private val runtimeManager by lazy { RuntimeManager(this, storage) }
    private val jarDownloader by lazy { ServerJarDownloader() }
    private val runner by lazy {
        ServerJvmRunner(this, runtimeManager, jarDownloader).also { r ->
            r.onExitListener = {
                writeState(running = false, exit = it)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private val binder = object : IServerHost.Stub() {
        override fun sendCommand(command: String) {
            runner.sendCommand(command)
        }

        override fun isRunning(): Boolean = runner.isRunning

        override fun exitCode(): Int = runner.exitCode ?: -999
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                scope.launch { handleStart(intent) }
            }
            ACTION_STOP -> {
                runner.stopGracefully()
            }
        }
        return START_STICKY
    }

    private suspend fun handleStart(intent: Intent) {
        if (runner.isRunning) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "Server host already running")
            return
        }

        writeState(running = false, exit = null)

        val serverDir = intent.getStringExtra(EXTRA_SERVER_DIR) ?: return
        val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: return
        val cores = intent.getIntExtra(EXTRA_CORES, 2)
        val config = Gson().fromJson(configJson, ServerConfig::class.java)

        val runtimeResult = runtimeManager.ensureRuntime(allowDownload = true)
        if (runtimeResult.isFailure) {
            LogBus.emit(com.mchost.data.LogLevel.ERROR, "Runtime: ${runtimeResult.exceptionOrNull()?.message}")
            writeState(running = false, exit = -1)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val dir = java.io.File(serverDir)
        jarDownloader.ensureServerJar(dir, config).onFailure { e ->
            LogBus.emit(com.mchost.data.LogLevel.ERROR, "Jar: ${e.message}")
        }

        runner.start(dir, config, cores).onSuccess {
            writeState(running = true, exit = null)
        }.onFailure { e ->
            LogBus.emit(com.mchost.data.LogLevel.ERROR, e.message ?: "Start failed")
            writeState(running = false, exit = -1)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Minecraft server",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Minecraft server running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun writeState(running: Boolean, exit: Int?) {
        runBlocking(Dispatchers.IO) {
            try {
                val state = ServerHostState(running, exit, Process.myPid())
                getFileStreamPath(STATE_FILE).bufferedWriter().use {
                    it.write(Gson().toJson(state))
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_START = "com.mchost.action.START_SERVER_HOST"
        const val ACTION_STOP = "com.mchost.action.STOP_SERVER_HOST"
        const val EXTRA_SERVER_DIR = "server_dir"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_CORES = "cores"
        const val STATE_FILE = "server-host.state"
        private const val CHANNEL_ID = "mchost_server_host"
        private const val NOTIFICATION_ID = 1002
    }
}
