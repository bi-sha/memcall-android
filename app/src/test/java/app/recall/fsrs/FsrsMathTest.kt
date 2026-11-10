package app.recall.fsrs

import app.recall.collection.CollectionMeta
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * FSRS-6 reference vectors.
 *
 * Expected values were generated with py-fsrs 6.3.2 (fsrs.Scheduler, DEFAULT_PARAMETERS,
 * enable_fuzzing=False). py-fsrs follows the fsrs-rs FSRS-6 formulas. Inputs use whole-day
 * elapsed times, so fsrs-rs's rounding and py-fsrs's floor agree.
 *
 * Tolerance: 0.01 on stability and difficulty; intervals must match exactly (within 0.01 days).
 * Re-generate with: py -3 -c "<see FsrsVectors comment>" if the weights change.
 */
class FsrsMathTest {

    private val w = FsrsScheduler.DEFAULT_WEIGHTS
    private val tol = 0.01

    private data class Vector(
        val label: String,
        val prevS: Double?,
        val prevD: Double?,
        val daysSinceLast: Double,
        val rating: Int,
        val expS: Double,
        val expD: Double,
        val expDue: Int,
        val formula: String,
    )

    private val vectors = listOf(
        Vector("review Good, mid stability", 10.0, 5.0, 10.0, 3, 32.026729, 4.990228, 32,
            "stabilityAfterSuccess (recallStability)"),
        Vector("review Again, lapse", 10.0, 5.0, 10.0, 1, 1.391987, 8.341762, 1,
            "stabilityAfterFailure (forgetStability)"),
        Vector("review Good, low stability", 2.0, 8.0, 2.0, 3, 4.880039, 7.987228, 5,
            "stabilityAfterSuccess (recallStability)"),
        Vector("review Good, long gap", 50.0, 2.0, 60.0, 3, 193.531804, 1.993228, 194,
            "stabilityAfterSuccess (recallStability)"),
        Vector("review Again, high difficulty", 1.0, 9.5, 1.0, 1, 0.304528, 9.820882, 1,
            "stabilityAfterFailure (forgetStability, min cap)"),
        Vector("review Again, long gap", 30.0, 7.0, 45.0, 1, 2.392557, 8.999149, 2,
            "stabilityAfterFailure (forgetStability)"),
        Vector("review Hard", 20.0, 5.0, 20.0, 2, 43.604339, 6.665995, 44,
            "stabilityAfterSuccess (hardPenalty w15)"),
        Vector("review Easy", 20.0, 5.0, 20.0, 4, 93.509422, 3.314461, 94,
            "stabilityAfterSuccess (easyBonus w16)"),
        Vector("new card Again", null, null, 0.0, 1, 0.212, 6.4133, 1,
            "initStability / initDifficulty"),
        Vector("new card Hard", null, null, 0.0, 2, 1.2931, 5.112171, 1,
            "initStability / initDifficulty"),
        Vector("new card Good", null, null, 0.0, 3, 2.3065, 2.118104, 2,
            "initStability / initDifficulty"),
        Vector("new card Easy", null, null, 0.0, 4, 8.2956, 1.0, 8,
            "initStability / initDifficulty"),
    )

    private fun check(label: String, formula: String, what: String, expected: Double, got: Double) {
        val delta = abs(expected - got)
        if (delta > tol) {
            fail(
                "BUG? $label: $what expected $expected got $got (delta $delta). " +
                    "Formula: $formula, FsrsScheduler.kt"
            )
        }
    }

    @Test
    fun matchesReferenceVectors() {
        for (v in vectors) {
            val prev = if (v.prevS == null) null else FsrsState(v.prevS, v.prevD!!)
            val r = FsrsScheduler.review(prev, v.rating, v.daysSinceLast, 0.9, w)
            check(v.label, v.formula, "stability", v.expS, r.state.stability)
            check(v.label, v.formula, "difficulty", v.expD, r.state.difficulty)
            assertEquals("${v.label}: interval", v.expDue, r.intervalDays)
        }
    }

    @Test
    fun formulaIdentitiesHold() {
        // R(S, S) = 0.9 is the defining property of FACTOR.
        assertEquals(0.9, FsrsScheduler.retrievability(10.0, 10.0, w), 1e-9)
        // Interval at 90% retention equals stability.
        assertEquals(10, FsrsScheduler.nextInterval(10.0, 0.9, w))
    }

    @Test
    fun lapseStabilityNeverExceedsPreviousStability() {
        for (s in listOf(0.5, 2.0, 10.0, 100.0)) {
            val r = FsrsScheduler.forgetStability(s, 5.0, 0.8, w)
            assertTrue("forget cap for s=$s", r <= s + 1e-12)
        }
    }

    /**
     * Cross-check against a real collection: reads col.crt from the bundled sample, takes a
     * new card graded Good, and checks due = todayDay + interval and queue = 2.
     *
     * This checks the day-number arithmetic and the FSRS interval. It does not run
     * GradeWriter (Android SQLite); GradeWriter is covered by the instrumented tests.
     */
    @Test
    fun dueDayMatchesCollectionCreationTime() {
        val tmp = Files.createTempFile("recall-crt", ".anki2").toFile()
        val source = javaClass.classLoader!!.getResourceAsStream("collection.anki2")
            ?: error("missing test resource collection.anki2")
        source.use { input -> tmp.outputStream().use { input.copyTo(it) } }

        DriverManager.getConnection("jdbc:sqlite:${tmp.path}").use { conn ->
            val crt = conn.createStatement().use { st ->
                st.executeQuery("SELECT crt FROM col").use { rs ->
                    rs.next(); rs.getLong(1)
                }
            }
            val meta = CollectionMeta(crt)
            val daysSinceCrt = 5L
            val nowSecs = crt + daysSinceCrt * CollectionMeta.SECONDS_PER_DAY + 3600
            val today = meta.todayDay(nowSecs)
            assertEquals(daysSinceCrt, today)

            val result = FsrsScheduler.review(null, 3, 0.0, 0.9, w)
            val dueDay = today + result.intervalDays

            conn.createStatement().use { st ->
                st.executeUpdate(
                    "UPDATE cards SET type = 2, queue = 2, due = $dueDay, ivl = ${result.intervalDays} " +
                        "WHERE id = (SELECT MIN(id) FROM cards)"
                )
                st.executeQuery("SELECT queue, due, ivl FROM cards WHERE id = (SELECT MIN(id) FROM cards)")
                    .use { rs ->
                        assertTrue(rs.next())
                        assertEquals(2, rs.getInt(1))
                        assertEquals(dueDay, rs.getLong(2))
                        assertEquals(result.intervalDays, rs.getInt(3))
                    }
            }
        }
        tmp.delete()
    }
}
