package dev.empyreal.client.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SavedStateDexProbeTest {
    // Synthetic DEX, not a game APK: const-string v2, invoke register(v0,v2,v1), return.
    private fun fixture() = Base64.getDecoder().decode(
        "ZGV4CjAzNQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB0AQAAcAAAAHhWNBIAAAAAAAAAAAAAAAAFAAAAcAAAAAIAAACEAAAAAAAAAAAAAAAAAAAAAAAAAAIAAACMAAAAAQAAAJwAAAAAAAAAAAAAALwAAADmAAAAEAEAABYBAAAyAQAAAAAAAAEAAAAAAAAAAgAAAAEAAAADAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAbAEAAAAAAAAoTGFuZHJvaWR4L2ZyYWdtZW50L2FwcC9GcmFnbWVudEFjdGl2aXR5OwAoTGFuZHJvaWR4L3NhdmVkc3RhdGUvU2F2ZWRTdGF0ZVJlZ2lzdHJ5OwAEaW5pdAAacmVnaXN0ZXJTYXZlZFN0YXRlUHJvdmlkZXIAGWFuZHJvaWQ6c3VwcG9ydDpsaWZlY3ljbGUAAAAAAwABAAMAAAAAAAAABgAAABoCBABuMAEAIAEOAAAAAQAAAtAC"
    )

    @Test fun findsTargetAndKnownLiteralWithoutEmittingStringContents() {
        val result = SavedStateDexProbe.inspect(fixture())
        assertEquals(setOf("FragmentActivity"), result.classes)
        val method = result.method!!
        assertTrue(method.contains("opcodes=1a,6e,0e"))
        assertTrue(method.contains("lifecycleLiteralRegs=2"))
        assertTrue(method.contains("registerProviderKeyRegs=2"))
        assertTrue(!method.contains("android:support:lifecycle"))
        assertTrue(method.contains("registers are not data-flow proof"))
    }

    @Test fun reportsZeroConstantSeparatelyFromKeyRegister() {
        val dex = fixture()
        dex[352] = 0x12; dex[353] = 0x02 // const/4 v2, 0
        dex[354] = 0; dex[355] = 0 // nop
        val method = SavedStateDexProbe.inspect(dex).method!!
        assertTrue(method.contains("nullConstRegs=2"))
        assertTrue(method.contains("lifecycleLiteralRegs=none"))
        assertTrue(method.contains("registerProviderKeyRegs=2"))
    }

    @Test fun unsupportedInstructionDoesNotPretendTheScanWasComplete() {
        val dex = fixture()
        dex[352] = 0x3e // unused/unsupported opcode
        val method = SavedStateDexProbe.inspect(dex).method!!
        assertTrue(method.contains("scan=unsupported-or-truncated"))
    }

    @Test fun rejectsTruncatedAndUnsupportedDex() {
        for (dex in listOf(fixture().copyOf(200), fixture().apply { this[6] = '1'.code.toByte() })) {
            var failed = false
            try { SavedStateDexProbe.inspect(dex) } catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed)
        }
    }
}
