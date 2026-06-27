package com.mchost.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
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
import com.mchost.ui.components.ModrinthBrowseSheet
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface as AppSurface
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Server files", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            server.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { startImport(ImportTarget.Current) },
                        enabled = !importing,
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import here", tint = Accent)
                    }
                    IconButton(onClick = { createKind = CreateKind.FOLDER; newName = ""; createError = null }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder", tint = Accent)
                    }
                    IconButton(onClick = { createKind = CreateKind.FILE; newName = ""; createError = null }) {
                        Icon(Icons.Default.Add, contentDescription = "New file", tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            PathBreadcrumb(path = path, onNavigate = { path = it })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Search files") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { startImport(ImportTarget.Plugins) },
                    enabled = !importing,
                ) {
                    Text("Import plugin")
                }
                OutlinedButton(
                    onClick = { startImport(ImportTarget.Mods) },
                    enabled = !importing,
                ) {
                    Text("Import mod")
                }
                OutlinedButton(
                    onClick = { startImport(ImportTarget.Datapacks) },
                    enabled = !importing,
                ) {
                    Text("Import datapack")
                }
                val jarType = config?.jarType ?: JarType.PAPER
                if (supportsModrinthPlugins(jarType)) {
                    OutlinedButton(onClick = { modrinthBrowse = ModrinthContentKind.PLUGINS }, enabled = !importing) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Plugin", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (supportsModrinthMods(jarType)) {
                    OutlinedButton(onClick = { modrinthBrowse = ModrinthContentKind.MODS }, enabled = !importing) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Mod", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                OutlinedButton(onClick = { modrinthBrowse = ModrinthContentKind.DATAPACKS }, enabled = !importing) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Pack", modifier = Modifier.padding(start = 4.dp))
                }
            }
            importMessage?.let {
                Text(
                    it,
                    color = Accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            actionError?.let {
                Text(
                    it,
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

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
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "This folder is empty.\nUse Import plugin/mod/datapack or + to add files.",
                        color = TextSecondary,
                    )
                }
                else -> {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        shape = RoundedCornerShape(12.dp),
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onNavigate("") }) {
            Text("root", color = Accent, fontFamily = FontFamily.Monospace)
        }
        segments.forEachIndexed { index, segment ->
            Text(" / ", color = TextSecondary)
            val subPath = segments.take(index + 1).joinToString("/")
            TextButton(onClick = { onNavigate(subPath) }) {
                Text(segment, color = Accent, fontFamily = FontFamily.Monospace)
            }
        }
    }
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
            ListItem(
                headlineContent = { Text("..") },
                supportingContent = { Text("Parent folder", color = TextSecondary) },
                leadingContent = {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Accent)
                },
                modifier = Modifier.clickableNoRipple(onGoUp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
        }
        entries.forEach { entry ->
            val editable = !entry.isDirectory && ServerFileAccess.isEditable(entry.name, entry.size)
            ListItem(
                headlineContent = {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    if (entry.isDirectory) {
                        Text("Folder", color = TextSecondary)
                    } else {
                        Text(
                            buildString {
                                append(formatSize(entry.size))
                                if (editable) append(" · editable")
                            },
                            color = TextSecondary,
                        )
                    }
                },
                leadingContent = {
                    Icon(
                        when {
                            entry.isDirectory -> Icons.Default.Folder
                            editable -> Icons.Default.Description
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = if (entry.isDirectory || editable) Accent else TextSecondary,
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                    }
                },
                modifier = Modifier.clickableNoRipple { onOpen(entry) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
        }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { tryBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!readOnly && loadState?.content != null) {
                        IconButton(
                            onClick = { save() },
                            enabled = dirty && !saving,
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = if (dirty) Accent else TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface),
            )
        },
        bottomBar = {
            if (!readOnly && loadState?.content != null) {
                Surface(color = AppSurface, tonalElevation = 4.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        when {
                            saveError != null -> Text(saveError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                            dirty -> Text("Unsaved changes", color = Accent, style = MaterialTheme.typography.bodySmall)
                            else -> Text("Saved", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { save() },
                            enabled = dirty && !saving,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.size(8.dp))
                            Text(if (saving) "Saving…" else "Save file", color = Color.Black)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            loadState == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent)
            }
            loadState?.error != null && loadState?.content == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(loadState!!.error!!, color = ErrorRed)
            }
            else -> {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorBg),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (!readOnly) text = it },
                        readOnly = readOnly,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE8E8E8),
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        placeholder = {
                            Text("File contents…", color = TextSecondary, fontFamily = FontFamily.Monospace)
                        },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved edits in $fileName.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
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
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
