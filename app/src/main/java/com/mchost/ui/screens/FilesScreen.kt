package com.mchost.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mchost.ui.theme.JetBrainsMono
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.JarType
import com.mchost.data.Server
import com.mchost.data.ServerFileEntry
import com.mchost.network.modrinth.ModrinthContentKind
import com.mchost.network.modrinth.supportsModrinthMods
import com.mchost.network.modrinth.supportsModrinthPlugins
import com.mchost.ui.components.CloudMcBackHeader
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcConfirmDialog
import com.mchost.ui.components.CloudMcPrimaryButton
import com.mchost.ui.components.CloudMcTopBar
import com.mchost.ui.components.ModrinthBrowseSheet
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.BorderSubtle
import com.mchost.ui.theme.ContainerRaised
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface as AppSurface
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.util.ServerFileAccess
import com.mchost.viewmodel.MCHostViewModel
import com.mchost.viewmodel.TextFileLoadResult
import kotlinx.coroutines.launch

private val EditorBg = Color(0xFF080808)

private sealed class FilesRoute {
    data object Browse : FilesRoute()
    data class Edit(val path: String) : FilesRoute()
}

private enum class CreateKind { FILE, FOLDER }

private sealed class ImportTarget {
    data object Plugins : ImportTarget()
    data object Mods : ImportTarget()
    data object Datapacks : ImportTarget()
    data object Current : ImportTarget()
}

