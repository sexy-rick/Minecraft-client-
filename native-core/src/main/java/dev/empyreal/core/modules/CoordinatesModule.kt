package dev.empyreal.core.modules

import dev.empyreal.core.jni.MemoryBridge
import dev.empyreal.core.module.NativeModule

class CoordinatesModule : NativeModule(
    id = "coords",
    name = "Coordinates",
    description = "Player X / Y / Z read from the level in memory.",
) {

    @Volatile
    private var base = 0L

    private val library: String
        get() = settings["lib"]?.trim().orEmpty().ifEmpty {
            Offsets.forVersion(null)?.gameLibrary ?: "libminecraftpe.so"
        }

    private fun offset(key: String): Long {
        val raw = settings[key]?.trim()
        val parsed = when {
            raw.isNullOrEmpty() -> null
            raw.startsWith("0x", ignoreCase = true) -> raw.substring(2).toLongOrNull(16)
            else -> raw.toLongOrNull()
        }
        return parsed
            ?: Offsets.forVersion(null)?.let { entry ->
                when (key) {
                    "x" -> entry.playerX
                    "y" -> entry.playerY
                    "z" -> entry.playerZ
                    else -> 0L
                }
            }
            ?: 0L
    }

    override val ready: Boolean
        get() = base != 0L && offset("x") != 0L && offset("y") != 0L && offset("z") != 0L

    override fun onEnable() {
        base = MemoryBridge.nativeModuleBase(library)
    }

    override fun onSettingsChanged() {
        base = MemoryBridge.nativeModuleBase(library)
    }

    override fun tick(context: dev.empyreal.core.module.TickContext) {
        if (base == 0L || context.tick % 20 == 0L) {
            base = MemoryBridge.nativeModuleBase(library)
        }
    }

    override fun writeState(): Map<String, String> {
        val ox = offset("x")
        val oy = offset("y")
        val oz = offset("z")
        if (ox == 0L && oy == 0L && oz == 0L) {
            return mapOf("ready" to "false", "reason" to "offsets_not_configured")
        }
        val currentBase = base.takeIf { it != 0L } ?: MemoryBridge.nativeModuleBase(library).also { base = it }
        if (currentBase == 0L) {
            return mapOf("ready" to "false", "reason" to "library_not_mapped")
        }

        val x = MemoryBridge.nativeReadFloat(currentBase + ox)
        val y = MemoryBridge.nativeReadFloat(currentBase + oy)
        val z = MemoryBridge.nativeReadFloat(currentBase + oz)

        if (x.isNaN() || y.isNaN() || z.isNaN()) {
            return mapOf("ready" to "false", "reason" to "read_failed")
        }

        return mapOf(
            "ready" to "true",
            "x" to fmt(x),
            "y" to fmt(y),
            "z" to fmt(z),
            "lib" to library,
            "base" to currentBase.toString(),
        )
    }

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.4f", v)
}
