package dev.empyreal.core.modules

object Offsets {

    data class Entry(
        val version: String,
        val playerX: Long,
        val playerY: Long,
        val playerZ: Long,
        val dimension: Long,
        val gameLibrary: String = "libminecraftpe.so",
    )

    private val table: List<Entry> = listOf(
        Entry(version = "*", playerX = 0L, playerY = 0L, playerZ = 0L, dimension = 0L),
    )

    fun forVersion(versionName: String?): Entry? =
        table.firstOrNull { it.version.equals(versionName, ignoreCase = true) }
            ?: table.firstOrNull { it.version == "*" }

    val configured: Boolean
        get() = table.any { it.playerX != 0L && it.playerY != 0L && it.playerZ != 0L }
}
