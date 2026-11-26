package dev.empyreal.client.core.log

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

object ExitReason {

    data class Record(
        val timestamp: Long,
        val reason: Int,
        val description: String,
        val status: Int,
        val importance: Int,
        val pss: Long,
        val rss: Long,
        val trace: String?,
        val nativeMapping: String? = null,
    )

    fun read(context: Context, max: Int = 6, includeNativeMapping: Boolean = false): List<Record> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return runCatching { readRecent(context, max, includeNativeMapping) }.getOrDefault(emptyList())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readRecent(context: Context, max: Int, includeNativeMapping: Boolean): List<Record> {
        val am = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return am.getHistoricalProcessExitReasons(context.packageName, 0, max)
            .map { info -> toRecord(info, includeNativeMapping) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun toRecord(info: ApplicationExitInfo, includeNativeMapping: Boolean): Record {
        // Android 12+ native tombstones are protobuf, not text. Do not render binary bytes as UTF-8.
        val trace = if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            null
        } else {
            runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        }
        val nativeMapping = if (includeNativeMapping && info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { info.traceInputStream?.use { NativeTombstoneMapping.summarize(it, info.pid) } }.getOrNull()
        } else null
        return Record(
            timestamp = info.timestamp,
            reason = info.reason,
            description = info.description.orEmpty(),
            status = info.status,
            importance = info.importance,
            pss = info.pss,
            rss = info.rss,
            trace = trace,
            nativeMapping = nativeMapping,
        )
    }

    fun reasonText(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Java crash (uncaught exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
        ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
        ApplicationExitInfo.REASON_EXIT_SELF -> "Exited by itself"
        ApplicationExitInfo.REASON_SIGNALED -> "Killed by a signal"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed for low memory"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "Stopped by the user"
        ApplicationExitInfo.REASON_USER_STOPPED -> "Stopped by the user"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "A dependency died"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to initialise"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Killed for resource use"
        ApplicationExitInfo.REASON_OTHER -> "Other"
        else -> "Reason $reason"
    }

    fun describe(record: Record): String = when (record.reason) {
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Signal ${record.status} · ${signalName(record.status)}"
        ApplicationExitInfo.REASON_CRASH -> "Exit code ${record.status}"
        else -> record.description
    }

    fun signalName(status: Int): String = when (status) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        4 -> "SIGILL"
        5 -> "SIGTRAP"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        13 -> "SIGPIPE"
        14 -> "SIGALRM"
        15 -> "SIGTERM"
        else -> "signal $status"
    }
}
