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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.Server
import com.mchost.data.ServerStatus
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.viewmodel.AppOverlay
import com.mchost.viewmodel.MCHostViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServersScreen(viewModel: MCHostViewModel, onDismiss: () -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val runtimeProgress by viewModel.serverManager.runtimeProgress.collectAsStateWithLifecycle()
    val header = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()).format(Date())

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
            Text("Servers", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Text(header, color = TextSecondary)
        runtimeProgress?.let { Text(it, color = WarnAmber) }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(servers) { server ->
                val config = configs[server.id]
                ServerListCard(
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
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            onDismiss()
            viewModel.showOverlay(AppOverlay.NEW_SERVER)
        }) { Text("New Server") }
    }
}

@Composable
private fun ServerListCard(server: Server, jarLabel: String, onSelect: () -> Unit, onToggle: () -> Unit) {
    val color = when (server.status) {
        ServerStatus.RUNNING -> Accent
        ServerStatus.STARTING, ServerStatus.STOPPING -> WarnAmber
        ServerStatus.STOPPED -> ErrorRed
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(server.name, fontWeight = FontWeight.Bold)
                Text(jarLabel, color = TextSecondary)
                Text(server.status.name, color = color)
            }
            Button(onClick = onToggle) {
                Text(if (server.status == ServerStatus.RUNNING) "Stop" else "Start")
            }
        }
    }
}
