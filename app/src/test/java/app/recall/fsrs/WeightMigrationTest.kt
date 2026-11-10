package app.recall.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightMigrationTest {
    private val defaults = FsrsScheduler.DEFAULT_WEIGHTS

    @Test
    fun seventeenWeightsMigrateToTwentyOneKeepingDecayDefault() {
        val src = List(17) { 1.0 + it }
        val out = WeightMigration.migrate(src)
        assertEquals(21, out.size)
        for (i in 0 until 17) assertEquals(src[i], out[i], 0.0)
        assertEquals(defaults[17], out[17], 0.0)
        assertEquals(defaults[20], out[20], 0.0)
        assertEquals(0.1542, out[20], 0.0)
    }

    @Test
    fun nineteenWeightsMigrateToTwentyOneKeepingDecayDefault() {
        val src = List(19) { 2.0 + it * 0.1 }
        val out = WeightMigration.migrate(src)
        assertEquals(21, out.size)
        for (i in 0 until 19) assertEquals(src[i], out[i], 0.0)
        assertEquals(defaults[19], out[19], 0.0)
        assertEquals(0.1542, out[20], 0.0)
    }

    @Test
    fun loadWeightsRoutesByLength() {
        val nineteen = List(19) { 0.3 }
        assertTrue(FsrsScheduler.loadWeights(nineteen).contentEquals(WeightMigration.migrate(nineteen)))
        val seventeen = List(17) { 0.3 }
        assertEquals(21, FsrsScheduler.loadWeights(seventeen).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherLengthsAreRejectedByMigration() {
        WeightMigration.migrate(List(20) { 1.0 })
    }
}
