package app.recall.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingPlannerTest {
    private val w = FsrsScheduler.DEFAULT_WEIGHTS
    private val today = 100L

    @Test
    fun lapseKeepsQueueTwoWithFsrsIntervalAndNoLearningPosition() {
        val card = CardSnapshot(isNew = false, due = 100L, ivl = 10, reps = 4, lapses = 0,
            priorState = FsrsState(10.0, 5.0))
        val plan = SchedulingPlanner.plan(card, Grade.AGAIN, today, 0.9, w)
        assertEquals(2, plan.queue)
        assertEquals(2, plan.type)
        assertEquals(1, plan.lapses)
        assertEquals(5, plan.reps)
        assertEquals(today + plan.intervalDays, plan.due)
        assertTrue("lapse interval is a real day count", plan.intervalDays >= 1)
        assertEquals(1, plan.revlogType)
        assertEquals(10, plan.lastIvl)
    }

    @Test
    fun newCardGoodGraduatesToReviewWithRepsOne() {
        val card = CardSnapshot(isNew = true, due = 0L, ivl = 0, reps = 0, lapses = 0, priorState = null)
        val plan = SchedulingPlanner.plan(card, Grade.GOOD, today, 0.9, w)
        assertEquals(2, plan.queue)
        assertEquals(2, plan.type)
        assertEquals(1, plan.reps)
        assertEquals(0, plan.lapses)
        assertEquals(today + plan.intervalDays, plan.due)
        assertEquals(0, plan.revlogType)
    }

    @Test
    fun newCardAgainStaysNewUnderOptionA() {
        val card = CardSnapshot(isNew = true, due = 7L, ivl = 0, reps = 0, lapses = 0, priorState = null)
        val plan = SchedulingPlanner.plan(card, Grade.AGAIN, today, 0.9, w)
        assertEquals(0, plan.queue)
        assertEquals(0, plan.type)
        assertEquals(7L, plan.due)
        assertEquals(1, plan.reps)
        assertEquals(0, plan.lapses)
        assertTrue("FSRS state is kept for the next presentation", plan.state.stability > 0.0)
    }

    @Test
    fun reviewGoodStaysInReviewQueue() {
        val card = CardSnapshot(isNew = false, due = 100L, ivl = 10, reps = 3, lapses = 1,
            priorState = FsrsState(10.0, 5.0))
        val plan = SchedulingPlanner.plan(card, Grade.GOOD, today, 0.9, w)
        assertEquals(2, plan.queue)
        assertEquals(1, plan.lapses)
        assertEquals(4, plan.reps)
    }
}