@Composable
fun FilesScreen(viewModel: MCHostViewModel, onDismiss: () -> Unit) {
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    if (server == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Background)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No active server", color = TextSecondary)
        }
        return
    }

    var route by remember { mutableStateOf<FilesRoute>(FilesRoute.Browse) }
    when (val current = route) {
        is FilesRoute.Edit -> FileEditorScreen(
            viewModel = viewModel,
            server = server!!,
            relativePath = current.path,
            onBack = { route = FilesRoute.Browse },
        )
        FilesRoute.Browse -> FilesBrowser(
            viewModel = viewModel,
            server = server!!,
            onDismiss = onDismiss,
            onOpenFile = { route = FilesRoute.Edit(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesBrowser(
    viewModel: MCHostViewModel,
    server: Server,
    onDismiss: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val config by viewModel.activeConfig.collectAsStateWithLifecycle()
    var path by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<ServerFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }
    var newName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ServerFileEntry?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingImportTarget by remember { mutableStateOf<ImportTarget?>(null) }
    var modrinthBrowse by remember { mutableStateOf<ModrinthContentKind?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }

    modrinthBrowse?.let { kind ->
        ModrinthBrowseSheet(
            viewModel = viewModel,
            kind = kind,
            gameVersion = config?.version ?: "1.21.1",
            jarType = config?.jarType ?: JarType.PAPER,
            onDismiss = { modrinthBrowse = null },
            onQueued = { pending ->
                scope.launch {
                    importing = true
                    viewModel.installModrinthOnServer(server, pending)
                        .onSuccess {
                            importMessage = "Installed ${pending.projectTitle}"
                            path = when (kind) {
                                ModrinthContentKind.PLUGINS -> "plugins"
                                ModrinthContentKind.MODS -> "mods"
                                ModrinthContentKind.DATAPACKS -> viewModel.datapacksPath(server)
                            }
                            refreshKey++
                        }
                        .onFailure { actionError = it.message ?: "Install failed" }
                    importing = false
                    modrinthBrowse = null
                }
            },
        )
        return
    }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val target = pendingImportTarget ?: return@rememberLauncherForActivityResult
        pendingImportTarget = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            actionError = null
            importMessage = null
            val folder = when (target) {
                ImportTarget.Current -> path
                ImportTarget.Plugins -> "plugins"
                ImportTarget.Mods -> "mods"
                ImportTarget.Datapacks -> viewModel.datapacksPath(server)
            }
            val importFn = when (target) {
                ImportTarget.Datapacks -> viewModel.importFiles(
                    server, folder, uris, defaultExtension = "zip",
                )
                else -> viewModel.importJarFiles(server, folder, uris)
            }
            importFn
                .onSuccess { names ->
                    importMessage = "Imported ${names.joinToString(", ")}"
                    if (target != ImportTarget.Current) {
                        path = folder
                    }
                    refreshKey++
                }
                .onFailure { e ->
                    actionError = e.message ?: "Import failed"
                }
            importing = false
        }
    }

    fun startImport(target: ImportTarget) {
        pendingImportTarget = target
        val mimes = when (target) {
            ImportTarget.Datapacks -> arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
                "*/*",
            )
            else -> arrayOf("application/java-archive", "application/octet-stream", "*/*")
        }
        importPicker.launch(mimes)
    }

    LaunchedEffect(server.id, path, filter, refreshKey) {
        loading = true
        entries = viewModel.listFiles(server, path)
            .filter { it.name.contains(filter, ignoreCase = true) }
        loading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = Accent,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("New file") },
                        onClick = {
                            showFabMenu = false
                            createKind = CreateKind.FILE
                            newName = ""
                            createError = null
                        },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        onClick = {
                            showFabMenu = false
                            createKind = CreateKind.FOLDER
                            newName = ""
                            createError = null
                        },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Import here") },
                        onClick = {
                            showFabMenu = false
                            startImport(ImportTarget.Current)
                        },
                        enabled = !importing,
                        leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    )
                }
            }
        },
        topBar = {
            CloudMcTopBar(
                onClose = onDismiss,
                actions = {
                    Box {
                        IconButton(onClick = { showImportMenu = true }, enabled = !importing) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Accent)
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import plugin") },
                                onClick = { showImportMenu = false; startImport(ImportTarget.Plugins) },
                            )
                            DropdownMenuItem(
                                text = { Text("Import mod") },
                                onClick = { showImportMenu = false; startImport(ImportTarget.Mods) },
                            )
                            DropdownMenuItem(
                                text = { Text("Import datapack") },
                                onClick = { showImportMenu = false; startImport(ImportTarget.Datapacks) },
                            )
                            val jarType = config?.jarType ?: JarType.PAPER
                            if (supportsModrinthPlugins(jarType)) {
                                DropdownMenuItem(
                                    text = { Text("Browse plugins (Modrinth)") },
                                    onClick = { showImportMenu = false; modrinthBrowse = ModrinthContentKind.PLUGINS },
                                )
                            }
                            if (supportsModrinthMods(jarType)) {
                                DropdownMenuItem(
                                    text = { Text("Browse mods (Modrinth)") },
                                    onClick = { showImportMenu = false; modrinthBrowse = ModrinthContentKind.MODS },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Browse datapacks (Modrinth)") },
                                onClick = { showImportMenu = false; modrinthBrowse = ModrinthContentKind.DATAPACKS },
                            )
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Accent)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Files",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                server.name,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            PathBreadcrumb(path = path, onNavigate = { path = it })
            if (showSearch) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Search files", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
            importMessage?.let {
                Text(
                    it,
                    color = Accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            actionError?.let {
                Text(
                    it,
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            when {
                importing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text("Importing…", color = TextSecondary)
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                entries.isEmpty() && path.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        .background(ContainerRaised, RoundedCornerShape(14.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "This folder is empty.\nTap + to add files or folders.",
                        color = TextSecondary,
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .background(ContainerRaised, RoundedCornerShape(14.dp)),
                    ) {
                        LazyFileList(
                            path = path,
                            entries = entries,
                            onGoUp = {
                                path = path.substringBeforeLast('/', missingDelimiterValue = "").let {
                                    if (it == path) "" else it
                                }
                            },
                            onOpen = { entry ->
                                if (entry.isDirectory) {
                                    path = entry.path
                                } else if (ServerFileAccess.isEditable(entry.name, entry.size)) {
                                    onOpenFile(entry.path)
                                } else {
                                    actionError = "${entry.name} cannot be edited (binary or too large)"
                                }
                            },
                            onDelete = { deleteTarget = it },
                        )
                    }
                }
            }
        }
    }

    createKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text(if (kind == CreateKind.FILE) "New file" else "New folder") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; createError = null },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    createError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = when (kind) {
                            CreateKind.FILE -> viewModel.createTextFile(server, path, newName.trim())
                            CreateKind.FOLDER -> viewModel.createDirectory(server, path, newName.trim())
                        }
                        result.onSuccess { createdPath ->
                            createKind = null
                            refreshKey++
                            if (kind == CreateKind.FILE) onOpenFile(createdPath)
                        }.onFailure { e ->
                            createError = e.message
                        }
                    }
                }) { Text("Create", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { createKind = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.deleteEntry(server, target.path)
                            .onSuccess {
                                deleteTarget = null
                                refreshKey++
                            }
                            .onFailure { e ->
                                actionError = e.message
                                deleteTarget = null
                            }
                    }
                }) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PathBreadcrumb(path: String, onNavigate: (String) -> Unit) {
    val segments = if (path.isBlank()) emptyList() else path.split('/')
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .background(ContainerRaised, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BreadcrumbSegment(
            label = "server",
            icon = Icons.Default.Dns,
            isLast = segments.isEmpty(),
            onClick = { onNavigate("") },
        )
        segments.forEachIndexed { index, segment ->
            Text(
                " › ",
                color = TextSecondary,
                fontFamily = JetBrainsMono,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            val subPath = segments.take(index + 1).joinToString("/")
            BreadcrumbSegment(
                label = segment,
                icon = breadcrumbIcon(segment, index == segments.lastIndex),
                isLast = index == segments.lastIndex,
                onClick = { onNavigate(subPath) },
            )
        }
    }
}

@Composable
private fun BreadcrumbSegment(
    label: String,
    icon: ImageVector,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isLast) Accent else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            color = if (isLast) TextPrimary else TextSecondary,
            fontFamily = JetBrainsMono,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun breadcrumbIcon(segment: String, isLast: Boolean): ImageVector = when {
    segment.equals("plugins", ignoreCase = true) -> Icons.Default.Extension
    segment.equals("mods", ignoreCase = true) -> Icons.Default.Apps
    isLast -> Icons.Default.FolderOpen
    else -> Icons.Default.Folder
}

@Composable
private fun LazyFileList(
    path: String,
    entries: List<ServerFileEntry>,
    onGoUp: () -> Unit,
    onOpen: (ServerFileEntry) -> Unit,
    onDelete: (ServerFileEntry) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (path.isNotEmpty()) {
            FileListRow(
                name = "..",
                subtitle = "Parent folder",
                icon = Icons.Default.Folder,
                iconTint = TextSecondary,
                subtitleColor = TextSecondary,
                onClick = onGoUp,
            )
            HorizontalDivider(color = BorderSubtle)
        }
        entries.forEach { entry ->
            FileEntryRow(
                entry = entry,
                onOpen = onOpen,
                onDelete = onDelete,
            )
            HorizontalDivider(color = BorderSubtle)
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: ServerFileEntry,
    onOpen: (ServerFileEntry) -> Unit,
    onDelete: (ServerFileEntry) -> Unit,
) {
    val isJar = !entry.isDirectory && entry.name.endsWith(".jar", ignoreCase = true)
    val editable = !entry.isDirectory && ServerFileAccess.isEditable(entry.name, entry.size)
    var showMenu by remember(entry.path) { mutableStateOf(false) }

    FileListRow(
        name = entry.name,
        subtitle = when {
            entry.isDirectory -> "Folder"
            else -> buildString {
                append(formatSize(entry.size))
                append(" • ")
                append(formatRelativeTime(entry.modified))
                if (editable) append(" • editable")
            }
        },
        icon = when {
            entry.isDirectory -> Icons.Default.Folder
            isJar -> Icons.Default.Apps
            editable -> Icons.Default.Description
            else -> Icons.Default.InsertDriveFile
        },
        iconTint = when {
            isJar -> Accent
            entry.isDirectory -> TextPrimary
            else -> TextSecondary
        },
        subtitleColor = if (isJar) Accent else TextSecondary,
        onClick = { onOpen(entry) },
        trailing = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = if (isJar) Accent else TextSecondary,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete(entry)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun FileListRow(
    name: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    subtitleColor: Color = TextSecondary,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                name,
                color = TextPrimary,
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = subtitleColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
    viewModel: MCHostViewModel,
    server: Server,
    relativePath: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loadState by remember { mutableStateOf<TextFileLoadResult?>(null) }
    var text by remember { mutableStateOf("") }
    var savedText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val dirty = loadState?.content != null && text != savedText
    val readOnly = loadState?.readOnly == true
    val fileName = relativePath.substringAfterLast('/')

    LaunchedEffect(server.id, relativePath) {
        loadState = null
        text = ""
        savedText = ""
        val result = viewModel.loadTextFile(server, relativePath)
        loadState = result
        if (result.content != null) {
            text = result.content
            savedText = result.content
        }
    }

    fun tryBack() {
        if (dirty) showDiscardDialog = true else onBack()
    }

    fun save() {
        scope.launch {
            saving = true
            saveError = null
            viewModel.saveTextFile(server, relativePath, text)
                .onSuccess { savedText = text }
                .onFailure { e -> saveError = e.message ?: "Save failed" }
            saving = false
        }
    }

    BackHandler { tryBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloudMcBackHeader(title = fileName, onBack = { tryBack() }, modifier = Modifier.weight(1f))
            if (!readOnly && loadState?.content != null) {
                IconButton(
                    onClick = { save() },
                    enabled = dirty && !saving,
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = if (dirty) Accent else TextSecondary)
                }
            }
        }
        Text(
            relativePath,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontFamily = JetBrainsMono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
        )

        when {
            loadState == null -> Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent)
            }
            loadState?.error != null && loadState?.content == null -> Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(loadState!!.error!!, color = ErrorRed)
            }
            else -> {
                CloudMcCard(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (!readOnly) text = it },
                        readOnly = readOnly,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMono,
                            color = Color(0xFFE8E8E8),
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        placeholder = {
                            Text("File contents…", color = TextSecondary, fontFamily = JetBrainsMono)
                        },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }

        if (!readOnly && loadState?.content != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                when {
                    saveError != null -> Text(saveError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    dirty -> Text("Unsaved changes", color = Accent, style = MaterialTheme.typography.bodySmall)
                    else -> Text("Saved", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                CloudMcPrimaryButton(
                    text = if (saving) "Saving…" else "Save file",
                    onClick = { save() },
                    enabled = dirty && !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDiscardDialog) {
        CloudMcConfirmDialog(
            title = "Discard changes?",
            message = "You have unsaved edits in $fileName.",
            confirmText = "Discard",
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            destructive = true,
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(indication = null, interactionSource = interactionSource, onClick = onClick)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> {
        val kb = bytes / 1024.0
        if (kb == kb.toLong().toDouble()) "${kb.toLong()} KB" else String.format("%.1f KB", kb)
    }
    else -> {
        val mb = bytes / (1024.0 * 1024.0)
        if (mb == mb.toLong().toDouble()) "${mb.toLong()} MB" else String.format("%.1f MB", mb)
    }
}

private fun formatRelativeTime(epochMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
        else -> "${minutes / (7 * 24 * 60)}w ago"
    }
}
