package com.mchost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mchost.ui.components.CloudMcBrandRow
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcOutlinedButton
import com.mchost.ui.components.CloudMcPageHeader
import com.mchost.ui.components.CloudMcPrimaryButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.JetBrainsMono
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.viewmodel.AppOverlay
import com.mchost.viewmodel.MCHostViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.Server
import com.mchost.data.ServerConfig
import com.mchost.data.ServerStatus
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: MCHostViewModel) {
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val config by viewModel.activeConfig.collectAsStateWithLifecycle()
    val runtimeProgress by viewModel.serverManager.runtimeProgress.collectAsStateWithLifecycle()
    val lastError by viewModel.serverManager.lastError.collectAsStateWithLifecycle()
    val connection by viewModel.networkManager.connectionAddress.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
    ) {
        CloudMcBrandRow()

        Spacer(Modifier.height(4.dp))
        CloudMcPageHeader(
            title = "Home",
            subtitle = "Manage your Minecraft server",
        )

        Spacer(Modifier.height(16.dp))

        runtimeProgress?.let { msg ->
            BannerCard(text = msg, color = WarnAmber)
            Spacer(Modifier.height(8.dp))
        }

        lastError?.let { err ->
            BannerCard(text = err, color = ErrorRed)
            Spacer(Modifier.height(8.dp))
        }

        val activeServer = server
        val activeConfig = config
        if (activeServer == null || activeConfig == null) {
            EmptyHome(onCreate = { viewModel.showOverlay(AppOverlay.NEW_SERVER) })
        } else {
            ActiveServerCard(
                server = activeServer,
                config = activeConfig,
                connection = connection,
                heapUsed = viewModel.serverManager.process.heapUsedMb,
                heapMax = viewModel.serverManager.process.heapMaxMb.coerceAtLeast(activeConfig.memoryMb),
                onToggle = { viewModel.toggleServer(activeServer, activeConfig) },
                onFiles = { viewModel.showOverlay(AppOverlay.FILES) },
                onSettings = { viewModel.showOverlay(AppOverlay.SETTINGS) },
                onDelete = { viewModel.deleteServer(activeServer) },
                onServers = { viewModel.showOverlay(AppOverlay.SERVERS) },
            )
        }

        Spacer(Modifier.height(16.dp))
        PromoCard()
    }
}

@Composable
private fun EmptyHome(onCreate: () -> Unit) {
    CloudMcCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No servers yet", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Create your first Minecraft server", color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            CloudMcPrimaryButton(text = "Create server", onClick = onCreate, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ActiveServerCard(
    server: Server,
    config: ServerConfig,
    connection: String,
    heapUsed: Int,
    heapMax: Int,
    onToggle: () -> Unit,
    onFiles: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    onServers: () -> Unit,
) {
    val (statusColor, statusText) = when (server.status) {
        ServerStatus.RUNNING -> Accent to "ONLINE"
        ServerStatus.STARTING -> WarnAmber to "STARTING"
        ServerStatus.STOPPING -> WarnAmber to "STOPPING"
        ServerStatus.STOPPED -> ErrorRed to "OFFLINE"
    }

    CloudMcCard(accentStripe = server.status == ServerStatus.RUNNING) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onServers),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Servers", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("View all ›", color = Accent, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(statusText, fontWeight = FontWeight.Bold, color = statusColor, fontFamily = JetBrainsMono)
        }
        Spacer(Modifier.height(8.dp))
        Text(server.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        if (server.status == ServerStatus.RUNNING && server.startedAt != null) {
            UptimeText(startedAt = server.startedAt)
        }
        Text(
            "${config.jarType.name} • ${config.version} • ${server.players}/${config.maxPlayers} players",
            color = TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(connection.ifBlank { "—" }, fontFamily = JetBrainsMono, color = Accent)
        Spacer(Modifier.height(12.dp))
        Text("RAM ${heapUsed}MB / ${heapMax}MB", color = TextSecondary, fontFamily = JetBrainsMono)
        LinearProgressIndicator(
            progress = { if (heapMax > 0) heapUsed.toFloat() / heapMax else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = Accent,
            trackColor = TextSecondary.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(16.dp))
        CloudMcPrimaryButton(
            text = if (server.status == ServerStatus.RUNNING || server.status == ServerStatus.STARTING) "Stop" else "Start",
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudMcOutlinedButton("Files", onFiles, Modifier.weight(1f))
            CloudMcOutlinedButton("Settings", onSettings, Modifier.weight(1f))
            CloudMcOutlinedButton("Delete", onDelete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun UptimeText(startedAt: Long) {
    var uptime by remember(startedAt) { mutableStateOf(formatUptime(startedAt)) }
    LaunchedEffect(startedAt) {
        while (true) {
            uptime = formatUptime(startedAt)
            delay(1000)
        }
    }
    Text("Uptime: $uptime", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
}

private fun formatUptime(startedAt: Long): String {
    val secs = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun BannerCard(text: String, color: Color) {
    CloudMcCard {
        Text(text, color = color)
    }
}

@Composable
private fun PromoCard() {
    CloudMcCard {
        Text("Play with friends", fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Use Network tab to expose your server via UPnP or LocalXpose", color = TextSecondary)
    }
}
