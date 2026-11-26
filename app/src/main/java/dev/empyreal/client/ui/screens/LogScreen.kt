package dev.empyreal.client.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.empyreal.client.core.log.AppLog
import dev.empyreal.client.core.log.ExitReason
import dev.empyreal.client.core.log.LastSession
import dev.empyreal.client.ui.components.ExpressiveCard
import dev.empyreal.client.ui.components.KeyValue
import dev.empyreal.client.ui.theme.LocalSpacing
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LogLine(
    val time: String,
    val level: Char,
    val tag: String,
    val text: String,
)

private val LINE_APP = Regex(
    """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^:]{1,64}):\s?(.*)$""",
)

private val LINE_LOGCAT = Regex(
    """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(?:(\d+)\s+)?(\d+)\s+([VDIWEF])\s+(.*)$""",
)

private fun header(line: String): LogLine? {
    LINE_APP.find(line)?.let { match ->
        return LogLine(
            time = match.groupValues[1],
            level = match.groupValues[2][0],
            tag = match.groupValues[3].trim(),
            text = match.groupValues[4],
        )
    }
    LINE_LOGCAT.find(line)?.let { match ->
        val body = match.groupValues[5]
        val colon = body.lastIndexOf(": ")
        val tag = if (colon >= 0) body.substring(0, colon).trim() else ""
        val text = if (colon >= 0) body.substring(colon + 2) else body.trim()
        return LogLine(match.groupValues[1], match.groupValues[4][0], tag, text)
    }
    return null
}

private fun isContinuation(line: String): Boolean {
    if (line.isBlank()) return false
    val trimmed = line.trimStart()
    return line.firstOrNull()?.isWhitespace() == true ||
        trimmed.startsWith("at ") ||
        trimmed.startsWith("Caused by") ||
        trimmed.startsWith("... ") ||
        trimmed.startsWith("suppressed:")
}

private fun parseAll(raw: List<String>): List<LogLine> {
    val out = ArrayList<LogLine>(raw.size)
    raw.forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.isBlank()) return@forEach
        val head = header(line)
        if (head != null) {
            out.add(head)
            return@forEach
        }
        val previous = out.lastOrNull()
        when {
            isContinuation(line) && previous != null ->
                out[out.lastIndex] = previous.copy(text = previous.text + "\n" + line.trim())
            line.trimStart().startsWith("===") ->
                out.add(LogLine("", 'I', "session", line.trim()))
            previous != null && !line.startsWith("---") ->
                out[out.lastIndex] = previous.copy(text = previous.text + "\n" + line)
            else ->
                out.add(LogLine("", 'I', "", line))
        }
    }
    return out
}

private const val HEADER_ITEMS = 5
private const val COPY_LIMIT = 250_000

