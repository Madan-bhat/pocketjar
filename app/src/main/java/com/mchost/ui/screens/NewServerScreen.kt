package com.mchost.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.JarType
import com.mchost.data.ServerConfig
import com.mchost.network.modrinth.ModrinthContentKind
import com.mchost.network.modrinth.PendingModrinthInstall
import com.mchost.network.modrinth.supportsModrinthMods
import com.mchost.network.modrinth.supportsModrinthPlugins
import com.mchost.ui.components.ModrinthBrowseSheet
import com.mchost.ui.components.PendingModrinthList
import com.mchost.ui.components.StepProgressIndicator
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary
import com.mchost.util.FileImporter
import com.mchost.viewmodel.CreateServerExtras
import com.mchost.viewmodel.MCHostViewModel

private val STEP_LABELS = listOf("Basics", "Software", "Addons", "Performance", "Launch")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewServerScreen(viewModel: MCHostViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var serverName by remember { mutableStateOf("My Server") }
    var worldName by remember { mutableStateOf("world") }
    var jarType by remember { mutableStateOf(JarType.PAPER) }
    var version by remember { mutableStateOf("1.21.1") }
    var customJarName by remember { mutableStateOf("server-custom.jar") }
    var memoryMb by remember { mutableIntStateOf(1024) }
    var viewDistance by remember { mutableIntStateOf(10) }
    var simulationDistance by remember { mutableIntStateOf(10) }
    var port by remember { mutableIntStateOf(25565) }
    var eula by remember { mutableStateOf(false) }
    var jarExpanded by remember { mutableStateOf(false) }
    var versionExpanded by remember { mutableStateOf(false) }
    var customJarUri by remember { mutableStateOf<Uri?>(null) }
    var customJarLabel by remember { mutableStateOf<String?>(null) }
    var pluginUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var modUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var datapackUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var modrinthPending by remember { mutableStateOf<List<PendingModrinthInstall>>(emptyList()) }
    var modrinthBrowse by remember { mutableStateOf<ModrinthContentKind?>(null) }
    var gameVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    val creating by viewModel.creatingServer.collectAsStateWithLifecycle()
    val maxMem = viewModel.maxDeviceMemoryMb()
    val context = LocalContext.current
    val effectiveJarType = if (customJarUri != null) JarType.CUSTOM else jarType

    LaunchedEffect(Unit) {
        gameVersions = viewModel.modrinthGameVersions()
        if (gameVersions.isNotEmpty() && version !in gameVersions) {
            version = gameVersions.first()
        }
    }

    val customJarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            customJarUri = uri
            val name = FileImporter.queryDisplayName(context, uri)
            customJarLabel = name
            if (!name.isNullOrBlank() && name.endsWith(".jar", ignoreCase = true)) {
                customJarName = name
            }
            jarType = JarType.CUSTOM
        }
    }
    val pluginPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pluginUris = pluginUris + uris
    }
    val modPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        modUris = modUris + uris
    }
    val datapackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        datapackUris = datapackUris + uris
    }

    val jarStepValid = when {
        customJarUri != null -> true
        jarType == JarType.CUSTOM -> false
        else -> !version.startsWith("26")
    }

    modrinthBrowse?.let { kind ->
        ModrinthBrowseSheet(
            viewModel = viewModel,
            kind = kind,
            gameVersion = version,
            jarType = effectiveJarType,
            onDismiss = { modrinthBrowse = null },
            onQueued = { modrinthPending = modrinthPending + it },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Create server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        StepProgressIndicator(step, STEP_LABELS)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> BasicsStep(serverName, { serverName = it }, worldName, { worldName = it })
                1 -> SoftwareStep(
                    jarType = jarType,
                    onJarType = { jarType = it; if (it != JarType.CUSTOM) { customJarUri = null; customJarLabel = null } },
                    jarExpanded = jarExpanded,
                    onJarExpanded = { jarExpanded = it },
                    version = version,
                    onVersion = { version = it },
                    versionExpanded = versionExpanded,
                    onVersionExpanded = { versionExpanded = it },
                    gameVersions = gameVersions,
                    customJarLabel = customJarLabel,
                    customJarName = customJarName,
                    onCustomJarName = { customJarName = it },
                    onPickJar = { customJarPicker.launch(arrayOf("*/*")) },
                )
                2 -> AddonsStep(
                    jarType = effectiveJarType,
                    worldName = worldName,
                    pluginCount = pluginUris.size,
                    modCount = modUris.size,
                    datapackCount = datapackUris.size,
                    modrinthPending = modrinthPending,
                    onRemoveModrinth = { modrinthPending = modrinthPending - it },
                    onImportPlugins = { pluginPicker.launch(arrayOf("*/*")) },
                    onImportMods = { modPicker.launch(arrayOf("*/*")) },
                    onImportDatapacks = {
                        datapackPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                    },
                    onBrowseModrinth = { modrinthBrowse = it },
                )
                3 -> PerformanceStep(
                    memoryMb = memoryMb,
                    onMemory = { memoryMb = it },
                    maxMem = maxMem,
                    viewDistance = viewDistance,
                    onViewDistance = { viewDistance = it },
                    simulationDistance = simulationDistance,
                    onSimulationDistance = { simulationDistance = it },
                )
                4 -> LaunchStep(port, { port = it.toIntOrNull() ?: port }, eula, { eula = it })
            }
        }

        if (creating) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Accent)
                Text("Creating server…", modifier = Modifier.padding(start = 12.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (step > 0) step-- else onDismiss() }, enabled = !creating) {
                Text(if (step > 0) "Back" else "Cancel")
            }
            if (step < 4) {
                Button(
                    onClick = { step++ },
                    enabled = !creating && (step != 1 || jarStepValid),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Next", color = Color.Black) }
            } else {
                val useCustom = jarType == JarType.CUSTOM || customJarUri != null
                Button(
                    onClick = {
                        val config = ServerConfig(
                            serverName = serverName,
                            worldName = worldName,
                            serverPort = port,
                            memoryMb = memoryMb,
                            version = version,
                            jarType = if (useCustom) JarType.CUSTOM else jarType,
                            customJarName = customJarName,
                            viewDistance = viewDistance,
                            simulationDistance = simulationDistance,
                            eulaAccepted = eula,
                        )
                        viewModel.createServer(
                            config,
                            CreateServerExtras(
                                customJarUri = customJarUri,
                                pluginUris = pluginUris,
                                modUris = modUris,
                                datapackUris = datapackUris,
                                modrinthInstalls = modrinthPending,
                            ),
                        )
                        onDismiss()
                    },
                    enabled = eula && serverName.isNotBlank() && !creating && (!useCustom || customJarUri != null),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Create server", color = Color.Black) }
            }
        }
    }
}

