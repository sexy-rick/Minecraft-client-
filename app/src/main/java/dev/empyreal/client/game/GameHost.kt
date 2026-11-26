package dev.empyreal.client.game

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import dalvik.system.BaseDexClassLoader
import dalvik.system.DexFile
import dev.empyreal.client.core.log.AppLog
import dev.empyreal.client.core.minecraft.MinecraftLauncher
import dev.empyreal.client.core.state.GameForeground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlin.concurrent.thread

object GameHost {

    private const val TAG = "EmpyrealGame"
    const val GAME_PACKAGE = "com.mojang.minecraftpe"
    private const val GAME_ACTIVITY = "com.mojang.minecraftpe.MainActivity"
    private const val CACHE_SCHEMA = "v3-pairip-verified"

    enum class Phase { Idle, Preparing, Ready, Failed }

    data class LaunchState(
        val phase: Phase = Phase.Idle,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(LaunchState())
    val state: StateFlow<LaunchState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile
    private var prepared = false

    @Volatile
    private var nativeLoaded = false

    @Volatile
    private var active = false

    private var packageName: String = GAME_PACKAGE
    private var preparedSourceDir: String = ""
    private var preparedUpdateTime: Long = -1L
    private var appClassLoader: BaseDexClassLoader? = null
    private var gameResources: Resources? = null
    private var gameAssets: AssetManager? = null
    private var nativeLibDir: String = ""
    private var extractedLibDir: File? = null
    private var nativeLibNames: List<String> = emptyList()

    @Volatile
    var gameTheme: Int = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    val resources: Resources? get() = gameResources
    val assets: AssetManager? get() = gameAssets

    fun logEnvironment() {
        runCatching {
            val mapped = File("/proc/self/maps").useLines { lines ->
                lines.map { it.substringAfterLast(' ') }
                    .filter { it.endsWith(".so") }
                    .map { it.substringAfterLast('/') }
                    .toSortedSet()
            }
            AppLog.i(TAG, "mapped libraries (${mapped.size}): ${mapped.joinToString(" ")}")
            AppLog.i(TAG, "game package $packageName · native dir $nativeLibDir")
            AppLog.i(TAG, "game resources ${gameResources != null} · assets ${gameAssets != null} · theme 0x${Integer.toHexString(gameTheme)}")
            appClassLoader?.let { loader ->
                runCatching { loader.findLibrary("minecraftpe") }
                    .onSuccess { AppLog.i(TAG, "findLibrary(minecraftpe) → $it") }
                    .onFailure { AppLog.w(TAG, "findLibrary(minecraftpe) failed: ${it.message}") }
            }
        }.onFailure { AppLog.w(TAG, "environment report failed: ${it.message}") }
    }

    fun preflight() {
        val pkg = packageName
        AppLog.i(TAG, "preflight for $pkg")
        appClassLoader?.let { loader ->
            runCatching { Class.forName("com.mojang.minecraftpe.MainActivity", false, loader) }
                .onSuccess { AppLog.i(TAG, "  game activity class: ${it.classLoader?.javaClass?.simpleName}") }
                .onFailure { AppLog.e(TAG, "  game activity class missing", it) }
            runCatching { loader.findLibrary("minecraftpe") }
                .onSuccess { AppLog.i(TAG, "  engine library: $it") }
                .onFailure { AppLog.e(TAG, "  engine library: not found", it) }
        } ?: AppLog.e(TAG, "  no game class loader")

        val res = gameResources
        if (res == null) {
            AppLog.e(TAG, "  game resources: none")
        } else {
            val samples = listOf(
                "string" to "app_name",
                "layout" to "activity_main",
                "style" to "Theme.Minecraft",
                "drawable" to "ic_launcher",
            )
            samples.forEach { (type, name) ->
                val id = runCatching { res.getIdentifier(name, type, pkg) }.getOrDefault(0)
                AppLog.i(TAG, "  resource $type/$name → ${if (id == 0) "missing" else "0x" + Integer.toHexString(id)}")
            }
        }

        val assets = gameAssets
        if (assets == null) {
            AppLog.e(TAG, "  game assets: none")
        } else {
            val count = runCatching { assets.list("")?.size ?: -1 }.getOrDefault(-1)
            AppLog.i(TAG, "  game assets top level: $count entries")
        }
        AppLog.i(TAG, "preflight done")
    }

    fun describe(): String =
        "prepared=$prepared nativeLoaded=$nativeLoaded active=$active error=$lastError"

    fun isGameInstalled(context: Context): Boolean =
        MinecraftLauncher.installedPackage(context) != null

    fun isGameActive(): Boolean = active

    fun onGameStarted() {
        active = true
        AppLog.i(TAG, "game activity started — thread ${Thread.currentThread().name}")
        _state.value = LaunchState(Phase.Ready)
        GameForeground.set(true)
    }

    fun onGameStopped() {
        active = false
        AppLog.i(TAG, "game activity stopped")
        _state.value = LaunchState(Phase.Idle)
        GameForeground.set(false)
    }

    fun launch(context: Context) {
        val app = context.applicationContext
        val pkg = MinecraftLauncher.installedPackage(app)
        AppLog.i(TAG, "launch tapped — installed=${pkg ?: "no"} prepared=$prepared nativeLoaded=$nativeLoaded")
        if (pkg == null) {
            AppLog.w(TAG, "Minecraft is not installed; opening the store")
            MinecraftLauncher.openStore(app)
            return
        }

        synchronized(lock) {
            if (_state.value.phase == Phase.Preparing) {
                AppLog.d(TAG, "preparation already in progress; ignoring duplicate tap")
                return
            }
            if (prepared && nativeLoaded && !isPackageStale(app, pkg)) {
                openGame(app)
                return
            }
            _state.value = LaunchState(Phase.Preparing, "Preparing Minecraft…")
        }

        thread(name = "empyreal-game-prepare", isDaemon = true) {
            val ready = ensurePrepared(app)
            AppLog.i(TAG, "preparation finished — ready=$ready detail=$lastError")
            if (ready) {
                main.post { openGame(app) }
            } else {
                Log.e(TAG, "in-process host unavailable, opening the installed game")
                AppLog.e(TAG, "in-process host unavailable; opening the installed game instead")
                _state.value = LaunchState(Phase.Failed, lastError)
                main.post { MinecraftLauncher.launch(app) }
            }
        }
    }

    fun prepareClasses(context: Context): Boolean {
        synchronized(lock) {
            val pkg = MinecraftLauncher.installedPackage(context) ?: return false
            if (prepared && !isPackageStale(context, pkg)) return true
            return runCatching { prepare(context.applicationContext) }
                .onFailure { error ->
                    lastError = error.message ?: error.javaClass.simpleName
                    Log.e(TAG, "game classes could not be prepared", error)
                    AppLog.e(TAG, "game classes could not be prepared", error)
                }
                .isSuccess
        }
    }

    fun ensurePrepared(context: Context): Boolean {
        synchronized(lock) {
            val pkg = MinecraftLauncher.installedPackage(context)
            if (pkg != null && prepared && isPackageStale(context, pkg) && !nativeLoaded) {
                AppLog.i(TAG, "detected Minecraft package update; re-preparing game host")
                prepared = false
            }
            if (prepared && nativeLoaded) return true
            runCatching {
                AppLog.i(TAG, "preparing game host…")
                prepare(context.applicationContext)
                loadNativeLibraries(context.applicationContext)
            }.onFailure { error ->
                lastError = error.message ?: error.javaClass.simpleName
                Log.e(TAG, "game host preparation failed", error)
                AppLog.e(TAG, "game host preparation failed", error)
            }
            return prepared && nativeLoaded
        }
    }

    private fun isPackageStale(context: Context, pkg: String): Boolean {
        if (!prepared) return true
        val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return true
        val updateTime = packageUpdateTime(context, pkg)
        return info.sourceDir.orEmpty() != preparedSourceDir || updateTime != preparedUpdateTime
    }

    private fun packageUpdateTime(context: Context, pkg: String): Long = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).lastUpdateTime
        }
    }.getOrDefault(0L)

    private fun openGame(context: Context) {
        val intent = Intent(context, GameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        AppLog.i(TAG, "starting GameActivity in this process")
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                lastError = error.message ?: error.javaClass.simpleName
                Log.e(TAG, "could not open the in-app game, opening the installed game", error)
                AppLog.e(TAG, "could not open the in-app game; opening the installed game instead", error)
                _state.value = LaunchState(Phase.Failed, lastError)
                MinecraftLauncher.launch(context)
            }
    }

    fun prepareGameEnvironment(context: Context) {
        AppLog.i(TAG, "preparing the game environment (library dir, security provider, Firebase)")
        runCatching { pointNativeLibraryDirAtGame() }
            .onFailure {
                Log.d(TAG, "native library dir not redirected: ${it.message}")
                AppLog.d(TAG, "native library dir not redirected: ${it.message}")
            }
        runCatching { installConscrypt() }
            .onFailure {
                Log.d(TAG, "Conscrypt not installed: ${it.message}")
                AppLog.d(TAG, "Conscrypt not installed: ${it.message}")
            }
        runCatching { initialiseGameFirebase(context) }
            .onFailure {
                Log.d(TAG, "game Firebase app not initialised: ${it.message}")
                AppLog.d(TAG, "game Firebase app not initialised: ${it.message}")
            }
        AppLog.i(TAG, "game environment ready")
    }

    private fun installConscrypt() {
        val conscrypt = Class.forName("org.conscrypt.Conscrypt")
        val provider = conscrypt.getMethod("newProvider").invoke(null) as java.security.Provider
        java.security.Security.insertProviderAt(provider, 1)
        Log.i(TAG, "Conscrypt installed as the first security provider")
    }

    private fun pointNativeLibraryDirAtGame() {
        if (nativeLibDir.isBlank()) return
        val thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null)
        val bound = field(thread, "mBoundApplication")
        (field(bound, "appInfo") as ApplicationInfo).nativeLibraryDir = nativeLibDir
    }

    private fun initialiseGameFirebase(context: Context) {
        val res = gameResources ?: return
        fun string(name: String): String? {
            val id = res.getIdentifier(name, "string", packageName)
            return if (id == 0) null else res.getString(id)
        }
        val appId = string("google_app_id") ?: return

        val builderClass = Class.forName("com.google.firebase.FirebaseOptions" + "\$" + "Builder")
        val builder = builderClass.getDeclaredConstructor().newInstance()
        fun set(method: String, value: String?) {
            if (!value.isNullOrBlank()) builderClass.getMethod(method, String::class.java).invoke(builder, value)
        }
        set("setApplicationId", appId)
        set("setApiKey", string("google_api_key"))
        set("setProjectId", string("project_id"))
        set("setGcmSenderId", string("gcm_defaultSenderId"))
        set("setStorageBucket", string("google_storage_bucket"))
        val options = builderClass.getMethod("build").invoke(builder)

        val firebaseApp = Class.forName("com.google.firebase.FirebaseApp")
        val apps = firebaseApp.getMethod("getApps", Context::class.java).invoke(null, context) as? List<*>
        if (!apps.isNullOrEmpty()) return
        firebaseApp.getMethod("initializeApp", Context::class.java, options.javaClass).invoke(null, context, options)
        Log.i(TAG, "game Firebase app initialised from its own resources")
    }

    private fun prepare(context: Context) {
        val pkg = MinecraftLauncher.installedPackage(context)
            ?: throw IllegalStateException("Minecraft is not installed")
        if (prepared && !isPackageStale(context, pkg)) return

        HiddenApi.exempt()

        val info: ApplicationInfo = context.packageManager.getApplicationInfo(pkg, 0)
        val updateTime = packageUpdateTime(context, pkg)
        AppLog.i(TAG, "package $pkg · version ${MinecraftLauncher.versionName(context)} · targetSdk ${info.targetSdkVersion}")
        AppLog.i(TAG, "apks ${apks(info)}")
        nativeLibDir = info.nativeLibraryDir.orEmpty()
        AppLog.i(TAG, "native library dir $nativeLibDir")
        val appLoader = context.classLoader as BaseDexClassLoader

        nativeLibNames = nativeLibraryNames(info)
        AppLog.i(TAG, "native libraries to load: ${nativeLibNames.joinToString(" ")}")

        val libDir = File(context.codeCacheDir, "game-libs").apply { mkdirs() }
        val stampFile = File(libDir, ".package-stamp")
        val currentStamp = "$CACHE_SCHEMA|$pkg|$updateTime|${info.sourceDir.orEmpty()}"
        val previousStamp = runCatching { stampFile.readText().trim() }.getOrNull()
        if (previousStamp != currentStamp) {
            AppLog.i(TAG, "refreshing game-libs cache ($CACHE_SCHEMA) for updated Minecraft install")
            libDir.listFiles()?.forEach { f ->
                runCatching {
                    f.setWritable(true, true)
                    f.delete()
                }
            }
            File(context.codeCacheDir, "loading-library.txt").delete()
            runCatching { stampFile.writeText(currentStamp) }
        }
        val bundled = bundledSdkLibraries(context, currentProcessAbi())
        pruneCacheOverrides(libDir, bundled)
        extractedLibDir = libDir

        putGameOnClasspath(appLoader, info)

        runCatching { Class.forName(GAME_ACTIVITY, false, appLoader) }
            .onFailure { error ->
                throw IllegalStateException(
                    "the game's classes are not reachable from our class loader: ${error.message}",
                    error,
                )
            }
        AppLog.i(TAG, "game classes verified on our class loader")

        dev.empyreal.core.Attached.configure(
            context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY),
            pkg,
        )

        packageName = pkg
        preparedSourceDir = info.sourceDir.orEmpty()
        preparedUpdateTime = updateTime
        appClassLoader = appLoader
        gameAssets = mergedAssetManager(info, context)
        @Suppress("DEPRECATION")
        gameResources = Resources(gameAssets, context.resources.displayMetrics, context.resources.configuration)
        AppLog.i(TAG, "game resources built (${gameResources?.javaClass?.simpleName})")
        gameTheme = runCatching {
            context.packageManager.getActivityInfo(ComponentName(pkg, GAME_ACTIVITY), 0).theme
        }.getOrDefault(0)
        prepared = true
        lastError = null

        Log.i(TAG, "game host ready: $pkg, ${nativeLibNames.size} native libraries")
        AppLog.i(TAG, "game host ready — $pkg, ${nativeLibNames.size} native libraries, theme 0x${Integer.toHexString(gameTheme)}")
    }

    private fun putGameOnClasspath(ours: BaseDexClassLoader, info: ApplicationInfo) {
        val pathList = field(ours, "pathList")

        val withDex = apks(info).filter(::containsDex)
        if (withDex.isEmpty()) throw IllegalStateException("the installed Minecraft has no classes")
        AppLog.i(TAG, "apks with classes: ${withDex.joinToString(" ")}")
        withDex.forEach { apk ->
            if (!appendDexPath(pathList, apk)) {
                throw IllegalStateException("the game's classes could not be added: $apk")
            }
        }
        AppLog.i(TAG, "game classes are on our class loader")

        addNativeSearchPath(pathList, nativeSearchPaths(info))
    }

    private fun containsDex(apk: String): Boolean = runCatching {
        ZipFile(apk).use { zip ->
            java.util.Collections.list(zip.entries()).any { Regex("classes\\d*\\.dex").matches(it.name) }
        }
    }.getOrDefault(false)

    private fun appendDexPath(pathList: Any, apk: String): Boolean {
        val type = pathList.javaClass

        method(type, "addDexPath", String::class.java, File::class.java)?.let { add ->
            runCatching { add.invoke(pathList, apk, null) }
                .onSuccess {
                    AppLog.i(TAG, "dex added through addDexPath: $apk")
                    return true
                }
                .onFailure { AppLog.w(TAG, "addDexPath($apk) failed: ${it.message}") }
        }
        method(type, "addDexPath", String::class.java)?.let { add ->
            runCatching { add.invoke(pathList, apk) }
                .onSuccess {
                    AppLog.i(TAG, "dex added through addDexPath(String): $apk")
                    return true
                }
                .onFailure { AppLog.w(TAG, "addDexPath(String) failed: ${it.message}") }
        }

        return runCatching {
            @Suppress("DEPRECATION")
            val dex = DexFile.loadDex(apk, null, 0)
            val element = Class.forName("dalvik.system.DexPathList\$Element")
                .getDeclaredConstructor(DexFile::class.java, File::class.java)
                .newInstance(dex, File(apk))
            append(pathList, "dexElements", arrayOf(element))
            AppLog.i(TAG, "dex added through DexFile.loadDex: $apk")
            true
        }.getOrElse { error ->
            AppLog.e(TAG, "dex could not be added: $apk", error)
            false
        }
    }

    private fun mergedAssetManager(info: ApplicationInfo, context: Context): AssetManager {
        val assets = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val addPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        (apks(info) + context.packageResourcePath).distinct().forEach { path ->
            runCatching { addPath.invoke(assets, path) }
                .onFailure { Log.w(TAG, "assets of $path not added: ${it.message}") }
        }
        return assets
    }

    private fun currentProcessAbi(): String =
        (if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS)
            .firstOrNull()
            ?: Build.SUPPORTED_ABIS.firstOrNull()
            ?: throw IllegalStateException("unknown CPU architecture")

    private fun nativeSearchPaths(info: ApplicationInfo): List<String> {
        val abi = currentProcessAbi()
        val paths = mutableListOf<String>()
        extractedLibDir?.let { paths += it.absolutePath }
        apks(info).forEach { apk -> paths += "$apk!/lib/$abi" }
        return paths
    }

    private fun addNativeSearchPath(pathList: Any, paths: List<String>) {
        if (paths.isEmpty()) return
        val add = method(pathList.javaClass, "addNativePath", Collection::class.java)
        if (add == null) {
            AppLog.w(TAG, "addNativePath unavailable; libraries will be loaded by absolute path")
            return
        }
        runCatching { add.invoke(pathList, paths) }
            .onSuccess { AppLog.i(TAG, "native search path added: ${paths.size} entries") }
            .onFailure { error ->
                AppLog.w(TAG, "addNativePath failed (${error.message}); libraries will be loaded by absolute path")
            }
    }

    private fun method(type: Class<*>, name: String, vararg params: Class<*>): java.lang.reflect.Method? =
        runCatching { type.getMethod(name, *params) }
            .recoverCatching { type.getDeclaredMethod(name, *params) }
            .getOrNull()
            ?.apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun append(target: Any, fieldName: String, extra: Any) {
        val elements = field(target, fieldName) as Array<Any>
        val more = extra as Array<Any>
        if (more.isEmpty()) throw IllegalStateException("the installed Minecraft has no $fieldName")
        val merged = java.lang.reflect.Array.newInstance(elements.javaClass.componentType, elements.size + more.size) as Array<Any>
        System.arraycopy(elements, 0, merged, 0, elements.size)
        System.arraycopy(more, 0, merged, elements.size, more.size)
        declaredField(target.javaClass, fieldName).set(target, merged)
    }

    private fun field(target: Any, name: String): Any = declaredField(target.javaClass, name).get(target)!!

    private fun declaredField(type: Class<*>, name: String): java.lang.reflect.Field {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { return current.getDeclaredField(name).apply { isAccessible = true } }
            current = current.superclass
        }
        throw NoSuchFieldException("$name on ${type.name}")
    }

    private fun nativeLibraryNames(info: ApplicationInfo): List<String> {
        val abi = currentProcessAbi()
        val names = linkedSetOf<String>()

        File(nativeLibDir).listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".so")) names += file.name
        }
        apks(info).forEach { apk ->
            runCatching {
                ZipFile(apk).use { zip ->
                    java.util.Collections.list(zip.entries())
                        .filter { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
                        .forEach { names += it.name.substringAfterLast('/') }
                }
            }
        }
        if (names.isEmpty()) throw IllegalStateException("the installed Minecraft has no native libraries for $abi")
        if ("libminecraftpe.so" !in names) throw IllegalStateException("the installed Minecraft has no engine library")

        return names.sortedWith(
            compareBy(
                { if (it == "libc++_shared.so") 0 else if (it == "libminecraftpe.so") 2 else 1 },
                { it },
            ),
        )
    }

    private fun apks(info: ApplicationInfo): List<String> = buildList {
        info.sourceDir?.let { add(it) }
        info.splitSourceDirs?.filter { it.isNotBlank() }?.let { addAll(it) }
    }

    private fun bundledSdkLibraries(context: Context, abi: String): Set<String> {
        val ownInfo = runCatching {
            context.packageManager.getApplicationInfo(context.packageName, 0)
        }.getOrElse { context.applicationInfo }
        val ownNativeDir = ownInfo.nativeLibraryDir.orEmpty()
        val ownApks = apks(ownInfo)
        val available = linkedSetOf<String>()
        for (fileName in NativeLoadPlan.BUNDLED_SPECS.keys) {
            val inDir = ownNativeDir.isNotBlank() && File(ownNativeDir, fileName).isFile
            val inApks = ownApks.any { apk ->
                runCatching {
                    ZipFile(apk).use { zip -> zip.getEntry("lib/$abi/$fileName") != null }
                }.getOrDefault(false)
            }
            if (inDir || inApks) available += fileName
        }
        return available
    }

    private fun pruneCacheOverrides(libDir: File, bundledAvailable: Set<String>) {
        libDir.listFiles()?.forEach { file ->
            val name = file.name
            val baseName = if (name.endsWith(".crc")) name.removeSuffix(".crc") else name
            if (ElfImageSanitizer.isPairipLibrary(baseName) || baseName in bundledAvailable) {
                runCatching {
                    file.setWritable(true, true)
                    file.delete()
                }
            }
        }
    }

    private fun loadNativeLibraries(context: Context) {
        if (nativeLoaded) return
        check(appClassLoader != null) { "game classes are not on the class path" }
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val abi = currentProcessAbi()
        val bundledAvailable = bundledSdkLibraries(context, abi)
        val libDir = File(context.codeCacheDir, "game-libs").apply { mkdirs() }
        pruneCacheOverrides(libDir, bundledAvailable)

        val extractedPaths = LinkedHashMap<String, String?>()
        for (name in nativeLibNames) {
            if (ElfImageSanitizer.isPairipLibrary(name) || name in bundledAvailable) continue
            extractedPaths[name] = loadablePath(context, info, abi, name)
        }

        val readable = extractedPaths.values.filterNotNull()
        val copiedBytes = readable.sumOf { File(it).length() }
        AppLog.i(
            TAG,
            "abi=$abi bundled=${bundledAvailable.joinToString().ifEmpty { "none" }} · " +
                "${readable.size} extracted (${copiedBytes / 1048576} MB in game-libs)",
        )

        val elfNeeded = extractedPaths.mapValues { (name, path) ->
            path?.let { readNeededWithLog(name, it) }.orEmpty()
        }

        val marker = File(context.codeCacheDir, "loading-library.txt")
        val killed = HashSet<String>()
        runCatching { marker.readText().trim() }
            .getOrNull()
            ?.takeIf { it in nativeLibNames && it != "libminecraftpe.so" }
            ?.let { name ->
                killed += name
                AppLog.e(TAG, "$name took the process down last time — it will not be loaded again")
            }

        val plan = NativeLoadPlan.plan(
            discoveredNames = nativeLibNames,
            extractedPaths = extractedPaths,
            elfNeeded = elfNeeded,
            bundledAvailable = bundledAvailable,
            killed = killed,
        )
        if (plan.skippedWrappers.isNotEmpty()) {
            AppLog.i(TAG, "skipped integrity wrappers: ${plan.skippedWrappers.joinToString(" ")}")
        }
        if (plan.deferred.isNotEmpty()) {
            AppLog.i(TAG, "deferred to game runtime: ${plan.deferred.joinToString(" ")}")
        }
        AppLog.i(TAG, "load plan: ${plan.steps.joinToString(" ") { it.name }}")

        val failed = LinkedHashMap<String, String>(plan.preFailed)
        var pending = plan.steps
        for (pass in 1..2) {
            if (pending.isEmpty()) break
            val nextPending = ArrayList<NativeLoadPlan.LoadStep>()
            var progressed = false
            for (step in pending) {
                val blocker = step.dependencies.firstOrNull { it in failed }
                if (blocker != null) {
                    failed[step.name] = "$blocker is not mapped"
                    AppLog.w(TAG, "${step.name} not attempted — $blocker is not mapped")
                    nextPending += step
                    continue
                }
                try {
                    runCatching { marker.writeText(step.name) }
                    when (step.method) {
                        NativeLoadPlan.LoadMethod.BUNDLED_SYSTEM_LOAD_LIBRARY -> {
                            val shortName = step.systemShortName
                                ?: throw IllegalStateException("missing bundled short name for ${step.name}")
                            System.loadLibrary(shortName)
                            AppLog.i(TAG, "loaded bundled ${step.name} via loadLibrary($shortName)")
                        }
                        NativeLoadPlan.LoadMethod.EXTRACTED_PATH -> {
                            val path = step.path
                                ?: throw IllegalStateException("missing extracted path for ${step.name}")
                            System.load(path)
                            AppLog.i(TAG, "loaded ${step.name} ← $path")
                        }
                    }
                    marker.delete()
                    Log.i(TAG, "loaded ${step.name}")
                    failed.remove(step.name)
                    progressed = true
                } catch (error: Throwable) {
                    marker.delete()
                    failed[step.name] = error.message ?: error.javaClass.simpleName
                    AppLog.e(TAG, "${step.name} could not be loaded — ${error.message}")
                    nextPending += step
                }
            }
            pending = nextPending
            if (!progressed) break
        }

        if ("libminecraftpe.so" in failed) {
            throw IllegalStateException(
                "could not load the Minecraft engine — " +
                    failed.entries.joinToString(", ") { "${it.key}: ${it.value}" },
            )
        }
        if (failed.isNotEmpty()) {
            AppLog.w(TAG, "not loaded, the game may still start: " + failed.keys.joinToString(" "))
        }
        nativeLoaded = true
        if (killed.isEmpty()) {
            marker.delete()
        }
        AppLog.i(TAG, "native libraries ready in this process")
    }

    private fun readNeededWithLog(name: String, path: String): List<String> =
        runCatching { ElfImageSanitizer.readNeededLibraries(File(path)) }
            .onFailure { AppLog.w(TAG, "headers of $name not read: ${it.message}") }
            .getOrDefault(emptyList())

    private fun loadablePath(context: Context, info: ApplicationInfo, abi: String, file: String): String? {
        if (ElfImageSanitizer.isPairipLibrary(file)) return null
        extractLibrary(context, info, abi, file)?.let { return it }

        val installed = File(nativeLibDir, file)
        if (installed.isFile && installed.canRead()) {
            AppLog.w(TAG, "$file used from the installed copy — this may be refused")
            return installed.absolutePath
        }
        return null
    }

    private fun extractLibrary(context: Context, info: ApplicationInfo, abi: String, file: String): String? {
        val dir = File(context.codeCacheDir, "game-libs").apply { mkdirs() }
        val target = File(dir, file)
        val crcFile = File(dir, "$file.crc")
        for (apk in apks(info)) {
            try {
                ZipFile(apk).use { zip ->
                    val entry = zip.getEntry("lib/$abi/$file") ?: return@use
                    val expectedStamp = "${entry.size}:${entry.crc}"
                    val cachedStamp = runCatching { crcFile.readText().trim() }.getOrNull()
                    val cacheValid = target.isFile &&
                        target.canRead() &&
                        target.length() == entry.size &&
                        cachedStamp == expectedStamp &&
                        !ElfImageSanitizer.hasBlockedNeeded(target)
                    if (!cacheValid) {
                        val tmp = File(dir, "$file.tmp")
                        runCatching {
                            tmp.setWritable(true, true)
                            tmp.delete()
                        }
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(tmp).use { output -> input.copyTo(output) }
                        }
                        val sanitized = ElfImageSanitizer.sanitizeInPlace(tmp)
                        if (sanitized.modified) {
                            AppLog.i(
                                TAG,
                                "sanitized $file (removed ${sanitized.removedNeeded.joinToString()})",
                            )
                        }
                        if (ElfImageSanitizer.hasBlockedNeeded(tmp)) {
                            throw IllegalStateException(
                                "Pairip dependencies remain in $file after ELF sanitization",
                            )
                        }
                        tmp.setReadable(true, true)
                        tmp.setWritable(false, false)
                        runCatching {
                            target.setWritable(true, true)
                            target.delete()
                        }
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            runCatching {
                                tmp.setWritable(true, true)
                                tmp.delete()
                            }
                        }
                        target.setReadable(true, true)
                        target.setWritable(false, false)
                        runCatching { crcFile.writeText(expectedStamp) }
                        AppLog.i(TAG, "copied $file from ${File(apk).name} (${entry.size / 1048576} MB)")
                    }
                    return target.absolutePath
                }
            } catch (error: Exception) {
                AppLog.w(TAG, "$file could not be taken from ${File(apk).name}: ${error.message}")
            }
        }
        return null
    }
}