private fun severity(level: Char): Int = when (level) {
    'V' -> 0
    'D' -> 1
    'I' -> 2
    'W' -> 3
    'E' -> 4
    'F', 'A' -> 4
    else -> 2
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val revision by AppLog.revision.collectAsState()
    var paused by rememberSaveable { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<String>>(emptyList()) }
    var minSeverity by rememberSaveable { mutableStateOf(1) }
    var query by rememberSaveable { mutableStateOf("") }
    var exitText by remember { mutableStateOf(LastSession.readExit(context)) }
    var showTrace by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    val all = remember(revision, paused, frozen) {
        if (paused && frozen.isNotEmpty()) frozen else AppLog.snapshot()
    }
    val lines = remember(all, minSeverity, query) {
        parseAll(all)
            .filter { severity(it.level) >= minSeverity }
            .filter { query.isBlank() || it.text.contains(query, true) || it.tag.contains(query, true) }
    }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 2
        }
    }

    LaunchedEffect(lines.size, paused) {
        if (!paused && atBottom && lines.isNotEmpty()) {
            listState.scrollToItem(HEADER_ITEMS + lines.lastIndex)
        }
    }

    val shareLog: () -> Unit = {
        scope.launch {
            val file = File(File(context.cacheDir, "logs").apply { mkdirs() }, "empyreal-log.txt")
            file.writeText(
                buildString {
                    appendLine(AppLog.full())
                    appendLine()
                    appendLine("=== how the previous session ended ===")
                    appendLine(exitText ?: "(no record)")
                    appendLine()
                    appendLine("=== logcat crash buffer ===")
                    appendLine(LastSession.readCrashBuffer(context) ?: "(empty)")
                },
            )
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Empyreal log")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(send, "Share log"))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.sm),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = "Log",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (paused) {
                        paused = false
                    } else {
                        frozen = AppLog.snapshot()
                        paused = true
                    }
                }) {
                    Icon(
                        if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                        tint = if (paused) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = spacing.xs)) {
                Text(
                    text = "Live log",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Every step Empyreal takes, everything the game prints, and what Android " +
                        "recorded about the last time the process ended — kept on disk, so a crash " +
                        "leaves its last lines behind.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        item {
            ExpressiveCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(spacing.sm))
                        Text(
                            text = "Last session",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))

                    val records = remember(exitText) { LastSession.records(context) }
                    if (records.isEmpty()) {
                        Text(
                            text = "Android has no record of this process ending unexpectedly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        records.take(3).forEach { record ->
                            KeyValue(
                                "How it ended",
                                ExitReason.reasonText(record.reason) + " — " + ExitReason.describe(record),
                            )
                            KeyValue("When", stamp(record.timestamp))
                            if (record.pss > 0) KeyValue("Memory", "${record.pss / 1024} MB")
                        }
                        if (exitText?.contains("--- trace ---") == true) {
                            Spacer(Modifier.height(spacing.xs))
                            TextButton(onClick = { showTrace = true }) { Text("Tombstone / stack trace") }
                        }
                    }

                    LastSession.readCrashBuffer(context)?.let { buffer ->
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = buffer.lineSequence()
                                .filter { it.contains("Fatal") || it.contains("signal") }
                                .toList()
                                .takeLast(3)
                                .joinToString("\n")
                                .ifBlank { "Logcat kept ${buffer.lineSequence().count()} crash lines." },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Share puts the full log, this record and both logcat buffers in one text file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ExpressiveCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        listOf(1 to "All", 2 to "Info", 3 to "Warnings", 4 to "Errors").forEach { (level, label) ->
                            FilterChip(
                                selected = minSeverity == level,
                                onClick = { minSeverity = level },
                                label = { Text(label) },
                                shape = RoundedCornerShape(100.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    labelColor = colors.onSurfaceVariant,
                                    selectedContainerColor = colors.primaryContainer,
                                    selectedLabelColor = colors.onPrimaryContainer,
                                ),
                                border = null,
                            )
                        }
                    }
                    Spacer(Modifier.height(spacing.sm))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter — a tag, a class, a word") },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        TextButton(onClick = {
                            runCatching {
                                val full = AppLog.full()
                                val clipped = if (full.length > COPY_LIMIT) {
                                    "… ${full.length - COPY_LIMIT} earlier characters omitted. " +
                                        "Use Share for the whole log.\n\n" + full.takeLast(COPY_LIMIT)
                                } else {
                                    full
                                }
                                clipboard.setText(AnnotatedString(clipped))
                            }
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.xs))
                            Text("Copy")
                        }
                        TextButton(onClick = shareLog) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.xs))
                            Text("Share")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showClear = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(spacing.xs))
                            Text("Clear")
                        }
                    }
                }
            }
        }

        item {
            ExpressiveCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (paused) "Paused" else "Streaming",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${lines.size} lines",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        itemsIndexed(lines) { _, line ->
            val tint = when (line.level) {
                'E', 'F' -> colors.error
                'W' -> colors.tertiary
                else -> colors.onSurfaceVariant
            }
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.xs, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = line.time.takeIf { it.length >= 12 }?.substring(6) ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text(
                            text = line.level.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text(
                            text = line.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (line.level == 'E' || line.level == 'F') colors.error else colors.onSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }

    if (showTrace) {
        AlertDialog(
            onDismissRequest = { showTrace = false },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exitText.orEmpty()))
                    showTrace = false
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { showTrace = false }) { Text("Close") } },
            text = {
                SelectionContainer {
                    Text(
                        text = exitText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear the log?") },
            text = { Text("The live log and the record of the previous session are deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    AppLog.clear(context)
                    LastSession.clear(context)
                    exitText = null
                    frozen = emptyList()
                    showClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } },
        )
    }
}

private fun stamp(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date(millis))
