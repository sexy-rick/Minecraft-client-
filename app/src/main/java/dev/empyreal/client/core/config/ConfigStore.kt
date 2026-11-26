package dev.empyreal.client.core.config

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ConfigStore(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dir: File = File(context.filesDir, "configs").apply { mkdirs() }

    @Serializable
    data class ConfigFile(
        val name: String,
        val author: String = "",
        val version: Int = 1,
        val enabled: Map<String, Boolean> = emptyMap(),
        val values: Map<String, Map<String, String>> = emptyMap(),
        val hud: Map<String, HudPlacement> = emptyMap(),
    ) {
        @Serializable
        data class HudPlacement(
            val x: Float = 0.5f,
            val y: Float = 0.5f,
            val scale: Float = 1f,
            val visible: Boolean = true,
        )
    }

    private fun sanitizeName(raw: String): String =
        raw.replace(Regex("""[^\w.\- ]"""), "_")
            .trim()
            .trim('.')
            .take(64)
            .ifBlank { "My config" }

    private fun safeFile(name: String): File {
        val clean = sanitizeName(name)
        val canonicalDir = dir.canonicalFile
        val target = File(canonicalDir, "$clean.json").canonicalFile
        check(target.parentFile == canonicalDir) { "Invalid config path" }
        return target
    }

    fun list(): List<ConfigFile> = dir.listFiles { f -> f.isFile && f.extension == "json" }
        .orEmpty()
        .mapNotNull { file ->
            runCatching { json.decodeFromString(ConfigFile.serializer(), file.readText()) }.getOrNull()
        }
        .sortedBy { it.name.lowercase() }

    fun load(name: String): ConfigFile? {
        val file = runCatching { safeFile(name) }.getOrNull() ?: return null
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(ConfigFile.serializer(), file.readText()) }.getOrNull()
    }

    fun save(config: ConfigFile) {
        val cleanName = sanitizeName(config.name)
        val normalized = if (cleanName == config.name) config else config.copy(name = cleanName)
        val file = safeFile(cleanName)
        file.writeText(json.encodeToString(normalized))
    }

    fun delete(name: String) {
        runCatching { safeFile(name).delete() }
    }

    fun exportJson(config: ConfigFile): String = json.encodeToString(config)

    fun importJson(text: String): ConfigFile? =
        runCatching { json.decodeFromString(ConfigFile.serializer(), text) }
            .getOrNull()
            ?.let { parsed -> parsed.copy(name = sanitizeName(parsed.name)) }
}
