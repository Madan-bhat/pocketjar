package com.mchost.runtime

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageManager(private val context: Context) {

    val serversRoot: File
        get() = File(context.filesDir, "MCHost/servers").also { it.mkdirs() }

    val runtimeRoot: File
        get() = File(context.filesDir, "runtime").also { it.mkdirs() }

    val backupsRoot: File
        get() = File(context.filesDir, "MCHost/backups").also { it.mkdirs() }

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
        return File(context.cacheDir, "mchost-jvm/${sanitizeFolderName(serverName)}").also { it.mkdirs() }
    }

    fun newServerId(): String = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

    private fun sanitizeFolderName(name: String): String {
        return name.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "server" }
    }
}
