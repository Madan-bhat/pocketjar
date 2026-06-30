package com.mchost.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mchost.ui.theme.JetBrainsMono
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.JarType
import com.mchost.data.ServerConfig
import com.mchost.network.modrinth.ModrinthContentKind
import com.mchost.network.modrinth.supportsModrinthMods
import com.mchost.network.modrinth.supportsModrinthPlugins
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcOutlinedButton
import com.mchost.ui.components.CloudMcPageHeader
import com.mchost.ui.components.CloudMcPrimaryButton
import com.mchost.ui.components.CloudMcSectionTitle
import com.mchost.ui.components.CloudMcTopBar
import com.mchost.ui.components.ModrinthBrowseSheet
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.util.FileImporter
import com.mchost.viewmodel.MCHostViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MCHostViewModel, onDismiss: () -> Unit) {
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val config by viewModel.activeConfig.collectAsStateWithLifecycle()
    if (server == null || config == null) {
        Column(Modifier.background(Background).padding(16.dp)) {
            Text("No active server", color = TextSecondary)
        }
        return
    }

    var draft by remember(config) { mutableStateOf(config!!) }
    var gameVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var versionExpanded by remember { mutableStateOf(false) }
    var modrinthBrowse by remember { mutableStateOf<ModrinthContentKind?>(null) }
    var installing by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    val activeServer = server!!
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        gameVersions = viewModel.modrinthGameVersions()
    }

    val customJarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importCustomJar(activeServer, uri)
            val name = FileImporter.queryDisplayName(context, uri)
            if (!name.isNullOrBlank()) {
                draft = draft.copy(jarType = JarType.CUSTOM, customJarName = name)
            }
        }
    }

    modrinthBrowse?.let { kind ->
        ModrinthBrowseSheet(
            viewModel = viewModel,
            kind = kind,
            gameVersion = draft.version,
            jarType = draft.jarType,
            onDismiss = { modrinthBrowse = null },
            onQueued = { pending ->
                installing = true
                installMessage = null
                scope.launch {
                    viewModel.installModrinthOnServer(activeServer, pending)
                        .onSuccess { installMessage = "Installed ${pending.projectTitle}" }
                        .onFailure { installMessage = it.message ?: "Install failed" }
                    installing = false
                    modrinthBrowse = null
                }
            },
        )
        return
    }

    Scaffold(
        containerColor = Background,
        topBar = { CloudMcTopBar(onClose = onDismiss) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            CloudMcPageHeader(
                title = "Settings",
                subtitle = activeServer.name,
            )

            if (installing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), color = Accent)
                    Text("Installing from Modrinth…", color = TextSecondary)
                }
            }
            installMessage?.let { Text(it, color = Accent, modifier = Modifier.padding(bottom = 8.dp)) }

            Section("General") {
                OutlinedTextField(
                    draft.serverName,
                    { draft = draft.copy(serverName = it) },
                    label = { Text("Server name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    draft.worldName,
                    { draft = draft.copy(worldName = it) },
                    label = { Text("World folder") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Root: ${viewModel.serverRootPath(activeServer)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = JetBrainsMono,
                )
            }

            Section("Software") {
                Text("Platform: ${draft.jarType.name}", color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                val versions = gameVersions.ifEmpty { listOf(draft.version) }
                ExposedDropdownMenuBox(expanded = versionExpanded, onExpandedChange = { versionExpanded = it }) {
                    OutlinedTextField(
                        draft.version,
                        {},
                        readOnly = true,
                        label = { Text("Minecraft version") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(versionExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(versionExpanded, { versionExpanded = false }) {
                        versions.forEach { v ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { draft = draft.copy(version = v); versionExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    draft.customJarName,
                    { draft = draft.copy(customJarName = it) },
                    label = { Text("Jar filename") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                CloudMcOutlinedButton(
                    text = "Import / replace server jar",
                    onClick = { customJarPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Addons") {
                Text("Install plugins, mods, or datapacks from Modrinth or your files.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (supportsModrinthPlugins(draft.jarType)) {
                    AddonInstallRow("Plugins", "plugins/") { modrinthBrowse = ModrinthContentKind.PLUGINS }
                }
                if (supportsModrinthMods(draft.jarType)) {
                    Spacer(Modifier.height(8.dp))
                    AddonInstallRow("Mods", "mods/") { modrinthBrowse = ModrinthContentKind.MODS }
                }
                Spacer(Modifier.height(8.dp))
                AddonInstallRow("Datapacks", "${draft.worldName}/datapacks/") {
                    modrinthBrowse = ModrinthContentKind.DATAPACKS
                }
            }

            Section("Performance") {
                Text("RAM: ${draft.memoryMb}MB", color = TextPrimary, fontFamily = JetBrainsMono)
                Slider(
                    draft.memoryMb.toFloat(),
                    { draft = draft.copy(memoryMb = it.toInt()) },
                    valueRange = 512f..viewModel.maxDeviceMemoryMb().toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
                Text("View distance: ${draft.viewDistance}", color = TextPrimary)
                Slider(
                    draft.viewDistance.toFloat(),
                    { draft = draft.copy(viewDistance = it.toInt()) },
                    valueRange = 4f..16f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
                Text("Simulation distance: ${draft.simulationDistance}", color = TextPrimary)
                Slider(
                    draft.simulationDistance.toFloat(),
                    { draft = draft.copy(simulationDistance = it.toInt()) },
                    valueRange = 4f..16f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    draft.serverPort.toString(),
                    { draft = draft.copy(serverPort = it.toIntOrNull() ?: draft.serverPort) },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Section("Gameplay") {
                ToggleRow("PvP", draft.pvp) { draft = draft.copy(pvp = it) }
                ToggleRow("Keep inventory", draft.keepInventory) { draft = draft.copy(keepInventory = it) }
                ToggleRow("Whitelist", draft.whitelist) { draft = draft.copy(whitelist = it) }
                ToggleRow("Online mode", draft.onlineMode) { draft = draft.copy(onlineMode = it) }
                ToggleRow("EULA accepted", draft.eulaAccepted) { draft = draft.copy(eulaAccepted = it) }
            }

            Section("Danger zone") {
                CloudMcOutlinedButton(
                    text = "Delete server",
                    onClick = { viewModel.deleteServer(activeServer); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            CloudMcPrimaryButton(
                text = "Save changes",
                onClick = {
                    viewModel.updateConfig(activeServer.id, draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddonInstallRow(title: String, path: String, onModrinth: () -> Unit) {
    CloudMcCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(path, color = TextSecondary, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
            }
            TextButton(onClick = onModrinth) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Accent)
                Text(" Modrinth", color = Accent)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    CloudMcSectionTitle(title)
    Spacer(Modifier.height(12.dp))
    CloudMcCard(content = content)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = TextPrimary)
        Switch(
            checked,
            onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.35f)),
        )
    }
}
