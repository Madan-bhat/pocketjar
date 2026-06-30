package com.mchost.network.modrinth

import com.mchost.data.JarType

enum class ModrinthContentKind(val projectType: String, val folder: String) {
    PLUGINS("plugin", "plugins"),
    MODS("mod", "mods"),
    DATAPACKS("datapack", "datapacks"),
}

data class ModrinthProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val downloads: Long,
    val author: String,
)

data class ModrinthVersion(
    val id: String,
    val versionNumber: String,
    val filename: String,
    val downloadUrl: String,
    val primary: Boolean,
    val gameVersions: List<String> = emptyList(),
)

data class ModrinthSearchResult(
    val projects: List<ModrinthProject>,
    val totalHits: Int,
)

data class PendingModrinthInstall(
    val projectId: String,
    val projectTitle: String,
    val versionId: String,
    val filename: String,
    val kind: ModrinthContentKind,
)

fun modrinthLoaders(jarType: JarType, kind: ModrinthContentKind): List<String> = when (kind) {
    ModrinthContentKind.PLUGINS -> when (jarType) {
        JarType.PURPUR -> listOf("purpur", "paper", "bukkit", "spigot")
        JarType.PAPER, JarType.CUSTOM -> listOf("paper", "bukkit", "spigot")
        else -> listOf("paper", "bukkit", "spigot")
    }
    ModrinthContentKind.MODS -> when (jarType) {
        JarType.FABRIC, JarType.CUSTOM -> listOf("fabric")
        else -> listOf("fabric", "forge", "neoforge", "quilt")
    }
    ModrinthContentKind.DATAPACKS -> emptyList()
}

fun supportsModrinthPlugins(jarType: JarType): Boolean =
    jarType in setOf(JarType.PAPER, JarType.PURPUR, JarType.CUSTOM)

fun supportsModrinthMods(jarType: JarType): Boolean =
    jarType in setOf(JarType.FABRIC, JarType.CUSTOM)

/** True when [supported] is the same release line as [target] (e.g. 1.21 ↔ 1.21.1). */
fun modrinthVersionMatches(target: String, supported: String): Boolean {
    if (target == supported) return true
    val targetParts = target.split('.').map { it.toIntOrNull() ?: 0 }
    val supportedParts = supported.split('.').map { it.toIntOrNull() ?: 0 }
    val shared = minOf(targetParts.size, supportedParts.size, 2).coerceAtLeast(1)
    return (0 until shared).all { targetParts[it] == supportedParts[it] }
}

fun ModrinthVersion.supportsGameVersion(target: String): Boolean {
    if (gameVersions.isEmpty()) return true
    return gameVersions.any { modrinthVersionMatches(target, it) }
}
