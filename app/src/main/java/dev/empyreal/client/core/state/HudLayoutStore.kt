package dev.empyreal.client.core.state

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HudPlacement(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val visible: Boolean = true,
)

class HudLayoutStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("empyreal_hud", Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<Map<String, HudPlacement>> = _layout.asStateFlow()

    val menuPosition: Pair<Float, Float>
        get() = Pair(
            prefs.getFloat(KEY_MENU_X, DEFAULT_MENU_X).coerceIn(0.04f, 0.96f),
            prefs.getFloat(KEY_MENU_Y, DEFAULT_MENU_Y).coerceIn(0.06f, 0.94f),
        )

    fun setMenuPosition(x: Float, y: Float) {
        prefs.edit()
            .putFloat(KEY_MENU_X, x.coerceIn(0.04f, 0.96f))
            .putFloat(KEY_MENU_Y, y.coerceIn(0.06f, 0.94f))
            .apply()
    }

    fun placement(id: String): HudPlacement = _layout.value[id] ?: defaultPlacement(id)

    fun update(id: String, placement: HudPlacement) {
        val clamped = placement.copy(
            x = placement.x.coerceIn(0.02f, 0.98f),
            y = placement.y.coerceIn(0.02f, 0.98f),
            scale = placement.scale.coerceIn(0.5f, 2.5f),
        )
        _layout.update { it + (id to clamped) }
        prefs.edit()
            .putString(
                id,
                listOf(clamped.x, clamped.y, clamped.scale, clamped.visible).joinToString(","),
            )
            .apply()
        HudSync.pushNow()
    }

    fun applyAll(placements: Map<String, HudPlacement>) {
        val normalized = DEFAULTS.mapValues { (id, def) ->
            val p = placements[id] ?: def
            p.copy(
                x = p.x.coerceIn(0.02f, 0.98f),
                y = p.y.coerceIn(0.02f, 0.98f),
                scale = p.scale.coerceIn(0.5f, 2.5f),
            )
        }
        _layout.value = normalized
        val editor = prefs.edit()
        DEFAULTS.keys.forEach { editor.remove(it) }
        normalized.forEach { (id, p) ->
            editor.putString(id, listOf(p.x, p.y, p.scale, p.visible).joinToString(","))
        }
        editor.apply()
        HudSync.pushNow()
    }

    fun snapshot(): Map<String, HudPlacement> = DEFAULTS.keys.associateWith { placement(it) }

    fun reset(id: String) = update(id, defaultPlacement(id))

    fun resetAll() {
        _layout.value = DEFAULTS
        val editor = prefs.edit()
        DEFAULTS.keys.forEach { editor.remove(it) }
        editor.putFloat(KEY_MENU_X, DEFAULT_MENU_X)
        editor.putFloat(KEY_MENU_Y, DEFAULT_MENU_Y)
        editor.apply()
        HudSync.pushNow()
    }

    private fun load(): Map<String, HudPlacement> {
        val map = DEFAULTS.toMutableMap()
        val entries = prefs.all
        entries.keys.forEach { key ->
            if (key == KEY_MENU_X || key == KEY_MENU_Y) return@forEach
            val raw = entries[key] as? String ?: return@forEach
            val parts = raw.split(",")
            if (parts.size == 4) {
                val def = defaultPlacement(key)
                map[key] = HudPlacement(
                    x = (parts[0].toFloatOrNull() ?: def.x).coerceIn(0.02f, 0.98f),
                    y = (parts[1].toFloatOrNull() ?: def.y).coerceIn(0.02f, 0.98f),
                    scale = (parts[2].toFloatOrNull() ?: def.scale).coerceIn(0.5f, 2.5f),
                    visible = parts[3].toBooleanStrictOrNull() ?: def.visible,
                )
            }
        }
        return map
    }

    companion object {
        const val KEY_MENU_X = "menu_x"
        const val KEY_MENU_Y = "menu_y"
        const val DEFAULT_MENU_X = 0.94f
        const val DEFAULT_MENU_Y = 0.42f

        val DEFAULTS: Map<String, HudPlacement> = mapOf(
            "crosshair" to HudPlacement(x = 0.50f, y = 0.50f, scale = 1.0f, visible = true),
            "fps" to HudPlacement(x = 0.08f, y = 0.07f, scale = 1.0f, visible = true),
            "cps" to HudPlacement(x = 0.08f, y = 0.14f, scale = 1.0f, visible = true),
            "coords" to HudPlacement(x = 0.16f, y = 0.21f, scale = 1.0f, visible = true),
            "clock" to HudPlacement(x = 0.92f, y = 0.07f, scale = 1.0f, visible = true),
            "speedrun" to HudPlacement(x = 0.90f, y = 0.14f, scale = 1.0f, visible = true),
        )

        fun defaultPlacement(id: String): HudPlacement = DEFAULTS[id] ?: HudPlacement()
    }
}
