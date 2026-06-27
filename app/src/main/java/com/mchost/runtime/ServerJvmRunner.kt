package com.mchost.runtime

import android.content.Context
import com.mchost.data.JarType
import com.mchost.data.ServerConfig
import java.io.File
import java.io.PipedInputStream
import kotlin.concurrent.thread

/**
 * Runs JLI_Launch using Pojav-style env setup. Must only run inside the :mcserver process
 * (no HWUI/Compose threads — in-process JVM in the UI process crashes RenderThread).
 */
class ServerJvmRunner(
    private val context: Context,
    private val runtimeManager: RuntimeManager,
    private val jarDownloader: ServerJarDownloader,
) {
    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var exitCode: Int? = null
        private set

    private var jvmThread: Thread? = null

    var onExitListener: ((Int?) -> Unit)? = null

    fun start(serverDir: File, config: ServerConfig, cores: Int): Result<Unit> {
        if (isRunning) return Result.failure(IllegalStateException("Server already running"))

        jarDownloader.checkJavaCompatibility(config)?.let {
            return Result.failure(IllegalStateException(it))
        }

        val jarFile = resolveJarFile(serverDir, config)
        if (!jarFile.exists()) {
            return Result.failure(IllegalStateException("Server jar missing in ${serverDir.absolutePath}"))
        }
        if (!config.eulaAccepted) {
            return Result.failure(IllegalStateException("EULA must be accepted"))
        }

        val tmpDir = File(serverDir, ".tmp").also { it.mkdirs() }
        val xmsMb = (config.memoryMb / 4).coerceAtLeast(128)
        val args = buildJvmArgs(config, jarFile, cores, xmsMb, runtimeManager.javaHome, tmpDir)

        isRunning = true
        exitCode = null
        LogBus.emit(com.mchost.data.LogLevel.INFO, "JVM starting in ${serverDir.absolutePath}")
        LogBus.emit(com.mchost.data.LogLevel.DEBUG, "JVM args: ${args.toList()}")

        jvmThread = thread(name = "MCHost-JVM", isDaemon = false) {
            try {
                val logsDir = File(serverDir, "logs").also { it.mkdirs() }
                runtimeManager.prepareForLaunch(tmpDir, context.applicationInfo.nativeLibraryDir)
                System.setIn(PipedInputStream())
                JvmNative.chdir(serverDir.absolutePath)
                val code = JvmNative.launchJVM(
                    args,
                    runtimeManager.javaHome.absolutePath,
                    runtimeManager.ldLibraryPath,
                    tmpDir.absolutePath,
                    serverDir.absolutePath,
                    logsDir.absolutePath,
                )
                exitCode = code
                LogBus.emit(com.mchost.data.LogLevel.INFO, "Server process exited with code $code")
            } catch (e: Exception) {
                LogBus.emit(com.mchost.data.LogLevel.ERROR, "JVM launch failed: ${e.message}")
                exitCode = -1
            } finally {
                isRunning = false
                onExitListener?.invoke(exitCode)
            }
        }
        return Result.success(Unit)
    }

    fun sendCommand(command: String) {
        val line = if (command.endsWith("\n")) command else "$command\n"
        JvmNative.writeStdin(line.toByteArray(Charsets.UTF_8))
    }

    fun stopGracefully() {
        if (!isRunning) return
        sendCommand("stop")
    }

    private fun resolveJarFile(serverDir: File, config: ServerConfig): File {
        val expected = jarDownloader.jarFileFor(serverDir, config)
        if (expected.exists()) return expected
        if (config.jarType == JarType.CUSTOM) {
            val custom = File(serverDir, config.customJarName)
            if (custom.exists()) return custom
        }
        return expected
    }

    private fun buildJvmArgs(
        config: ServerConfig,
        jarFile: File,
        cores: Int,
        xmsMb: Int,
        javaHome: File,
        tmpDir: File,
    ): Array<String> = arrayOf(
        "java",
        "-Djava.home=${javaHome.absolutePath}",
        "-Djava.io.tmpdir=${tmpDir.absolutePath}",
        "-Dos.name=Linux",
        "-Dos.version=Android",
        "-Djdk.lang.Process.launchMechanism=FORK",
        "-DPaper.IgnoreJavaVersion=true",
        "-XX:+UseG1GC",
        "-XX:-UseZGC",
        "-XX:-UseShenandoahGC",
        "-XX:ActiveProcessorCount=$cores",
        "-Xmx${config.memoryMb}M",
        "-Xms${xmsMb}M",
        "-Djava.awt.headless=true",
        "-jar", jarFile.absolutePath,
        "--nogui",
    )
}
