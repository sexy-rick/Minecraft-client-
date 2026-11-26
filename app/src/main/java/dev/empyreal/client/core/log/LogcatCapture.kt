package dev.empyreal.client.core.log

import kotlin.concurrent.thread

object LogcatCapture {

    private const val TAG = "EmpyrealLogcat"

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    private var generation = 0

    @Volatile
    private var process: Process? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        spawn(generation)
    }

    @Synchronized
    fun stop() {
        running = false
        generation++
        val p = process
        process = null
        runCatching { p?.destroy() }
    }

    @Synchronized
    fun restart() {
        generation++
        val previous = process
        process = null
        runCatching { previous?.destroy() }
        running = true
        spawn(generation)
    }

    private fun spawn(gen: Int) {
        thread(name = "empyreal-logcat", isDaemon = true) {
            AppLog.i(TAG, "capture started")
            var failures = 0
            var backoff = 1000L
            while (running && gen == generation) {
                try {
                    val p = ProcessBuilder("logcat", "-v", "threadtime", "-T", "800")
                        .redirectErrorStream(true)
                        .start()
                    process = p
                    try {
                        p.inputStream.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                if (!running || gen != generation) break
                                AppLog.raw(line)
                            }
                        }
                    } finally {
                        runCatching { p.destroy() }
                        runCatching { p.waitFor() }
                        if (process === p) process = null
                    }

                    failures++
                } catch (error: Exception) {
                    if (!running || gen != generation) break
                    failures++
                    if (failures <= 3) {
                        AppLog.w(TAG, "capture interrupted: ${error.message}")
                    }
                }
                if (!running || gen != generation) break
                if (failures >= 5) {
                    AppLog.w(TAG, "logcat is not available on this device; live capture stopped")
                    break
                }
                runCatching { Thread.sleep(backoff) }
                backoff = (backoff * 2).coerceAtMost(15_000L)
            }
            if (gen == generation) running = false
            AppLog.i(TAG, "capture stopped")
        }
    }
}
