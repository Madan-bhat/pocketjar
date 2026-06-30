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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mchost.ui.theme.JetBrainsMono
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.JarType
import com.mchost.data.ServerConfig
import com.mchost.network.modrinth.ModrinthContentKind
import com.mchost.network.modrinth.PendingModrinthInstall
import com.mchost.network.modrinth.supportsModrinthMods
import com.mchost.network.modrinth.supportsModrinthPlugins
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcOutlinedButton
import com.mchost.ui.components.CloudMcPageHeader
import com.mchost.ui.components.CloudMcPrimaryButton
import com.mchost.ui.components.CloudMcSectionTitle
import com.mchost.ui.components.CloudMcTopBar
import com.mchost.ui.components.CloudMcWizardFooter
import com.mchost.ui.components.ModrinthBrowseSheet
import com.mchost.ui.components.PendingModrinthList
import com.mchost.ui.components.StepProgressIndicator
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.TextPrimary
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
    val context = androidx.compose.ui.platform.LocalContext.current
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

    val stepTitles = listOf(
        "Server basics" to "Name your server and world folder",
        "Server software" to "Choose platform and Minecraft version",
        "Plugins, mods & datapacks" to "Optional — add now or later from Files",
        "Resource allocation" to "Configure RAM and view distances",
        "Review & launch" to "Port, EULA, and create your server",
    )

    Scaffold(
        containerColor = Background,
        topBar = { CloudMcTopBar(onClose = onDismiss) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            CloudMcPageHeader(
                title = "New server",
                stepLabel = "STEP ${step + 1} OF ${STEP_LABELS.size}",
            )
            StepProgressIndicator(step, STEP_LABELS)

            CloudMcSectionTitle(
                title = stepTitles[step].first,
                subtitle = stepTitles[step].second,
            )
            Spacer(Modifier.height(12.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                CloudMcCard {
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
                        4 -> LaunchStep(port, { port = it.toIntOrNull() ?: port }, eula, { eula = it }, serverName, jarType, version, memoryMb)
                    }
                }
            }

            if (creating) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Accent)
                    Text("Creating server…", modifier = Modifier.padding(start = 12.dp), color = TextSecondary)
                }
            }

            CloudMcWizardFooter(
                backLabel = if (step > 0) "Back" else "Cancel",
                onBack = { if (step > 0) step-- else onDismiss() },
                primaryLabel = if (step < 4) "Continue" else "Create server",
                onPrimary = {
                    if (step < 4) {
                        step++
                    } else {
                        val useCustom = jarType == JarType.CUSTOM || customJarUri != null
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
                    }
                },
                primaryEnabled = when {
                    step == 1 -> jarStepValid
                    step == 4 -> {
                        val useCustom = jarType == JarType.CUSTOM || customJarUri != null
                        eula && serverName.isNotBlank() && (!useCustom || customJarUri != null)
                    }
                    else -> true
                } && !creating,
                loading = creating,
            )
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
    OutlinedTextField(
        serverName,
        onServerName,
        label = { Text("Server name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    if (serverName.isNotBlank()) {
        Text("✓ Name available", color = Accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        worldName,
        onWorldName,
        label = { Text("World folder name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Saved under files/servers/",
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = JetBrainsMono,
    )
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
    ExposedDropdownMenuBox(expanded = jarExpanded, onExpandedChange = onJarExpanded) {
        OutlinedTextField(
            jarType.name,
            {},
            readOnly = true,
            label = { Text("Platform") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(jarExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(12.dp),
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
    CloudMcOutlinedButton(
        text = if (customJarLabel != null) customJarLabel else "Choose .jar file",
        onClick = onPickJar,
        modifier = Modifier.fillMaxWidth(),
    )
    if (customJarLabel != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            customJarName,
            onCustomJarName,
            label = { Text("Filename in server folder") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
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
    if (supportsModrinthPlugins(jarType)) {
        AddonCard("Plugins", "→ plugins/ • $pluginCount imported", onImportPlugins) {
            onBrowseModrinth(ModrinthContentKind.PLUGINS)
        }
        Spacer(Modifier.height(8.dp))
    }
    if (supportsModrinthMods(jarType)) {
        AddonCard("Mods", "→ mods/ • $modCount imported", onImportMods) {
            onBrowseModrinth(ModrinthContentKind.MODS)
        }
        Spacer(Modifier.height(8.dp))
    }
    AddonCard("Datapacks", "→ $worldName/datapacks/ • $datapackCount imported", onImportDatapacks) {
        onBrowseModrinth(ModrinthContentKind.DATAPACKS)
    }
    PendingModrinthList(modrinthPending, onRemoveModrinth)
}

@Composable
private fun AddonCard(
    title: String,
    subtitle: String,
    onImport: () -> Unit,
    onModrinth: () -> Unit,
) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudMcOutlinedButton("Import", onImport, Modifier.weight(1f))
            CloudMcPrimaryButton("Modrinth", onModrinth, Modifier.weight(1f))
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
    Text("RAM ALLOCATION", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontFamily = JetBrainsMono)
    Text("${memoryMb} MB", style = MaterialTheme.typography.headlineMedium, color = Accent, fontWeight = FontWeight.Bold)
    Slider(
        memoryMb.toFloat(),
        { onMemory(it.toInt()) },
        valueRange = 512f..maxMem.toFloat(),
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("512 MB", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text("~${maxMem} MB", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    Text("View distance: $viewDistance", color = TextPrimary)
    Slider(
        viewDistance.toFloat(),
        { onViewDistance(it.toInt()) },
        valueRange = 4f..16f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
    )
    Text("Simulation distance: $simulationDistance", color = TextPrimary)
    Slider(
        simulationDistance.toFloat(),
        { onSimulationDistance(it.toInt()) },
        valueRange = 4f..16f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
    )
}

@Composable
private fun LaunchStep(
    port: Int,
    onPort: (String) -> Unit,
    eula: Boolean,
    onEula: (Boolean) -> Unit,
    serverName: String,
    jarType: JarType,
    version: String,
    memoryMb: Int,
) {
    Text("Configuration", fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(8.dp))
    ReviewRow("Server", serverName)
    ReviewRow("Software", jarType.name)
    ReviewRow("Version", version)
    ReviewRow("RAM", "${memoryMb} MB")
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        port.toString(),
        onPort,
        label = { Text("Port") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            eula,
            onEula,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.35f)),
        )
        Text("I accept the Minecraft EULA", modifier = Modifier.padding(start = 8.dp), color = TextPrimary)
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodySmall)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}
