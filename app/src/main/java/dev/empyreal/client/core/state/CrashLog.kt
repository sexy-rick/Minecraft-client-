package dev.empyreal.client.core.state

import android.app.Application
import android.os.Build
import android.util.Log
import dev.empyreal.client.core.log.AppLog
import dev.empyreal.client.game.GameHost
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {

    private const val FILE = "last_crash.txt"
    private const val MAX_BYTES = 64 * 1024

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.e("EmpyrealCrash", "uncaught exception on ${thread.name}", error) }
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: android.content.Context): String? =
        File(context.applicationContext.filesDir, FILE)
            .takeIf { it.isFile && it.length() > 0 }
            ?.readText()
            ?.takeIf { it.isNotBlank() }

    fun clear(context: android.content.Context) {
        File(context.applicationContext.filesDir, FILE).delete()
    }

    private fun write(app: Application, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US).format(Date())
        val text = buildString {
            appendLine("time: $stamp")
            appendLine("thread: ${thread.name}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("host: ${GameHost.describe()}")
            appendLine()
            appendLine(Log.getStackTraceString(error).trim())
            appendLine()
            appendLine("--- other threads ---")
            Thread.getAllStackTraces().forEach { (other, frames) ->
                if (other === thread) return@forEach
                appendLine(other.name)
                frames.take(10).forEach { appendLine("    at $it") }
            }
        }
        File(app.filesDir, FILE).writeText(text.take(MAX_BYTES))
    }
}
