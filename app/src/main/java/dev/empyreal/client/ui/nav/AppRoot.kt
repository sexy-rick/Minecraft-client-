package dev.empyreal.client.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.empyreal.client.ui.screens.ConfigsScreen
import dev.empyreal.client.ui.screens.ContentScreen
import dev.empyreal.client.ui.screens.HomeScreen
import dev.empyreal.client.ui.screens.LogScreen
import dev.empyreal.client.ui.screens.HudScreen
import dev.empyreal.client.ui.screens.ModuleDetailScreen
import dev.empyreal.client.ui.screens.ModulesScreen
import dev.empyreal.client.ui.screens.OffsetsScreen
import dev.empyreal.client.ui.screens.SettingsScreen

private data class TopLevel(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevel = listOf(
    TopLevel("home", "Home", Icons.Rounded.Home),
    TopLevel("modules", "Modules", Icons.Rounded.Dashboard),
    TopLevel("hud", "HUD", Icons.Rounded.Layers),
    TopLevel("configs", "Configs", Icons.Rounded.Tune),
    TopLevel("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun AppRoot(
    onThemeChanged: (Boolean) -> Unit,
    onDynamicChanged: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in topLevel.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    topLevel.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            enterTransition = {
                fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 32 }
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutVertically(tween(220)) { it / 32 }
            },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenModules = { navController.navigate("modules") },
                    onOpenModule = { navController.navigate("module/$it") { launchSingleTop = true } },
                    onOpenConfigs = { navController.navigate("configs") },
                    onOpenHud = { navController.navigate("hud") },
                    onOpenContent = { navController.navigate("content") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenLog = { navController.navigate("log") },
                )
            }
            composable("modules") {
                ModulesScreen(
                    onOpenModule = { navController.navigate("module/$it") { launchSingleTop = true } },
                )
            }
            composable(
                route = "module/{id}",
                arguments = listOf(navArgument("id") { defaultValue = "" }),
            ) { entry ->
                ModuleDetailScreen(
                    moduleId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { if (!navController.navigateUp()) navController.popBackStack() },
                )
            }
            composable("hud") { HudScreen() }
            composable("configs") { ConfigsScreen() }
            composable("settings") {
                SettingsScreen(
                    onThemeChanged = onThemeChanged,
                    onDynamicChanged = onDynamicChanged,
                    onOpenOffsets = { navController.navigate("offsets") },
                    onOpenLog = { navController.navigate("log") },
                )
            }
            composable("offsets") {
                OffsetsScreen(onBack = { navController.popBackStack() })
            }
            composable("log") {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable("content") {
                ContentScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
