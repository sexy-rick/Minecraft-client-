package dev.empyreal.client.game

internal object NativeLoadPlan {

    enum class LoadMethod {
        EXTRACTED_PATH,
        BUNDLED_SYSTEM_LOAD_LIBRARY,
    }

    data class BundledSpec(
        val fileName: String,
        val shortName: String,
        val dependencies: List<String>,
    )

    data class LoadStep(
        val name: String,
        val method: LoadMethod,
        val systemShortName: String? = null,
        val path: String? = null,
        val dependencies: List<String> = emptyList(),
    )

    data class Plan(
        val steps: List<LoadStep>,
        val deferred: List<String>,
        val skippedWrappers: List<String>,
        val preFailed: Map<String, String>,
    )

    val BUNDLED_SPECS: Map<String, BundledSpec> = mapOf(
        "libmaesdk.so" to BundledSpec(
            fileName = "libmaesdk.so",
            shortName = "maesdk",
            dependencies = listOf("libc++_shared.so"),
        ),
        "libPlayFabMultiplayer.so" to BundledSpec(
            fileName = "libPlayFabMultiplayer.so",
            shortName = "PlayFabMultiplayer",
            dependencies = listOf("libHttpClient.Android.so", "libc++_shared.so"),
        ),
    )

    val DEFERRED_TO_GAME: Set<String> = setOf("libconscrypt_jni.so")

    private val ENGINE_ROOTS = listOf(
        "libc++_shared.so",
        "libHttpClient.Android.so",
        "libPlayFabMultiplayer.so",
        "libmaesdk.so",
        "libMallocRedirect.so",
        "libfmod.so",
        "libMediaDecoders_Android.so",
        "libminecraftpe.so",
    )

    fun isBundledSdk(name: String): Boolean = name in BUNDLED_SPECS

    fun plan(
        discoveredNames: List<String>,
        extractedPaths: Map<String, String?>,
        elfNeeded: Map<String, List<String>>,
        bundledAvailable: Set<String>,
        killed: Set<String> = emptySet(),
    ): Plan {
        val skippedWrappers = discoveredNames.filter { ElfImageSanitizer.isPairipLibrary(it) }
        val activePool = LinkedHashSet<String>()
        discoveredNames.filterNotTo(activePool) { ElfImageSanitizer.isPairipLibrary(it) }
        elfNeeded["libminecraftpe.so"].orEmpty()
            .filter { it in bundledAvailable && !ElfImageSanitizer.isPairipLibrary(it) }
            .forEach { activePool += it }

        val deferred = activePool.filter { it in DEFERRED_TO_GAME }

        val effectiveDeps = LinkedHashMap<String, List<String>>()
        for (name in activePool) {
            val rawDeps = if (name in bundledAvailable) {
                BUNDLED_SPECS[name]?.dependencies.orEmpty()
            } else {
                elfNeeded[name].orEmpty()
            }
            effectiveDeps[name] = rawDeps
                .filter { dep ->
                    dep != name &&
                        !ElfImageSanitizer.isPairipLibrary(dep) &&
                        dep in activePool
                }
                .distinct()
        }

        val ordered = mutableListOf<String>()
        val done = HashSet<String>()
        val visiting = HashSet<String>()

        fun visit(name: String, forceInclude: Boolean) {
            if (name in done || ElfImageSanitizer.isPairipLibrary(name)) return
            if (!forceInclude && name in DEFERRED_TO_GAME) return
            if (!visiting.add(name)) return
            effectiveDeps[name].orEmpty().forEach { dep ->
                visit(dep, forceInclude = true)
            }
            visiting -= name
            done += name
            ordered += name
        }

        val roots = (listOf("libminecraftpe.so") + ENGINE_ROOTS.filter { name ->
            name in activePool &&
                (name != "libPlayFabMultiplayer.so" || (name in bundledAvailable && "libHttpClient.Android.so" in activePool))
        }).distinct()
        roots.filter { it in activePool }.forEach { visit(it, forceInclude = false) }

        val preFailed = LinkedHashMap<String, String>()
        val steps = ArrayList<LoadStep>(ordered.size)

        for (name in ordered) {
            if (name in killed) {
                preFailed[name] = "took the process down on the previous launch"
                continue
            }
            val bundled = BUNDLED_SPECS[name]?.takeIf { name in bundledAvailable }
            if (bundled != null) {
                steps += LoadStep(
                    name = name,
                    method = LoadMethod.BUNDLED_SYSTEM_LOAD_LIBRARY,
                    systemShortName = bundled.shortName,
                    dependencies = effectiveDeps[name].orEmpty(),
                )
            } else {
                val path = extractedPaths[name]
                if (path == null) {
                    preFailed[name] = "no readable copy"
                } else {
                    steps += LoadStep(
                        name = name,
                        method = LoadMethod.EXTRACTED_PATH,
                        path = path,
                        dependencies = effectiveDeps[name].orEmpty(),
                    )
                }
            }
        }

        return Plan(
            steps = steps,
            deferred = deferred,
            skippedWrappers = skippedWrappers,
            preFailed = preFailed,
        )
    }
}
