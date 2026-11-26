package dev.empyreal.client.core.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.empyreal.client.core.minecraft.MinecraftLauncher

object ContentImport {

    enum class Kind(val label: String, val extensions: List<String>, val hint: String) {
        WORLD("World", listOf("mcworld"), "Appears under Play → Worlds after import"),
        RESOURCE_PACK("Resource pack", listOf("mcpack", "zip"), "Enable it in Settings → Resource Packs"),
        BEHAVIOR_PACK("Behavior pack", listOf("mcpack", "zip"), "Apply it to a world under World Settings"),
        SKIN_PACK("Skin pack", listOf("mcpack", "zip"), "Choose it in the profile / skin picker"),
        ADDON("Addon", listOf("mcaddon"), "Contains a resource and a behavior pack"),
    }

    data class Entry(val time: Long, val kind: Kind, val name: String)

    sealed class Result {
        data class HandedOver(val kind: Kind) : Result()
        data object MinecraftMissing : Result()
        data object WrongType : Result()
        data object Refused : Result()
    }

    private const val PREFS = "empyreal_content"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 30

    fun import(context: Context, kind: Kind, uri: Uri, displayName: String): Result {
        if (MinecraftLauncher.installedPackage(context) == null) return Result.MinecraftMissing

        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty() || ext !in kind.extensions) return Result.WrongType

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/octet-stream")
            .setClassName(context, "dev.empyreal.client.game.GameActivity")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
        if (!ok) return Result.Refused

        record(context, Entry(System.currentTimeMillis(), kind, displayName))
        return Result.HandedOver(kind)
    }

    fun history(context: Context): List<Entry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty()
            .mapNotNull { raw ->
                val parts = raw.split("|", limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val time = parts[0].toLongOrNull() ?: return@mapNotNull null
                val kind = Kind.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
                Entry(time, kind, parts[2])
            }
            .sortedByDescending { it.time }
    }

    private fun record(context: Context, entry: Entry) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = (history(context) + entry).sortedByDescending { it.time }.take(MAX_HISTORY)
        val encoded = items.map { "${it.time}|${it.kind.name}|${it.name}" }.toSet()
        prefs.edit().putStringSet(KEY_HISTORY, encoded).apply()
    }
}
