package io.github.greprohan.llamadroid.modelimport

import org.junit.Assert.*
import org.junit.Test

class ModelNameTest {
    @Test fun ordinaryUnicodeNameIsPreserved() {
        assertEquals("模型 🌍.gguf", ModelName.sanitize("模型 🌍.gguf"))
        assertTrue(ModelName.isValid("模型 🌍.gguf"))
    }

    @Test fun missingOrBlankNamesUseBoundedFallback() {
        listOf(null, "", " \t\n", "\u200E\u202E").forEach {
            assertEquals(ModelName.FALLBACK, ModelName.sanitize(it))
        }
        assertTrue(ModelName.isValid(ModelName.FALLBACK))
    }

    @Test fun controlsFormatsAndLineSeparatorsAreNeutralized() {
        assertEquals("a       b", ModelName.sanitize("a\u0000\t\n\u007F\u202E\u2028\u2029b"))
        assertFalse(ModelName.isValid("a\u202Eb"))
        assertFalse(ModelName.isValid("a\u0000b"))
    }

    @Test fun namesAreTrimmedWithoutChangingInteriorSpaces() {
        assertEquals("my  model.gguf", ModelName.sanitize("  my  model.gguf  "))
        assertFalse(ModelName.isValid(" model "))
        assertFalse(ModelName.isValid(""))
    }

    @Test fun asciiPrefixIsLimitedTo256CodePoints() {
        assertEquals("a".repeat(256), ModelName.sanitize("a".repeat(100_000)))
        assertFalse(ModelName.isValid("a".repeat(257)))
    }

    @Test fun supplementaryCharactersAreNotSplitAtTheLimit() {
        val name = "🌍".repeat(256)
        assertEquals(name, ModelName.sanitize(name + "🌍"))
        assertEquals(1024, name.toByteArray(Charsets.UTF_8).size)
        assertTrue(ModelName.isValid(name))
        assertFalse(ModelName.isValid(name + "🌍"))
    }

    @Test fun loneSurrogatesAreNeutralizedRatherThanSilentlyUtf8Replaced() {
        assertEquals("a b c", ModelName.sanitize("a\uD800b\uDC00c"))
        assertFalse(ModelName.isValid("a\uD800b"))
        assertFalse(ModelName.isValid("a\uDC00b"))
    }

    @Test fun hugeBlankPrefixDoesNotRequireScanningForANonblankSuffix() {
        assertEquals(ModelName.FALLBACK, ModelName.sanitize(" ".repeat(100_000) + "late"))
    }
}
