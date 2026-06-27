package com.mchost.network

import android.content.Context
import com.mchost.runtime.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class LocalXposeAgent(private val context: Context) {

    companion object {
        private const val BINARY_NAME = "libloclx.so"
        private const val MIN_BINARY_BYTES = 1_000_000L
        private val ENDPOINT_REGEX = Regex("""(?i)([\w.-]+\.loclx\.io:\d+)""")

        fun termuxCommand(port: Int, region: String = "ap"): String =
            "export ACCESS_TOKEN=YOUR_TOKEN\n" +
                "./loclx tunnel tcp --to 127.0.0.1:$port --region ${region.ifBlank { "ap" }}"
    }

    private var tunnelPid: Int = -1
    private var tunnelProcess: Process? = null
    @Volatile
    private var activeAddress: String? = null

    suspend fun start(port: Int, accessToken: String, region: String = "ap"): Result<String> =
        withContext(Dispatchers.IO) {
            stop()
            val token = accessToken.trim()
            if (token.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Paste your Termux tunnel address above, or add a token to try in-app tunnel",
                    ),
                )
            }

            val binary = locateBinary()
            if (binary == null) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "loclx not found in app install. Use Termux (see Manual tunnel above).",
                    ),
                )
            }

            val home = context.filesDir
            val localTarget = "127.0.0.1:$port"
            val regionCode = region.ifBlank { "ap" }

            try {
                File(home, ".access").writeText(token)
                LogBus.emit(
                    com.mchost.data.LogLevel.INFO,
                    "loclx binary OK (${binary.length() / 1024 / 1024} MB) → $localTarget region=$regionCode",
                )

                val logFile = File(home, "localxpose/loclx.log").also {
                    it.parentFile?.mkdirs()
                }
                logFile.delete()

                val args = arrayOf(
                    binary.absolutePath,
                    "tunnel", "tcp",
                    "--to", localTarget,
                    "--region", regionCode,
                )
                val env = arrayOf(
                    "ACCESS_TOKEN=$token",
                    "HOME=${home.absolutePath}",
                    "TERM=dumb",
                    "PATH=/system/bin:/vendor/bin",
                )

                val pid = NativeExec.nativeSpawn(binary.absolutePath, args, env, logFile.absolutePath)
                if (pid <= 0) {
                    return@withContext Result.failure(
                        IOException("Native exec failed for loclx — use Termux tunnel instead"),
                    )
                }
                tunnelPid = pid

                val readResult = readTunnelFromLog(logFile, pid, timeoutMs = 60_000)
                readResult.address?.let { addr ->
                    activeAddress = addr
                    LogBus.emit(com.mchost.data.LogLevel.INFO, "LocalXpose tunnel: $addr → $localTarget")
                    return@withContext Result.success(addr)
                }

                stop()
                Result.failure(
                    IllegalStateException(
                        readResult.errorMessage + "\n\nTermux:\n" + termuxCommand(port, regionCode),
                    ),
                )
            } catch (e: Exception) {
                stop()
                Result.failure(e)
            }
        }

    fun stop() {
        activeAddress = null
        if (tunnelPid > 0) {
            try {
                NativeExec.nativeKill(tunnelPid)
                Thread.sleep(300)
            } catch (_: Exception) {
            }
            tunnelPid = -1
        }
        tunnelProcess?.let { proc ->
            try {
                proc.destroy()
                proc.waitFor(2, TimeUnit.SECONDS)
                if (proc.isAlive) proc.destroyForcibly()
            } catch (_: Exception) {
            }
        }
        tunnelProcess = null
    }

    fun currentAddress(): String? = activeAddress

    private fun locateBinary(): File? {
        val exec = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        LogBus.emit(
            com.mchost.data.LogLevel.INFO,
            "loclx path=${exec.absolutePath} exists=${exec.exists()} bytes=${exec.length()}",
        )
        if (exec.exists() && exec.length() >= MIN_BINARY_BYTES) {
            exec.setReadable(true, false)
            exec.setExecutable(true, false)
            return exec
        }
        return null
    }

    private data class TunnelReadResult(val address: String?, val errorMessage: String)

    private fun readTunnelFromLog(logFile: File, pid: Int, timeoutMs: Long): TunnelReadResult {
        val output = StringBuilder()
        var lastPos = 0L
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            if (logFile.exists() && logFile.length() > lastPos) {
                RandomAccessFile(logFile, "r").use { raf ->
                    raf.seek(lastPos)
                    val chunk = ByteArray((logFile.length() - lastPos).toInt())
                    val read = raf.read(chunk)
                    if (read > 0) {
                        lastPos += read
                        val text = String(chunk, 0, read)
                        output.append(text)
                        text.lineSequence().forEach { line ->
                            if (line.isBlank()) return@forEach
                            LogBus.emit(com.mchost.data.LogLevel.INFO, "loclx: $line")
                            parseAddress(line)?.let {
                                return TunnelReadResult(it, "")
                            }
                        }
                    }
                }
            }
            if (!NativeExec.nativeIsAlive(pid)) break
            Thread.sleep(250)
        }

        val text = output.toString().trim()
        return TunnelReadResult(null, buildErrorMessage(text))
    }

    private fun parseAddress(line: String): String? {
        ENDPOINT_REGEX.find(line)?.groupValues?.get(1)?.let { return it }
        if (line.contains("loclx.io", ignoreCase = true)) {
            val cleaned = line.substringAfter("Tunneling TCP:", line)
                .substringAfter("-->", line)
                .trim()
            ENDPOINT_REGEX.find(cleaned)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    private fun buildErrorMessage(output: String): String {
        val lower = output.lowercase()
        when {
            output.contains("permission denied", ignoreCase = true) ||
                output.contains("error=13", ignoreCase = true) ->
                return "Android blocked loclx — use Termux and paste the tunnel address above"
            lower.contains("unauthorized") || lower.contains("invalid token") ->
                return "Token rejected — copy a fresh access token from localxpose.io/dashboard/access"
            lower.contains("incorrect usage") ->
                return "loclx CLI error: ${output.lineSequence().lastOrNull() ?: "unknown"}"
            output.isNotBlank() -> {
                val last = output.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                return last?.let { "loclx: $it" } ?: "loclx produced no tunnel address"
            }
        }
        return "loclx produced no output — start the server, run loclx in Termux, paste address above"
    }
}
