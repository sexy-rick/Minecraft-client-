package dev.empyreal.core

import android.app.Application
import android.content.Context
import android.os.Build

object Attached {

    @Volatile
    var packageName: String = "unknown"
        private set

    @Volatile
    var processName: String = "unknown"
        private set

    @Volatile
    var versionName: String = ""
        private set

    @Volatile
    var sourceDir: String = ""
        private set

    @Volatile
    var dataDir: String = ""
        private set

    @Volatile
    var uid: Int = -1
        private set

    fun configure(gameContext: Context, gamePackage: String) {
        packageName = gamePackage
        dataDir = gameContext.dataDir.absolutePath
        uid = android.os.Process.myUid()
        processName = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else null
        }.getOrNull() ?: gameContext.packageName

        runCatching {
            @Suppress("DEPRECATION")
            val info = gameContext.packageManager.getApplicationInfo(gamePackage, 0)
            sourceDir = info.sourceDir.orEmpty()
            @Suppress("DEPRECATION")
            versionName = gameContext.packageManager.getPackageInfo(gamePackage, 0).versionName.orEmpty()
        }
    }
}
