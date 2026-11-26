package dev.empyreal.client.core.log

import android.content.Context
import android.os.Build
import android.os.Process
import dev.empyreal.client.core.minecraft.MinecraftLauncher
import dev.empyreal.client.core.state.CrashLog
import dev.empyreal.client.game.GameHost
import java.io.File

internal object DiagnosticReportCollector {
    fun collect(context: Context): String {
        val app = context.applicationContext
        val environment = buildString {
            @Suppress("DEPRECATION")
            val appVersion = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
            appendLine("Empyreal: ${app.packageName} ${appVersion ?: "unknown version"}")
            appendLine("Loader cache schema: ${GameHost.CACHE_SCHEMA}")
            appendLine("Minecraft package: ${MinecraftLauncher.installedPackage(app) ?: "not installed"}")
            appendLine("Minecraft version: ${MinecraftLauncher.versionName(app) ?: "unknown"}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Process: ${if (Process.is64Bit()) "64-bit" else "32-bit"}")
            appendLine("Host: ${GameHost.describe()}")
            val marker = runCatching { File(app.codeCacheDir, "loading-library.txt").readText().trim() }.getOrNull()
            appendLine("Last unfinished native load: ${marker?.take(200)?.ifBlank { null } ?: "none recorded"}")
        }
        return DiagnosticReport.build(
            environment = environment,
            entries = AppLog.snapshot(),
            lastExit = runCatching { LastSession.readExit(app) }.getOrNull(),
            javaCrash = runCatching { CrashLog.read(app) }.getOrNull(),
            nativeCrash = runCatching {
                NativeCrashSummary.extract(LastSession.readCrashBuffer(app), app.packageName)
            }.getOrNull(),
        )
    }
}
