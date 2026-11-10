package app.recall.fsrs

import kotlin.math.max

/** The card fields the planner needs, read before grading. */
data class CardSnapshot(
    val isNew: Boolean,
    val due: Long,
    val ivl: Int,
    val reps: Int,
    val lapses: Int,
    /** FSRS state from cards.data, or null when none is stored. */
    val priorState: FsrsState?,
)

/** Everything GradeWriter needs to write for one grade. */
data class UpdatePlan(
    val queue: Int,
    val type: Int,
    val due: Long,
    val ivl: Int,
    val reps: Int,
    val lapses: Int,
    val state: FsrsState,
    val intervalDays: Int,
    /** 0 learn, 1 review (Anki revlog type). */
    val revlogType: Int,
    val lastIvl: Int,
)

/**
 * Pure grade planning, so the policy rules can be tested without a database.
 * FRAGILE: elapsed days for a review card = today - (due - ivl), which assumes due was set as
 * lastReviewDay + ivl. Cards written by other tools may not follow that.
 */
object SchedulingPlanner {

    fun plan(
        card: CardSnapshot,
        grade: Grade,
        today: Long,
        desiredRetention: Double,
        weights: DoubleArray,
    ): UpdatePlan {
        val elapsed = if (card.isNew) 0.0 else max(0L, today - (card.due - card.ivl)).toDouble()
        val result = FsrsScheduler.review(card.priorState, grade.rating, elapsed, desiredRetention, weights)
        val interval = result.intervalDays

        val staysNew = card.isNew && grade == Grade.AGAIN && Policy.NEW_GRADUATE_ON_FIRST_GOOD
        if (staysNew) {
            return UpdatePlan(
                queue = 0,
                type = 0,
                due = card.due,
                ivl = 0,
                reps = card.reps + 1,
                lapses = card.lapses,
                state = result.state,
                intervalDays = interval,
                revlogType = 0,
                lastIvl = 0,
            )
        }

        val lapse = !card.isNew && grade == Grade.AGAIN
        // LAPSE_NO_RELEARNING: a lapse stays in the review queue with the FSRS interval.
        check(Policy.LAPSE_NO_RELEARNING) { "relearning queues are not implemented" }
        return UpdatePlan(
            queue = 2,
            type = 2,
            due = today + interval,
            ivl = interval,
            reps = card.reps + 1,
            lapses = card.lapses + if (lapse) 1 else 0,
            state = result.state,
            intervalDays = interval,
            revlogType = if (card.isNew) 0 else 1,
            lastIvl = if (card.isNew) 0 else card.ivl,
        )
    }
}
