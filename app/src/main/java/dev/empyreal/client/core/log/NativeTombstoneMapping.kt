package dev.empyreal.client.core.log

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Minimal bounded reader for public Android debuggerd tombstone.proto fields.
 * Never outputs mapping paths, addresses, register values, or memory contents.
 * Unsupported, oversized, truncated or mismatched tombstones yield no summary.
 */
internal object NativeTombstoneMapping {
    private const val LIMIT = 16 * 1024 * 1024
    private const val MAX_MAPPINGS = 20000
    private data class Mapping(val begin: Long, val end: Long, val read: Boolean, val write: Boolean, val execute: Boolean, val category: String)

    fun summarize(input: InputStream, expectedPid: Int): String? = runCatching {
        if (expectedPid <= 0) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(count > 0)
            total += count
            require(total <= LIMIT)
            out.write(buffer, 0, count)
        }
        val reader = Reader(out.toByteArray())
        var arch = 0L
        var pid = 0L
        var fault: Long? = null
        val maps = ArrayList<Mapping>()
        reader.fields { field, wire, r ->
            when {
                field == 1 && wire == 0 -> arch = r.varint()
                field == 5 && wire == 0 -> pid = r.varint()
                field == 10 && wire == 2 -> {
                    var hasAddress = false
                    var address = 0L
                    r.message().fields { f, w, s ->
                        when {
                            f == 8 && w == 0 -> hasAddress = s.varint() != 0L
                            f == 9 && w == 0 -> address = s.varint()
                            else -> s.skip(w)
                        }
                    }
                    if (hasAddress) fault = address
                }
                field == 17 && wire == 2 -> {
                    require(maps.size < MAX_MAPPINGS)
                    var begin = 0L; var end = 0L
                    var read = false; var write = false; var execute = false
                    var category = "anonymous"
                    r.message().fields { f, w, m ->
                        when {
                            f == 1 && w == 0 -> begin = m.varint()
                            f == 2 && w == 0 -> end = m.varint()
                            f == 4 && w == 0 -> read = m.varint() != 0L
                            f == 5 && w == 0 -> write = m.varint() != 0L
                            f == 6 && w == 0 -> execute = m.varint() != 0L
                            f == 7 && w == 2 -> {
                                val name = m.string(4096)
                                category = when {
                                    name.startsWith("[heap") -> "heap"
                                    name.startsWith("[stack") -> "stack"
                                    name.startsWith("[") || name.isEmpty() -> "anonymous"
                                    else -> "file-backed or named"
                                }
                            }
                            else -> m.skip(w)
                        }
                    }
                    require(java.lang.Long.compareUnsigned(begin, end) < 0)
                    maps.add(Mapping(begin, end, read, write, execute, category))
                }
                else -> r.skip(wire)
            }
        }
        if (pid != expectedPid.toLong() || fault == null || maps.isEmpty()) return null
        // Android arm64 addresses can contain a top-byte tag, not part of the VMA address.
        val address = if (arch == 1L) fault!! and 0x00ffffffffffffffL else fault!!
        val found = maps.firstOrNull {
            java.lang.Long.compareUnsigned(address, it.begin) >= 0 && java.lang.Long.compareUnsigned(address, it.end) < 0
        }
        if (found == null) "Fault mapping: no reported mapping contains the fault address; cause remains unknown."
        else "Fault mapping: ${found.category}; ${if (found.read) "readable" else "not readable"}, " +
            "${if (found.write) "writable" else "not writable"}, ${if (found.execute) "executable" else "not executable"}. Access cause remains unknown."
    }.getOrNull()

    private class Reader(private val data: ByteArray, private var pos: Int = 0, private val end: Int = data.size) {
        fun varint(): Long {
            var result = 0L
            for (i in 0..9) {
                require(pos < end)
                val b = data[pos++].toInt() and 255
                require(i != 9 || b <= 1)
                result = result or ((b and 127).toLong() shl (i * 7))
                if (b and 128 == 0) return result
            }
            error("bad varint")
        }
        fun fields(consume: (Int, Int, Reader) -> Unit) {
            while (pos < end) {
                val key = varint()
                require(key > 0 && key ushr 3 <= 0x1fffffffL)
                consume((key ushr 3).toInt(), (key and 7).toInt(), this)
            }
        }
        fun message(): Reader {
            val size = varint()
            require(size >= 0 && size <= (end - pos).toLong())
            val child = Reader(data, pos, pos + size.toInt())
            pos += size.toInt()
            return child
        }
        fun string(max: Int): String {
            val child = message()
            require(child.end - child.pos <= max)
            return String(data, child.pos, child.end - child.pos, Charsets.UTF_8)
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> advance(8)
                2 -> { val size = varint(); require(size >= 0 && size <= (end - pos).toLong()); advance(size.toInt()) }
                5 -> advance(4)
                else -> error("unsupported wire type")
            }
        }
        private fun advance(count: Int) { require(count <= end - pos); pos += count }
    }
}
