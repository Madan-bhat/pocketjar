package com.mchost.util

import java.io.File

object ServerFileAccess {
    const val MAX_EDIT_BYTES = 1024 * 1024

    private val binaryExtensions = setOf(
        "jar", "zip", "gz", "tar", "mca", "mcr", "dat", "nbt", "lock",
        "png", "jpg", "jpeg", "gif", "webp", "so", "class", "db", "sqlite",
    )

    private val knownTextFiles = setOf(
        "server.properties", "eula.txt", "bukkit.yml", "spigot.yml", "paper-global.yml",
        "paper-world-defaults.yml", "ops.json", "whitelist.json", "banned-players.json",
        "banned-ips.json", "usercache.json", "commands.yml", "permissions.yml",
    )

    fun resolve(root: File, relativePath: String): File? {
        val target = if (relativePath.isBlank()) root else File(root, relativePath)
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        val rootPath = canonicalRoot.path
        val targetPath = canonicalTarget.path
        if (targetPath != rootPath && !targetPath.startsWith("$rootPath${File.separator}")) return null
        return canonicalTarget
    }

    fun isEditable(name: String, size: Long): Boolean {
        if (size > MAX_EDIT_BYTES) return false
        val lower = name.lowercase()
        if (lower in knownTextFiles) return true
        val ext = lower.substringAfterLast('.', "")
        if (ext == lower) return size <= 256 * 1024
        return ext !in binaryExtensions
    }

    fun isValidNewName(name: String): Boolean {
        if (name.isBlank() || name.contains('/') || name.contains('\\')) return false
        if (name == "." || name == "..") return false
        return name.none { it.code < 32 }
    }
}
