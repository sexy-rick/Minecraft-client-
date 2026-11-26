package dev.empyreal.client.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashSummaryTest {
    private fun line(body: String) = "11-26 15:24:11.186 19408 19408 F DEBUG   : $body"
    private fun block(packageName: String, frame: String) = listOf(
        line("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***"),
        line("Cmdline: $packageName"),
        line("pid: 18444, tid: 19320, name: game-p  >>> $packageName <<<"),
        line("signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr 0x1234"),
        line("backtrace:"),
        line("      #00 pc 0000000000046288  $frame"),
    ).joinToString("\n")

    @Test fun extractsLatestMatchingCrashNotOtherApps() {
        val text = block("dev.empyreal.client.debug", "/cache/first.so") + "\n" +
            block("other.package", "/other/secret.so") + "\n" +
            block("dev.empyreal.client.debug", "/cache/libpairipcore.so")
        val summary = requireNotNull(NativeCrashSummary.extract(text, "dev.empyreal.client.debug"))
        assertTrue(summary.contains("signal 11"))
        assertTrue(summary.contains("#00 pc 0000000000046288"))
        assertTrue(summary.contains("libpairipcore.so"))
        assertFalse(summary.contains("other/secret"))
        assertFalse(summary.contains("first.so"))
    }

    @Test fun ignoresUnmatchedAndMissingBacktraces() {
        assertEquals(null, NativeCrashSummary.extract(block("other.package", "other.so"), "dev.empyreal.client.debug"))
        assertEquals(null, NativeCrashSummary.extract(line("signal 11 (SIGSEGV)"), "dev.empyreal.client.debug"))
        assertEquals(null, NativeCrashSummary.extract("", "dev.empyreal.client.debug"))
    }

    @Test fun excludesFramesFromAnotherDebuggerProcess() {
        val text = listOf(
            line("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***"),
            line("Cmdline: dev.empyreal.client.debug"),
            "11-26 15:24:11.186 999 999 F DEBUG   :       #00 pc 01 /other.so",
            line("      #00 pc 02 /own.so"),
        ).joinToString("\n")
        val summary = requireNotNull(NativeCrashSummary.extract(text, "dev.empyreal.client.debug"))
        assertTrue(summary.contains("/own.so"))
        assertFalse(summary.contains("/other.so"))
    }
}
