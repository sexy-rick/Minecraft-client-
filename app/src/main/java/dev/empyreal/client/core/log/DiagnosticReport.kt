package dev.empyreal.client.core.log

/** A bounded excerpt; the full log remains available separately for deeper investigation. */
internal object DiagnosticReport {
    private const val MAX_EVENTS = 120
    private const val MAX_EVENT_CHARS = 4_000
    private const val MAX_EVENTS_CHARS = 24_000
    private const val MAX_EXIT_CHARS = 12_000
    private const val MAX_CRASH_CHARS = 8_000
    private val timestamp = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s")
    private val relevant = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+[VDIWEF]/Empyreal(?:Game|Core|Exit|Crash)?:")

    fun build(environment: String, entries: List<String>, lastExit: String?, javaCrash: String?): String {
        val events = ArrayDeque<String>()
        var current: StringBuilder? = null
        var eventTruncated = false
        var eventsDropped = false
        fun finishEvent() {
            current?.let {
                if (eventTruncated) it.append("\n[event truncated]")
                events.addLast(it.toString())
                if (events.size > MAX_EVENTS) {
                    events.removeFirst()
                    eventsDropped = true
                }
            }
            current = null
            eventTruncated = false
        }
        entries.forEach { entry ->
            entry.lineSequence().forEach { line ->
                when {
                    line.startsWith("=== session ") -> {
                        finishEvent()
                        current = StringBuilder(line.take(MAX_EVENT_CHARS))
                        eventTruncated = line.length > MAX_EVENT_CHARS
                    }
                    relevant.containsMatchIn(line) -> {
                        finishEvent()
                        current = StringBuilder(line.take(MAX_EVENT_CHARS))
                        eventTruncated = line.length > MAX_EVENT_CHARS
                    }
                    timestamp.containsMatchIn(line) || line.startsWith("---") -> finishEvent()
                    current != null -> {
                        val remaining = MAX_EVENT_CHARS - current!!.length - 1
                        if (remaining > 0) current!!.append('\n').append(line.take(remaining))
                        if (line.length > remaining) eventTruncated = true
                    }
                }
            }
        }
        finishEvent()
        val eventText = events.joinToString("\n")
        return buildString {
            appendLine("=== Empyreal diagnostic report v1 ===")
            appendLine("This report may contain device details, file paths and crash messages. Review before sharing.")
            appendLine("\n=== Environment ===")
            appendLine(head(environment, 4_000))
            appendLine("\n=== Recent Empyreal events (current and previous sessions if retained) ===")
            if (eventsDropped && eventText.length <= MAX_EVENTS_CHARS) appendLine("[earlier events truncated]")
            appendLine(if (eventText.isBlank()) "(not available)" else tail(eventText, MAX_EVENTS_CHARS))
            appendLine("\n=== Android previous process exit (may be unavailable or unrelated) ===")
            appendLine(head(lastExit, MAX_EXIT_CHARS))
            appendLine("\n=== Last recorded Java crash (may be older) ===")
            appendLine(head(javaCrash, MAX_CRASH_CHARS))
        }
    }

    private fun head(text: String?, max: Int): String = when {
        text.isNullOrBlank() -> "(not available)"
        text.length > max -> text.take(max) + "\n[truncated]"
        else -> text
    }

    private fun tail(text: String, max: Int): String =
        if (text.length > max) "[earlier events truncated]\n" + text.takeLast(max) else text
}
