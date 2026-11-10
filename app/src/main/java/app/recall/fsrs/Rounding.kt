package app.recall.fsrs

import kotlin.math.floor

/**
 * The only rounding used in the scheduler. Half values round away from zero:
 * 0.5 -> 1, -0.5 -> -1, 2.5 -> 3, -2.5 -> -3. Do not use kotlin.math.round (banker's rounding).
 */
object Rounding {
    fun roundHalfUp(x: Double): Long {
        require(x.isFinite()) { "cannot round $x" }
        return if (x >= 0) floor(x + 0.5).toLong() else -floor(-x + 0.5).toLong()
    }
}
