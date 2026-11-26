package dev.empyreal.core.hud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HudWidgetWire(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val visible: Boolean = true,
)

@Serializable
data class HudModuleWire(
    val id: String,
    val name: String,
    val enabled: Boolean = false,
)

@Serializable
data class HudWire(
    val layout: Map<String, HudWidgetWire> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
    val modules: List<HudModuleWire> = emptyList(),
    val settings: Map<String, Map<String, String>> = emptyMap(),
    val menuX: Float = 0.94f,
    val menuY: Float = 0.42f,
    val speedrunRunning: Boolean = false,
)

object HudState {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var layout: Map<String, HudWidgetWire> = emptyMap()
        private set

    @Volatile
    var texts: Map<String, String> = emptyMap()
        private set

    @Volatile
    var modules: List<HudModuleWire> = emptyList()
        private set

    @Volatile
    var settings: Map<String, Map<String, String>> = emptyMap()
        private set

    @Volatile
    var menuX = 0.94f
        private set

    @Volatile
    var menuY = 0.42f
        private set

    @Volatile
    var speedrunRunning: Boolean = false
        private set

    @Volatile var fps: Int = 0
    @Volatile var cps: Int = 0
    @Volatile var foreground: Boolean = false
    @Volatile var menuOpen: Boolean = false
    @Volatile var userHolding: Boolean = false

    @Volatile var onModuleToggle: ((String, Boolean) -> Unit)? = null
    @Volatile var onMenuMoved: ((Float, Float) -> Unit)? = null
    @Volatile var onSpeedrunAction: ((String) -> Unit)? = null

    fun apply(payload: String?) {
        if (payload.isNullOrBlank()) return
        runCatching { json.decodeFromString(HudWire.serializer(), payload) }
            .getOrNull()
            ?.let { apply(it) }
    }

    fun apply(wire: HudWire) {
        layout = wire.layout
        texts = wire.texts
        modules = wire.modules
        settings = wire.settings
        menuX = wire.menuX.coerceIn(0.04f, 0.96f)
        menuY = wire.menuY.coerceIn(0.06f, 0.94f)
        speedrunRunning = wire.speedrunRunning
        InGameHud.refresh()
    }

    fun moveMenu(x: Float, y: Float, persist: Boolean = false) {
        val clampedX = x.coerceIn(0.04f, 0.96f)
        val clampedY = y.coerceIn(0.06f, 0.94f)
        menuX = clampedX
        menuY = clampedY
        if (persist) {
            onMenuMoved?.invoke(clampedX, clampedY)
        }
    }

    fun widget(id: String): HudWidgetWire? = layout[id]

    fun text(id: String): String? = texts[id]

    fun float(moduleId: String, settingId: String, fallback: Float): Float =
        settings[moduleId]?.get(settingId)?.toFloatOrNull() ?: fallback

    fun bool(moduleId: String, settingId: String, fallback: Boolean): Boolean =
        settings[moduleId]?.get(settingId)?.toBooleanStrictOrNull() ?: fallback

    fun string(moduleId: String, settingId: String, fallback: String): String =
        settings[moduleId]?.get(settingId)?.takeIf { it.isNotBlank() } ?: fallback

    fun isEnabled(moduleId: String): Boolean = modules.firstOrNull { it.id == moduleId }?.enabled ?: false

    fun toggle(moduleId: String) {
        val next = !isEnabled(moduleId)
        modules = modules.map { m -> if (m.id == moduleId) m.copy(enabled = next) else m }
        onModuleToggle?.invoke(moduleId, next)
    }

    fun speedrunAction(action: String) {
        if (action == "toggle") {
            speedrunRunning = !speedrunRunning
        } else if (action == "reset") {
            speedrunRunning = false
        }
        onSpeedrunAction?.invoke(action)
    }
}
