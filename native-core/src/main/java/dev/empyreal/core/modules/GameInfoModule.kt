package dev.empyreal.core.modules

import android.os.Build
import android.os.Process
import dev.empyreal.core.Attached
import dev.empyreal.core.hud.HudState
import dev.empyreal.core.module.NativeModule

class GameInfoModule : NativeModule(
    id = "gameinfo",
    name = "Game Info",
    description = "Reports the attached process, ABI and game version.",
) {

    override fun writeState(): Map<String, String> = buildMap {
        put("package", Attached.packageName)
        put("process", Attached.processName)
        put("version", Attached.versionName.ifBlank { "unknown" })
        put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        put("pid", Process.myPid().toString())
        put("sdk", Build.VERSION.SDK_INT.toString())
        put("fps", HudState.fps.toString())
        put("cps", HudState.cps.toString())
        put("foreground", if (HudState.foreground) "1" else "0")
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("core", "attached")
    }
}
