package dev.empyreal.core.hud

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import kotlin.math.roundToInt

object InGameHud {

    private const val VIEW_ID = 0x00e11d0a

    private val main = Handler(Looper.getMainLooper())

    private var view: HudView? = null

    private var frames = 0
    private var sampleStart = 0L
    private var sampling = false

    @Volatile
    var onModuleToggle: ((String, Boolean) -> Unit)? = null

    @Volatile
    var onMenuMoved: ((Float, Float) -> Unit)? = null

    @Volatile
    var onSpeedrunAction: ((String) -> Unit)? = null

    fun install(activity: Activity) {
        HudState.onModuleToggle = { id, on -> onModuleToggle?.invoke(id, on) }
        HudState.onMenuMoved = { x, y -> onMenuMoved?.invoke(x, y) }
        HudState.onSpeedrunAction = { action -> onSpeedrunAction?.invoke(action) }
        HudState.foreground = true

        observeTouches(activity.window)
        AutoTap.attach(activity)

        val decor = activity.window?.decorView as? ViewGroup ?: return
        val currentView = view
        if (currentView != null && currentView.context === activity && currentView.parent === decor) {
            startLoopsIfNeeded()
            return
        }

        currentView?.let { old -> (old.parent as? ViewGroup)?.removeView(old) }

        val existing = decor.findViewById<HudView>(VIEW_ID)
        val hud = existing ?: HudView(activity).apply {
            id = VIEW_ID
            decor.addView(
                this,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        view = hud
        startLoopsIfNeeded()
    }

    private fun startLoopsIfNeeded() {
        if (sampling) return
        sampling = true
        main.post {
            if (!sampling) return@post
            frames = 0
            sampleStart = SystemClock.uptimeMillis()
            Choreographer.getInstance().removeFrameCallback(fpsTick)
            Choreographer.getInstance().postFrameCallback(fpsTick)
        }
        main.removeCallbacks(tapLoop)
        main.post(tapLoop)
    }

    fun uninstall(activity: Activity? = null) {
        val currentView = view
        if (activity != null && currentView != null && currentView.context !== activity) {
            AutoTap.detach(activity)
            return
        }

        currentView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
        view = null
        AutoTap.stop()
        AutoTap.detach(activity)

        sampling = false
        main.removeCallbacks(tapLoop)
        main.post { Choreographer.getInstance().removeFrameCallback(fpsTick) }

        HudState.foreground = false
        HudState.menuOpen = false
        HudState.userHolding = false
        HudState.fps = 0
        HudState.cps = 0
        ClickCounter.reset()
    }

    private fun observeTouches(window: Window?) {
        val target = window ?: return
        val current = target.callback ?: return
        if (current is TouchCountingCallback) return
        runCatching { target.callback = TouchCountingCallback(current) }
    }

    private class TouchCountingCallback(private val real: Window.Callback) : Window.Callback by real {
        private var activePointers = 0

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (!AutoTap.isSyntheticEvent) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePointers = 1
                        HudState.userHolding = !HudState.menuOpen
                        if (!HudState.menuOpen) ClickCounter.press()
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        activePointers = event.pointerCount
                        if (!HudState.menuOpen) ClickCounter.press()
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        activePointers = (event.pointerCount - 1).coerceAtLeast(0)
                        if (activePointers == 0) HudState.userHolding = false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activePointers = 0
                        HudState.userHolding = false
                    }
                }
            }
            return real.dispatchTouchEvent(event)
        }
    }

    private val fpsTick = object : Choreographer.FrameCallback {
        override fun doFrame(nanos: Long) {
            if (!sampling) return
            frames++
            val now = SystemClock.uptimeMillis()
            val elapsed = now - sampleStart
            if (elapsed >= 500L) {
                HudState.fps = (frames * 1000f / elapsed).roundToInt()
                HudState.cps = ClickCounter.current()
                frames = 0
                sampleStart = now
                view?.invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val tapLoop = object : Runnable {
        override fun run() {
            if (!sampling) return
            val requireHold = HudState.bool("autoclicker", "hold", false)
            val canTap = HudState.foreground &&
                !HudState.menuOpen &&
                HudState.isEnabled("autoclicker") &&
                (!requireHold || HudState.userHolding)
            if (canTap) {
                AutoTap.start()
                AutoTap.tap()
                main.postDelayed(this, AutoTap.nextDelayMs())
            } else {
                AutoTap.stop()
                main.postDelayed(this, 120L)
            }
        }
    }

    fun refresh() {
        main.post { view?.invalidate() }
    }
}

private object ClickCounter {
    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    fun press() {
        val now = SystemClock.uptimeMillis()
        timestamps.addLast(now)
        prune(now)
        HudState.cps = timestamps.size
    }

    @Synchronized
    fun current(): Int {
        val now = SystemClock.uptimeMillis()
        prune(now)
        return timestamps.size
    }

    @Synchronized
    fun reset() {
        timestamps.clear()
    }

    private fun prune(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() > 1000L) {
            timestamps.removeFirst()
        }
    }
}
