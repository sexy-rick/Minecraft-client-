package dev.empyreal.client.game

import org.junit.Assert.assertEquals
import org.junit.Test

class NativePathOrderTest {
    private fun reorder(paths: List<String>): List<String> = NativePathOrder.prioritizeCache(
        paths, isCache = { it == "cache" }, isSystem = { it.startsWith("system") },
    )

    @Test fun cacheMovesBetweenHostAndSystemWhileOtherPathsRemainOrdered() {
        assertEquals(
            listOf("host-dir", "host-apk", "cache", "system-a", "system-b", "game-apk"),
            reorder(listOf("host-dir", "host-apk", "system-a", "system-b", "cache", "game-apk")),
        )
    }

    @Test fun cacheAlreadyAheadOfSystemIsUnchanged() {
        val paths = listOf("host", "cache", "system-a", "game-apk")
        assertEquals(paths, reorder(paths))
    }

    @Test fun missingSystemOrCacheDoesNotReorder() {
        assertEquals(listOf("host", "cache"), reorder(listOf("host", "cache")))
        assertEquals(listOf("host", "system-a"), reorder(listOf("host", "system-a")))
    }

    @Test fun preservesRelativeOrderOfUnrelatedPaths() {
        assertEquals(listOf("host", "cache", "system-a", "other", "system-b", "game-apk"),
            reorder(listOf("host", "system-a", "other", "system-b", "cache", "game-apk")))
    }
}
