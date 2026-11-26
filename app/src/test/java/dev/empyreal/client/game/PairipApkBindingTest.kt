package dev.empyreal.client.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PairipApkBindingTest {
    private class RuntimeFixture {
        companion object {
            private var apkPath: String? = null
            fun path(): String? = apkPath
            fun readAsset(name: String): String = ZipFile(requireNotNull(apkPath)).use { apk ->
                val entry = requireNotNull(apk.getEntry("assets/$name"))
                apk.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }
    }

    private class UnsupportedRuntime

    private fun apk(withAsset: Boolean = false): File = File.createTempFile("minecraft-base", ".apk").apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
            if (withAsset) {
                zip.putNextEntry(ZipEntry("assets/fixture"))
                zip.write("game bytecode".toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test fun resolvesGameAssetInsteadOfHostAsset() {
        val host = apk()
        val base = apk(withAsset = true)
        try {
            PairipApkBinding.bind(RuntimeFixture::class.java, host)
            assertTrue(runCatching { RuntimeFixture.readAsset("fixture") }.isFailure)
            PairipApkBinding.bind(RuntimeFixture::class.java, base)
            assertEquals("game bytecode", RuntimeFixture.readAsset("fixture"))
        } finally {
            host.delete()
            base.delete()
        }
    }

    @Test fun suppliesInstalledBaseApkToRuntime() {
        val base = apk()
        try {
            PairipApkBinding.bind(RuntimeFixture::class.java, base)
            assertEquals(base.absolutePath, RuntimeFixture.path())
        } finally {
            base.delete()
        }
    }

    @Test fun rejectsInvalidArchivesAndUnknownRuntimeLayout() {
        val invalid = File.createTempFile("not-apk", ".apk")
        val noManifest = File.createTempFile("no-manifest", ".apk")
        ZipOutputStream(noManifest.outputStream()).use { }
        val base = apk()
        try {
            val previous = RuntimeFixture.path()
            assertTrue(runCatching { PairipApkBinding.bind(RuntimeFixture::class.java, invalid) }.isFailure)
            assertTrue(runCatching { PairipApkBinding.bind(RuntimeFixture::class.java, noManifest) }.isFailure)
            assertEquals(previous, RuntimeFixture.path())
            assertTrue(runCatching { PairipApkBinding.bind(UnsupportedRuntime::class.java, base) }.isFailure)
        } finally {
            invalid.delete()
            noManifest.delete()
            base.delete()
        }
    }
}
