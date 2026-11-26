package dev.empyreal.client.core.state

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

object PlayStats {

    data class Snapshot(
        val todayMs: Long = 0,
        val totalMs: Long = 0,
        val sessions: Int = 0,
        val inSession: Boolean = false,
    )

    private const val PREFS = "empyreal_stats"
    private const val KEY_DAY = "day"
    private const val KEY_TODAY = "today_ms"
    private const val KEY_TOTAL = "total_ms"
    private const val KEY_SESSIONS = "sessions"

    private const val MIN_SESSION_MS = 2_000L

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var sessionStartElapsedMs: Long = 0L

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sessionStartElapsedMs = 0L
        rollDayIfNeeded()
        publish()
    }

    @Synchronized
    fun onForegroundChanged(foreground: Boolean) {
        if (!::prefs.isInitialized) return
        if (foreground) {
            if (sessionStartElapsedMs == 0L) {
                sessionStartElapsedMs = SystemClock.elapsedRealtime()
            }
            publish()
            return
        }
        val start = sessionStartElapsedMs
        if (start == 0L) return
        sessionStartElapsedMs = 0L

        val delta = (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
        if (delta < MIN_SESSION_MS) {
            publish()
            return
        }
        rollDayIfNeeded()
        prefs.edit()
            .putLong(KEY_TODAY, prefs.getLong(KEY_TODAY, 0) + delta)
            .putLong(KEY_TOTAL, prefs.getLong(KEY_TOTAL, 0) + delta)
            .putInt(KEY_SESSIONS, prefs.getInt(KEY_SESSIONS, 0) + 1)
            .apply()
        publish()
    }

    private fun rollDayIfNeeded() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, null) == today) return
        prefs.edit().putString(KEY_DAY, today).putLong(KEY_TODAY, 0).apply()
    }

    private fun publish() {
        _snapshot.value = Snapshot(
            todayMs = prefs.getLong(KEY_TODAY, 0),
            totalMs = prefs.getLong(KEY_TOTAL, 0),
            sessions = prefs.getInt(KEY_SESSIONS, 0),
            inSession = sessionStartElapsedMs != 0L,
        )
    }

    fun format(ms: Long): String {
        val minutes = ms / 60_000L
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${"%02d".format(minutes % 60)}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
