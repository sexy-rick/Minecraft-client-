package dev.empyreal.client.core.minecraft

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object MinecraftLauncher {

    const val PACKAGE = "com.mojang.minecraftpe"
    const val PACKAGE_BETA = "com.mojang.minecraftpe.beta"

    fun installedPackage(context: Context): String? {
        val pm = context.packageManager
        return listOf(PACKAGE, PACKAGE_BETA).firstOrNull { pkg ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
        }
    }

    fun isInstalled(context: Context): Boolean = installedPackage(context) != null

    fun isGamePackage(pkg: String?) = pkg == PACKAGE || pkg == PACKAGE_BETA

    fun versionName(context: Context): String? {
        val pkg = installedPackage(context) ?: return null
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionName
            }
        }.getOrNull()
    }

    fun installedIcon(context: Context) = runCatching {
        context.packageManager.getApplicationIcon(installedPackage(context) ?: PACKAGE)
    }.getOrNull()

    fun openStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(web) }
        }
    }

}
