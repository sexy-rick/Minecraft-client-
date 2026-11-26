package dev.empyreal.client

import android.app.Application
import android.os.Build
import dev.empyreal.client.core.config.ConfigStore
import dev.empyreal.client.core.log.AppLog
import dev.empyreal.client.core.log.LastSession
import dev.empyreal.client.core.log.LogcatCapture
import dev.empyreal.client.core.minecraft.CoreBridge
import dev.empyreal.client.core.offset.OffsetStore
import dev.empyreal.client.core.state.CrashLog
import dev.empyreal.client.core.state.Favorites
import dev.empyreal.client.core.state.GameForeground
import dev.empyreal.client.core.state.HudLayoutStore
import dev.empyreal.client.core.state.HudSync
import dev.empyreal.client.core.state.ModuleManager
import dev.empyreal.client.core.state.PlayStats
import dev.empyreal.client.core.state.SpeedrunTimer
import dev.empyreal.client.game.GameHost
import dev.empyreal.core.hud.InGameHud

class EmpyrealApp : Application() {

    lateinit var modules: ModuleManager
        private set
    lateinit var configs: ConfigStore
        private set
    lateinit var hudLayout: HudLayoutStore
        private set
    lateinit var offsets: OffsetStore
        private set
    lateinit var favorites: Favorites
        private set

    override fun onCreate() {
        super.onCreate()

        AppLog.init(this)

        CrashLog.install(this)
        AppLog.i(TAG, "process start · pid ${android.os.Process.myPid()} · android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        AppLog.i(TAG, "device ${Build.MANUFACTURER} ${Build.MODEL} · abi ${Build.SUPPORTED_ABIS.joinToString()}")
        LogcatCapture.start()
        Thread({
            LastSession.collect(this)
        }, "empyreal-last-session").start()

        modules = ModuleManager(this)
        configs = ConfigStore(this)
        hudLayout = HudLayoutStore(this)
        offsets = OffsetStore(this)
        favorites = Favorites(this)
        PlayStats.init(this)
        instance = this

        GameHost.prepareClasses(this)

        CoreBridge.start()
        modules.pushToCore()
        offsets.list().firstOrNull()?.let { row ->
            modules.setRaw("coords", OffsetStore.toSettings(row))
        }

        CoreBridge.onVersionDetected = { version ->
            offsets.forVersion(version)?.let { row ->
                modules.setRaw("coords", OffsetStore.toSettings(row))
            }
        }

        InGameHud.onModuleToggle = { id, on -> modules.setEnabled(id, on) }
        InGameHud.onMenuMoved = { x, y -> hudLayout.setMenuPosition(x, y) }
        InGameHud.onSpeedrunAction = { action ->
            when (action) {
                "toggle" -> SpeedrunTimer.toggle()
                "split" -> SpeedrunTimer.split()
                "reset" -> SpeedrunTimer.reset()
            }
            HudSync.pushNow()
        }

        GameForeground.addListener { foreground ->
            if (foreground &&
                modules.isEnabled("speedrun") &&
                modules.bool("speedrun", "auto_start") &&
                !SpeedrunTimer.state.value.running &&
                SpeedrunTimer.state.value.elapsedMs == 0L
            ) {
                SpeedrunTimer.start()
                HudSync.pushNow()
            }
        }

        HudSync.start(this)
    }

    companion object {
        lateinit var instance: EmpyrealApp
            private set

        val isInitialized: Boolean
            get() = ::instance.isInitialized

        private const val TAG = "Empyreal"
    }
}
