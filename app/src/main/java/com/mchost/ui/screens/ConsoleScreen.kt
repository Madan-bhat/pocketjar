package com.mchost.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.ServerStatus
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.ui.theme.logColor
import com.mchost.viewmodel.MCHostViewModel

private val TerminalBg = Color(0xFF080808)
private val QuickCommands = listOf("list", "help", "say Hello", "save-all", "whitelist on", "stop")

@Composable
fun ConsoleScreen(viewModel: MCHostViewModel) {
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val verboseLogs by viewModel.logVerbose.collectAsStateWithLifecycle()
    var command by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val displayed = logs
    val canSend = server?.status == ServerStatus.RUNNING

    LaunchedEffect(displayed.size) {
        if (displayed.isNotEmpty()) {
            listState.scrollToItem(displayed.lastIndex)
        }
    }

    fun submitCommand(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        if (!canSend) {
            sendError = "Start the server from Home before sending commands"
            return
        }
        sendError = null
        if (viewModel.sendCommand(trimmed)) {
            history = (history + trimmed).takeLast(40)
            command = ""
        } else {
            sendError = "Could not send — server console not connected"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Console", style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (server?.status) {
                        ServerStatus.RUNNING -> "Connected — type commands below"
                        ServerStatus.STARTING -> "Server starting…"
                        ServerStatus.STOPPING -> "Server stopping…"
                        else -> "Server offline — start from Home tab"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (server?.status) {
                        ServerStatus.RUNNING -> Accent
                        ServerStatus.STARTING, ServerStatus.STOPPING -> WarnAmber
                        else -> TextSecondary
                    },
                )
            }
            IconButton(onClick = { viewModel.clearLogs() }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = TextSecondary)
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !verboseLogs,
                onClick = { viewModel.setLogVerbose(false) },
                label = { Text("Essential") },
                colors = chipColors(),
            )
            FilterChip(
                selected = verboseLogs,
                onClick = { viewModel.setLogVerbose(true) },
                label = { Text("All logs") },
                colors = chipColors(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = TerminalBg),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (server == null) {
                            "Create a server on Home"
                        } else if (!verboseLogs) {
                            "Waiting for server events…\nSwitch to All logs for bootstrap output"
                        } else {
                            "Waiting for log output…"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(displayed, key = { it.id }) { entry ->
                        Text(
                            entry.message,
                            color = logColor(entry.level),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(12.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickCommands.forEach { quick ->
                    FilterChip(
                        selected = false,
                        onClick = { submitCommand(quick) },
                        enabled = canSend || quick == "help",
                        label = { Text(quick) },
                        colors = chipColors(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            sendError?.let {
                Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it; sendError = null },
                    modifier = Modifier.weight(1f),
                    enabled = canSend,
                    placeholder = {
                        Text(if (canSend) "e.g. list, say Hello, op Steve" else "Start server to send commands")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submitCommand(command) }),
                )
                Button(
                    onClick = { submitCommand(command) },
                    enabled = canSend && command.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent: ", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    history.takeLast(3).reversed().forEach { past ->
                        TextButton(onClick = { command = past }) {
                            Text(past, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Accent.copy(alpha = 0.2f),
    selectedLabelColor = Accent,
    containerColor = Surface,
    labelColor = TextSecondary,
)
