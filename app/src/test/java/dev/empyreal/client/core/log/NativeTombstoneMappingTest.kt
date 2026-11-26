package dev.empyreal.client.core.log

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTombstoneMappingTest {
    private fun v(v: Long): ByteArray {
        var n = v
        val out = ArrayList<Byte>()
        do { val next = n ushr 7; out.add(((n and 127) or if (next != 0L) 128 else 0).toByte()); n = next } while (n != 0L)
        return out.toByteArray()
    }
    private fun number(field: Int, value: Long) = v((field * 8).toLong()) + v(value)
    private fun nested(field: Int, value: ByteArray) = v((field * 8 + 2).toLong()) + v(value.size.toLong()) + value
    private fun mapping(begin: Long, end: Long, readable: Boolean, name: String) =
        number(1, begin) + number(2, end) + number(4, if (readable) 1 else 0) + nested(7, name.toByteArray())
    private fun tombstone(pid: Int, address: Long, map: ByteArray) = number(1, 1) + number(5, pid.toLong()) +
        nested(10, number(8, 1) + number(9, address)) + nested(17, map)
    private fun parse(data: ByteArray, pid: Int = 123) = NativeTombstoneMapping.summarize(ByteArrayInputStream(data), pid)

    @Test fun taggedArm64AddressMatchesMappingAndDoesNotRevealName() {
        val data = tombstone(123, 0xb400000000001234UL.toLong(), mapping(0x1000, 0x2000, false, "/private/secret"))
        val result = requireNotNull(parse(data))
        assertTrue(result.contains("not readable")); assertTrue(result.contains("file-backed")); assertTrue(!result.contains("secret"))
    }
    @Test fun detectsUnmappedAddressAndPidMismatch() {
        val data = tombstone(123, 0x2000, mapping(0x1000, 0x2000, true, "[anon:scudo]"))
        assertTrue(requireNotNull(parse(data)).contains("no reported mapping"))
        assertNull(parse(data, 124))
    }
    @Test fun missingAddressAndMalformedDataAreUnavailable() {
        assertNull(parse(number(5, 123) + nested(17, mapping(0, 10, true, ""))))
        assertNull(parse(byteArrayOf(0x52, 0x7f, 1)))
        assertNull(parse(number(5, 123) + nested(10, number(8, 1) + number(9, 1))))
    }
    @Test fun oversizedAndInvalidVarintsAreRejected() {
        val oversized = ByteArray(16 * 1024 * 1024 + 1)
        assertNull(parse(oversized))
        assertNull(parse(byteArrayOf(0x08) + ByteArray(10) { 0xff.toByte() }))
    }
    @Test fun respectsNonArmAddressAndReadPermission() {
        val data = number(1, 3) + number(5, 123) + nested(10, number(8, 1) + number(9, 0x1234)) +
            nested(17, mapping(0x1000, 0x2000, true, "[heap]"))
        assertEquals("Fault mapping: heap; readable, not writable, not executable. Access cause remains unknown.", parse(data))
    }
}
