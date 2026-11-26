package dev.empyreal.client.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLoadPlanTest {
    private val engine = "libminecraftpe.so"
    private val provider = "libpairipcore.so"

    private fun plan(killed: Set<String> = emptySet(), needed: List<String> = listOf(provider)) =
        NativeLoadPlan.plan(
            discoveredNames = listOf(engine, provider),
            extractedPaths = mapOf(engine to "/cache/$engine", provider to "/cache/$provider"),
            elfNeeded = mapOf(engine to needed),
            bundledAvailable = emptySet(),
            killed = killed,
        )

    @Test fun declaredProviderLoadsBeforeEngine() {
        val steps = plan().steps
        assertEquals(listOf(provider, engine), steps.map { it.name })
        assertEquals(listOf(provider), steps.last().dependencies)
    }

    @Test fun unrelatedProviderIsNotEagerlyLoaded() {
        assertEquals(listOf(engine), plan(needed = emptyList()).steps.map { it.name })
    }

    @Test fun transitiveDependenciesLoadFirst() {
        val helper = "libhelper.so"
        val result = NativeLoadPlan.plan(
            discoveredNames = listOf(engine, provider, helper),
            extractedPaths = listOf(engine, provider, helper).associateWith { "/cache/$it" },
            elfNeeded = mapOf(engine to listOf(provider), provider to listOf(helper)),
            bundledAvailable = emptySet(),
        )
        assertEquals(listOf(helper, provider, engine), result.steps.map { it.name })
    }

    @Test fun previousProviderCrashBlocksEngine() {
        val result = plan(killed = setOf(provider))
        assertTrue(provider in result.preFailed)
        assertFalse(result.steps.any { it.name == provider })
        assertEquals(listOf(provider), result.steps.single().dependencies)
    }

    @Test fun missingProviderIsReportedBeforeLoadingEngine() {
        val result = NativeLoadPlan.plan(
            discoveredNames = listOf(engine, provider),
            extractedPaths = mapOf(engine to "/cache/$engine", provider to null),
            elfNeeded = mapOf(engine to listOf(provider)),
            bundledAvailable = emptySet(),
        )
        assertTrue(provider in result.preFailed)
        assertEquals(listOf(provider), result.steps.single().dependencies)
    }
}
