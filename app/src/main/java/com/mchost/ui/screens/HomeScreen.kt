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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.Server
import com.mchost.data.ServerConfig
import com.mchost.data.ServerStatus
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.viewmodel.AppOverlay
import com.mchost.viewmodel.MCHostViewModel
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
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.showOverlay(AppOverlay.SERVERS) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MCHost", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Servers ›", color = Accent)
        }

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
            )
        }

        Spacer(Modifier.height(16.dp))
        PromoCard()
    }
}

@Composable
private fun EmptyHome(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No servers yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Create your first Minecraft server", color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("CREATE SERVER", color = Color.Black)
            }
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
) {
    val (statusColor, statusText) = when (server.status) {
        ServerStatus.RUNNING -> Accent to "ONLINE"
        ServerStatus.STARTING -> WarnAmber to "STARTING"
        ServerStatus.STOPPING -> WarnAmber to "STOPPING"
        ServerStatus.STOPPED -> ErrorRed to "OFFLINE"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(statusText, fontWeight = FontWeight.Bold, color = statusColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(server.name, style = MaterialTheme.typography.titleLarge)
            if (server.status == ServerStatus.RUNNING && server.startedAt != null) {
                UptimeText(startedAt = server.startedAt)
            }
            Text(
                "${config.jarType.name} • ${config.version} • ${server.players}/${config.maxPlayers} players",
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(connection.ifBlank { "—" }, fontFamily = FontFamily.Monospace, color = Accent)
            Spacer(Modifier.height(12.dp))
            Text("RAM ${heapUsed}MB / ${heapMax}MB", color = TextSecondary)
            LinearProgressIndicator(
                progress = { if (heapMax > 0) heapUsed.toFloat() / heapMax else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                val running = server.status == ServerStatus.RUNNING || server.status == ServerStatus.STARTING
                Text(if (running) "STOP" else "START", color = Color.Black)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onFiles, modifier = Modifier.weight(1f)) { Text("Files") }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
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
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
        Text(text, modifier = Modifier.padding(12.dp), color = color)
    }
}

@Composable
private fun PromoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Play with friends", fontWeight = FontWeight.Bold)
            Text("Use Network tab to expose your server via UPnP or LocalXpose", color = TextSecondary)
        }
    }
}
