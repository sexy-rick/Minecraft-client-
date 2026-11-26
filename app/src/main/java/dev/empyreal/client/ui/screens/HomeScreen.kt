package dev.empyreal.client.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.minecraft.MinecraftLauncher
import dev.empyreal.client.core.registry.ModuleRegistry
import dev.empyreal.client.core.log.LastSession
import dev.empyreal.client.core.state.CrashLog
import dev.empyreal.client.core.state.PlayStats
import dev.empyreal.client.game.GameHost
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.components.SectionHeader
import dev.empyreal.client.ui.components.StatusPill
import dev.empyreal.client.ui.theme.LocalSpacing

private data class LauncherStatus(
    val minecraftInstalled: Boolean,
    val minecraftVersion: String?,
    val abi: String?,
    val hostError: String?,
)

@Composable
private fun rememberLauncherStatus(): LauncherStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(tick, context) {
        LauncherStatus(
            minecraftInstalled = GameHost.isGameInstalled(context),
            minecraftVersion = MinecraftLauncher.versionName(context),
            abi = Build.SUPPORTED_ABIS.firstOrNull(),
            hostError = GameHost.lastError,
        )
    }
}

@Composable
fun HomeScreen(
    onOpenModules: () -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenConfigs: () -> Unit,
    onOpenHud: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit = {},
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val status = rememberLauncherStatus()
    val enabled by EmpyrealApp.instance.modules.enabled.collectAsState()
    val stats by PlayStats.snapshot.collectAsState()
    val launch by GameHost.state.collectAsState()
    val preparing = launch.phase == GameHost.Phase.Preparing
    val enabledModules = remember(enabled) {
        ModuleRegistry.all.filter { enabled[it.id] == true }
    }
    val moduleCount = remember { ModuleRegistry.all.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.xl,
            bottom = spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Empyreal",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onBackground,
                    )
                    Text(
                        text = "Bedrock client for Android · built for the community",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        item {

            var report by remember(status) {
                mutableStateOf(CrashLog.read(context) ?: LastSession.summary(context))
            }
            report?.let { crash ->
                val clipboard = LocalClipboardManager.current
                ExpressiveCard {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(spacing.sm))
                            Text(
                                text = "Last crash",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = crash.lineSequence().take(14).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "Send this report to the developers to help investigate the crash.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            TextButton(onClick = onOpenLog) {
                                Text("Open log", color = colors.primary)
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(crash)) }) {
                                Text("Copy", color = colors.primary)
                            }
                            TextButton(onClick = {
                                CrashLog.clear(context)
                                LastSession.clear(context)
                                report = null
                            }) {
                                Text("Dismiss", color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            ExpressiveCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MinecraftIcon(pkg = MinecraftLauncher.installedPackage(context) ?: "com.mojang.minecraftpe")
                        Spacer(Modifier.width(spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Minecraft",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = when {
                                    status.minecraftVersion != null -> "Version ${status.minecraftVersion}"
                                    else -> "Not installed — get it from the Play Store"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        StatusPill(
                            text = status.abi ?: "unknown ABI",
                            active = status.minecraftInstalled,
                        )
                    }

                    Spacer(Modifier.height(spacing.md))
                    Text(
                        text = "Experimental: replaces PairIP integrity checks. Upstream hooks may alter game or telemetry behavior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(spacing.sm))

                    Button(
                        onClick = {
                            when {
                                status.minecraftInstalled -> GameHost.launch(context)
                                else -> MinecraftLauncher.openStore(context)
                            }
                        },
                        enabled = !preparing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        if (preparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = colors.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = if (status.minecraftInstalled) {
                                    Icons.Rounded.PlayArrow
                                } else {
                                    Icons.Rounded.Storefront
                                },
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = when {
                                preparing -> "Starting…"
                                status.minecraftInstalled -> "Launch"
                                else -> "Get Minecraft"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(spacing.xs))

                    val activeHostError = when {
                        launch.phase == GameHost.Phase.Failed -> launch.message ?: status.hostError
                        else -> status.hostError
                    }
                    Text(
                        text = when {
                            preparing -> launch.message ?: "Loading the game's libraries…"
                            activeHostError != null -> "Could not start Minecraft inside Empyreal: $activeHostError"
                            else -> "Minecraft opens right here — your HUD and modules come with it."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (activeHostError != null && !preparing) colors.error else colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(spacing.xs))

                    Text(
                        text = "Playtime · today ${PlayStats.format(stats.todayMs)}" +
                            " · all time ${PlayStats.format(stats.totalMs)}" +
                            if (stats.sessions > 0) " · ${stats.sessions} sessions" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ExpressiveCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Extension, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(spacing.sm))
                            Text(
                                text = "Mods",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(spacing.sm))
                        CountLine("Modules", moduleCount)
                        CountLine("Active", enabledModules.size)
                        Spacer(Modifier.height(spacing.md))
                        FilledTonalButton(
                            onClick = onOpenModules,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 10.dp),
                        ) {
                            Text("Manage Mods", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ExpressiveCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        ActionRow(Icons.Rounded.Layers, "HUD editor", onOpenHud)
                        ActionRow(Icons.Rounded.Tune, "Configs", onOpenConfigs)
                        ActionRow(Icons.Rounded.Inventory2, "Import content", onOpenContent)
                        ActionRow(Icons.Rounded.BugReport, "Live log", onOpenLog)
                    }
                }
            }
        }

        item {
            Column {
                SectionHeader(
                    title = "Active modules",
                    trailing = {
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            modifier = Modifier.clickable(onClick = onOpenModules),
                        )
                    },
                )
                Spacer(Modifier.height(spacing.sm))
                if (enabledModules.isEmpty()) {
                    ExpressiveCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Dashboard, contentDescription = null, tint = colors.onSurfaceVariant)
                            Spacer(Modifier.width(spacing.md))
                            Text(
                                text = "Nothing enabled yet. Open Mods and turn something on.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(enabledModules, key = { it.id }) { module ->
                            Surface(
                                onClick = { onOpenModule(module.id) },
                                shape = RoundedCornerShape(18.dp),
                                color = colors.surfaceContainerHighest,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        module.icon,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(spacing.sm))
                                    Text(
                                        text = module.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.onSurface,
                                    )
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
private fun CountLine(label: String, value: Int) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MinecraftIcon(pkg: String) {
    val context = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Minecraft",
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
    } else {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Icon(
                Icons.Rounded.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
