package dev.empyreal.core.hud

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import kotlin.random.Random

object AutoTap {

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    @Volatile
    private var host: ViewGroup? = null

    private val syntheticDispatch = ThreadLocal<Boolean>()
    private var pendingUp: Runnable? = null

    val isSyntheticEvent: Boolean
        get() = syntheticDispatch.get() == true

    fun attach(activity: Activity) {
        host = activity.window?.decorView as? ViewGroup
    }

    fun detach(activity: Activity? = null) {
        val current = host
        if (activity == null || current === activity.window?.decorView) {
            cancelPendingUp()
            host = null
        }
    }

    fun start() {
        running = true
    }

    fun stop() {
        running = false
        cancelPendingUp()
    }

    fun isRunning(): Boolean = running

    private fun cancelPendingUp() {
        pendingUp?.let { main.removeCallbacks(it) }
        pendingUp = null
    }

    fun tap() {
        val target = host ?: return
        if (!target.isAttachedToWindow) return
        val width = target.width.toFloat()
        val height = target.height.toFloat()
        if (width <= 0f || height <= 0f) return

        cancelPendingUp()

        val xPercent = (HudState.float("autoclicker", "tap_x", 50f) / 100f).coerceIn(0f, 1f)
        val yPercent = (HudState.float("autoclicker", "tap_y", 72f) / 100f).coerceIn(0f, 1f)
        val jitter = if (HudState.bool("autoclicker", "jitter", true)) 3f else 0f
        val x = ((xPercent * width) + Random.nextFloat() * jitter - jitter / 2f)
            .coerceIn(1f, (width - 1f).coerceAtLeast(1f))
        val y = ((yPercent * height) + Random.nextFloat() * jitter - jitter / 2f)
            .coerceIn(1f, (height - 1f).coerceAtLeast(1f))

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            syntheticDispatch.set(true)
            runCatching { target.dispatchTouchEvent(down) }
        } finally {
            syntheticDispatch.set(false)
            down.recycle()
        }

        val holdMs = (nextDelayMs() / 3L).coerceIn(18L, 42L)
        val release = Runnable {
            pendingUp = null
            val releaseTarget = host ?: return@Runnable
            if (!releaseTarget.isAttachedToWindow) return@Runnable
            val upTime = SystemClock.uptimeMillis().coerceAtLeast(downTime + 1L)
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            try {
                syntheticDispatch.set(true)
                runCatching { releaseTarget.dispatchTouchEvent(up) }
            } finally {
                syntheticDispatch.set(false)
                up.recycle()
            }
        }
        pendingUp = release
        main.postDelayed(release, holdMs)
    }

    fun nextDelayMs(): Long {
        val min = HudState.float("autoclicker", "cps_min", 8f).coerceIn(1f, 30f)
        val max = HudState.float("autoclicker", "cps_max", 12f).coerceIn(min, 30f)
        val cps = min + (max - min) * Random.nextFloat()
        return (1000f / cps).toLong().coerceAtLeast(25L)
    }
}
