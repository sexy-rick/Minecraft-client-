package dev.empyreal.client.game

import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** Selects the original installed APK for the game's existing bytecode loader. */
internal object PairipApkBinding {
    fun bind(runtimeClass: Class<*>, installedBase: File) {
        require(installedBase.isFile && installedBase.canRead()) { "Minecraft base APK is not readable" }
        ZipFile(installedBase).use { apk ->
            require(apk.getEntry("AndroidManifest.xml") != null) { "Minecraft base APK has no manifest" }
        }
        val field = runtimeClass.getDeclaredField("apkPath")
        require(field.type == String::class.java && Modifier.isStatic(field.modifiers) && !Modifier.isFinal(field.modifiers)) {
            "unsupported PairIP APK path field"
        }
        field.isAccessible = true
        field.set(null, installedBase.absolutePath)
        check(field.get(null) == installedBase.absolutePath) { "could not set Minecraft base APK path" }
    }
}
