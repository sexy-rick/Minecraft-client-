package dev.empyreal.client.game

/** Read-only, bounded DEX inspection. It reports only class presence and the shape of one method. */
internal object SavedStateDexProbe {
    private const val FRAGMENT = "Landroidx/fragment/app/FragmentActivity;"
    private const val SAVED_STATE = "Landroidx/savedstate/SavedStateRegistry;"
    private const val COMPONENT = "Landroidx/activity/ComponentActivity;"
    private const val LIFECYCLE_KEY = "android:support:lifecycle"
    private val targets = listOf(FRAGMENT, SAVED_STATE, COMPONENT)

    data class Result(val classes: Set<String>, val method: String?)

    fun inspect(bytes: ByteArray): Result {
        val d = Dex(bytes)
        val found = linkedSetOf<String>()
        var summary: String? = null
        for (i in 0 until d.classCount) {
            val classDef = d.classOffset + i * 32
            val descriptor = targets.firstOrNull { d.typeEquals(d.u32(classDef), it) } ?: continue
            found += descriptor.substringAfterLast('/').removeSuffix(";")
            if (descriptor == FRAGMENT) {
                val dataOffset = d.u32(classDef + 24)
                if (dataOffset != 0) summary = d.fragmentInit(dataOffset)
            }
        }
        return Result(found, summary)
    }

    private class Dex(private val bytes: ByteArray) {
        private val stringCount: Int
        private val stringOffset: Int
        private val typeCount: Int
        private val typeOffset: Int
        private val methodCount: Int
        private val methodOffset: Int
        val classCount: Int
        val classOffset: Int

