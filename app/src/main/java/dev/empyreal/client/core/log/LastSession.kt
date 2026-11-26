package dev.empyreal.client.core.log

import android.app.ApplicationExitInfo
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LastSession {

    private const val DIR = "logs"
    private const val EXIT_FILE = "last_exit.txt"
    private const val CRASH_BUFFER = "last_crash_buffer.txt"
    private const val MAIN_BUFFER = "last_main_buffer.txt"

    fun collect(context: Context) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        runCatching { writeExit(context, dir) }
            .onFailure { AppLog.w(TAG, "exit reasons unavailable: ${it.message}") }
        runCatching { writeBuffer(dir, CRASH_BUFFER, listOf("logcat", "-b", "crash", "-v", "threadtime", "-d", "-t", "800")) }
        runCatching { writeBuffer(dir, MAIN_BUFFER, listOf("logcat", "-b", "main", "-b", "system", "-v", "threadtime", "-d", "-t", "6000")) }
    }

    private fun writeExit(context: Context, dir: File) {
        val records = ExitReason.read(context)
        cached = records
        if (records.isEmpty()) return
        val text = buildString {
            records.forEachIndexed { index, record ->
                if (index > 0) appendLine("\n" + "─".repeat(60))
                appendLine("time:      ${format(record.timestamp)}")
                appendLine("reason:    ${ExitReason.reasonText(record.reason)}")
                appendLine("detail:    ${ExitReason.describe(record)}")
                appendLine("status:    ${record.status}")
                appendLine("memory:    ${record.pss / 1024} MB pss · ${record.rss / 1024} MB rss")
                appendLine("importance: ${record.importance}")
                record.trace?.let { trace ->
                    appendLine()
                    appendLine("--- trace ---")
                    appendLine(trace.trimEnd().take(60_000))
                }
                if (record.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && android.os.Build.VERSION.SDK_INT >= 31) {
                    appendLine("Native tombstone is binary; see the captured crash-buffer backtrace when available.")
                }
            }
        }
        File(dir, EXIT_FILE).writeText(text)
        records.firstOrNull()?.let { record ->
            AppLog.i(TAG, "previous session ended: ${ExitReason.reasonText(record.reason)} — ${ExitReason.describe(record)}")
            record.trace?.let { trace ->
                firstFrame(trace)?.let { AppLog.w(TAG, "previous crash frame: $it") }
            }
        }
    }

    fun firstFrame(trace: String): String? {
        val lines = trace.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val signal = lines.firstOrNull { it.startsWith("***") || it.startsWith("signal ") }
        val backtrace = lines.indexOfFirst { it.contains("backtrace:") }
        val frame = if (backtrace >= 0) lines.drop(backtrace + 1).firstOrNull { it.startsWith("#") } else null
        return listOfNotNull(signal, frame).joinToString(" | ").ifBlank { null }
    }

    private fun writeBuffer(dir: File, name: String, command: List<String>) {
        val text = runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            try {
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                runCatching { process.destroy() }
                runCatching { process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
            }
        }.getOrDefault("")
        if (text.isBlank()) {
            File(dir, name).delete()
            return
        }
        File(dir, name).writeText(text.take(400_000))
    }

    fun records(context: Context): List<ExitReason.Record> =
        cached.ifEmpty { ExitReason.read(context) }

    @Volatile
    private var cached: List<ExitReason.Record> = emptyList()

    fun summary(context: Context): String? {
        val record = records(context).firstOrNull() ?: return null
        if (record.reason != ApplicationExitInfo.REASON_CRASH &&
            record.reason != ApplicationExitInfo.REASON_CRASH_NATIVE &&
            record.reason != ApplicationExitInfo.REASON_ANR
        ) {
            return null
        }
        return buildString {
            appendLine("time: ${format(record.timestamp)}")
            appendLine("reason: ${ExitReason.reasonText(record.reason)}")
            appendLine("detail: ${ExitReason.describe(record)}")
            if (record.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                val crashBuffer = runCatching { readCrashBuffer(context) }.getOrNull()
                NativeCrashSummary.extract(crashBuffer, context.packageName)?.let { summary ->
                    appendLine("\nRecent crash buffer (may be unrelated to this exit):")
                    appendLine(summary)
                }
            } else {
                record.trace?.let { trace ->
                    appendLine()
                    trace.lineSequence()
                        .filter { it.contains("signal ") || it.trim().startsWith("#0") || it.contains("Cmdline") }
                        .take(12)
                        .forEach { appendLine(it.trim()) }
                }
            }
        }.trim()
    }

    fun readExit(context: Context): String? = read(context, EXIT_FILE)
    fun readCrashBuffer(context: Context): String? = read(context, CRASH_BUFFER)
    fun readMainBuffer(context: Context): String? = read(context, MAIN_BUFFER)

    private fun read(context: Context, name: String): String? =
        File(File(context.filesDir, DIR), name)
            .takeIf { it.isFile && it.length() > 0 }
            ?.readText()
            ?.trim()
            ?.ifEmpty { null }

    fun clear(context: Context) {
        cached = emptyList()
        val dir = File(context.filesDir, DIR)
        listOf(EXIT_FILE, CRASH_BUFFER, MAIN_BUFFER).forEach { File(dir, it).delete() }
    }

    private fun format(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    private const val TAG = "EmpyrealExit"
}
