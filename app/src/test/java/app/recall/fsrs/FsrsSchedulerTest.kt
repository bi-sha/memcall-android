package app.recall.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsSchedulerTest {

    private val w = FsrsScheduler.DEFAULT_WEIGHTS

    @Test
    fun retrievabilityIsNinetyPercentAtStability() {
        assertEquals(0.9, FsrsScheduler.retrievability(10.0, 10.0, w), 1e-9)
    }

    @Test
    fun intervalEqualsStabilityAtNinetyPercentRetention() {
        assertEquals(10, FsrsScheduler.nextInterval(10.0, 0.9, w))
    }

    @Test
    fun higherRetentionGivesShorterInterval() {
        val high = FsrsScheduler.nextInterval(10.0, 0.95, w)
        val low = FsrsScheduler.nextInterval(10.0, 0.85, w)
        assertTrue("0.95 should be shorter than 0.85", high < low)
    }

    @Test
    fun intervalIsAtLeastOneDay() {
        assertEquals(1, FsrsScheduler.nextInterval(0.01, 0.9, w))
    }

    @Test
    fun newGoodCardStartsAtWeightTwo() {
        val r = FsrsScheduler.review(null, 3, 0.0, 0.9, w)
        assertEquals(w[2], r.state.stability, 1e-9)
    }

    @Test
    fun newAgainCardStartsAtWeightZero() {
        val r = FsrsScheduler.review(null, 1, 0.0, 0.9, w)
        assertEquals(w[0], r.state.stability, 1e-9)
    }

    @Test
    fun initialDifficultyForGoodMatchesFormula() {
        val expected = w[4] - Math.exp(w[5] * 2) + 1.0
        assertEquals(expected, FsrsScheduler.initDifficulty(3, w), 1e-9)
    }

    @Test
    fun goodReviewGrowsStability() {
        val r = FsrsScheduler.review(FsrsState(10.0, 5.0), 3, 10.0, 0.9, w)
        assertTrue(r.state.stability > 10.0)
        assertTrue(r.intervalDays > 10)
    }

    @Test
    fun againReducesStability() {
        val r = FsrsScheduler.review(FsrsState(10.0, 5.0), 1, 10.0, 0.9, w)
        assertTrue(r.state.stability < 10.0)
    }

    @Test
    fun againRaisesDifficulty() {
        val r = FsrsScheduler.review(FsrsState(10.0, 5.0), 1, 10.0, 0.9, w)
        assertTrue(r.state.difficulty > 5.0)
    }

    @Test
    fun difficultyStaysWithinBounds() {
        var state: FsrsState? = null
        repeat(300) {
            state = FsrsScheduler.review(state, 1, 1.0, 0.9, w).state
        }
        val d = state!!.difficulty
        assertTrue(d >= FsrsScheduler.MIN_DIFFICULTY && d <= FsrsScheduler.MAX_DIFFICULTY)
    }

    @Test
    fun sameDayGoodDoesNotShrinkStability() {
        val r = FsrsScheduler.review(FsrsState(10.0, 5.0), 3, 0.0, 0.9, w)
        assertTrue(r.state.stability >= 10.0)
    }

    @Test
    fun intervalGrowsWithStability() {
        val short = FsrsScheduler.nextInterval(3.0, 0.9, w)
        val long = FsrsScheduler.nextInterval(30.0, 0.9, w)
        assertTrue(long > short)
    }

    @Test
    fun invalidWeightsFallBackToDefaults() {
        assertTrue(FsrsScheduler.loadWeights(List(5) { 0.0 }).contentEquals(FsrsScheduler.DEFAULT_WEIGHTS))
        assertTrue(FsrsScheduler.loadWeights(null).contentEquals(FsrsScheduler.DEFAULT_WEIGHTS))
        assertTrue(FsrsScheduler.loadWeights(List(21) { Double.NaN }).contentEquals(FsrsScheduler.DEFAULT_WEIGHTS))
    }

    @Test
    fun defaultWeightsAreTheLockedFsrs6Vector() {
        val locked = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729,
            0.5425, 0.0912, 0.0658, 0.1542,
        )
        assertTrue(FsrsScheduler.DEFAULT_WEIGHTS.contentEquals(locked))
        assertEquals(0.1542, FsrsScheduler.DEFAULT_WEIGHTS[20], 0.0)
    }

    @Test
    fun twentyOneWeightsAreUsedAsGiven() {
        val custom = List(21) { 0.5 + it * 0.01 }
        assertTrue(FsrsScheduler.loadWeights(custom).contentEquals(custom.toDoubleArray()))
    }
}
