package com.mchost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.mchost.ui.theme.JetBrainsMono
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.Server
import com.mchost.data.ServerStatus
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcPageHeader
import com.mchost.ui.components.CloudMcPrimaryButton
import com.mchost.ui.components.CloudMcTopBar
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.BorderSubtle
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.viewmodel.AppOverlay
import com.mchost.viewmodel.MCHostViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(viewModel: MCHostViewModel, onDismiss: () -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val runtimeProgress by viewModel.serverManager.runtimeProgress.collectAsStateWithLifecycle()
    val header = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()).format(Date())

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
                title = "Servers",
                subtitle = header,
            )
            runtimeProgress?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = WarnAmber)
            }
            Spacer(Modifier.height(16.dp))

            if (servers.isEmpty()) {
                CloudMcCard {
                    Text("No servers yet", color = TextSecondary)
                }
            } else {
                CloudMcCard {
                    LazyColumn {
                        items(servers, key = { it.id }) { server ->
                            val config = configs[server.id]
                            ServerListRow(
                                server = server,
                                jarLabel = config?.let { "${it.jarType.name} ${it.version}" } ?: "—",
                                onSelect = {
                                    viewModel.setActiveServer(server.id)
                                    onDismiss()
                                },
                                onToggle = {
                                    config?.let { viewModel.toggleServer(server, it) }
                                },
                            )
                            if (server != servers.last()) {
                                HorizontalDivider(color = BorderSubtle)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            CloudMcPrimaryButton(
                text = "New server",
                onClick = {
                    onDismiss()
                    viewModel.showOverlay(AppOverlay.NEW_SERVER)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServerListRow(
    server: Server,
    jarLabel: String,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    val color = when (server.status) {
        ServerStatus.RUNNING -> Accent
        ServerStatus.STARTING, ServerStatus.STOPPING -> WarnAmber
        ServerStatus.STOPPED -> ErrorRed
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(end = 12.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    server.status.name.take(3),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                )
            }
            Column {
                Text(server.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(jarLabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        CloudMcPrimaryButton(
            text = if (server.status == ServerStatus.RUNNING) "Stop" else "Start",
            onClick = onToggle,
        )
    }
}
