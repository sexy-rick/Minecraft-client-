package dev.empyreal.client.core.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test fun includesUsefulEvidenceAndExcludesUnrelatedLogcat() {
        val report = DiagnosticReport.build(
            environment = "Minecraft: 1.26.66.1\nABI: arm64-v8a",
            entries = listOf(
                "=== session previous ===",
                "11-26 13:45:49.000 E/EmpyrealGame: load failed\njava.lang.UnsatisfiedLinkError: ExecuteProgram\n    at game.Native.load(Native.kt:5)",
                "11-26 13:45:49.001 4 4 E OtherApp: private noise",
                "=== session current ===",
                "11-26 13:46:00.000 I/EmpyrealExit: previous session ended",
            ),
            lastExit = "reason: Native crash\n--- trace ---\nsignal 6",
            javaCrash = null,
        )
        assertTrue(report.contains("Minecraft: 1.26.66.1"))
        assertTrue(report.contains("ExecuteProgram"))
        assertTrue(report.contains("Native.kt:5"))
        assertTrue(report.contains("signal 6"))
        assertTrue(report.contains("session previous"))
        assertFalse(report.contains("private noise"))
        assertTrue(report.contains("not available"))
    }

    @Test fun emptyAndLongInputsStayBounded() {
        val report = DiagnosticReport.build("", listOf("11-26 13:45:49.000 E/EmpyrealGame: " + "x".repeat(100_000)), "y".repeat(100_000), "z".repeat(100_000))
        assertTrue(report.length < 60_000)
        assertTrue(report.contains("truncated"))
        assertTrue(DiagnosticReport.build("", emptyList(), null, null).contains("not available"))
        assertTrue(DiagnosticReport.build("e".repeat(100_000), emptyList(), null, null).length < 60_000)
    }

    @Test fun identifiesTruncatedIndividualEvent() {
        val report = DiagnosticReport.build("test", listOf("11-26 13:45:49.000 E/EmpyrealGame: " + "x".repeat(10_000)), null, null)
        assertTrue(report.contains("event truncated"))
    }

    @Test fun retainsLatestEventsWhenThereAreMany() {
        val entries = (1..200).map { "11-26 13:45:49.000 I/EmpyrealGame: event $it" }
        val report = DiagnosticReport.build("test", entries, null, null)
        assertTrue(report.contains("event 200"))
        assertFalse(report.contains("event 1\n"))
        assertTrue(report.contains("earlier events truncated"))
    }
}
