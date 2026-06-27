package com.mchost.util

import com.mchost.data.Server
import com.mchost.runtime.StorageManager
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupType(val label: String, val prefix: String) {
    WORLD("World", "world"),
    FULL("Full server", "full"),
}

data class ServerBackupInfo(
    val id: String,
    val serverId: String,
    val serverName: String,
    val type: BackupType,
    val file: File,
    val createdAt: Long,
    val sizeBytes: Long,
)

class ServerBackupManager(private val storage: StorageManager) {

    fun backupsDir(serverId: String): File =
        File(storage.backupsRoot, serverId).also { it.mkdirs() }

    fun listBackups(server: Server): List<ServerBackupInfo> {
        val dir = backupsDir(server.id)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            ?.mapNotNull { parseBackupFile(server, it) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun createBackup(server: Server, worldName: String, type: BackupType): Result<ServerBackupInfo> = runCatching {
        val serverDir = storage.resolveServerDir(server.id, server.name)
        if (!serverDir.exists()) throw IllegalStateException("Server folder not found")

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = server.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "server" }
        val zipFile = File(backupsDir(server.id), "${type.prefix}-${safeName}-$timestamp.zip")

        when (type) {
            BackupType.WORLD -> {
                val worldDir = File(serverDir, worldName)
                if (!worldDir.isDirectory) throw IllegalStateException("World folder not found: $worldName")
                zipPaths(serverDir, listOf(worldDir.relativeTo(serverDir).path), zipFile)
            }
            BackupType.FULL -> zipDirectory(serverDir, zipFile) { relativePath ->
                !shouldSkipFromFullBackup(relativePath)
            }
        }

        ServerBackupInfo(
            id = zipFile.nameWithoutExtension,
            serverId = server.id,
            serverName = server.name,
            type = type,
            file = zipFile,
            createdAt = zipFile.lastModified(),
            sizeBytes = zipFile.length(),
        )
    }

    fun restoreBackup(server: Server, worldName: String, backup: ServerBackupInfo): Result<Unit> = runCatching {
        require(backup.serverId == server.id) { "Backup belongs to a different server" }
        val serverDir = storage.resolveServerDir(server.id, server.name)
        when (backup.type) {
            BackupType.WORLD -> {
                val worldDir = File(serverDir, worldName)
                if (worldDir.exists()) worldDir.deleteRecursively()
                unzipTo(backup.file, serverDir)
            }
            BackupType.FULL -> {
                preserveAndRestoreFull(serverDir, backup.file)
            }
        }
    }

    fun deleteBackup(backup: ServerBackupInfo): Result<Unit> = runCatching {
        if (!backup.file.delete()) throw IllegalStateException("Could not delete backup")
    }

    private fun preserveAndRestoreFull(serverDir: File, zipFile: File) {
        val preserved = listOf(".tmp", "logs").mapNotNull { name ->
            val f = File(serverDir, name)
            if (!f.exists()) return@mapNotNull null
            val stash = File(serverDir.parentFile, ".restore-stash-${serverDir.name}-$name")
            if (stash.exists()) stash.deleteRecursively()
            f.renameTo(stash)
            name to stash
        }
        try {
            serverDir.listFiles()?.forEach { child ->
                if (child.name.startsWith(".restore-stash-")) return@forEach
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            }
            unzipTo(zipFile, serverDir)
        } finally {
            preserved.forEach { (name, stash) ->
                val target = File(serverDir, name)
                if (target.exists()) {
                    if (target.isDirectory) target.deleteRecursively() else target.delete()
                }
                stash.renameTo(target)
            }
        }
    }

    private fun zipDirectory(root: File, zipFile: File, include: (String) -> Boolean) {
        ZipArchiveOutputStream(FileOutputStream(zipFile)).use { zos ->
            root.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val relative = file.relativeTo(root).path.replace('\\', '/')
                if (!include(relative)) return@forEach
                addFileToZip(zos, file, relative)
            }
        }
    }

    private fun zipPaths(root: File, relativePaths: List<String>, zipFile: File) {
        ZipArchiveOutputStream(FileOutputStream(zipFile)).use { zos ->
            relativePaths.forEach { rel ->
                val target = File(root, rel)
                if (target.isFile) {
                    addFileToZip(zos, target, rel.replace('\\', '/'))
                } else if (target.isDirectory) {
                    target.walkTopDown().forEach { file ->
                        if (!file.isFile) return@forEach
                        val relative = file.relativeTo(root).path.replace('\\', '/')
                        addFileToZip(zos, file, relative)
                    }
                }
            }
        }
    }

    private fun addFileToZip(zos: ZipArchiveOutputStream, file: File, entryName: String) {
        val entry = ZipArchiveEntry(entryName).apply {
            size = file.length()
            time = file.lastModified()
        }
        zos.putArchiveEntry(entry)
        FileInputStream(file).use { input -> input.copyTo(zos) }
        zos.closeArchiveEntry()
    }

    private fun unzipTo(zipFile: File, destDir: File) {
        destDir.mkdirs()
        ZipArchiveInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun shouldSkipFromFullBackup(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        return normalized.startsWith(".tmp/") ||
            normalized == ".tmp" ||
            normalized.startsWith("logs/") ||
            normalized.endsWith("/session.lock")
    }

    private fun parseBackupFile(server: Server, file: File): ServerBackupInfo? {
        val name = file.nameWithoutExtension
        val type = when {
            name.startsWith("world-") -> BackupType.WORLD
            name.startsWith("full-") -> BackupType.FULL
            else -> return null
        }
        return ServerBackupInfo(
            id = name,
            serverId = server.id,
            serverName = server.name,
            type = type,
            file = file,
            createdAt = file.lastModified(),
            sizeBytes = file.length(),
        )
    }
}
