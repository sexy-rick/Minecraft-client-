package dev.empyreal.client.game

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object ElfImageSanitizer {

    private const val MAX_ELF_TABLE_BYTES = 16L * 1024L * 1024L
    private const val ELF_MAGIC_BE = 0x7F454C46
    private const val ELFCLASS32 = 1
    private const val ELFCLASS64 = 2
    private const val ELFDATA2LSB = 1
    private const val ELFDATA2MSB = 2
    private const val PT_LOAD = 1
    private const val PT_DYNAMIC = 2
    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L
    private const val DT_STRSZ = 10L

    data class SanitizeResult(
        val modified: Boolean,
        val removedNeeded: List<String>,
        val remainingNeeded: List<String>,
    )

    private class ParsedDynamic(
        val is64: Boolean,
        val byteOrder: ByteOrder,
        val dynamicOffset: Long,
        val dynamicSize: Int,
        val dynamicBytes: ByteArray,
        val strings: ByteArray,
    )

    fun isPairipLibrary(name: String): Boolean {
        val base = name.substringAfterLast('/')
        return base == "libpairipcore.so" || (base.startsWith("libpairip") && base.endsWith(".so"))
    }

    fun readNeededLibraries(path: File): List<String> {
        if (!path.isFile) return emptyList()
        return runCatching {
            RandomAccessFile(path, "r").use { raf ->
                val parsed = parseDynamic(raf) ?: return emptyList()
                val stride = if (parsed.is64) 16 else 8
                val buf = ByteBuffer.wrap(parsed.dynamicBytes).order(parsed.byteOrder)
                val result = ArrayList<String>()
                var at = 0
                while (at + stride <= parsed.dynamicSize) {
                    val tag = readTag(buf, at, parsed.is64)
                    val value = readVal(buf, at, parsed.is64)
                    at += stride
                    if (tag == DT_NULL) break
                    if (tag == DT_NEEDED) {
                        val name = elfString(parsed.strings, value)
                        if (name.isNotEmpty()) result.add(name)
                    }
                }
                result
            }
        }.getOrDefault(emptyList())
    }

    fun hasBlockedNeeded(
        path: File,
        blocked: (String) -> Boolean = ::isPairipLibrary,
    ): Boolean = readNeededLibraries(path).any(blocked)

    fun sanitizeInPlace(
        path: File,
        blocked: (String) -> Boolean = ::isPairipLibrary,
    ): SanitizeResult {
        if (!path.isFile) return SanitizeResult(false, emptyList(), emptyList())

        val parsed = RandomAccessFile(path, "r").use { parseDynamic(it) }
            ?: return SanitizeResult(false, emptyList(), emptyList())

        val stride = if (parsed.is64) 16 else 8
        val buf = ByteBuffer.wrap(parsed.dynamicBytes).order(parsed.byteOrder)

        val kept = ArrayList<ByteArray>()
        val removed = ArrayList<String>()
        val remaining = ArrayList<String>()
        var foundNull = false

        var at = 0
        while (at + stride <= parsed.dynamicSize) {
            val tag = readTag(buf, at, parsed.is64)
            val value = readVal(buf, at, parsed.is64)
            val entry = parsed.dynamicBytes.copyOfRange(at, at + stride)
            at += stride
            if (tag == DT_NULL) {
                kept.add(entry)
                foundNull = true
                break
            }

            if (tag == DT_NEEDED) {
                val soname = elfString(parsed.strings, value)
                if (soname.isNotEmpty() && blocked(soname)) {
                    removed.add(soname)
                    continue
                }
                if (soname.isNotEmpty()) remaining.add(soname)
            }
            kept.add(entry)
        }

        if (removed.isEmpty()) {
            return SanitizeResult(modified = false, removedNeeded = emptyList(), remainingNeeded = remaining)
        }

        if (!foundNull) {
            val nullEntry = ByteArray(stride)
            kept.add(nullEntry)
        }
        val rewritten = ByteArray(parsed.dynamicSize)
        var out = 0
        for (entry in kept) {
            System.arraycopy(entry, 0, rewritten, out, stride)
            out += stride
        }

        RandomAccessFile(path, "rw").use { raf ->
            raf.seek(parsed.dynamicOffset)
            raf.write(rewritten)
        }

        return SanitizeResult(modified = true, removedNeeded = removed, remainingNeeded = remaining)
    }

    private fun parseDynamic(file: RandomAccessFile): ParsedDynamic? {
        val fileLen = file.length()
        if (fileLen < 52L) return null

        val ident = ByteArray(16)
        file.seek(0L)
        file.readFully(ident)
        if (ByteBuffer.wrap(ident).order(ByteOrder.BIG_ENDIAN).getInt(0) != ELF_MAGIC_BE) {
            return null
        }

        val is64 = when (ident[4].toInt() and 0xFF) {
            ELFCLASS64 -> true
            ELFCLASS32 -> false
            else -> return null
        }
        val byteOrder = when (ident[5].toInt() and 0xFF) {
            ELFDATA2LSB -> ByteOrder.LITTLE_ENDIAN
            ELFDATA2MSB -> ByteOrder.BIG_ENDIAN
            else -> return null
        }

        val headerSize = if (is64) 64 else 52
        if (fileLen < headerSize) return null
        file.seek(0L)
        val header = ByteBuffer.allocate(headerSize).order(byteOrder)
        file.readFully(header.array())

        val phoff: Long
        val phentsize: Int
        val phnum: Int
        if (is64) {
            phoff = header.getLong(32)
            phentsize = header.getShort(54).toInt() and 0xFFFF
            phnum = header.getShort(56).toInt() and 0xFFFF
            if (phentsize < 56) return null
        } else {
            phoff = header.getInt(28).toLong() and 0xFFFFFFFFL
            phentsize = header.getShort(42).toInt() and 0xFFFF
            phnum = header.getShort(44).toInt() and 0xFFFF
            if (phentsize < 32) return null
        }
        if (phnum == 0 || phoff <= 0L) return null

        val phTableBytes = phentsize.toLong() * phnum.toLong()
        if (phTableBytes <= 0L || phTableBytes > MAX_ELF_TABLE_BYTES || phoff + phTableBytes > fileLen) {
            return null
        }

        val table = ByteBuffer.allocate(phTableBytes.toInt()).order(byteOrder)
        file.seek(phoff)
        file.readFully(table.array())

        var dynamicOffset = -1L
        var dynamicSize = 0L
        val loads = ArrayList<LongArray>()
        for (index in 0 until phnum) {
            val at = index * phentsize
            val type = table.getInt(at)
            if (is64) {
                val pOffset = table.getLong(at + 8)
                val pVaddr = table.getLong(at + 16)
                val pFilesz = table.getLong(at + 32)
                if (type == PT_LOAD) {
                    loads.add(longArrayOf(pVaddr, pFilesz, pOffset))
                } else if (type == PT_DYNAMIC) {
                    dynamicOffset = pOffset
                    dynamicSize = pFilesz
                }
            } else {
                val pOffset = table.getInt(at + 4).toLong() and 0xFFFFFFFFL
                val pVaddr = table.getInt(at + 8).toLong() and 0xFFFFFFFFL
                val pFilesz = table.getInt(at + 16).toLong() and 0xFFFFFFFFL
                if (type == PT_LOAD) {
                    loads.add(longArrayOf(pVaddr, pFilesz, pOffset))
                } else if (type == PT_DYNAMIC) {
                    dynamicOffset = pOffset
                    dynamicSize = pFilesz
                }
            }
        }
        if (dynamicOffset <= 0L || dynamicSize <= 0L || dynamicSize > MAX_ELF_TABLE_BYTES || dynamicOffset + dynamicSize > fileLen) {
            return null
        }

        val dynamicBytes = ByteArray(dynamicSize.toInt())
        file.seek(dynamicOffset)
        file.readFully(dynamicBytes)
        val entries = ByteBuffer.wrap(dynamicBytes).order(byteOrder)

        var strtabVaddr = -1L
        var strtabSize = 0L
        val stride = if (is64) 16 else 8
        var at = 0
        while (at + stride <= entries.capacity()) {
            val tag = readTag(entries, at, is64)
            val value = readVal(entries, at, is64)
            at += stride
            if (tag == DT_NULL) break
            if (tag == DT_STRTAB) strtabVaddr = value
            if (tag == DT_STRSZ) strtabSize = value
        }
        if (strtabVaddr < 0L || strtabSize <= 0L || strtabSize > MAX_ELF_TABLE_BYTES) {
            return null
        }

        var strtabOffset = -1L
        for (segment in loads) {
            val vaddr = segment[0]
            val filesz = segment[1]
            if (filesz > 0L && strtabVaddr >= vaddr && strtabVaddr < vaddr + filesz) {
                strtabOffset = strtabVaddr - vaddr + segment[2]
                break
            }
        }
        if (strtabOffset < 0L || strtabOffset + strtabSize > fileLen) return null

        val strings = ByteArray(strtabSize.toInt())
        file.seek(strtabOffset)
        file.readFully(strings)

        return ParsedDynamic(
            is64 = is64,
            byteOrder = byteOrder,
            dynamicOffset = dynamicOffset,
            dynamicSize = dynamicSize.toInt(),
            dynamicBytes = dynamicBytes,
            strings = strings,
        )
    }

    private fun readTag(buf: ByteBuffer, at: Int, is64: Boolean): Long =
        if (is64) buf.getLong(at) else buf.getInt(at).toLong()

    private fun readVal(buf: ByteBuffer, at: Int, is64: Boolean): Long =
        if (is64) buf.getLong(at + 8) else buf.getInt(at + 4).toLong() and 0xFFFFFFFFL

    private fun elfString(bytes: ByteArray, offset: Long): String {
        if (offset < 0L || offset >= bytes.size) return ""
        val start = offset.toInt()
        var end = start
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, start, end - start, Charsets.US_ASCII)
    }
}
