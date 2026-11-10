package app.recall.fsrs

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundingTest {
    @Test
    fun halfValuesRoundAwayFromZero() {
        assertEquals(1L, Rounding.roundHalfUp(0.5))
        assertEquals(-1L, Rounding.roundHalfUp(-0.5))
        assertEquals(3L, Rounding.roundHalfUp(2.5))
        assertEquals(-3L, Rounding.roundHalfUp(-2.5))
    }

    @Test
    fun nonHalfValuesRoundToNearest() {
        assertEquals(0L, Rounding.roundHalfUp(0.49))
        assertEquals(1L, Rounding.roundHalfUp(0.51))
        assertEquals(-1L, Rounding.roundHalfUp(-0.51))
        assertEquals(0L, Rounding.roundHalfUp(0.0))
    }
}
