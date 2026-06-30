package com.mchost.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mchost.ui.screens.BackupScreen
import com.mchost.ui.screens.ConsoleScreen
import com.mchost.ui.screens.FilesScreen
import com.mchost.ui.screens.HomeScreen
import com.mchost.ui.screens.NetworkScreen
import com.mchost.ui.screens.NewServerScreen
import com.mchost.ui.screens.ServersScreen
import com.mchost.ui.screens.SettingsScreen
import com.mchost.ui.theme.Accent
import com.mchost.ui.theme.Background
import com.mchost.ui.theme.ContainerRaised
import com.mchost.ui.theme.NavActivePurple
import com.mchost.ui.theme.TextPrimary
import com.mchost.ui.theme.TextSecondary
import com.mchost.viewmodel.AppOverlay
import com.mchost.viewmodel.MCHostViewModel

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Tab("home", "Home", Icons.Default.Home)
    data object Console : Tab("console", "Console", Icons.Default.Terminal)
    data object Network : Tab("network", "Network", Icons.Default.SettingsEthernet)
    data object Backup : Tab("backup", "Backup", Icons.Default.Backup)
}

@Composable
fun MCHostAppNav(viewModel: MCHostViewModel) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Home, Tab.Console, Tab.Network, Tab.Backup)
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()

    if (overlay != AppOverlay.NONE) {
        when (overlay) {
            AppOverlay.SERVERS -> ServersScreen(viewModel) { viewModel.dismissOverlay() }
            AppOverlay.NEW_SERVER -> NewServerScreen(viewModel) { viewModel.dismissOverlay() }
            AppOverlay.SETTINGS -> SettingsScreen(viewModel) { viewModel.dismissOverlay() }
            AppOverlay.FILES -> FilesScreen(viewModel) { viewModel.dismissOverlay() }
            else -> Unit
        }
        return
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = ContainerRaised,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Box(
                                modifier = if (selected) {
                                    Modifier
                                        .background(NavActivePurple, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                } else {
                                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) TextPrimary else TextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                        label = {
                            Text(
                                tab.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextPrimary,
                            selectedTextColor = TextPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute != Tab.Console.route) {
                FloatingActionButton(
                    onClick = { viewModel.showOverlay(AppOverlay.NEW_SERVER) },
                    containerColor = Accent,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New server", tint = Color.Black)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) { HomeScreen(viewModel) }
            composable(Tab.Console.route) { ConsoleScreen(viewModel) }
            composable(Tab.Network.route) { NetworkScreen(viewModel) }
            composable(Tab.Backup.route) { BackupScreen(viewModel) }
        }
    }
}
