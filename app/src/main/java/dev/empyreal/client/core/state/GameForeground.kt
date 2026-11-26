package dev.empyreal.client.core.state

import java.util.concurrent.CopyOnWriteArrayList

object GameForeground {

    @Volatile
    var isForeground: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun set(foreground: Boolean) {
        if (foreground == isForeground) return
        isForeground = foreground
        runCatching { PlayStats.onForegroundChanged(foreground) }
        listeners.forEach { listener -> runCatching { listener(foreground) } }
    }
}
