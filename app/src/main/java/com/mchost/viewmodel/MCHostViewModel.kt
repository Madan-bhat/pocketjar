package com.mchost.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mchost.data.ConfigRepository
import com.mchost.data.JarType
import com.mchost.data.LogRepository
import com.mchost.data.NetworkPrefs
import com.mchost.data.NetworkRepository
import com.mchost.data.Server
import com.mchost.data.ServerConfig
import com.mchost.data.ServerFileEntry
import com.mchost.data.ServerRepository
import com.mchost.data.ServerStatus
import com.mchost.runtime.LogBus
import com.mchost.runtime.RuntimeManager
import com.mchost.runtime.ServerJarDownloader
import com.mchost.runtime.StorageManager
import com.mchost.service.NetworkManager
import com.mchost.service.ServerManager
import com.mchost.network.modrinth.ModrinthClient
import com.mchost.network.modrinth.ModrinthContentKind
import com.mchost.network.modrinth.ModrinthSearchResult
import com.mchost.network.modrinth.ModrinthVersion
import com.mchost.network.modrinth.PendingModrinthInstall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mchost.util.BackupType
import com.mchost.util.FileImporter
import com.mchost.util.ServerBackupInfo
import com.mchost.util.ServerBackupManager
import com.mchost.util.ServerFileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class AppOverlay {
    NONE, SERVERS, NEW_SERVER, SETTINGS, FILES,
}

data class CreateServerExtras(
    val customJarUri: Uri? = null,
    val pluginUris: List<Uri> = emptyList(),
    val modUris: List<Uri> = emptyList(),
    val datapackUris: List<Uri> = emptyList(),
    val modrinthInstalls: List<PendingModrinthInstall> = emptyList(),
)

data class TextFileLoadResult(
    val content: String? = null,
    val error: String? = null,
    val readOnly: Boolean = false,
)

class MCHostViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = StorageManager(app)
    private val serverRepo = ServerRepository(app)
    private val configRepo = ConfigRepository(app)
    private val networkRepo = NetworkRepository(app)
    private val logRepo = LogRepository(viewModelScope)
    private val runtimeManager = RuntimeManager(app, storage)
    private val jarDownloader = ServerJarDownloader()
    val networkManager = NetworkManager(app)
    private val modrinthClient = ModrinthClient()
    private val backupManager = ServerBackupManager(storage)

    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

    val serverManager = ServerManager(
        context = app,
        scope = viewModelScope,
        storage = storage,
        runtimeManager = runtimeManager,
        jarDownloader = jarDownloader,
        networkManager = networkManager,
        onServerUpdate = { server -> serverRepo.upsertServer(server) },
    )

    val servers = serverRepo.servers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val configs = configRepo.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val networkPrefs = networkRepo.networkPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, NetworkPrefs())
    val logs = logRepo.entries

    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _overlay = MutableStateFlow(AppOverlay.NONE)
    val overlay: StateFlow<AppOverlay> = _overlay.asStateFlow()

    private val _creatingServer = MutableStateFlow(false)
    val creatingServer: StateFlow<Boolean> = _creatingServer.asStateFlow()

    val activeServer = combine(servers, activeServerId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeConfig = combine(activeServerId, configs) { id, map ->
        id?.let { map[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            serverRepo.activeServerId.collect { _activeServerId.value = it }
        }
        viewModelScope.launch {
            val list = serverRepo.servers.first()
            if (!serverManager.process.isSessionActive()) {
                list.filter { it.status != ServerStatus.STOPPED }.forEach { server ->
                    serverRepo.upsertServer(server.copy(status = ServerStatus.STOPPED, startedAt = null))
                }
            }
        }
        viewModelScope.launch {
            runtimeManager.ensureRuntime(allowDownload = true) { progress ->
                LogBus.emit(com.mchost.data.LogLevel.INFO, progress.message)
            }.onFailure { e ->
                LogBus.emit(com.mchost.data.LogLevel.WARN, "Runtime setup: ${e.message}")
            }
        }
    }

    fun setActiveServer(id: String) {
        viewModelScope.launch { serverRepo.setActiveServerId(id) }
    }

    fun showOverlay(overlay: AppOverlay) {
        _overlay.value = overlay
    }

    fun dismissOverlay() {
        _overlay.value = AppOverlay.NONE
    }

    fun toggleServer(server: Server, config: ServerConfig) {
        viewModelScope.launch {
            when (server.status) {
                ServerStatus.RUNNING, ServerStatus.STARTING ->
                    serverManager.stopServer(server, config)
                else -> {
                    val prefs = networkPrefs.value
                    serverManager.startServer(server, config, prefs)
                }
            }
        }
    }

    fun deleteServer(server: Server) {
        viewModelScope.launch {
            if (server.status == ServerStatus.RUNNING) {
                configs.value[server.id]?.let { serverManager.stopServer(server, it) }
            }
            val dir = storage.resolveServerDir(server.id, server.name)
            dir.deleteRecursively()
            configRepo.deleteConfig(server.id)
            serverRepo.deleteServer(server.id)
        }
    }

    fun createServer(config: ServerConfig, extras: CreateServerExtras = CreateServerExtras()) {
        viewModelScope.launch {
            _creatingServer.value = true
            try {
                val id = storage.newServerId()
                val serverDir = storage.serverDir(id, config.serverName)
                val finalConfig = if (extras.customJarUri != null) {
                    val dest = File(serverDir, config.customJarName)
                    FileImporter.copyUri(getApplication(), extras.customJarUri, dest)
                    config.copy(jarType = JarType.CUSTOM)
                } else {
                    config
                }

                extras.pluginUris.forEach { uri ->
                    FileImporter.copyUriToSubdir(getApplication(), uri, serverDir, "plugins")
                }
                extras.modUris.forEach { uri ->
                    FileImporter.copyUriToSubdir(getApplication(), uri, serverDir, "mods")
                }
                extras.datapackUris.forEach { uri ->
                    FileImporter.copyUriToSubdir(
                        getApplication(),
                        uri,
                        serverDir,
                        "${finalConfig.worldName}/datapacks",
                    )
                }
                installPendingModrinth(serverDir, finalConfig.worldName, extras.modrinthInstalls)

                val server = Server(
                    id = id,
                    name = finalConfig.serverName,
                    worldName = finalConfig.worldName,
                    maxPlayers = finalConfig.maxPlayers,
                )

                configRepo.saveConfig(id, finalConfig)
                serverRepo.upsertServer(server)
                serverRepo.setActiveServerId(id)

                if (finalConfig.jarType != JarType.CUSTOM) {
                    LogBus.emit(com.mchost.data.LogLevel.INFO, "Downloading server jar…")
                    jarDownloader.ensureServerJar(serverDir, finalConfig) { msg ->
                        LogBus.emit(com.mchost.data.LogLevel.INFO, msg)
                    }.onFailure { e ->
                        LogBus.emit(com.mchost.data.LogLevel.ERROR, "Jar download failed: ${e.message}")
                    }
                }
            } finally {
                _creatingServer.value = false
            }
        }
    }

    fun updateConfig(serverId: String, config: ServerConfig) {
        viewModelScope.launch { configRepo.saveConfig(serverId, config) }
    }

    fun importCustomJar(server: Server, uri: Uri) {
        viewModelScope.launch {
            val serverDir = storage.resolveServerDir(server.id, server.name)
            val config = configs.value[server.id] ?: return@launch
            val name = FileImporter.queryDisplayName(getApplication(), uri)
                ?.takeIf { it.endsWith(".jar", ignoreCase = true) }
                ?: config.customJarName
            val dest = File(serverDir, name)
            FileImporter.copyUri(getApplication(), uri, dest)
            updateConfig(
                server.id,
                config.copy(jarType = JarType.CUSTOM, customJarName = name),
            )
            LogBus.emit(com.mchost.data.LogLevel.INFO, "Imported custom jar: $name")
        }
    }

    fun saveNetworkPrefs(prefs: NetworkPrefs) {
        viewModelScope.launch { networkRepo.saveNetworkPrefs(prefs) }
    }

    fun applyNetworkForwarding(
        method: com.mchost.data.ForwardingMethod,
        port: Int,
        localXposeToken: String? = null,
        localXposeRegion: String? = null,
        localXposeManualAddress: String? = null,
    ) {
        viewModelScope.launch {
            var prefs = networkPrefs.value.copy(forwardingMethod = method)
            if (method == com.mchost.data.ForwardingMethod.LOCALXPOSE) {
                val manual = localXposeManualAddress?.trim().orEmpty()
                if (manual.isNotBlank()) {
                    prefs = prefs.copy(localXposeManualAddress = manual)
                }
                val token = localXposeToken?.trim().orEmpty()
                if (token.isNotBlank()) {
                    prefs = prefs.copy(
                        localXposeToken = token,
                        localXposeRegion = localXposeRegion?.trim()?.ifBlank { "ap" } ?: prefs.localXposeRegion,
                    )
                }
                if (manual.isBlank() && token.isBlank()) {
                    LogBus.emit(
                        com.mchost.data.LogLevel.WARN,
                        "Paste tunnel address from Termux, or add a LocalXpose token",
                    )
                    return@launch
                }
            }
            networkRepo.saveNetworkPrefs(prefs)
            networkManager.applyForwarding(method, port, prefs)
        }
    }

    fun saveLocalXposeSettings(
        token: String,
        region: String,
        manualAddress: String? = null,
    ) {
        viewModelScope.launch {
            val prefs = networkPrefs.value.copy(
                localXposeToken = token.trim().ifBlank { null },
                localXposeRegion = region,
                localXposeManualAddress = manualAddress?.trim()?.ifBlank { null },
            )
            networkRepo.saveNetworkPrefs(prefs)
        }
    }

    fun sendCommand(command: String): Boolean {
        if (!serverManager.process.isRunning) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "Cannot send command — server is not running")
            return false
        }
        serverManager.process.sendCommand(command)
        return true
    }

    fun clearLogs() = logRepo.clear()

    fun setLogVerbose(verbose: Boolean) = logRepo.setVerbose(verbose)

    val logVerbose = logRepo.verbose

    private fun serverRoot(server: Server): File =
        storage.resolveServerDir(server.id, server.name).canonicalFile

    suspend fun listFiles(server: Server, relativePath: String): List<ServerFileEntry> =
        withContext(Dispatchers.IO) {
            val root = serverRoot(server)
            val target = ServerFileAccess.resolve(root, relativePath) ?: return@withContext emptyList()
            if (!target.isDirectory) return@withContext emptyList()
            target.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { f ->
                    ServerFileEntry(
                        name = f.name,
                        path = f.relativeTo(root).path,
                        isDirectory = f.isDirectory,
                        size = if (f.isFile) f.length() else 0,
                        modified = f.lastModified(),
                    )
                } ?: emptyList()
        }

    suspend fun loadTextFile(server: Server, relativePath: String): TextFileLoadResult =
        withContext(Dispatchers.IO) {
            val root = serverRoot(server)
            val file = ServerFileAccess.resolve(root, relativePath) ?: return@withContext TextFileLoadResult(
                error = "Invalid path",
            )
            if (!file.isFile) return@withContext TextFileLoadResult(error = "Not a file")
            if (!ServerFileAccess.isEditable(file.name, file.length())) {
                return@withContext TextFileLoadResult(
                    error = "Cannot edit ${file.name} (binary or over ${ServerFileAccess.MAX_EDIT_BYTES / 1024} KB)",
                    readOnly = true,
                )
            }
            runCatching { file.readText() }
                .fold(
                    onSuccess = { TextFileLoadResult(content = it) },
                    onFailure = { TextFileLoadResult(error = it.message ?: "Read failed") },
                )
        }

    suspend fun saveTextFile(server: Server, relativePath: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = serverRoot(server)
                val file = ServerFileAccess.resolve(root, relativePath)
                    ?: throw IllegalArgumentException("Invalid path")
                if (!file.isFile) throw IllegalArgumentException("Not a file")
                if (!ServerFileAccess.isEditable(file.name, file.length())) {
                    throw IllegalArgumentException("File is not editable")
                }
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
        }

    suspend fun createTextFile(server: Server, parentPath: String, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!ServerFileAccess.isValidNewName(name)) {
                    throw IllegalArgumentException("Invalid file name")
                }
                val root = serverRoot(server)
                val parent = ServerFileAccess.resolve(root, parentPath)
                    ?: throw IllegalArgumentException("Invalid folder")
                if (!parent.isDirectory) throw IllegalArgumentException("Parent is not a folder")
                val file = File(parent, name)
                if (file.exists()) throw IllegalArgumentException("Already exists")
                file.parentFile?.mkdirs()
                file.writeText("")
                file.relativeTo(root).path
            }
        }

    suspend fun createDirectory(server: Server, parentPath: String, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!ServerFileAccess.isValidNewName(name)) {
                    throw IllegalArgumentException("Invalid folder name")
                }
                val root = serverRoot(server)
                val parent = ServerFileAccess.resolve(root, parentPath)
                    ?: throw IllegalArgumentException("Invalid folder")
                if (!parent.isDirectory) throw IllegalArgumentException("Parent is not a folder")
                val dir = File(parent, name)
                if (dir.exists()) throw IllegalArgumentException("Already exists")
                if (!dir.mkdir()) throw IllegalArgumentException("Could not create folder")
                dir.relativeTo(root).path
            }
        }

    suspend fun deleteEntry(server: Server, relativePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = serverRoot(server)
                val target = ServerFileAccess.resolve(root, relativePath)
                    ?: throw IllegalArgumentException("Invalid path")
                if (target == root) throw IllegalArgumentException("Cannot delete server root")
                if (target.isDirectory) {
                    if (!target.deleteRecursively()) throw IllegalArgumentException("Delete failed")
                } else {
                    if (!target.delete()) throw IllegalArgumentException("Delete failed")
                }
                Unit
            }
        }

    fun datapacksPath(server: Server): String {
        val world = configs.value[server.id]?.worldName ?: server.worldName
        return "$world/datapacks"
    }

    suspend fun importFiles(
        server: Server,
        targetPath: String,
        uris: List<Uri>,
        defaultExtension: String? = "jar",
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.success(emptyList())
        runCatching {
            val root = serverRoot(server)
            val destDir = if (targetPath.isBlank()) {
                root
            } else {
                ServerFileAccess.resolve(root, targetPath)
                    ?: throw IllegalArgumentException("Invalid folder: $targetPath")
            }
            if (!destDir.isDirectory && destDir.exists()) {
                throw IllegalArgumentException("Not a folder: $targetPath")
            }
            destDir.mkdirs()
            val names = uris.map { uri ->
                FileImporter.copyUriToDir(getApplication(), uri, destDir, defaultExtension).name
            }
            LogBus.emit(
                com.mchost.data.LogLevel.INFO,
                "Imported ${names.size} file(s) → ${if (targetPath.isBlank()) "server root" else targetPath}/",
            )
            names
        }
    }

    suspend fun importJarFiles(
        server: Server,
        targetPath: String,
        uris: List<Uri>,
    ): Result<List<String>> = importFiles(server, targetPath, uris, defaultExtension = "jar")

    /** @deprecated Use [loadTextFile] — kept for any legacy callers */
    fun readTextFile(server: Server, relativePath: String, maxBytes: Int = 512 * 1024): String? {
        val root = serverRoot(server)
        val file = ServerFileAccess.resolve(root, relativePath) ?: return null
        if (!file.isFile) return null
        if (file.length() > maxBytes) return "File too large to preview (${file.length()} bytes)"
        return runCatching { file.readText() }.getOrNull()
    }

    fun maxDeviceMemoryMb() = serverManager.maxMemoryMb()

    fun serverRootPath(server: Server) = storage.resolveServerDir(server.id, server.name).absolutePath

    suspend fun listBackups(server: Server): List<ServerBackupInfo> = withContext(Dispatchers.IO) {
        backupManager.listBackups(server)
    }

    suspend fun createBackup(server: Server, type: BackupType): Result<ServerBackupInfo> =
        withContext(Dispatchers.IO) {
            _backupInProgress.value = true
            try {
                val config = configs.value[server.id]
                    ?: return@withContext Result.failure(IllegalStateException("Server config not found"))
                backupManager.createBackup(server, config.worldName, type).also { result ->
                    result.onSuccess {
                        LogBus.emit(com.mchost.data.LogLevel.INFO, "${type.label} backup saved (${it.file.name})")
                    }
                }
            } finally {
                _backupInProgress.value = false
            }
        }

    suspend fun restoreBackup(server: Server, backup: ServerBackupInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            _backupInProgress.value = true
            try {
                if (server.status == ServerStatus.RUNNING || server.status == ServerStatus.STARTING) {
                    return@withContext Result.failure(IllegalStateException("Stop the server before restoring"))
                }
                val config = configs.value[server.id]
                    ?: return@withContext Result.failure(IllegalStateException("Server config not found"))
                backupManager.restoreBackup(server, config.worldName, backup).also { result ->
                    result.onSuccess {
                        LogBus.emit(com.mchost.data.LogLevel.INFO, "Restored from ${backup.file.name}")
                    }
                }
            } finally {
                _backupInProgress.value = false
            }
        }

    suspend fun deleteBackup(backup: ServerBackupInfo): Result<Unit> = withContext(Dispatchers.IO) {
        backupManager.deleteBackup(backup)
    }

    suspend fun modrinthGameVersions(): List<String> = withContext(Dispatchers.IO) {
        modrinthClient.fetchGameVersions().getOrElse {
            listOf("1.21.4", "1.21.3", "1.21.1", "1.20.4", "1.20.1")
        }
    }

    suspend fun searchModrinth(
        query: String,
        kind: ModrinthContentKind,
        gameVersion: String,
        jarType: com.mchost.data.JarType,
    ): Result<ModrinthSearchResult> = withContext(Dispatchers.IO) {
        modrinthClient.search(query, kind, gameVersion, jarType)
    }

    suspend fun modrinthProjectVersions(
        projectId: String,
        kind: ModrinthContentKind,
        gameVersion: String,
        jarType: com.mchost.data.JarType,
    ): Result<List<ModrinthVersion>> = withContext(Dispatchers.IO) {
        modrinthClient.getProjectVersions(projectId, kind, gameVersion, jarType)
    }

    suspend fun installModrinthOnServer(
        server: Server,
        install: PendingModrinthInstall,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val serverDir = storage.resolveServerDir(server.id, server.name)
            val config = configs.value[server.id]
            val subdir = when (install.kind) {
                ModrinthContentKind.PLUGINS -> "plugins"
                ModrinthContentKind.MODS -> "mods"
                ModrinthContentKind.DATAPACKS -> "${config?.worldName ?: server.worldName}/datapacks"
            }
            val dest = File(serverDir, subdir).also { it.mkdirs() }
            val file = File(dest, install.filename)
            modrinthClient.downloadVersion(install.versionId, file).getOrThrow()
            LogBus.emit(
                com.mchost.data.LogLevel.INFO,
                "Installed ${install.projectTitle} → $subdir/${install.filename}",
            )
        }
    }

    private suspend fun installPendingModrinth(
        serverDir: File,
        worldName: String,
        installs: List<PendingModrinthInstall>,
    ) {
        installs.forEach { install ->
            val subdir = when (install.kind) {
                ModrinthContentKind.PLUGINS -> "plugins"
                ModrinthContentKind.MODS -> "mods"
                ModrinthContentKind.DATAPACKS -> "$worldName/datapacks"
            }
            val dest = File(serverDir, subdir).also { it.mkdirs() }
            modrinthClient.downloadVersion(install.versionId, File(dest, install.filename))
                .onSuccess {
                    LogBus.emit(
                        com.mchost.data.LogLevel.INFO,
                        "Downloaded ${install.projectTitle} from Modrinth",
                    )
                }
                .onFailure { e ->
                    LogBus.emit(
                        com.mchost.data.LogLevel.WARN,
                        "Modrinth download failed (${install.projectTitle}): ${e.message}",
                    )
                }
        }
    }
}
