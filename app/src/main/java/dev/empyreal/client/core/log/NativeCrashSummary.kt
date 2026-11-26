package dev.empyreal.client.core.log

/** Extract only our app's most recent debuggerd backtrace from the textual crash buffer. */
internal object NativeCrashSummary {
    private val debugLine = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+\d+\s+F\s+DEBUG\s*:\s?(.*)$""")
    private val frame = Regex("""^#\d+\s+pc\s+.*""")
    private const val MAX_FRAMES = 24
    private const val MAX_LINE_CHARS = 500

    fun extract(buffer: String?, packageName: String): String? {
        if (buffer.isNullOrBlank() || packageName.isBlank()) return null
        var debuggerPid: String? = null
        var matching = false
        var lines = mutableListOf<String>()
        var frames = 0
        var latest: String? = null
        fun finish() {
            if (matching && frames > 0) latest = lines.joinToString("\n")
        }
        buffer.lineSequence().forEach { line ->
            val match = debugLine.matchEntire(line) ?: return@forEach
            val pid = match.groupValues[1]
            val message = match.groupValues[2].trim()
            if (message.startsWith("*** *** ***")) {
                finish()
                debuggerPid = pid
                matching = false
                frames = 0
                lines = mutableListOf()
                return@forEach
            }
            if (pid != debuggerPid) return@forEach
            if (message.startsWith("Cmdline: ")) {
                val cmdline = message.removePrefix("Cmdline: ").trim()
                matching = cmdline == packageName || cmdline.startsWith("$packageName:")
            }
            if (!matching) return@forEach
            when {
                message.startsWith("Cmdline:") || message.startsWith("pid:") ||
                    message.startsWith("signal ") || message.startsWith("Abort message:") ->
                    lines.add(message.take(MAX_LINE_CHARS))
                frame.matches(message) && frames < MAX_FRAMES -> {
                    lines.add(message.take(MAX_LINE_CHARS))
                    frames++
                }
            }
        }
        finish()
        return latest
    }
}
