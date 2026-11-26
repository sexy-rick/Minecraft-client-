package dev.empyreal.client.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.offset.OffsetStore
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.components.KeyValue
import dev.empyreal.client.ui.theme.LocalSpacing

@Composable
fun OffsetsScreen(onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val store = EmpyrealApp.instance.offsets
    val core by CoreBridge.state.collectAsState()

    var saved by remember { mutableStateOf(store.list()) }
    var pattern by rememberSaveable { mutableStateOf("") }
    var module by rememberSaveable { mutableStateOf(OffsetStore.DEFAULT_LIBRARY) }
    var maxResults by rememberSaveable { mutableStateOf("64") }
    var peekAddress by remember { mutableStateOf(0L) }
    var showManual by remember { mutableStateOf(false) }

    val hits: List<Long> = remember(core.tick, core.values["probe.hits"]) {
        core.values["probe.hits"]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
    }
    val peekValues: List<String> = remember(core.tick, core.values["peek.values"]) {
        core.values["peek.values"]
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }
    val probeBase = core.values["probe.base"]?.toLongOrNull() ?: 0L
    val probeSize = core.values["probe.size"]?.toLongOrNull() ?: 0L
    val probeCount = core.values["probe.count"] ?: "0"
    val probeDuration = core.values["probe.duration_ms"] ?: "0"
    val probeError = core.values["probe.error"].orEmpty()

    fun refresh() { saved = store.list() }

    fun scan() {
        if (!core.attached) return
        CoreBridge.setCoreModule("probe", true)
        EmpyrealApp.instance.modules.setRaw(
            "probe",
            mapOf(
                "pattern" to pattern,
                "module" to module,
                "max" to maxResults,
            ),
        )
    }

    fun peek(address: Long) {
        peekAddress = address
        CoreBridge.setCoreModule("peek", true)
        EmpyrealApp.instance.modules.setRaw(
            "peek",
            mapOf("address" to address.toString(), "count" to "8"),
        )
    }

    fun saveFrom(address: Long, index: Int) {
        val version = core.gameVersion.ifBlank { "unknown" }
        val xAbs = address + index * 4L
        val yAbs = address + (index + 1) * 4L
        val zAbs = address + (index + 2) * 4L
        val base = probeBase
        val useRelative = base > 0L && xAbs >= base
        val row = if (useRelative) {
            OffsetStore.Offsets(version, xAbs - base, yAbs - base, zAbs - base, module)
        } else {
            OffsetStore.Offsets(version, xAbs, yAbs, zAbs, module)
        }
        store.save(row)
        refresh()
        EmpyrealApp.instance.modules.setRaw("coords", OffsetStore.toSettings(row))
    }

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
                    text = "Offsets",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = spacing.xs)) {
                Text(
                    text = "Offset calibration",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Find the player position in memory for this game version. " +
                        "Scan for a byte pattern, inspect the hits, save the one that tracks " +
                        "your movement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = if (core.attached) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "Core",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (core.attached) "attached" else "not attached",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (core.attached) colors.primary else colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))
                    KeyValue("Game version", core.gameVersion.ifBlank { "—" })
                    KeyValue("PID", if (core.pid == 0) "—" else core.pid.toString())
                    KeyValue("ABI", core.abi.ifBlank { "—" })
                    if (probeBase != 0L) {
                        KeyValue("Library base", "0x" + java.lang.Long.toHexString(probeBase))
                        KeyValue("Library size", "${probeSize / 1024 / 1024} MB")
                    }
                    if (!core.attached) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = "Launch the game once, then come back here to calibrate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Saved offsets",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Add manually",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showManual = true }
                        .padding(4.dp),
                )
            }
        }
        if (saved.isEmpty()) {
            item {
                ExpressiveCard {
                    Text(
                        text = "Nothing saved yet. Scan below, or add a row by hand.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(saved, key = { it.version }) { row ->
                ExpressiveCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = row.version,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = OffsetStore.describe(row),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(onClick = {
                                store.delete(row.version)
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
                        Button(
                            onClick = {
                                EmpyrealApp.instance.modules.setRaw(
                                    "coords",
                                    OffsetStore.toSettings(row),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Text(
                        text = "Signature scan",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "IDA-style bytes with ?? wildcards. The scan runs inside the " +
                            "game process and only touches readable mappings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(spacing.md))

                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pattern") },
                        placeholder = { Text("48 8B ?? 00 8B 44 24 ?? 89 46") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )

                    Spacer(Modifier.height(spacing.sm))

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedTextField(
                            value = module,
                            onValueChange = { module = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Library") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                        OutlinedTextField(
                            value = maxResults,
                            onValueChange = { maxResults = it.filter { c -> c.isDigit() }.take(3) },
                            modifier = Modifier.width(96.dp),
                            label = { Text("Max") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    Spacer(Modifier.height(spacing.md))

                    Button(
                        onClick = { scan() },
                        enabled = core.attached && pattern.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text("Scan", style = MaterialTheme.typography.labelLarge)
                    }

                    if (probeError.isNotBlank()) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = "Error: $probeError",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }

                    if (hits.isNotEmpty()) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = "$probeCount hits in ${probeDuration}ms — tap one to inspect",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(hits.take(64), key = { it }) { address ->
            val selected = peekAddress == address
            Surface(
                onClick = { peek(address) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "0x" + java.lang.Long.toHexString(address),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (peekAddress != 0L) {
            item {
                ExpressiveCard {
                    Column {
                        Text(
                            text = "Inspecting 0x" + java.lang.Long.toHexString(peekAddress),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "Walk around in-game. The row that changes is your position.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(spacing.md))

                        if (peekValues.isEmpty()) {
                            Text(
                                text = "Waiting for the core…",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        } else {
                            peekValues.forEachIndexed { index, value ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "+${index * 4}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(44.dp),
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        onClick = { saveFrom(peekAddress, index) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.secondaryContainer,
                                    ) {
                                        Text(
                                            text = "Save",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = "“Save” treats this row as X, the next as Y, the one after as Z. " +
                                    if (probeBase > 0L) {
                                        "Offsets are stored relative to the library base, so they keep " +
                                            "working across restarts."
                                    } else {
                                        "Scan a library first so offsets can be stored relative to its base."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            ExpressiveCard {
                Column {
                    Text(
                        text = "How to find the pattern",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "1. Pull libminecraftpe.so from the installed APK.\n" +
                            "2. Find the level/player pointer chain for your version in a disassembler.\n" +
                            "3. Copy 8–16 stable bytes around it as the pattern.\n" +
                            "4. Scan here, tap hits, and confirm the floats track your position.\n" +
                            "5. Save — the offsets are pushed to the core automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showManual) {
        ManualOffsetDialog(
            defaultVersion = core.gameVersion.ifBlank { "1.21.51" },
            onDismiss = { showManual = false },
            onSave = { version, x, y, z ->
                store.save(OffsetStore.Offsets(version, x, y, z, module))
                refresh()
                showManual = false
            },
        )
    }
}

@Composable
private fun ManualOffsetDialog(
    defaultVersion: String,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, Long) -> Unit,
) {
    var version by remember { mutableStateOf(defaultVersion) }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var z by remember { mutableStateOf("") }
    val xv = parseOffset(x)
    val yv = parseOffset(y)
    val zv = parseOffset(z)
    val valid = version.isNotBlank() && xv != null && yv != null && zv != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add offsets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Version") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it.filter { c -> c.isDigit() || c in "xXabcdefABCDEF" } },
                    label = { Text("X offset (RVA)") },
                    placeholder = { Text("0x1A2B3C") },
                    singleLine = true,
                    isError = x.isNotBlank() && xv == null,
                    supportingText = {
                        if (x.isNotBlank() && xv == null) Text("Enter a decimal or 0x-prefixed hex value")
                    },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it.filter { c -> c.isDigit() || c in "xXabcdefABCDEF" } },
                    label = { Text("Y offset (RVA)") },
                    placeholder = { Text("0x1A2B3C") },
                    singleLine = true,
                    isError = y.isNotBlank() && yv == null,
                    supportingText = {
                        if (y.isNotBlank() && yv == null) Text("Enter a decimal or 0x-prefixed hex value")
                    },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                OutlinedTextField(
                    value = z,
                    onValueChange = { z = it.filter { c -> c.isDigit() || c in "xXabcdefABCDEF" } },
                    label = { Text("Z offset (RVA)") },
                    placeholder = { Text("0x1A2B3C") },
                    singleLine = true,
                    isError = z.isNotBlank() && zv == null,
                    supportingText = {
                        if (z.isNotBlank() && zv == null) Text("Enter a decimal or 0x-prefixed hex value")
                    },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val a = parseOffset(x) ?: return@TextButton
                    val b = parseOffset(y) ?: return@TextButton
                    val c = parseOffset(z) ?: return@TextButton
                    onSave(version.trim(), a, b, c)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun parseOffset(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    return if (text.startsWith("0x", ignoreCase = true) || text.startsWith("0X")) {
        text.substring(2).toLongOrNull(16)
    } else {
        text.toLongOrNull(10) ?: text.toLongOrNull(16)
    }
}
