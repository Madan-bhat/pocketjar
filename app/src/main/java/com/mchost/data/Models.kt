package com.mchost.data

enum class ServerStatus {
    RUNNING,
    STOPPED,
    STARTING,
    STOPPING,
}

enum class JarType {
    VANILLA,
    PAPER,
    PURPUR,
    FABRIC,
    CUSTOM,
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

enum class ForwardingMethod {
    UPNP,
    LOCALXPOSE,
    TAILSCALE,
    MANUAL,
}

data class Server(
    val id: String,
    val name: String,
    val worldName: String,
    val players: Int = 0,
    val maxPlayers: Int = 20,
    val status: ServerStatus = ServerStatus.STOPPED,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
)

data class ServerConfig(
    val serverName: String,
    val worldName: String,
    val worldType: String = "normal",
    val worldSeed: String = "",
    val maxPlayers: Int = 20,
    val serverPort: Int = 25565,
    val memoryMb: Int = 1024,
    val version: String = "1.21.1",
    val jarType: JarType = JarType.PAPER,
    val customJarName: String = "server-custom.jar",
    val viewDistance: Int = 10,
    val simulationDistance: Int = 10,
    val gamemode: String = "survival",
    val difficulty: String = "normal",
    val pvp: Boolean = true,
    val keepInventory: Boolean = false,
    val whitelist: Boolean = false,
    val onlineMode: Boolean = true,
    val eulaAccepted: Boolean = false,
)

data class LogEntry(
    val id: Long,
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class NetworkPrefs(
    val forwardingMethod: ForwardingMethod = ForwardingMethod.UPNP,
    val localXposeToken: String? = null,
    val localXposeRegion: String = "ap",
    /** Paste ap.loclx.io:12345 from Termux if in-app loclx cannot run */
    val localXposeManualAddress: String? = null,
)

data class RuntimeProgress(
    val message: String,
    val percent: Int? = null,
)

data class ServerFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
)
