package app.recall.scheduler

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 spaced repetition scheduling, as a pure function so it can be unit tested.
 * Reference: SuperMemo SM-2 algorithm.
 */
object Sm2 {

    /** Four-button grading used in the review screen. */
    enum class Grade(val quality: Int) {
        AGAIN(1),
        HARD(3),
        GOOD(4),
        EASY(5),
    }

    /** Scheduling state stored per card. */
    data class Review(val interval: Int, val reps: Int, val ease: Double) {
        companion object {
            val NEW = Review(interval = 0, reps = 0, ease = 2.5)
        }
    }

    /** Returns the next scheduling state after the user answers with [quality] (0..5). */
    fun schedule(current: Review, quality: Int): Review {
        require(quality in 0..5) { "quality must be 0..5, was $quality" }

        val ease = max(
            1.3,
            current.ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)),
        )

        // Failed recall: restart the learning steps, see the card again tomorrow.
        if (quality < 3) {
            return Review(interval = 1, reps = 0, ease = ease)
        }

        val reps = current.reps + 1
        val interval = when (reps) {
            1 -> 1
            2 -> 6
            else -> (current.interval * ease).roundToInt().coerceAtLeast(1)
        }
        return Review(interval = interval, reps = reps, ease = ease)
    }

    fun grade(current: Review, grade: Grade): Review = schedule(current, grade.quality)
}
