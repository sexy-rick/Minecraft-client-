package dev.empyreal.client.game

/** Moves only the extracted game directory ahead of system paths, retaining app precedence. */
internal object NativePathOrder {
    fun <T> prioritizeCache(elements: List<T>, isCache: (T) -> Boolean, isSystem: (T) -> Boolean): List<T> {
        val cacheIndex = elements.indexOfFirst(isCache)
        val systemIndex = elements.indexOfFirst(isSystem)
        if (cacheIndex < 0 || systemIndex < 0 || cacheIndex <= systemIndex) return elements
        return elements.toMutableList().apply { add(systemIndex, removeAt(cacheIndex)) }
    }
}
