package com.mchost.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.ServerStatus
import com.mchost.ui.components.CloudMcBrandRow
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcConfirmDialog
import com.mchost.ui.components.CloudMcOutlinedButton
import com.mchost.ui.components.CloudMcPageHeader
import com.mchost.ui.components.CloudMcPrimaryButton
import com.mchost.ui.components.CloudMcRadioRow
import com.mchost.ui.components.CloudMcSectionTitle
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.BorderSubtle
import com.mchost.ui.theme.ContainerRaised
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.JetBrainsMono
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.util.BackupType
import com.mchost.util.ServerBackupInfo
import com.mchost.viewmodel.ExternalBackupPreview
import com.mchost.viewmodel.MCHostViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(viewModel: MCHostViewModel) {
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val config by viewModel.activeConfig.collectAsStateWithLifecycle()
    val backingUp by viewModel.backupInProgress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var backups by remember { mutableStateOf<List<ServerBackupInfo>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoreTarget by remember { mutableStateOf<ServerBackupInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<ServerBackupInfo?>(null) }
    var externalPreview by remember { mutableStateOf<ExternalBackupPreview?>(null) }
    var externalType by remember { mutableStateOf(BackupType.FULL) }
    var saveCopyAfterRestore by remember { mutableStateOf(true) }

    val activeServer = server
    val activeConfig = config
    val serverRunning = activeServer?.status == ServerStatus.RUNNING ||
        activeServer?.status == ServerStatus.STARTING

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null || activeServer == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            message = null
            viewModel.prepareExternalBackup(uri)
                .onSuccess { preview ->
                    externalPreview = preview
                    externalType = preview.detectedType
                    saveCopyAfterRestore = true
                }
                .onFailure { e -> error = e.message ?: "Could not read zip file" }
        }
    }

    LaunchedEffect(activeServer?.id, refreshKey) {
        backups = if (activeServer != null) viewModel.listBackups(activeServer) else emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        CloudMcBrandRow()
        Spacer(Modifier.height(12.dp))
        CloudMcPageHeader(
            title = "Backup",
            subtitle = "Save, restore, and import server zip files",
        )

        if (activeServer == null || activeConfig == null) {
            Spacer(Modifier.height(24.dp))
            CloudMcCard {
                Text("Select a server from Home to manage backups.", color = TextSecondary)
            }
            return
        }

        if (serverRunning) {
            Spacer(Modifier.height(12.dp))
            Text("Stop the server before restoring a backup.", color = WarnAmber)
        }

        Spacer(Modifier.height(16.dp))

        CloudMcCard(accentStripe = true) {
            CloudMcSectionTitle(
                title = "Restore from zip",
                subtitle = "Pick a backup zip from Downloads, Drive, or any folder",
            )
            Spacer(Modifier.height(12.dp))
            CloudMcPrimaryButton(
                text = "Choose zip file",
                onClick = {
                    zipPicker.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !backingUp,
            )
            Text(
                "Works with saved backups here or other server/world zip archives.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        CloudMcSectionTitle(
            title = "Create backup",
            subtitle = "${activeServer.name} • world: ${activeConfig.worldName}",
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudMcOutlinedButton(
                text = "World",
                onClick = {
                    scope.launch {
                        error = null
                        message = null
                        viewModel.createBackup(activeServer, BackupType.WORLD)
                            .onSuccess {
                                message = "World backup saved"
                                refreshKey++
                            }
                            .onFailure { e -> error = e.message ?: "Backup failed" }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
            )
            CloudMcOutlinedButton(
                text = "Full server",
                onClick = {
                    scope.launch {
                        error = null
                        message = null
                        viewModel.createBackup(activeServer, BackupType.FULL)
                            .onSuccess {
                                message = "Full backup saved"
                                refreshKey++
                            }
                            .onFailure { e -> error = e.message ?: "Backup failed" }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
            )
        }
        Text(
            "World = ${activeConfig.worldName}/ only • Full = plugins, mods, configs + world",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (backingUp) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.padding(end = 8.dp))
                Text("Working on backup…", color = TextSecondary)
            }
        }

        message?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 8.dp)) }
        error?.let { Text(it, color = ErrorRed, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(20.dp))
        CloudMcSectionTitle(title = "Saved backups")
        Spacer(Modifier.height(12.dp))

        if (backups.isEmpty()) {
            CloudMcCard {
                Text("No backups yet — create one above or restore from a zip file.", color = TextSecondary)
            }
        } else {
            CloudMcCard {
                backups.forEachIndexed { index, backup ->
                    BackupRow(
                        backup = backup,
                        onShare = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                backup.file,
                            )
                            context.startActivity(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }.let { Intent.createChooser(it, "Export backup") },
                            )
                        },
                        onRestore = { restoreTarget = backup },
                        onDelete = { deleteTarget = backup },
                    )
                    if (index < backups.lastIndex) {
                        HorizontalDivider(color = BorderSubtle)
                    }
                }
            }
        }
    }

    externalPreview?.let { preview ->
        ExternalRestoreDialog(
            preview = preview,
            selectedType = externalType,
            onTypeChange = { externalType = it },
            saveCopy = saveCopyAfterRestore,
            onSaveCopyChange = { saveCopyAfterRestore = it },
            serverRunning = serverRunning,
            backingUp = backingUp,
            onConfirm = {
                val srv = activeServer ?: return@ExternalRestoreDialog
                scope.launch {
                    viewModel.restoreExternalBackup(srv, preview, externalType, saveCopyAfterRestore)
                        .onSuccess {
                            message = "Restored from ${preview.displayName}"
                            refreshKey++
                            error = null
                        }
                        .onFailure { e -> error = e.message ?: "Restore failed" }
                    externalPreview = null
                }
            },
            onDismiss = {
                preview.tempFile.delete()
                externalPreview = null
            },
        )
    }

    restoreTarget?.let { backup ->
        val serverForRestore = activeServer ?: return@let
        CloudMcConfirmDialog(
            title = "Restore backup?",
            message = "This will replace ${if (backup.type == BackupType.WORLD) "the world folder" else "server files"} " +
                "with \"${backup.file.name}\". Current data may be overwritten.",
            confirmText = "Restore",
            onDismiss = { restoreTarget = null },
            onConfirm = {
                scope.launch {
                    viewModel.restoreBackup(serverForRestore, backup)
                        .onSuccess {
                            message = "Restore complete"
                            refreshKey++
                            error = null
                        }
                        .onFailure { e -> error = e.message ?: "Restore failed" }
                    restoreTarget = null
                }
            },
            confirmEnabled = !serverRunning && !backingUp,
        )
    }

    deleteTarget?.let { backup ->
        CloudMcConfirmDialog(
            title = "Delete backup?",
            message = "Remove ${backup.file.name}? This cannot be undone.",
            confirmText = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    viewModel.deleteBackup(backup)
                        .onSuccess { refreshKey++ }
                        .onFailure { e -> error = e.message ?: "Delete failed" }
                    deleteTarget = null
                }
            },
            destructive = true,
        )
    }
}

