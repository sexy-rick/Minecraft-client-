package dev.empyreal.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.minecraft.MinecraftLauncher
import dev.empyreal.client.core.state.HudSync
import dev.empyreal.client.core.state.ThemePrefs
import dev.empyreal.client.game.GameHost
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.components.InfoRow
import dev.empyreal.client.ui.theme.LocalSpacing

@Composable
fun SettingsScreen(
    onThemeChanged: (Boolean) -> Unit,
    onDynamicChanged: (Boolean) -> Unit,
    onOpenOffsets: () -> Unit = {},
    onOpenLog: () -> Unit = {},
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val prefs = remember { ThemePrefs(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    var mode by remember { mutableStateOf(prefs.darkMode) }
    var useDynamic by remember { mutableStateOf(prefs.dynamicColor) }
    var hud by remember { mutableStateOf(prefs.hudEnabled) }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val core by CoreBridge.state.collectAsState()

    @Suppress("UNUSED_EXPRESSION") tick
    val gameVersion = remember(tick) { MinecraftLauncher.versionName(context) }

    fun setMode(value: String) {
        mode = value
        prefs.darkMode = value
        onThemeChanged(if (value == ThemePrefs.MODE_SYSTEM) systemDark else value == ThemePrefs.MODE_DARK)
    }

    fun setDynamic(value: Boolean) {
        useDynamic = value
        prefs.dynamicColor = value
        onDynamicChanged(value)
    }

    fun setHud(value: Boolean) {
        hud = value
        prefs.hudEnabled = value
        HudSync.hudEnabled = value
        HudSync.pushNow()
    }

    val launchState by GameHost.state.collectAsState()
    val preparing = launchState.phase == GameHost.Phase.Preparing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.xl, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Appearance, game and client info",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        listOf(
                            ThemePrefs.MODE_SYSTEM to "System",
                            ThemePrefs.MODE_LIGHT to "Light",
                            ThemePrefs.MODE_DARK to "Dark",
                        ).forEach { (value, label) ->
                            val selected = mode == value
                            Surface(
                                onClick = { setMode(value) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) colors.primary else colors.surfaceContainerHighest,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.md))
                    ToggleRow(
                        title = "Material You colours",
                        subtitle = "Use the wallpaper palette instead of the Empyreal palette.",
                        checked = useDynamic,
                        onChange = { setDynamic(it) },
                    )
                    ToggleRow(
                        title = "In-game HUD",
                        subtitle = "Widgets drawn inside Minecraft. Arrange them in the HUD tab.",
                        checked = hud,
                        onChange = { setHud(it) },
                    )
                }
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "Game",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))

                    InfoRow(
                        icon = Icons.Rounded.SportsEsports,
                        title = "Minecraft",
                        subtitle = when {
                            core.attached -> "Running in this app"
                            preparing -> launchState.message.orEmpty().ifBlank { "Preparing Minecraft…" }
                            gameVersion != null -> "Version $gameVersion · opens inside Empyreal"
                            else -> "Not installed — get it from the Play Store"
                        },
                        trailing = {
                            when {
                                core.attached -> Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                preparing -> Text(
                                    text = "Preparing…",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant,
                                )
                                else -> Text(
                                    text = "Launch",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primary,
                                )
                            }
                        },
                        onClick = {
                            if (!core.attached && !preparing) GameHost.launch(context)
                        },
                    )

                    InfoRow(
                        icon = Icons.Rounded.Memory,
                        title = "Offset calibration",
                        subtitle = "Scan, inspect and save coordinates offsets",
                        trailing = {
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                            )
                        },
                        onClick = onOpenOffsets,
                    )

                    GameHost.lastError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.error,
                        )
                    }
                }
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                    }
                    Spacer(Modifier.height(spacing.xs))
                    InfoRow(
                        icon = Icons.Rounded.BugReport,
                        title = "Live log",
                        subtitle = "Every step, the game's own output, and how the last session ended",
                        onClick = onOpenLog,
                    )
                }
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))
                    AboutRow("Client", "Empyreal")
                    AboutRow("Build", "1.0.0")
                    AboutRow("Target", "Minecraft Bedrock for Android")
                    AboutRow("Made for", "The community — free, no key system")
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Empyreal does not ship Minecraft and does not touch your normal install. " +
                            "The game runs inside Empyreal itself, and every module runs in that same " +
                            "process — one tap, nothing to install, nothing to repair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.onPrimary,
            ),
        )
    }
}
