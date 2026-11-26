package dev.empyreal.core.module

import android.os.Process
import android.util.Log
import dev.empyreal.core.modules.CoordinatesModule
import dev.empyreal.core.modules.GameInfoModule
import dev.empyreal.core.modules.PeekModule
import dev.empyreal.core.modules.ProbeModule
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

object CoreModuleRegistry {

    private val modules = listOf(
        GameInfoModule(),
        CoordinatesModule(),
        ProbeModule(),
        PeekModule(),
    )

    private val byId = modules.associateBy { it.id }
    private val tickCounter = AtomicLong(0)

    @Volatile
    private var started = false

    @Volatile
    private var lastTickNs = System.nanoTime()

    fun start() {
        if (started) return
        started = true

        thread(name = "empyreal-core-tick", isDaemon = true) {
            while (true) {
                val startNs = System.nanoTime()
                val tick = tickCounter.incrementAndGet()
                val context = TickContext(
                    tick = tick,
                    deltaMs = (startNs - lastTickNs) / 1_000_000L,
                )
                lastTickNs = startNs

                modules.forEach { module ->
                    if (module.enabled) {
                        runCatching { module.tick(context) }
                            .onFailure { Log.w("EmpyrealCore", "module ${module.id} tick failed", it) }
                    }
                }

                val elapsedNs = System.nanoTime() - startNs
                val targetNs = 50_000_000L
                if (elapsedNs < targetNs) {
                    runCatching { Thread.sleep((targetNs - elapsedNs) / 1_000_000L) }
                }
            }
        }
    }

    fun setEnabled(id: String, on: Boolean) {
        byId[id]?.setEnabled(on)
    }

    fun applySettings(id: String, settings: Map<String, String>) {
        byId[id]?.applySettings(settings)
    }

    fun syncEnabled(map: Map<String, String>) {
        map.forEach { (id, raw) ->
            if (id == "gameinfo") return@forEach
            byId[id]?.setEnabled(raw.equals("true", ignoreCase = true))
        }
    }

    fun moduleIds(): List<String> = modules.map { it.id }

    fun snapshot(): StateFrame {
        val values = LinkedHashMap<String, String>()
        val activeIds = ArrayList<String>(modules.size)
        modules.forEach { module ->
            if (!module.enabled) return@forEach
            activeIds += module.id
            runCatching { module.writeState() }
                .onSuccess { map ->
                    map.forEach { (key, value) ->
                        values["${module.id}.$key"] = value
                    }
                }
                .onFailure { Log.w("EmpyrealCore", "module ${module.id} writeState failed", it) }
        }
        return StateFrame(
            type = StateFrame.TYPE_STATE,
            pid = Process.myPid(),
            tick = tickCounter.get(),
            modules = activeIds,
            values = values,
        )
    }
}
