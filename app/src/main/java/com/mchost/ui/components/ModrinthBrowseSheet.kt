package com.mchost.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mchost.data.JarType
import com.mchost.network.modrinth.ModrinthContentKind
import com.mchost.network.modrinth.ModrinthProject
import com.mchost.network.modrinth.ModrinthVersion
import com.mchost.network.modrinth.PendingModrinthInstall
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.Surface
import com.mchost.ui.theme.TextSecondary
import com.mchost.viewmodel.MCHostViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModrinthBrowseSheet(
    viewModel: MCHostViewModel,
    kind: ModrinthContentKind,
    gameVersion: String,
    jarType: JarType,
    onDismiss: () -> Unit,
    onQueued: (PendingModrinthInstall) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ModrinthProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<ModrinthProject?>(null) }
    var versions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var versionsLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(kind, gameVersion, jarType, query) {
        delay(350)
        loading = true
        error = null
        viewModel.searchModrinth(query, kind, gameVersion, jarType)
            .onSuccess { results = it.projects }
            .onFailure { error = it.message ?: "Search failed" }
        loading = false
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (kind) {
                            ModrinthContentKind.PLUGINS -> "Modrinth Plugins"
                            ModrinthContentKind.MODS -> "Modrinth Mods"
                            ModrinthContentKind.DATAPACKS -> "Modrinth Datapacks"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedProject != null) selectedProject = null else onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (selectedProject == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search Modrinth…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Minecraft $gameVersion • sorted by downloads",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, color = ErrorRed)
                    }
                    results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results", color = TextSecondary)
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.id }) { project ->
                            ModrinthProjectCard(project) {
                                selectedProject = project
                                versionsLoading = true
                                scope.launch {
                                    viewModel.modrinthProjectVersions(project.id, kind, gameVersion, jarType)
                                        .onSuccess { versions = it }
                                        .onFailure {
                                            versions = emptyList()
                                            error = it.message
                                        }
                                    versionsLoading = false
                                }
                            }
                        }
                    }
                }
            } else {
                val project = selectedProject!!
                Text(project.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(project.description, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                when {
                    versionsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                    versions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No compatible versions for $gameVersion", color = TextSecondary)
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(versions, key = { it.id }) { version ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onQueued(
                                            PendingModrinthInstall(
                                                projectId = project.id,
                                                projectTitle = project.title,
                                                versionId = version.id,
                                                filename = version.filename,
                                                kind = kind,
                                            ),
                                        )
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(containerColor = Surface),
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(version.versionNumber, fontWeight = FontWeight.SemiBold)
                                        Text(version.filename, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Add", color = Accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModrinthProjectCard(project: ModrinthProject, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = project.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Background),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(project.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    project.description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDownloads(project.downloads)} downloads • ${project.author}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatDownloads(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
fun PendingModrinthList(
    items: List<PendingModrinthInstall>,
    onRemove: (PendingModrinthInstall) -> Unit,
) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text("From Modrinth (${items.size})", style = MaterialTheme.typography.titleSmall)
    items.forEach { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.projectTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.filename, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onRemove(item) }) { Text("Remove", color = ErrorRed) }
        }
    }
}
