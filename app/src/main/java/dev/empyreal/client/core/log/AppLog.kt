package dev.empyreal.client.core.log

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {

    private const val CAPACITY = 4000
    private const val MAX_FILE_BYTES = 3L * 1024 * 1024
    private const val PREVIOUS_TAIL = 1200

    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private var writer: BufferedWriter? = null
    private var logFile: File? = null
    private var bytes = 0L
    private var lastBump = 0L

    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    val revision = MutableStateFlow(0L)

    fun init(context: Context) {
        synchronized(lock) {
            if (writer != null) return
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val file = File(dir, "live.log")

            runCatching { file.useLines { lines -> lines.toList().takeLast(PREVIOUS_TAIL) } }
                .getOrNull()
                ?.forEach { buffer.addLast(it) }
            openWriter(file)
            append("=== session ${stamp()} · pid ${android.os.Process.myPid()} ===")
            revision.value = revision.value + 1
        }
    }

    private fun openWriter(file: File) {
        runCatching {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val old = File(file.parentFile, "live.1.log")
                old.delete()
                file.renameTo(old)
            }
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
            logFile = file
            bytes = file.length()
        }
    }

    private fun rotate() {
        val file = logFile ?: return
        writer?.let { runCatching { it.close() } }
        val old = File(file.parentFile, "live.1.log")
        old.delete()
        file.renameTo(old)
        writer = null
        openWriter(file)
    }

    fun d(tag: String, msg: String) = line("D", tag, msg, null)
    fun i(tag: String, msg: String) = line("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = line("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = line("E", tag, msg, t)

    fun raw(line: String) {
        if (line.isBlank()) return

        if (OWN_LINE.containsMatchIn(line)) return
        append(line.trimEnd())
    }

    private val OWN_LINE = Regex("""^\d{2}-\d{2}\s+\S+\s+\d+\s+\d+\s+[VDIWEF]\s+Empyreal[A-Za-z]*\s*:""")

    private fun line(level: String, tag: String, msg: String, t: Throwable?) {
        val text = "${stamp()} $level/$tag: $msg" + (t?.let { "\n" + Log.getStackTraceString(it).trimEnd() } ?: "")
        val priority = when (level) {
            "E" -> Log.ERROR
            "W" -> Log.WARN
            "D" -> Log.DEBUG
            else -> Log.INFO
        }
        runCatching { Log.println(priority, tag, msg + (t?.let { "\n" + Log.getStackTraceString(it) } ?: "")) }
        append(text)
    }

    private fun append(text: String) {
        synchronized(lock) {
            buffer.addLast(text)
            while (buffer.size > CAPACITY) buffer.removeFirst()
            val out = writer
            if (out != null) {
                runCatching {
                    out.write(text)
                    out.write("\n")
                    out.flush()
                    bytes += text.toByteArray(Charsets.UTF_8).size + 1
                    if (bytes > MAX_FILE_BYTES) rotate()
                }
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastBump >= 250) {
                lastBump = now
                revision.value = revision.value + 1
            }
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    fun full(): String {
        val previous = runCatching {
            File(logFile?.parentFile, "live.1.log").useLines { lines -> lines.toList() }
        }.getOrDefault(emptyList())
        return (previous + snapshot()).joinToString("\n")
    }

    fun clear(context: Context) {
        synchronized(lock) {
            buffer.clear()
            writer?.let { runCatching { it.close() } }
            writer = null
            val dir = File(context.filesDir, "logs")
            File(dir, "live.log").delete()
            File(dir, "live.1.log").delete()
            openWriter(File(dir, "live.log"))
            append("=== log cleared ${stamp()} ===")
            revision.value = revision.value + 1
        }
    }

    private fun stamp(): String = synchronized(format) { format.format(Date()) }
}
