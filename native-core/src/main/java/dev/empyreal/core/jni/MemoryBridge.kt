package dev.empyreal.core.jni

object MemoryBridge {

    class BytePattern(val bytes: ByteArray, val mask: ByteArray)

    fun parsePattern(text: String): BytePattern? {
        val cleaned = text.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("\"").removeSuffix("\"")
        if (cleaned.isEmpty()) return null

        val tokens = cleaned.split(" ")
        val bytes = ArrayList<Byte>(tokens.size)
        val mask = ArrayList<Byte>(tokens.size)

        for (token in tokens) {
            when {
                token == "??" || token == "?" -> {
                    bytes += 0
                    mask += 0
                }
                token.length == 2 && token.all { it.isHex() } -> {
                    bytes += token.toInt(16).toByte()
                    mask += 1
                }
                else -> return null
            }
        }
        if (bytes.isEmpty()) return null
        return BytePattern(bytes.toByteArray(), mask.toByteArray())
    }

    private fun Char.isHex(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun nativeReadString(address: Long, maxLen: Int): String? {
        val raw = nativeReadCStringBytes(address, maxLen) ?: return null
        return String(raw, Charsets.UTF_8)
    }

    external fun nativePid(): Int

    external fun nativeReadable(address: Long): Boolean

    external fun nativeModuleBase(name: String): Long

    external fun nativeModuleSize(name: String): Long

    external fun nativeReadFloat(address: Long): Float
    external fun nativeReadInt(address: Long): Int
    external fun nativeReadLong(address: Long): Long
    private external fun nativeReadCStringBytes(address: Long, maxLen: Int): ByteArray?

    external fun nativeReadFloatArray(address: Long, count: Int): FloatArray?

    external fun nativeReadByteArray(address: Long, count: Int): ByteArray?

    external fun nativeScan(
        module: String,
        pattern: ByteArray,
        mask: ByteArray,
        maxResults: Int,
    ): LongArray?
}
