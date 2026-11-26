package dev.empyreal.client.core.state

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.abs

object SpeedrunTimer {

    data class Split(
        val index: Int,
        val atMs: Long,
        val deltaMs: Long,
    )

    data class State(
        val running: Boolean = false,
        val startedAt: Long = 0L,
        val accumulated: Long = 0L,
        val splits: List<Split> = emptyList(),
        val bestSplits: Map<Int, Long> = emptyMap(),
        val finished: Boolean = false,
    ) {
        val elapsedMs: Long
            get() = if (running) {
                accumulated + (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            } else {
                accumulated
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start() {
        _state.update { s ->
            if (s.running) s
            else s.copy(running = true, startedAt = SystemClock.elapsedRealtime(), finished = false)
        }
    }

    fun pause() {
        _state.update { s ->
            if (!s.running) s
            else s.copy(
                running = false,
                accumulated = s.accumulated + (SystemClock.elapsedRealtime() - s.startedAt).coerceAtLeast(0L),
            )
        }
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun split() {
        _state.update { s ->
            if (!s.running) return@update s
            val now = s.elapsedMs
            val index = s.splits.size
            val best = s.bestSplits[index]
            val delta = if (best != null) now - best else 0L
            val bestSplits = if (best == null || now < best) s.bestSplits + (index to now) else s.bestSplits
            s.copy(
                splits = s.splits + Split(index, now, delta),
                bestSplits = bestSplits,
            )
        }
    }

    fun reset() {
        _state.update { prev ->
            State(bestSplits = prev.bestSplits)
        }
    }

    fun format(ms: Long, subseconds: Boolean = true): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSec = safeMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        val cs = (safeMs % 1000) / 10
        val base = if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
        return if (subseconds) String.format(Locale.US, "%s.%02d", base, cs) else base
    }

    fun formatDelta(ms: Long): String {
        val sign = if (ms < 0) "-" else "+"
        val a = abs(ms)
        val s = a / 1000
        val cs = (a % 1000) / 10
        return String.format(Locale.US, "%s%d.%02d", sign, s, cs)
    }
}
