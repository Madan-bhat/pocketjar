package com.mchost.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Single place for backup zip filenames and restore staging paths.
 *
 * Layout: backups/{serverId}/world_20250630-143022.zip
 * Imports: backups/{serverId}/import_world_my-save_20250630-143022.zip
 */
object BackupArchiveNaming {
    private val TIMESTAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun timestamp(): String = TIMESTAMP.format(Date())

    fun createdArchiveName(type: BackupType): String = "${type.slug}_${timestamp()}.zip"

    fun importedArchiveName(type: BackupType, sourceBaseName: String): String {
        val source = sanitizeStem(sourceBaseName).take(48)
        val stem = source.ifBlank { "archive" }
        return "import_${type.slug}_${stem}_${timestamp()}.zip"
    }

    fun parseType(fileName: String): BackupType? {
        val stem = fileName.substringBeforeLast('.')
        return when {
            stem.startsWith("import_full_") ||
                stem.startsWith("full_imported_") ||
                stem.startsWith("full-imported-") -> BackupType.FULL
            stem.startsWith("import_world_") ||
                stem.startsWith("world_imported_") ||
                stem.startsWith("world-imported-") -> BackupType.WORLD
            stem.startsWith("full-") || stem.startsWith("full_") -> BackupType.FULL
            stem.startsWith("world-") || stem.startsWith("world_") -> BackupType.WORLD
            else -> null
        }
    }

    fun restoreStagingFile(cacheDir: File): File {
        val dir = File(cacheDir, "restore").also { it.mkdirs() }
        return File(dir, "${UUID.randomUUID()}.zip")
    }

    private fun sanitizeStem(name: String): String =
        name.substringBeforeLast('.')
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_', '.')
}

private val BackupType.slug: String
    get() = when (this) {
        BackupType.WORLD -> "world"
        BackupType.FULL -> "full"
    }
