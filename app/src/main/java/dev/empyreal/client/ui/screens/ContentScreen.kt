package dev.empyreal.client.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.empyreal.client.core.content.ContentImport
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.components.SectionHeader
import dev.empyreal.client.ui.theme.LocalSpacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingKind by remember { mutableStateOf<ContentImport.Kind?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val kind = pendingKind
        pendingKind = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        val message = when (val result = ContentImport.import(context, kind, uri, displayName(context, uri))) {
            is ContentImport.Result.HandedOver -> "Handed the ${result.kind.label.lowercase()} to Minecraft — check the game."
            ContentImport.Result.MinecraftMissing -> "Minecraft isn't installed."
            ContentImport.Result.WrongType -> "That isn't a ${kind.extensions.joinToString(" / ") { ".$it" }} file."
            ContentImport.Result.Refused -> "Minecraft didn't accept this file."
        }
        tick++
        scope.launch { snackbar.showSnackbar(message) }
    }

    val history = remember(tick) { ContentImport.history(context) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.sm, bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Content", style = MaterialTheme.typography.displaySmall, color = colors.onBackground)
                        Text(
                            "Worlds and packs · imported by Minecraft itself",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ContentTile(Icons.Rounded.Public, "Worlds", ".mcworld", Modifier.weight(1f)) {
                        pendingKind = ContentImport.Kind.WORLD
                        picker.launch(arrayOf("*/*"))
                    }
                    ContentTile(Icons.Rounded.AccountBox, "Skin Packs", ".mcpack", Modifier.weight(1f)) {
                        pendingKind = ContentImport.Kind.SKIN_PACK
                        picker.launch(arrayOf("*/*"))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ContentTile(Icons.Rounded.Palette, "Resource Packs", ".mcpack", Modifier.weight(1f)) {
                        pendingKind = ContentImport.Kind.RESOURCE_PACK
                        picker.launch(arrayOf("*/*"))
                    }
                    ContentTile(Icons.Rounded.Extension, "Behavior Packs", ".mcpack", Modifier.weight(1f)) {
                        pendingKind = ContentImport.Kind.BEHAVIOR_PACK
                        picker.launch(arrayOf("*/*"))
                    }
                }
            }
            item {
                FilledTonalButton(
                    onClick = {
                        pendingKind = ContentImport.Kind.ADDON
                        picker.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(spacing.sm))
                    Text("Import .mcaddon", fontWeight = FontWeight.Bold)
                }
            }

            item {
                ExpressiveCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            "Minecraft imports the file itself — Empyreal only hands it over. " +
                                "Your game folder stays untouched and nothing needs extra storage access. " +
                                "Worlds show up under Play, packs under Settings → Resource / Behavior Packs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Recently imported")
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Pick a world or pack above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                items(history.size, key = { history[it].time.toString() + it }) { index ->
                    val entry = history[index]
                    ExpressiveCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    entry.kind.label.take(1),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${entry.kind.label} · ${SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(entry.time))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun ContentTile(icon: ImageVector, label: String, ext: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    ExpressiveCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(spacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, fontWeight = FontWeight.Bold)
            Text(ext, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
    }
    return uri.lastPathSegment ?: "file"
}