@Composable
private fun BasicsStep(
    serverName: String,
    onServerName: (String) -> Unit,
    worldName: String,
    onWorldName: (String) -> Unit,
) {
    Text("Basics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(serverName, onServerName, label = { Text("Server name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(worldName, onWorldName, label = { Text("World folder name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
    Text("Saved under files/MCHost/servers/", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftwareStep(
    jarType: JarType,
    onJarType: (JarType) -> Unit,
    jarExpanded: Boolean,
    onJarExpanded: (Boolean) -> Unit,
    version: String,
    onVersion: (String) -> Unit,
    versionExpanded: Boolean,
    onVersionExpanded: (Boolean) -> Unit,
    gameVersions: List<String>,
    customJarLabel: String?,
    customJarName: String,
    onCustomJarName: (String) -> Unit,
    onPickJar: () -> Unit,
) {
    Text("Server software", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    ExposedDropdownMenuBox(expanded = jarExpanded, onExpandedChange = onJarExpanded) {
        OutlinedTextField(
            jarType.name,
            {},
            readOnly = true,
            label = { Text("Platform") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(jarExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(jarExpanded, { onJarExpanded(false) }) {
            JarType.entries.filter { it != JarType.CUSTOM }.forEach { type ->
                DropdownMenuItem(text = { Text(type.name) }, onClick = { onJarType(type); onJarExpanded(false) })
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    if (jarType != JarType.CUSTOM && customJarLabel == null) {
        val versions = gameVersions.ifEmpty { listOf("1.21.1", "1.20.4") }
        ExposedDropdownMenuBox(expanded = versionExpanded, onExpandedChange = onVersionExpanded) {
            OutlinedTextField(
                version,
                {},
                readOnly = true,
                label = { Text("Minecraft version") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(versionExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(versionExpanded, { onVersionExpanded(false) }) {
                versions.forEach { v ->
                    DropdownMenuItem(text = { Text(v) }, onClick = { onVersion(v); onVersionExpanded(false) })
                }
            }
        }
        if (version.startsWith("26")) {
            Spacer(Modifier.height(8.dp))
            Text("Requires Java 25+ — bundled JRE is 21", color = ErrorRed)
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Or import your own server jar", color = TextSecondary)
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = onPickJar, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Text(
            if (customJarLabel != null) " $customJarLabel" else " Choose .jar file",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (customJarLabel != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(customJarName, onCustomJarName, label = { Text("Filename in server folder") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AddonsStep(
    jarType: JarType,
    worldName: String,
    pluginCount: Int,
    modCount: Int,
    datapackCount: Int,
    modrinthPending: List<PendingModrinthInstall>,
    onRemoveModrinth: (PendingModrinthInstall) -> Unit,
    onImportPlugins: () -> Unit,
    onImportMods: () -> Unit,
    onImportDatapacks: () -> Unit,
    onBrowseModrinth: (ModrinthContentKind) -> Unit,
) {
    Text("Plugins, mods & datapacks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text("Optional — add now or later from Files / Settings", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))

    if (supportsModrinthPlugins(jarType)) {
        AddonCard(
            title = "Plugins",
            subtitle = "→ plugins/ • $pluginCount imported",
            onImport = onImportPlugins,
            onModrinth = { onBrowseModrinth(ModrinthContentKind.PLUGINS) },
        )
    }
    if (supportsModrinthMods(jarType)) {
        Spacer(Modifier.height(8.dp))
        AddonCard(
            title = "Mods",
            subtitle = "→ mods/ • $modCount imported",
            onImport = onImportMods,
            onModrinth = { onBrowseModrinth(ModrinthContentKind.MODS) },
        )
    }
    Spacer(Modifier.height(8.dp))
    AddonCard(
        title = "Datapacks",
        subtitle = "→ $worldName/datapacks/ • $datapackCount imported",
        onImport = onImportDatapacks,
        onModrinth = { onBrowseModrinth(ModrinthContentKind.DATAPACKS) },
    )
    PendingModrinthList(modrinthPending, onRemoveModrinth)
}

@Composable
private fun AddonCard(
    title: String,
    subtitle: String,
    onImport: () -> Unit,
    onModrinth: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(" Import", modifier = Modifier.padding(start = 4.dp))
                }
                Button(
                    onClick = onModrinth,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black)
                    Text(" Modrinth", color = Color.Black, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PerformanceStep(
    memoryMb: Int,
    onMemory: (Int) -> Unit,
    maxMem: Int,
    viewDistance: Int,
    onViewDistance: (Int) -> Unit,
    simulationDistance: Int,
    onSimulationDistance: (Int) -> Unit,
) {
    Text("Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text("RAM: ${memoryMb}MB (max ~${maxMem}MB)")
    Slider(memoryMb.toFloat(), { onMemory(it.toInt()) }, valueRange = 512f..maxMem.toFloat())
    Text("View distance: $viewDistance")
    Slider(viewDistance.toFloat(), { onViewDistance(it.toInt()) }, valueRange = 4f..16f)
    Text("Simulation distance: $simulationDistance")
    Slider(simulationDistance.toFloat(), { onSimulationDistance(it.toInt()) }, valueRange = 4f..16f)
}

@Composable
private fun LaunchStep(
    port: Int,
    onPort: (String) -> Unit,
    eula: Boolean,
    onEula: (Boolean) -> Unit,
) {
    Text("Launch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(port.toString(), onPort, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(eula, onEula)
        Text("I accept the Minecraft EULA", modifier = Modifier.padding(start = 8.dp))
    }
}
