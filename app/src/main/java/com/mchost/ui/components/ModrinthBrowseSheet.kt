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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.mchost.ui.theme.BorderSubtle
import com.mchost.ui.theme.ErrorRed
import com.mchost.ui.theme.JetBrainsMono
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.viewmodel.MCHostViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

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
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ModrinthProject>>(emptyList()) }
    var totalHits by remember { mutableIntStateOf(0) }
    var selectedProject by remember { mutableStateOf<ModrinthProject?>(null) }
    var versions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var versionsLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val canLoadMore by remember {
        derivedStateOf { results.size < totalHits }
    }

    fun loadPage(offset: Int, append: Boolean) {
        scope.launch {
            if (append) loadingMore = true else loading = true
            error = null
            viewModel.searchModrinth(query, kind, gameVersion, jarType, offset = offset, limit = PAGE_SIZE)
                .onSuccess { page ->
                    totalHits = page.totalHits
                    results = if (append) results + page.projects else page.projects
                }
                .onFailure { e ->
                    if (!append) {
                        results = emptyList()
                        totalHits = 0
                    }
                    error = e.message ?: "Search failed"
                }
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(kind, gameVersion, jarType, query) {
        delay(350)
        loadPage(offset = 0, append = false)
    }

    LaunchedEffect(listState, canLoadMore, loading, loadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalItems) ->
                if (
                    canLoadMore &&
                    !loading &&
                    !loadingMore &&
                    totalItems > 0 &&
                    lastVisible >= totalItems - 3
                ) {
                    loadPage(offset = results.size, append = true)
                }
            }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            CloudMcTopBar(
                onClose = {
                    if (selectedProject != null) {
                        selectedProject = null
                        versions = emptyList()
                    } else {
                        onDismiss()
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Accent)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            CloudMcPageHeader(
                title = when (kind) {
                    ModrinthContentKind.PLUGINS -> "Plugins"
                    ModrinthContentKind.MODS -> "Mods"
                    ModrinthContentKind.DATAPACKS -> "Datapacks"
                },
                subtitle = buildString {
                    append("Minecraft $gameVersion")
                    if (selectedProject == null && totalHits > 0) {
                        append(" • ${results.size} of $totalHits")
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            if (selectedProject == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search Modrinth…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Accent) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                    error != null && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, color = ErrorRed)
                    }
                    results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results", color = TextSecondary)
                    }
                    else -> CloudMcCard(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(results, key = { it.id }) { project ->
                                ModrinthProjectRow(project) {
                                    selectedProject = project
                                    versionsLoading = true
                                    versions = emptyList()
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
                                if (project != results.last() || loadingMore || canLoadMore) {
                                    HorizontalDivider(color = BorderSubtle)
                                }
                            }
                            if (loadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                                    }
                                }
                            } else if (canLoadMore) {
                                item(key = "load_more") {
                                    TextButton(
                                        onClick = { loadPage(offset = results.size, append = true) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Load more", color = Accent)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val project = selectedProject!!
                Text(project.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(project.description, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                when {
                    versionsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                    versions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No versions found for Minecraft $gameVersion.\nTry a different server version in Settings.",
                            color = TextSecondary,
                        )
                    }
                    else -> CloudMcCard(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(versions, key = { it.id }) { version ->
                                Row(
                                    Modifier
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
                                        }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(version.versionNumber, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(version.filename, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        if (version.gameVersions.isNotEmpty()) {
                                            Text(
                                                version.gameVersions.take(4).joinToString(", "),
                                                color = Accent,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMono,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                    Text("Add", color = Accent, fontWeight = FontWeight.Bold)
                                }
                                if (version != versions.last()) {
                                    HorizontalDivider(color = BorderSubtle)
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
private fun ModrinthProjectRow(project: ModrinthProject, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
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
            Text(project.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
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
    Text("From Modrinth (${items.size})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    items.forEach { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.projectTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                Text(item.filename, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onRemove(item) }) { Text("Remove", color = ErrorRed) }
        }
    }
}
