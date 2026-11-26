package dev.empyreal.client.core.minecraft

import android.os.Process
import dev.empyreal.client.core.log.AppLog
import dev.empyreal.client.game.GameHost
import dev.empyreal.core.NativeLoader
import dev.empyreal.core.module.CoreModuleRegistry
import dev.empyreal.core.module.StateFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

object CoreBridge {

    data class CoreState(
        val attached: Boolean = false,
        val pid: Int = 0,
        val tick: Long = 0,
        val modules: List<String> = emptyList(),
        val values: Map<String, String> = emptyMap(),
    ) {
        val gameVersion: String get() = values["gameinfo.version"]?.takeIf { it != "unknown" } ?: ""
        val abi: String get() = values["gameinfo.abi"] ?: ""
        fun value(key: String): String? = values[key]
    }

    private val _state = MutableStateFlow(CoreState())
    val state: StateFlow<CoreState> = _state.asStateFlow()

    @Volatile
    var onVersionDetected: ((String) -> Unit)? = null

    @Volatile
    private var lastVersion: String? = null

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        runCatching { NativeLoader.load() }
            .onSuccess { AppLog.i("EmpyrealCore", "core native library loaded in pid ${Process.myPid()}") }
            .onFailure {
                android.util.Log.e("Empyreal", "core library not loaded", it)
                AppLog.e("EmpyrealCore", "core native library not loaded", it)
            }
        CoreModuleRegistry.start()
        AppLog.i("EmpyrealCore", "core module registry started")

        CoreModuleRegistry.setEnabled("gameinfo", true)

        thread(name = "empyreal-core-state", isDaemon = true) {
            while (true) {
                runCatching { refresh() }
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun refresh() {
        val frame: StateFrame = CoreModuleRegistry.snapshot()
        val version = frame.values["gameinfo.version"]
        if (!version.isNullOrBlank() && version != "unknown" && version != lastVersion) {
            lastVersion = version
            onVersionDetected?.invoke(version)
        }
        _state.value = CoreState(

            attached = GameHost.isGameActive(),
            pid = Process.myPid(),
            tick = frame.tick,
            modules = frame.modules,
            values = frame.values,
        )
    }

    fun pushSettings(moduleId: String, settings: Map<String, String>) {
        CoreModuleRegistry.applySettings(moduleId, settings)
    }

    fun setCoreModule(moduleId: String, enabled: Boolean) {
        CoreModuleRegistry.setEnabled(moduleId, enabled)
    }

    fun sync(enabled: Map<String, Boolean>, values: Map<String, Map<String, String>>) {
        CoreModuleRegistry.syncEnabled(enabled.mapValues { it.value.toString() })
        values.forEach { (moduleId, settings) -> CoreModuleRegistry.applySettings(moduleId, settings) }
    }
}
