package dev.empyreal.core.modules

import dev.empyreal.core.jni.MemoryBridge
import dev.empyreal.core.module.NativeModule
import dev.empyreal.core.module.TickContext

class PeekModule : NativeModule(
    id = "peek",
    name = "Peek",
    description = "Reads floats at a given address on demand.",
) {

    @Volatile
    private var address = 0L

    @Volatile
    private var count = 8

    @Volatile
    private var values: List<Float> = emptyList()

    override fun onSettingsChanged() {
        val raw = settings["address"]?.trim().orEmpty()
        address = when {
            raw.isEmpty() -> 0L
            raw.startsWith("0x", ignoreCase = true) -> raw.substring(2).toLongOrNull(16) ?: 0L
            else -> raw.toLongOrNull() ?: 0L
        }
        count = settings["count"]?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 8
        readNow()
    }

    override fun tick(context: TickContext) {
        if (address != 0L) {
            readNow()
        }
    }

    private fun readNow() {
        val target = address
        val len = count
        values = if (target != 0L) {
            MemoryBridge.nativeReadFloatArray(target, len)?.toList().orEmpty()
        } else {
            emptyList()
        }
    }

    override fun writeState(): Map<String, String> {
        val snapshot = values
        return mapOf(
            "address" to address.toString(),
            "count" to snapshot.size.toString(),
            "values" to snapshot.joinToString(",") { v ->
                if (v.isNaN()) "NaN" else String.format(java.util.Locale.US, "%.3f", v)
            },
        )
    }
}
