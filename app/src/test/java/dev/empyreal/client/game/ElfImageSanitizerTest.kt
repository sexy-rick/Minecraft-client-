package dev.empyreal.client.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfImageSanitizerTest {

    @Test
    fun recognizesPairipLibraries() {
        assertTrue(ElfImageSanitizer.isPairipLibrary("libpairipcore.so"))
        assertTrue(ElfImageSanitizer.isPairipLibrary("libpairipother.so"))
        assertFalse(ElfImageSanitizer.isPairipLibrary("libminecraftpe.so"))
    }

    @Test
    fun removesPairipNeededDependencyFromElf() {
        val elf = syntheticElfWithPairipDependency()
        val file = File.createTempFile("pairip-", ".so")
        try {
            file.writeBytes(elf)

            val result = ElfImageSanitizer.sanitizeInPlace(file)

            assertTrue(result.modified)
            assertEquals(listOf("libpairipcore.so"), result.removedNeeded)
            assertEquals(listOf("libc++_shared.so"), result.remainingNeeded)
            assertFalse(ElfImageSanitizer.hasBlockedNeeded(file))
        } finally {
            file.delete()
        }
    }

    private fun syntheticElfWithPairipDependency(): ByteArray {
        val bytes = ByteArray(0x400)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2
        bytes[5] = 1
        buf.putShort(16, 3)
        buf.putShort(18, 62)
        buf.putInt(20, 1)
        buf.putLong(32, 64)
        buf.putShort(52, 64)
        buf.putShort(54, 56)
        buf.putShort(56, 2)

        buf.putInt(64, 1)
        buf.putInt(64 + 4, 4)
        buf.putLong(64 + 8, 0)
        buf.putLong(64 + 16, 0x400000)
        buf.putLong(64 + 32, 0x400)
        buf.putLong(64 + 40, 0x400)
        buf.putLong(64 + 48, 0x1000)

        buf.putInt(120, 2)
        buf.putInt(120 + 4, 6)
        buf.putLong(120 + 8, 0x200)
        buf.putLong(120 + 16, 0x400200)
        buf.putLong(120 + 32, 0x40)
        buf.putLong(120 + 40, 0x40)

        buf.putLong(0x200, 1)
        buf.putLong(0x208, 0)
        buf.putLong(0x210, 1)
        buf.putLong(0x218, 17)
        buf.putLong(0x220, 5)
        buf.putLong(0x228, 0x400300)
        buf.putLong(0x230, 10)
        buf.putLong(0x238, 64)
        buf.putLong(0x240, 0)
        buf.putLong(0x248, 0)

        val strings = "libc++_shared.so\u0000libpairipcore.so\u0000"
        System.arraycopy(strings.toByteArray(), 0, bytes, 0x300, strings.length)
        return bytes
    }
}
