package dev.empyreal.client.game

import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.mojang.minecraftpe.MainActivity
import dev.empyreal.client.core.log.AppLog
import dev.empyreal.client.core.minecraft.MinecraftLauncher
import dev.empyreal.client.core.state.GameForeground
import dev.empyreal.core.hud.HudState
import dev.empyreal.core.hud.InGameHud

class GameActivity : MainActivity() {

    private var gameReady = false

    override fun getResources(): Resources = GameHost.resources ?: super.getResources()

    override fun getAssets(): AssetManager = GameHost.assets ?: super.getAssets()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.i(TAG, "GameActivity.onCreate — thread ${Thread.currentThread().name}")
        if (!GameHost.ensurePrepared(applicationContext)) {
            Log.e(TAG, "game host not ready; opening the installed Minecraft instead")
            AppLog.e(TAG, "game host not ready; opening the installed Minecraft instead")
            Toast.makeText(this, "Opening the installed Minecraft…", Toast.LENGTH_SHORT).show()
            fallbackToExternalMinecraft()
            finish()
            markCalled()
            return
        }

        Log.i(TAG, "starting Minecraft in-process")
        AppLog.i(TAG, "starting Minecraft in-process")
        GameHost.prepareGameEnvironment(this)
        GameHost.logEnvironment()
        GameHost.preflight()

        if (GameHost.gameTheme != 0) setTheme(GameHost.gameTheme)

        AppLog.i(TAG, "entering Mojang MainActivity.onCreate — everything after this is the game's own code")

        val failure: Throwable? = try {
            super.onCreate(savedInstanceState)
            null
        } catch (error: Throwable) {
            error
        }
        if (failure != null) {
            AppLog.e(TAG, "the game's own onCreate threw — falling back to the installed game", failure)
            Toast.makeText(this, "Minecraft could not start here — opening the installed game", Toast.LENGTH_LONG).show()
            markCalled()
            GameHost.onGameStopped()
            runCatching { finish() }
            fallbackToExternalMinecraft()
            return
        }
        gameReady = true
        GameHost.onGameStarted()
        AppLog.i(TAG, "Mojang MainActivity.onCreate returned")
    }

    private fun fallbackToExternalMinecraft() {
        val incoming = intent
        if (incoming?.action == Intent.ACTION_VIEW && incoming.data != null) {
            val pkg = MinecraftLauncher.installedPackage(this)
            if (pkg != null) {
                val forward = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(incoming.data, incoming.type ?: "application/octet-stream")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (runCatching { startActivity(forward); true }.getOrDefault(false)) {
                    return
                }
            }
        }
        MinecraftLauncher.launch(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        if (!gameReady) {
            markCalled()
            return
        }
        AppLog.i(TAG, "GameActivity.onPostCreate — installing the HUD")
        super.onPostCreate(savedInstanceState)
        runCatching { InGameHud.install(this) }
            .onSuccess { AppLog.i(TAG, "in-game HUD installed") }
            .onFailure {
                Log.e(TAG, "in-game HUD not installed", it)
                AppLog.e(TAG, "in-game HUD not installed", it)
            }
    }

    override fun onStart() {
        if (!gameReady) {
            markCalled()
            return
        }
        AppLog.i(TAG, "GameActivity.onStart")
        super.onStart()
        AppLog.i(TAG, "GameActivity.onStart returned")
    }

    override fun onResume() {
        if (!gameReady) {
            markCalled()
            return
        }
        AppLog.i(TAG, "GameActivity.onResume")
        super.onResume()
        HudState.foreground = true
        GameForeground.set(true)
        runCatching { InGameHud.install(this) }
        AppLog.i(TAG, "GameActivity.onResume returned")
    }

    override fun onPause() {
        if (!gameReady) {
            markCalled()
            return
        }
        AppLog.i(TAG, "GameActivity.onPause")
        HudState.foreground = false
        HudState.menuOpen = false
        GameForeground.set(false)
        super.onPause()
        AppLog.i(TAG, "GameActivity.onPause returned")
    }

    override fun onStop() {
        if (!gameReady) {
            markCalled()
            return
        }
        AppLog.i(TAG, "GameActivity.onStop")
        super.onStop()
        AppLog.i(TAG, "GameActivity.onStop returned")
    }

    override fun onNewIntent(intent: Intent) {
        if (!gameReady) {
            return
        }
        AppLog.i(TAG, "GameActivity.onNewIntent")
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "Minecraft activity destroyed")
        AppLog.i(TAG, "GameActivity.onDestroy")
        runCatching { InGameHud.uninstall(this) }
        if (gameReady) {
            GameHost.onGameStopped()
            super.onDestroy()
        } else {
            markCalled()
        }
    }

    private fun markCalled() {
        runCatching {
            var type: Class<*>? = javaClass
            while (type != null) {
                val field = type.declaredFields.firstOrNull { it.name == "mCalled" }
                if (field != null) {
                    field.isAccessible = true
                    field.setBoolean(this, true)
                    return
                }
                type = type.superclass
            }
        }
    }

    private companion object {
        const val TAG = "EmpyrealGame"
    }
}
