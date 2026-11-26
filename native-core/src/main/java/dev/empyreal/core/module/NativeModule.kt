package dev.empyreal.core.module

import android.util.Log

data class TickContext(
    val tick: Long,
    val deltaMs: Long,
)

abstract class NativeModule(
    val id: String,
    val name: String,
    val description: String,
) {

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var settings: Map<String, String> = emptyMap()
        private set

    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        runCatching { if (on) onEnable() else onDisable() }
            .onFailure { Log.w("EmpyrealCore", "module $id setEnabled($on) failed", it) }
    }

    fun applySettings(next: Map<String, String>) {
        settings = next
        runCatching { onSettingsChanged() }
            .onFailure { Log.w("EmpyrealCore", "module $id applySettings failed", it) }
    }

    protected open fun onEnable() {}
    protected open fun onDisable() {}
    protected open fun onSettingsChanged() {}

    open fun tick(context: TickContext) {}

    open fun writeState(): Map<String, String> = emptyMap()

    open val ready: Boolean get() = true
}
