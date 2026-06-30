package com.mchost.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mchost.ui.theme.JetBrainsMono
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mchost.data.ForwardingMethod
import com.mchost.data.ServerStatus
import com.mchost.network.ForwardingStatus
import com.mchost.ui.components.CloudMcBrandRow
import com.mchost.ui.components.CloudMcCard
import com.mchost.ui.components.CloudMcOutlinedButton
import com.mchost.ui.components.CloudMcPageHeader
import com.mchost.ui.components.CloudMcPrimaryButton
import com.mchost.ui.components.CloudMcSectionTitle
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.ui.theme.WarnAmber
import com.mchost.viewmodel.MCHostViewModel

@Composable
fun NetworkScreen(viewModel: MCHostViewModel) {
    val prefs by viewModel.networkPrefs.collectAsStateWithLifecycle()
    val server by viewModel.activeServer.collectAsStateWithLifecycle()
    val config by viewModel.activeConfig.collectAsStateWithLifecycle()
    val forwardInfo by viewModel.networkManager.forwardInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localIp = viewModel.networkManager.localIp()
    val port = config?.serverPort ?: 25565
    val isApplying = forwardInfo.status == ForwardingStatus.APPLYING
    val serverRunning = server?.status == ServerStatus.RUNNING

    var loclxTokenInput by remember(prefs.localXposeToken) {
        mutableStateOf(prefs.localXposeToken.orEmpty())
    }
    var loclxRegionInput by remember(prefs.localXposeRegion) {
        mutableStateOf(prefs.localXposeRegion)
    }
    var loclxManualInput by remember(prefs.localXposeManualAddress) {
        mutableStateOf(prefs.localXposeManualAddress.orEmpty())
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
            title = "Network",
            subtitle = "Let friends join from outside your Wi‑Fi",
        )
        Spacer(Modifier.height(16.dp))

        StatusCard(forwardInfo.status, forwardInfo.message, isApplying)
        Spacer(Modifier.height(12.dp))

        AddressCard(
            title = "Share with friends",
            address = forwardInfo.publicAddress.ifBlank { forwardInfo.localAddress.ifBlank { "$localIp:$port" } },
            subtitle = when {
                forwardInfo.publicAddress.isNotBlank() -> "Public address (after forwarding)"
                else -> "Local only — forwarding not active yet"
            },
            onCopy = { copyToClipboard(context, forwardInfo.shareAddress.ifBlank { "$localIp:$port" }) },
        )
        Spacer(Modifier.height(8.dp))
        AddressCard(
            title = "Same Wi‑Fi / LAN",
            address = forwardInfo.localAddress.ifBlank { "$localIp:$port" },
            subtitle = "Players on your home network",
            onCopy = { copyToClipboard(context, forwardInfo.localAddress.ifBlank { "$localIp:$port" }) },
        )

        Spacer(Modifier.height(16.dp))
        CloudMcSectionTitle("Method")
        Spacer(Modifier.height(8.dp))
        ForwardingMethod.entries.forEach { method ->
            ForwardingCard(
                title = methodLabel(method),
                description = methodDescription(method, port, localIp),
                selected = prefs.forwardingMethod == method,
                onSelect = {
                    viewModel.saveNetworkPrefs(prefs.copy(forwardingMethod = method))
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        CloudMcPrimaryButton(
            text = if (isApplying) "Applying…" else if (serverRunning) "Apply port forward" else "Test port forward",
            onClick = {
                viewModel.applyNetworkForwarding(
                    method = prefs.forwardingMethod,
                    port = port,
                    localXposeToken = if (prefs.forwardingMethod == ForwardingMethod.LOCALXPOSE) {
                        loclxTokenInput
                    } else {
                        null
                    },
                    localXposeRegion = if (prefs.forwardingMethod == ForwardingMethod.LOCALXPOSE) {
                        loclxRegionInput
                    } else {
                        null
                    },
                    localXposeManualAddress = if (prefs.forwardingMethod == ForwardingMethod.LOCALXPOSE) {
                        loclxManualInput
                    } else {
                        null
                    },
                )
            },
            enabled = !isApplying,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!serverRunning) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Start your server from Home for friends to connect. You can test UPnP anytime.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (prefs.forwardingMethod == ForwardingMethod.LOCALXPOSE) {
            Spacer(Modifier.height(12.dp))
            LocalXposeConfigCard(
                port = port,
                token = loclxTokenInput,
                region = loclxRegionInput,
                manualAddress = loclxManualInput,
                onTokenChange = { loclxTokenInput = it },
                onRegionChange = { loclxRegionInput = it },
                onManualChange = { loclxManualInput = it },
                onSave = { token, region, manual ->
                    loclxTokenInput = token
                    loclxRegionInput = region
                    loclxManualInput = manual
                    viewModel.saveLocalXposeSettings(token, region, manual)
                },
                onGetToken = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://localxpose.io/dashboard/access")),
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        ManualHelpCard(method = prefs.forwardingMethod, port = port, localIp = localIp)
    }
}

@Composable
private fun StatusCard(status: ForwardingStatus, message: String, applying: Boolean) {
    val (icon, color, label) = when {
        applying -> Triple(Icons.Default.Refresh, WarnAmber, "Applying…")
        status == ForwardingStatus.ACTIVE -> Triple(Icons.Default.CheckCircle, Accent, "Port forward active")
        status == ForwardingStatus.FAILED -> Triple(Icons.Default.Error, ErrorRed, "Forwarding failed")
        status == ForwardingStatus.MANUAL -> Triple(Icons.Default.Warning, WarnAmber, "Manual setup required")
        else -> Triple(Icons.Default.Warning, TextSecondary, "Not configured")
    }
    CloudMcCard(accentStripe = status == ForwardingStatus.ACTIVE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Column(Modifier.padding(start = 12.dp)) {
                Text(label, fontWeight = FontWeight.Bold, color = color)
                if (message.isNotBlank()) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun AddressCard(title: String, address: String, subtitle: String, onCopy: () -> Unit) {
    CloudMcCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontFamily = JetBrainsMono)
                Text(address, fontFamily = JetBrainsMono, color = Accent, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Accent)
            }
        }
    }
}

@Composable
private fun ManualHelpCard(method: ForwardingMethod, port: Int, localIp: String) {
    val steps = when (method) {
        ForwardingMethod.UPNP -> listOf(
            "Connect phone to Wi‑Fi (same network as players on LAN).",
            "Tap Apply port forward — we ask your router via UPnP.",
            "Share the public address with friends outside your network.",
            "If UPnP fails, switch to Manual and forward on your router admin page.",
        )
        ForwardingMethod.MANUAL -> listOf(
            "Open your router admin page (often 192.168.1.1).",
            "Add port forward: TCP $port → $localIp (this phone).",
            "Share your public IP:$port with friends (shown above after Apply).",
            "Some carriers block inbound ports — try LocalXpose tunnel instead.",
        )
        ForwardingMethod.TAILSCALE -> listOf(
            "Install Tailscale on this phone and friends' devices.",
            "Join the same tailnet.",
            "Share your Tailscale IP (100.x.x.x:$port) from the Tailscale app.",
        )
        ForwardingMethod.LOCALXPOSE -> listOf(
            "Easiest: run loclx in Termux (see command below), paste ap.loclx.io:port here.",
            "Or add token below to try in-app tunnel (may not work on all phones).",
            "Start the Minecraft server before opening the tunnel.",
            "Share the tunnel address with friends.",
        )
    }
    CloudMcCard {
        Text("How it works", fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { i, step ->
            Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ForwardingCard(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    CloudMcCard(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = Accent),
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun methodLabel(method: ForwardingMethod) = when (method) {
    ForwardingMethod.UPNP -> "UPnP (automatic)"
    ForwardingMethod.MANUAL -> "Manual router"
    ForwardingMethod.TAILSCALE -> "Tailscale VPN"
    ForwardingMethod.LOCALXPOSE -> "LocalXpose tunnel"
}

private fun methodDescription(method: ForwardingMethod, port: Int, localIp: String) = when (method) {
    ForwardingMethod.UPNP -> "Best default — configures router automatically"
    ForwardingMethod.MANUAL -> "Forward TCP $port → $localIp in router settings"
    ForwardingMethod.TAILSCALE -> "Private mesh VPN, no router changes"
    ForwardingMethod.LOCALXPOSE -> "Termux tunnel or paste address below"
}

@Composable
private fun LocalXposeConfigCard(
    port: Int,
    token: String,
    region: String,
    manualAddress: String,
    onTokenChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onManualChange: (String) -> Unit,
    onSave: (String, String, String) -> Unit,
    onGetToken: () -> Unit,
) {
    val context = LocalContext.current
    var tokenInput by remember(token) { mutableStateOf(token) }
    var regionInput by remember(region) { mutableStateOf(region) }
    var manualInput by remember(manualAddress) { mutableStateOf(manualAddress) }
    val termuxCmd = com.mchost.network.LocalXposeAgent.termuxCommand(port, regionInput.ifBlank { "ap" })
    CloudMcCard {
        Text("LocalXpose (recommended: Termux)", fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "In-app loclx often fails on Android. Run this in Termux, then paste the address below.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = manualInput,
            onValueChange = {
                manualInput = it
                onManualChange(it)
            },
            label = { Text("Tunnel address (required)") },
            placeholder = { Text("ap.loclx.io:12345") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        CloudMcOutlinedButton(
            text = "Copy Termux command",
            onClick = { copyToClipboard(context, termuxCmd) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            termuxCmd,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMono,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Optional — in-app tunnel", fontWeight = FontWeight.Bold, color = TextPrimary)
        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                onTokenChange(it)
            },
            label = { Text("Access token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = regionInput,
            onValueChange = {
                val v = it.lowercase().take(2)
                regionInput = v
                onRegionChange(v)
            },
            label = { Text("Region (ap, us, eu)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudMcOutlinedButton("Get token", onGetToken, Modifier.weight(1f))
            CloudMcPrimaryButton("Save", { onSave(tokenInput, regionInput.ifBlank { "ap" }, manualInput) }, Modifier.weight(1f))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("MCHost address", text))
}
