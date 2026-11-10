package app.recall.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedAnswerTest {

    @Test
    fun ignoresCaseAndSurroundingSpaces() {
        assertTrue(TypedAnswer.matches("  ALPHA ", "alpha"))
    }

    @Test
    fun ignoresAccents() {
        assertTrue(TypedAnswer.matches("cafe", "café"))
    }

    @Test
    fun allowsOneTypoOnShortTargets() {
        assertTrue(TypedAnswer.matches("de", "da"))
        assertFalse(TypedAnswer.matches("dxy", "da"))
    }

    @Test
    fun allowsTwoTyposOnLongerTargets() {
        assertTrue(TypedAnswer.matches("alpa", "alpha"))
        assertTrue(TypedAnswer.matches("alphx", "alpha"))
    }

    @Test
    fun firstCharacterMustMatch() {
        assertFalse(TypedAnswer.matches("xlpha", "alpha"))
        assertFalse(TypedAnswer.matches("bts", "beta"))
    }

    @Test
    fun emptyInputNeverMatches() {
        assertFalse(TypedAnswer.matches("", "alpha"))
        assertFalse(TypedAnswer.matches("   ", "alpha"))
    }

    @Test
    fun unrelatedWordsDoNotMatch() {
        assertFalse(TypedAnswer.matches("ab", "cd"))
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, TypedAnswer.levenshtein("abc", "abc"))
        assertEquals(1, TypedAnswer.levenshtein("abc", "abd"))
        assertEquals(3, TypedAnswer.levenshtein("", "abc"))
    }
}
