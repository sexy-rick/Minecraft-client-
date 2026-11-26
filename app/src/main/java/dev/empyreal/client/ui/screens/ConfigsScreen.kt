package dev.empyreal.client.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.config.ConfigStore
import dev.empyreal.client.core.model.Setting
import dev.empyreal.client.core.model.SettingValue
import dev.empyreal.client.core.registry.ModuleRegistry
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.theme.LocalSpacing

@Composable
fun ConfigsScreen() {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val store = EmpyrealApp.instance.configs
    val modules = EmpyrealApp.instance.modules
    val enabled by modules.enabled.collectAsState()
    val values by modules.values.collectAsState()

    var configs by remember { mutableStateOf(store.list()) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()
            if (!text.isNullOrBlank()) pendingImport = text
        }
    }

    fun refresh() { configs = store.list() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = spacing.lg, end = spacing.lg, top = spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Configs",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Save, share and switch module setups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = {
                    showCreate = true
                    newName = ""
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New config", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Surface(
                onClick = { importer.launch("application/json") },
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.md))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item {
                ExpressiveCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Current setup",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "${enabled.count { it.value }} modules on",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(configs, key = { it.name }) { config ->
                ExpressiveCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = config.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = "${config.enabled.count { it.value }} modules" +
                                        if (config.author.isNotBlank()) " · by ${config.author}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                store.delete(config.name)
                                refresh()
                            }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete",
                                    tint = colors.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Button(
                                onClick = { applyConfig(store, modules, config); refresh() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary,
                                ),
                            ) {
                                Text("Apply", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                onClick = { shareConfig(context, store, config) },
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceContainerHighest,
                                modifier = Modifier.weight(1f).height(40.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = colors.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Save current setup") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Config name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim().ifBlank { "My config" }
                    store.save(currentConfig(name, enabled, values))
                    refresh()
                    showCreate = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import config") },
            text = { Text("Replace your current modules with this imported config?") },
            confirmButton = {
                TextButton(onClick = {
                    store.importJson(text)?.let { imported ->
                        store.save(imported)
                        applyConfig(store, modules, imported)
                    }
                    pendingImport = null
                    refresh()
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }
}

private fun currentConfig(
    name: String,
    enabled: Map<String, Boolean>,
    values: Map<String, Map<String, SettingValue>>,
): ConfigStore.ConfigFile {
    val encoded = values.mapValues { (_, inner) ->
        inner.mapValues { (_, v) -> encodeValue(v) }
    }
    val hud = EmpyrealApp.instance.hudLayout.snapshot().mapValues { (_, p) ->
        ConfigStore.ConfigFile.HudPlacement(x = p.x, y = p.y, scale = p.scale, visible = p.visible)
    }
    return ConfigStore.ConfigFile(
        name = name,
        author = "me",
        enabled = enabled.filterValues { it },
        values = encoded,
        hud = hud,
    )
}

private fun encodeValue(v: SettingValue): String = when (v) {
    is SettingValue.Bool -> v.value.toString()
    is SettingValue.Float -> v.value.toString()
    is SettingValue.String -> v.value
    is SettingValue.Color -> v.value.toString()
}

private fun applyConfig(
    store: ConfigStore,
    modules: dev.empyreal.client.core.state.ModuleManager,
    config: ConfigStore.ConfigFile,
) {
    val values = ModuleRegistry.all.associate { module ->
        module.id to module.settings.associate { setting ->
            val raw = config.values[module.id]?.get(setting.id)
            setting.id to decodeValue(setting, raw)
        }
    }
    modules.applySnapshot(
        dev.empyreal.client.core.state.ModuleManager.Snapshot(
            enabled = config.enabled,
            values = values,
        ),
    )
    if (config.hud.isNotEmpty()) {
        EmpyrealApp.instance.hudLayout.applyAll(
            config.hud.mapValues { (_, p) ->
                dev.empyreal.client.core.state.HudPlacement(
                    x = p.x,
                    y = p.y,
                    scale = p.scale,
                    visible = p.visible,
                )
            },
        )
    }
}

private fun decodeValue(setting: Setting, raw: String?): SettingValue = when (setting) {
    is Setting.Toggle -> SettingValue.Bool(
        raw?.let { if (it == "true" || it == "false") it.toBoolean() else null } ?: setting.default,
    )
    is Setting.Slider -> SettingValue.Float(
        raw?.toFloatOrNull()?.coerceIn(setting.min, setting.max) ?: setting.default,
    )
    is Setting.Choice -> {
        val candidate = raw ?: setting.default
        SettingValue.String(if (candidate in setting.options) candidate else setting.default)
    }
    is Setting.Keybind -> SettingValue.String(raw ?: setting.default)
    is Setting.Color -> SettingValue.Color(raw?.toLongOrNull() ?: setting.default)
}

private fun shareConfig(
    context: android.content.Context,
    store: ConfigStore,
    config: ConfigStore.ConfigFile,
) {
    val json = store.exportJson(config)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "${config.name}.json")
        putExtra(android.content.Intent.EXTRA_TEXT, json)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(intent, "Share config"))
    }
}
