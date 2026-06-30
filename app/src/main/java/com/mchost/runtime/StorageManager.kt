package com.mchost.runtime

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageManager(private val context: Context) {

    init {
        migrateLegacyLayout()
    }

    val serversRoot: File
        get() = File(context.filesDir, DIR_SERVERS).also { it.mkdirs() }

    val runtimeRoot: File
        get() = File(context.filesDir, DIR_RUNTIME).also { it.mkdirs() }

    val backupsRoot: File
        get() = File(context.filesDir, DIR_BACKUPS).also { it.mkdirs() }

    fun serverDir(serverId: String, serverName: String): File {
        val folder = sanitizeFolderName(serverName.ifBlank { serverId })
        return File(serversRoot, folder).also { it.mkdirs() }
    }

    fun resolveServerDir(serverId: String, serverName: String): File {
        val byName = File(serversRoot, sanitizeFolderName(serverName))
        if (byName.exists()) return byName
        return serverDir(serverId, serverName)
    }

    fun jvmTempDir(serverName: String): File {
        return File(File(context.cacheDir, DIR_CACHE_JVM), sanitizeFolderName(serverName)).also { it.mkdirs() }
    }

    fun newServerId(): String = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

    private fun migrateLegacyLayout() {
        val legacyRoot = File(context.filesDir, LEGACY_APP_ROOT)
        if (!legacyRoot.exists()) return
        moveTree(File(legacyRoot, DIR_SERVERS), serversRoot)
        moveTree(File(legacyRoot, DIR_BACKUPS), backupsRoot)
        legacyRoot.deleteRecursively()
    }

    private fun moveTree(from: File, to: File) {
        if (!from.exists()) return
        to.mkdirs()
        from.listFiles()?.forEach { child ->
            val dest = File(to, child.name)
            if (dest.exists() && child.isDirectory && dest.isDirectory) {
                child.listFiles()?.forEach { nested ->
                    nested.renameTo(uniqueDest(File(dest, nested.name)))
                }
                child.deleteRecursively()
            } else {
                child.renameTo(uniqueDest(dest))
            }
        }
        from.deleteRecursively()
    }

    private fun uniqueDest(dest: File): File {
        if (!dest.exists()) return dest
        var candidate = dest
        var index = 1
        while (candidate.exists()) {
            candidate = File(dest.parentFile, "${dest.name}-migrated-$index")
            index++
        }
        return candidate
    }

    private fun sanitizeFolderName(name: String): String {
        return name.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "server" }
    }

    companion object {
        const val DIR_SERVERS = "servers"
        const val DIR_BACKUPS = "backups"
        const val DIR_RUNTIME = "runtime"
        const val DIR_CACHE_JVM = "jvm"
        private const val LEGACY_APP_ROOT = "MCHost"
    }
}
