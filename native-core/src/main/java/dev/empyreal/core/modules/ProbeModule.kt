package dev.empyreal.core.modules

import dev.empyreal.core.jni.MemoryBridge
import dev.empyreal.core.module.NativeModule
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ProbeModule : NativeModule(
    id = "probe",
    name = "Offset Probe",
    description = "Runs a byte-pattern scan over the game library on demand.",
) {

    private data class Result(
        val pattern: String = "",
        val module: String = "",
        val count: Int = 0,
        val hits: List<Long> = emptyList(),
        val error: String = "",
        val durationMs: Long = 0,
        val base: Long = 0,
        val size: Long = 0,
    )

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "empyreal-probe").apply { isDaemon = true }
    }

    private val result = AtomicReference(Result())

    override fun onSettingsChanged() {
        val pattern = settings["pattern"]?.trim().orEmpty()
        if (pattern.isEmpty()) return
        val module = settings["module"]?.trim().orEmpty().ifEmpty { "libminecraftpe.so" }
        val max = settings["max"]?.toIntOrNull()?.coerceIn(1, 256) ?: 64

        executor.execute {
            val started = System.currentTimeMillis()
            val base = MemoryBridge.nativeModuleBase(module)
            val size = MemoryBridge.nativeModuleSize(module)

            val parsed = MemoryBridge.parsePattern(pattern)
            if (parsed == null) {
                result.set(Result(pattern, module, 0, emptyList(), "bad_pattern", 0, base, size))
                return@execute
            }

            val hits = runCatching {
                MemoryBridge.nativeScan(module, parsed.bytes, parsed.mask, max)?.toList()
            }.getOrNull()

            if (hits == null) {
                result.set(Result(pattern, module, 0, emptyList(), "scan_failed", 0, base, size))
            } else {
                result.set(
                    Result(
                        pattern = pattern,
                        module = module,
                        count = hits.size,
                        hits = hits,
                        durationMs = System.currentTimeMillis() - started,
                        base = base,
                        size = size,
                    ),
                )
            }
        }
    }

    override fun writeState(): Map<String, String> {
        val r = result.get()
        return mapOf(
            "pattern" to r.pattern,
            "module" to r.module,
            "base" to r.base.toString(),
            "size" to r.size.toString(),
            "count" to r.count.toString(),
            "error" to r.error,
            "duration_ms" to r.durationMs.toString(),
            "hits" to r.hits.joinToString(","),
        )
    }
}