        init {
            require(bytes.size >= 112 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x64, 0x65, 0x78, 0x0a))) { "not a standard DEX" }
            val version = String(bytes, 4, 3, Charsets.US_ASCII)
            require(version in setOf("035", "037", "038", "039", "040") && bytes[7].toInt() == 0) { "unsupported DEX version" }
            require(u32(32) == bytes.size && u32(40) == 0x12345678) { "unsupported DEX header" }
            stringCount = u32(56); stringOffset = u32(60); table(stringOffset, stringCount, 4)
            typeCount = u32(64); typeOffset = u32(68); table(typeOffset, typeCount, 4)
            methodCount = u32(88); methodOffset = u32(92); table(methodOffset, methodCount, 8)
            classCount = u32(96); classOffset = u32(100); table(classOffset, classCount, 32)
        }

        fun u32(offset: Int): Int {
            require(offset >= 0 && offset <= bytes.size - 4) { "truncated DEX" }
            val value = (bytes[offset].toLong() and 255) or ((bytes[offset + 1].toLong() and 255) shl 8) or
                ((bytes[offset + 2].toLong() and 255) shl 16) or ((bytes[offset + 3].toLong() and 255) shl 24)
            require(value <= Int.MAX_VALUE) { "DEX integer out of range" }
            return value.toInt()
        }

        private fun u16(offset: Int): Int {
            require(offset >= 0 && offset <= bytes.size - 2) { "truncated DEX" }
            return (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        }

        private fun table(offset: Int, count: Int, width: Int) {
            require(count >= 0 && count.toLong() * width + offset <= bytes.size) { "invalid DEX table" }
        }

        private fun leb(pos: Int): Pair<Int, Int> {
            var value = 0L; var p = pos
            for (shift in 0..28 step 7) {
                require(p in bytes.indices) { "truncated DEX LEB128" }
                val b = bytes[p++].toInt() and 255
                value = value or ((b and 127).toLong() shl shift)
                if (b and 128 == 0) {
                    require(value <= Int.MAX_VALUE) { "DEX LEB128 out of range" }
                    return value.toInt() to p
                }
            }
            throw IllegalArgumentException("invalid DEX LEB128")
        }

        private fun stringEquals(index: Int, expected: String): Boolean {
            require(index in 0 until stringCount) { "bad string index" }
            var p = leb(u32(stringOffset + index * 4)).second
            if (p + expected.length >= bytes.size) return false
            for (character in expected) if ((bytes[p++].toInt() and 255) != character.code) return false
            return bytes[p].toInt() == 0
        }

        fun typeEquals(index: Int, expected: String): Boolean {
            require(index in 0 until typeCount) { "bad type index" }
            return stringEquals(u32(typeOffset + index * 4), expected)
        }

        private fun methodMatches(index: Int, owner: String, name: String): Boolean {
            require(index in 0 until methodCount) { "bad method index" }
            val entry = methodOffset + index * 8
            return typeEquals(u16(entry), owner) && stringEquals(u32(entry + 4), name)
        }

        fun fragmentInit(offset: Int): String {
            var p = offset
            fun next(): Int { val (v, next) = leb(p); p = next; return v }
            val staticFields = next(); val instanceFields = next(); val directMethods = next(); val virtualMethods = next()
            require(staticFields.toLong() + instanceFields + directMethods + virtualMethods <= 1_000_000) { "oversized class data" }
            repeat(staticFields + instanceFields) { next(); next() }
            for (count in listOf(directMethods, virtualMethods)) {
                var index = 0
                repeat(count) {
                    val delta = next()
                    require(index.toLong() + delta < methodCount) { "bad encoded method" }
                    index += delta
                    next() // access flags
                    val code = next()
                    if (methodMatches(index, FRAGMENT, "init")) return if (code == 0) "init has no DEX code" else codeSummary(code)
                }
            }
            return "init method not found"
        }

        private fun codeSummary(code: Int): String {
            val insns = u32(code + 12)
            require(insns <= 4096 && code.toLong() + 16 + insns.toLong() * 2 <= bytes.size) { "oversized or truncated init code" }
            val start = code + 16
            var at = 0
            val lifecycleRegisters = mutableListOf<Int>()
            val nullRegisters = mutableListOf<Int>()
            val resultRegisters = mutableListOf<Int>()
            val staticRegisters = mutableListOf<Int>()
            val keyRegisters = mutableListOf<Int>()
            val opcodes = mutableListOf<String>()
            var flow = false
            var status = "complete"
            while (at < insns && opcodes.size < 128) {
                val word = u16(start + at * 2)
                val op = word and 255
                val width = instructionWidth(op)
                if (width == 0 || at + width > insns || (op == 0 && word != 0)) { status = "unsupported-or-truncated"; break }
                opcodes += op.toString(16).padStart(2, '0')
                if (op in 0x28..0x3d || op == 0x27) flow = true
                if (op == 0x12 && ((word shr 12) and 15) == 0) nullRegisters += (word shr 8) and 15
                if (op == 0x0c) resultRegisters += (word shr 8) and 255
                if (op == 0x62) staticRegisters += (word shr 8) and 255 // sget-object
                if (op == 0x1a || op == 0x1b) {
                    val stringIndex = if (op == 0x1a) u16(start + (at + 1) * 2) else u32(start + (at + 1) * 2)
                    if (stringEquals(stringIndex, LIFECYCLE_KEY)) lifecycleRegisters += (word shr 8) and 255
                }
                if (op in 0x6e..0x72 || op in 0x74..0x78) {
                    val methodIndex = u16(start + (at + 1) * 2)
                    if (methodMatches(methodIndex, SAVED_STATE, "registerSavedStateProvider")) {
                        val count = if (op <= 0x72) (word shr 12) and 15 else (word shr 8) and 255
                        if (count >= 2) {
                            val registers = u16(start + (at + 2) * 2)
                            keyRegisters += if (op <= 0x72) (registers shr 4) and 15 else registers + 1
                        }
                    }
                }
                at += width
            }
            if (at < insns && status == "complete") status = "instruction-limit"
            fun List<Int>.display() = if (isEmpty()) "none" else distinct().joinToString(",")
            return "init scan=$status controlFlow=$flow opcodes=${opcodes.joinToString(",")} " +
                "lifecycleLiteralRegs=${lifecycleRegisters.display()} nullConstRegs=${nullRegisters.display()} " +
                "moveResultObjectRegs=${resultRegisters.display()} staticObjectRegs=${staticRegisters.display()} " +
                "registerProviderKeyRegs=${keyRegisters.display()} (registers are not data-flow proof)"
        }

        private fun instructionWidth(op: Int): Int = when (op) {
            0x00, 0x01, 0x04, 0x07, in 0x0a..0x12, 0x1d, 0x1e, 0x21, 0x27, 0x28, in 0x7b..0x8f, in 0xb0..0xcf -> 1
            0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29,
            in 0x2d..0x3d, in 0x44..0x6d, in 0x90..0xaf, in 0xd0..0xe2, 0xfe, 0xff -> 2
            0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, in 0x24..0x26, in 0x2a..0x2c, in 0x6e..0x72, in 0x74..0x78, 0xfc, 0xfd -> 3
            0xfa, 0xfb -> 4
            0x18 -> 5
            else -> 0
        }
    }
}
