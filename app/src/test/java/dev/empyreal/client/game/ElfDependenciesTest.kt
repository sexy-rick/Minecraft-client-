package dev.empyreal.client.game

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ElfDependenciesTest {
    @Test fun invalidElfReportsAnErrorInsteadOfNoDependencies() {
        val file = File.createTempFile("invalid-elf", ".so")
        try {
            file.writeText("not an ELF file")
            assertTrue(runCatching { ElfDependencies.readNeededLibraries(file) }.isFailure)
        } finally {
            file.delete()
        }
    }

    @Test fun missingFileReportsAnError() {
        val file = File.createTempFile("missing-elf", ".so")
        file.delete()
        assertTrue(runCatching { ElfDependencies.readNeededLibraries(file) }.isFailure)
    }
}
