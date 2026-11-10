package app.recall.fsrs

/**
 * Behaviour switches for this app's scheduling. They are constants so each rule is visible
 * in one place and covered by a test.
 */
object Policy {
    /**
     * A lapse keeps the card in the review queue (queue = 2) and schedules it by FSRS
     * interval. There is no relearning step and no learning position.
     */
    const val LAPSE_NO_RELEARNING = true

    /**
     * Option A: a new card graduates to review on its first Good. Again keeps it new, with
     * its FSRS state stored, so it comes back in the new queue.
     */
    const val NEW_GRADUATE_ON_FIRST_GOOD = true
}
