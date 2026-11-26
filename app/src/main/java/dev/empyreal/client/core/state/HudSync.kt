package dev.empyreal.client.core.state

import android.content.Context
import dev.empyreal.client.EmpyrealApp
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.model.SettingValue
import dev.empyreal.client.core.registry.ModuleRegistry
import dev.empyreal.core.hud.HudModuleWire
import dev.empyreal.core.hud.HudState
import dev.empyreal.core.hud.HudWidgetWire
import dev.empyreal.core.hud.HudWire
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

object HudSync {

    val WIDGETS = listOf("crosshair", "fps", "cps", "clock", "coords", "speedrun")

    @Volatile
    var hudEnabled = true

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        hudEnabled = ThemePrefs(context).hudEnabled
        pushNow()

        thread(name = "empyreal-hud-sync", isDaemon = true) {
            while (true) {
                runCatching { push() }
                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun pushNow() {
        if (!EmpyrealApp.isInitialized) return
        runCatching { push() }
    }

    private fun push() {
        val app = EmpyrealApp.instance
        val crosshairSize = app.modules.float("crosshair", "scale").takeIf { it > 0f } ?: 1f

        val layout = WIDGETS.associateWith { id ->
            val placement = app.hudLayout.placement(id)
            val moduleEnabled = app.modules.isEnabled(id)
            val effectiveScale = if (id == "crosshair") {
                (placement.scale * crosshairSize).coerceIn(0.4f, 4f)
            } else {
                placement.scale
            }
            HudWidgetWire(
                x = placement.x,
                y = placement.y,
                scale = effectiveScale,
                visible = hudEnabled && placement.visible && moduleEnabled,
            )
        }

        val values = CoreBridge.state.value.values
        val timerState = SpeedrunTimer.state.value
        val showTenths = app.modules.bool("speedrun", "tenths")

        val texts = buildMap {
            val fpsVal = values["gameinfo.fps"]?.toIntOrNull() ?: HudState.fps
            if (fpsVal > 0) {
                put("fps", "$fpsVal fps")
            }
            val cpsVal = values["gameinfo.cps"]?.toIntOrNull() ?: HudState.cps
            put("cps", "$cpsVal cps")
            put("clock", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
            coordinates(app, values)?.let { put("coords", it) }
            put("speedrun", formatTimer(timerState.elapsedMs, showTenths))
        }

        val modules = ModuleRegistry.all.map { module ->
            HudModuleWire(id = module.id, name = module.name, enabled = app.modules.isEnabled(module.id))
        }

        val settings = ModuleRegistry.all.associate { module ->
            module.id to module.settings.associate { setting ->
                setting.id to when (val value = app.modules.value(module.id, setting.id)) {
                    is SettingValue.Bool -> value.value.toString()
                    is SettingValue.Float -> value.value.toString()
                    is SettingValue.String -> value.value
                    is SettingValue.Color -> value.value.toString()
                }
            }
        }

        val (menuX, menuY) = app.hudLayout.menuPosition

        HudState.apply(
            HudWire(
                layout = layout,
                texts = texts,
                modules = modules,
                settings = settings,
                menuX = menuX,
                menuY = menuY,
                speedrunRunning = timerState.running,
            ),
        )
    }

    private fun coordinates(app: EmpyrealApp, values: Map<String, String>): String? {
        val x = values["coords.x"]?.toFloatOrNull()
        val y = values["coords.y"]?.toFloatOrNull()
        val z = values["coords.z"]?.toFloatOrNull()
        if (x == null || y == null || z == null) {
            return if (values["coords.status"] == "uncalibrated") "XYZ uncalibrated" else null
        }
        val decimals = app.modules.float("coords", "decimals").roundToInt().coerceIn(0, 3)
        val showAxis = app.modules.bool("coords", "show_axis")
        val fmt = "%.${decimals}f"
        val xs = fmt.format(Locale.US, x)
        val ys = fmt.format(Locale.US, y)
        val zs = fmt.format(Locale.US, z)
        return if (showAxis) {
            "X $xs  Y $ys  Z $zs"
        } else {
            "$xs / $ys / $zs"
        }
    }

    private fun formatTimer(ms: Long, tenthsEnabled: Boolean): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val tenths = (ms % 1000) / 100
        val base = if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.US, minutes, seconds)
        }
        return if (tenthsEnabled) "%s.%d".format(Locale.US, base, tenths) else base
    }
}
