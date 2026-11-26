package dev.empyreal.client.core.offset

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OffsetStore(context: Context) {

    data class Offsets(
        val version: String,
        val x: Long,
        val y: Long,
        val z: Long,
        val lib: String = DEFAULT_LIBRARY,
    ) {
        val isComplete: Boolean get() = x != 0L && y != 0L && z != 0L
    }

    private val file = File(context.filesDir, "offsets.json")

    fun list(): List<Offsets> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        Offsets(
                            version = o.optString("version", "unknown"),
                            x = o.optLong("x", 0L),
                            y = o.optLong("y", 0L),
                            z = o.optLong("z", 0L),
                            lib = o.optString("lib", DEFAULT_LIBRARY),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun forVersion(version: String): Offsets? =
        list().firstOrNull { it.version.equals(version, ignoreCase = true) }

    fun save(offsets: Offsets) {
        val next = list().filterNot { it.version.equals(offsets.version, ignoreCase = true) } + offsets
        val array = JSONArray()
        next.forEach {
            array.put(
                JSONObject()
                    .put("version", it.version)
                    .put("x", it.x)
                    .put("y", it.y)
                    .put("z", it.z)
                    .put("lib", it.lib),
            )
        }
        file.writeText(array.toString(2))
    }

    fun delete(version: String) {
        val next = list().filterNot { it.version.equals(version, ignoreCase = true) }
        val array = JSONArray()
        next.forEach {
            array.put(
                JSONObject()
                    .put("version", it.version)
                    .put("x", it.x)
                    .put("y", it.y)
                    .put("z", it.z)
                    .put("lib", it.lib),
            )
        }
        file.writeText(array.toString(2))
    }

    companion object {
        const val DEFAULT_LIBRARY = "libminecraftpe.so"

        fun toSettings(offsets: Offsets): Map<String, String> = mapOf(
            "x" to offsets.x.toString(),
            "y" to offsets.y.toString(),
            "z" to offsets.z.toString(),
            "lib" to offsets.lib,
        )

        fun describe(offsets: Offsets): String =
            "${hex(offsets.x)} · ${hex(offsets.y)} · ${hex(offsets.z)}"

        private fun hex(value: Long): String =
            if (value >= 0x1000L) "0x" + java.lang.Long.toHexString(value) else value.toString()
    }
}