@Composable
private fun ExternalRestoreDialog(
    preview: ExternalBackupPreview,
    selectedType: BackupType,
    onTypeChange: (BackupType) -> Unit,
    saveCopy: Boolean,
    onSaveCopyChange: (Boolean) -> Unit,
    serverRunning: Boolean,
    backingUp: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ContainerRaised,
        title = {
            Text("Restore from zip", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                Text(preview.displayName, color = TextPrimary, fontFamily = JetBrainsMono, maxLines = 2)
                Text(formatSize(preview.sizeBytes), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("BACKUP CONTAINS", color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono)
                CloudMcRadioRow(
                    label = "World only",
                    description = "Replaces the world folder",
                    selected = selectedType == BackupType.WORLD,
                    onSelect = { onTypeChange(BackupType.WORLD) },
                )
                CloudMcRadioRow(
                    label = "Full server",
                    description = "Replaces plugins, configs, and world",
                    selected = selectedType == BackupType.FULL,
                    onSelect = { onTypeChange(BackupType.FULL) },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Save copy to backups", color = TextPrimary)
                    Switch(
                        checked = saveCopy,
                        onCheckedChange = onSaveCopyChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.35f),
                        ),
                    )
                }
                if (serverRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text("Stop the server before restoring.", color = WarnAmber, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !serverRunning && !backingUp) {
                Text("Restore", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun BackupRow(
    backup: ServerBackupInfo,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember(backup.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(backup.type.label, fontWeight = FontWeight.SemiBold, color = Accent, fontFamily = JetBrainsMono)
            Text(backup.file.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1)
            Text(
                "${formatSize(backup.sizeBytes)} • ${formatDate(backup.createdAt)}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Accent)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Export") },
                    onClick = { showMenu = false; onShare() },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Restore") },
                    onClick = { showMenu = false; onRestore() },
                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(ms))
