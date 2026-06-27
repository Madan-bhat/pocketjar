package com.mchost.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.ServerStatus
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.util.BackupType
import com.mchost.util.ServerBackupInfo
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

    val activeServer = server
    val activeConfig = config
    val serverRunning = activeServer?.status == ServerStatus.RUNNING ||
        activeServer?.status == ServerStatus.STARTING

    LaunchedEffect(activeServer?.id, refreshKey) {
        if (activeServer != null) {
            backups = viewModel.listBackups(activeServer)
        } else {
            backups = emptyList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        Text("Backup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Save your world and server files locally", color = TextSecondary)

        if (activeServer == null || activeConfig == null) {
            Spacer(Modifier.height(24.dp))
            Text("Select a server from Home to manage backups.", color = TextSecondary)
            return
        }

        val running = serverRunning
        if (running) {
            Spacer(Modifier.height(12.dp))
            Text("Stop the server before restoring a backup.", color = WarnAmber)
        }

        Spacer(Modifier.height(16.dp))
        Text(activeServer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("World: ${activeConfig.worldName}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        error = null
                        message = null
                        viewModel.createBackup(activeServer, BackupType.WORLD)
                            .onSuccess {
                                message = "World backup saved"
                                refreshKey++
                            }
                            .onFailure { error = it.message ?: "Backup failed" }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = Color.Black)
                Text(" World", color = Color.Black, modifier = Modifier.padding(start = 4.dp))
            }
            Button(
                onClick = {
                    scope.launch {
                        error = null
                        message = null
                        viewModel.createBackup(activeServer, BackupType.FULL)
                            .onSuccess {
                                message = "Full backup saved"
                                refreshKey++
                            }
                            .onFailure { error = it.message ?: "Backup failed" }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !backingUp,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = Color.Black)
                Text(" Full", color = Color.Black, modifier = Modifier.padding(start = 4.dp))
            }
        }

        Text(
            "World = ${activeConfig.worldName}/ only • Full = plugins, mods, configs + world (no logs)",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (backingUp) {
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.padding(end = 8.dp))
                Text("Creating backup…")
            }
        }

        message?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 8.dp)) }
        error?.let { Text(it, color = ErrorRed, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(16.dp))
        Text("Saved backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        if (backups.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("No backups yet", color = TextSecondary)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(backups, key = { it.id }) { backup ->
                    BackupCard(
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
                }
            }
        }
    }

    restoreTarget?.let { backup ->
        val serverForRestore = activeServer ?: return@let
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This will replace ${if (backup.type == BackupType.WORLD) "the world folder" else "server files"} " +
                        "with \"${backup.file.name}\". Current data may be overwritten.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.restoreBackup(serverForRestore, backup)
                                .onSuccess {
                                    message = "Restore complete"
                                    refreshKey++
                                    error = null
                                }
                                .onFailure { error = it.message ?: "Restore failed" }
                            restoreTarget = null
                        }
                    },
                    enabled = !serverRunning && !backingUp,
                ) { Text("Restore", color = if (serverRunning) TextSecondary else Accent) }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { backup ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete backup?") },
            text = { Text("Remove ${backup.file.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteBackup(backup)
                                .onSuccess { refreshKey++ }
                                .onFailure { error = it.message ?: "Delete failed" }
                            deleteTarget = null
                        }
                    },
                ) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupCard(
    backup: ServerBackupInfo,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(backup.type.label, fontWeight = FontWeight.SemiBold, color = Accent)
                Text(formatSize(backup.sizeBytes), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(backup.file.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(formatDate(backup.createdAt), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text(" Export", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text(" Restore", modifier = Modifier.padding(start = 4.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                }
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
