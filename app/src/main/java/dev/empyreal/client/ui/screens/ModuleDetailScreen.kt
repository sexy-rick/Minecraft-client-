package dev.empyreal.client.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.model.Implementation
import dev.empyreal.client.core.model.Module
import dev.empyreal.client.core.model.Setting
import dev.empyreal.client.core.model.SettingValue
import dev.empyreal.client.core.state.SpeedrunTimer
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.registry.ModuleRegistry
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.components.ImplementationBadge
import dev.empyreal.client.ui.theme.LocalSpacing
import kotlinx.coroutines.delay

private val DefaultPalette = listOf(
    0xFF8CD6A9L, 0xFFFFB877L, 0xFFA3CDF0L, 0xFFFFFFFFL,
    0xFFFFB4ABL, 0xFFFFE08AL, 0xFFC5B8FFL, 0xFFB8D9A8L,
)

@Composable
fun ModuleDetailScreen(moduleId: String, onBack: () -> Unit) {
    val module = ModuleRegistry.byId(moduleId)
    if (module == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val modules = EmpyrealApp.instance.modules
    val enabled by modules.enabled.collectAsState()
    val values by modules.values.collectAsState()
    val core by CoreBridge.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current

    val checked = enabled[module.id] == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = "Module",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = spacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (checked) colors.primaryContainer else colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        module.icon,
                        contentDescription = null,
                        tint = if (checked) colors.onPrimaryContainer else colors.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ImplementationBadge(module.implementation)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = module.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                ExpressiveCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Enable module",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = when {
                                    module.implementation == Implementation.NATIVE_CORE && core.attached &&
                                        checked -> "Active in-game"
                                    module.implementation == Implementation.NATIVE_CORE && !core.attached &&
                                        checked -> "Ready for next launch"
                                    checked -> "On"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { modules.setEnabled(module.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.primary,
                                checkedThumbColor = colors.onPrimary,
                                uncheckedTrackColor = colors.surfaceContainerHighest,
                                uncheckedThumbColor = colors.outline,
                            ),
                        )
                    }
                }
            }
        }

        if (module.controls) {
            item { ModuleControls(module.id) }
        }

        if (module.implementation == Implementation.NATIVE_CORE && core.attached) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(spacing.md)) {
                        Text(
                            text = "Running in game",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                append("pid ${core.pid}")
                                if (core.gameVersion.isNotBlank()) append(" · ${core.gameVersion}")
                                if (core.abi.isNotBlank()) append(" · ${core.abi}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSecondaryContainer,
                        )
                        val ready = core.value("${module.id}.ready")
                        if (ready == "false") {
                            Spacer(Modifier.height(spacing.sm))
                            Text(
                                text = "This module is still not configured for this game version " +
                                    "(offsets unresolved). It will not report data yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        if (module.settings.isNotEmpty()) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = 4.dp, top = spacing.sm),
                )
            }
            items(module.settings, key = { it.id }) { setting ->
                SettingRow(
                    module = module,
                    setting = setting,
                    value = values[module.id]?.get(setting.id)
                        ?: dev.empyreal.client.core.state.ModuleManager.defaultOf(setting),
                    onValue = { modules.set(module.id, setting.id, it) },
                )
            }
            item {
                Surface(
                    onClick = { modules.reset(module.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "Reset to defaults",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    module: Module,
    setting: Setting,
    value: SettingValue,
    onValue: (SettingValue) -> Unit,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme

    ExpressiveCard {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = setting.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            if (setting.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(spacing.sm))

            when (setting) {
                is Setting.Toggle -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if ((value as? SettingValue.Bool)?.value == true) "On" else "Off",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = (value as? SettingValue.Bool)?.value == true,
                            onCheckedChange = { onValue(SettingValue.Bool(it)) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.primary,
                                checkedThumbColor = colors.onPrimary,
                            ),
                        )
                    }
                }

                is Setting.Slider -> {
                    val current = (value as? SettingValue.Float)?.value ?: setting.default
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = formatSlider(current, setting),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${formatSlider(setting.min, setting)} – ${formatSlider(setting.max, setting)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = current,
                        onValueChange = { onValue(SettingValue.Float(it)) },
                        valueRange = setting.min..setting.max,
                        steps = if (setting.step <= 0f) 0 else {
                            (((setting.max - setting.min) / setting.step).toInt() - 1).coerceAtLeast(0)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.surfaceContainerHighest,
                        ),
                    )
                }

                is Setting.Choice -> {
                    val current = (value as? SettingValue.String)?.value ?: setting.default
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(setting.options) { option ->
                            val selected = option == current
                            Surface(
                                onClick = { onValue(SettingValue.String(option)) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) colors.primary else colors.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }

                is Setting.Keybind -> {
                    val current = (value as? SettingValue.String)?.value ?: setting.default
                    KeybindPicker(current = current, onChange = { onValue(SettingValue.String(it)) })
                }

                is Setting.Color -> {
                    val current = (value as? SettingValue.Color)?.value ?: setting.default
                    val palette = setting.options.ifEmpty { DefaultPalette }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(palette) { c ->
                            val selected = c == current
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) colors.onSurface else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { onValue(SettingValue.Color(c)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
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

private fun formatSlider(v: Float, s: Setting.Slider): String {
    val text = if (s.decimals > 0) {
        String.format(java.util.Locale.US, "%.${s.decimals}f", v)
    } else {
        v.toInt().toString()
    }
    return text + s.unit
}

@Composable
private fun ModuleControls(moduleId: String) {
    when (moduleId) {
        "speedrun" -> SpeedrunControls()
    }
}

@Composable
private fun SpeedrunControls() {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val modules = EmpyrealApp.instance.modules
    val state by SpeedrunTimer.state.collectAsState()

    var now by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.running) {
        while (state.running) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(50)
        }
    }
    if (!state.running) {
        LaunchedEffect(state.accumulated) {
            now = android.os.SystemClock.elapsedRealtime()
        }
    }

    val elapsed = if (state.running) {
        state.accumulated + (now - state.startedAt).coerceAtLeast(0L)
    } else {
        state.accumulated
    }
    val text = SpeedrunTimer.format(elapsed, modules.bool("speedrun", "tenths"))

    ExpressiveCard {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Speedrun",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.displayMedium,
                color = colors.primary,
            )
            if (state.splits.isNotEmpty()) {
                Spacer(Modifier.height(spacing.xs))
                state.splits.takeLast(4).forEach { split ->
                    val delta = if (split.deltaMs == 0L) "" else "  " + SpeedrunTimer.formatDelta(split.deltaMs)
                    Text(
                        text = "Split ${split.index + 1}  " + SpeedrunTimer.format(split.atMs, false) + delta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Surface(
                    onClick = { SpeedrunTimer.toggle() },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.primary,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.running) "Pause" else "Start",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onPrimary,
                        )
                    }
                }
                Surface(
                    onClick = { SpeedrunTimer.split() },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.secondaryContainer,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Split",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSecondaryContainer,
                        )
                    }
                }
                Surface(
                    onClick = { SpeedrunTimer.reset() },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceContainerHighest,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeybindPicker(current: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }

    Surface(
        onClick = { picking = true },
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerHighest,
    ) {
        Text(
            text = if (current.equals("NONE", ignoreCase = true)) "Not set — tap to bind" else current,
            style = MaterialTheme.typography.labelLarge,
            color = if (current.equals("NONE", ignoreCase = true)) colors.onSurfaceVariant else colors.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }

    if (picking) {
        KeybindDialog(
            onKey = { onChange(it); picking = false },
            onClear = { onChange("NONE"); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun KeybindDialog(
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = "Press a key",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Press any key on a connected keyboard. Back cancels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainerHighest)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val code = event.nativeKeyEvent.keyCode
                            when (code) {
                                android.view.KeyEvent.KEYCODE_BACK,
                                android.view.KeyEvent.KEYCODE_ESCAPE -> {
                                    onDismiss()
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_VOLUME_UP,
                                android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
                                android.view.KeyEvent.KEYCODE_VOLUME_MUTE -> false
                                else -> {
                                    onKey(keyCodeToName(code))
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Listening…",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row {
                    TextButton(onClick = onClear) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

private fun keyCodeToName(keyCode: Int): String =
    android.view.KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
