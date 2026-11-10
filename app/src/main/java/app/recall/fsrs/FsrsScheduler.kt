package app.recall.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * FSRS-6 scheduling math, ported from fsrs-rs (model_v6.rs, inference_v6 defaults) and checked
 * against py-fsrs 6.3.2. Pure Kotlin, no Android or Anki dependencies.
 *
 * Weights are a 21-element vector w[0..20]. w[20] is the trainable decay.
 *
 *   DECAY  = -w[20]
 *   FACTOR = 0.9^(1/DECAY) - 1
 *   R(t,S) = (1 + FACTOR * round(t) / S)^DECAY      so R(S,S) = 0.9 at t = S
 *   I(S,r) = S / FACTOR * (r^(1/DECAY) - 1)          interval for desired retention r
 *
 * Same-day reviews (elapsed < 1 day) use the short-term formula for every rating.
 * Lapses are capped at s / exp(w17 * w18).
 */
object FsrsScheduler {
    const val WEIGHT_COUNT = 21
    const val MAX_INTERVAL_DAYS = 36500
    const val MIN_STABILITY = 0.001
    const val MAX_STABILITY = 36500.0
    const val MIN_DIFFICULTY = 1.0
    const val MAX_DIFFICULTY = 10.0
    const val DEFAULT_DESIRED_RETENTION = 0.9

    private const val HARD = 2
    private const val GOOD = 3
    private const val EASY = 4

    /**
     * FSRS-6 default weights. Identical to py-fsrs 6.3.2 DEFAULT_PARAMETERS
     * (fsrs.scheduler.DEFAULT_PARAMETERS), and the first nine match fsrs-rs's FSRS-6 defaults.
     */
    val DEFAULT_WEIGHTS: DoubleArray = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )

    /**
     * Weights for a deck's dconf entry.
     *  - 21 finite values: used as given
     *  - 17 or 19 values: migrated to 21 by WeightMigration
     *  - anything else (missing, wrong length, non-finite): FSRS-6 defaults
     */
    fun loadWeights(dconf: List<Double>?): DoubleArray {
        if (dconf == null) return DEFAULT_WEIGHTS
        if (dconf.any { !it.isFinite() }) return DEFAULT_WEIGHTS
        return when (dconf.size) {
            WEIGHT_COUNT -> dconf.toDoubleArray()
            17, 19 -> WeightMigration.migrate(dconf)
            else -> DEFAULT_WEIGHTS
        }
    }

    fun decay(w: DoubleArray): Double = -w[20]

    fun factor(w: DoubleArray): Double = 0.9.pow(1.0 / decay(w)) - 1.0

    /** Probability of recall after [elapsedDays] for a card with [stability]. */
    fun retrievability(elapsedDays: Double, stability: Double, w: DoubleArray): Double {
        val s = max(stability, MIN_STABILITY)
        val t = Rounding.roundHalfUp(max(elapsedDays, 0.0)).toDouble()
        return (1.0 + factor(w) * t / s).pow(decay(w))
    }

    /** Days until retrievability falls to [desiredRetention]. Clamped to 1..MAX_INTERVAL_DAYS. */
    fun nextInterval(stability: Double, desiredRetention: Double, w: DoubleArray): Int {
        val s = max(stability, MIN_STABILITY)
        val r = desiredRetention.coerceIn(0.0001, 0.9999)
        val raw = s / factor(w) * (r.pow(1.0 / decay(w)) - 1.0)
        if (!raw.isFinite()) return 1
        return Rounding.roundHalfUp(raw).coerceIn(1L, MAX_INTERVAL_DAYS.toLong()).toInt()
    }

    /** S0(rating) = w[rating - 1]. */
    fun initStability(rating: Int, w: DoubleArray): Double {
        require(rating in 1..4) { "rating must be 1..4" }
        return clampStability(w[rating - 1])
    }

    /** D0(rating) = w[4] - exp(w[5] * (rating - 1)) + 1, clamped to 1..10. */
    fun initDifficulty(rating: Int, w: DoubleArray): Double =
        clampDifficulty(w[4] - exp(w[5] * (rating - 1)) + 1.0)

    /** Linear damping toward the current difficulty, then mean reversion toward D0(Easy). */
    fun nextDifficulty(difficulty: Double, rating: Int, w: DoubleArray): Double {
        val delta = -w[6] * (rating - GOOD)
        val damped = difficulty + (10.0 - difficulty) / 9.0 * delta
        val reverted = w[7] * initDifficulty(EASY, w) + (1.0 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    /** Stability after a successful recall (rating 2..4). */
    fun recallStability(s: Double, d: Double, r: Double, rating: Int, w: DoubleArray): Double {
        val hardPenalty = if (rating == HARD) w[15] else 1.0
        val easyBonus = if (rating == EASY) w[16] else 1.0
        val growth = exp(w[8]) * (11.0 - d) * s.pow(-w[9]) *
            (exp((1.0 - r) * w[10]) - 1.0) * hardPenalty * easyBonus
        return clampStability(s * (growth + 1.0))
    }

    /** Stability after a lapse (rating 1). Capped at s / exp(w17 * w18), as in fsrs-rs. */
    fun forgetStability(s: Double, d: Double, r: Double, w: DoubleArray): Double {
        val raw = w[11] * d.pow(-w[12]) * ((s + 1.0).pow(w[13]) - 1.0) * exp((1.0 - r) * w[14])
        val cap = s / exp(w[17] * w[18])
        return clampStability(min(raw, cap))
    }

    /**
     * Same-day update (elapsed < 1 day), as fsrs-rs stability_short_term_scalar:
     * sinc = exp(w17 * (rating - 3 + w18)) * s^-w19; for rating >= 2 the factor is at least 1.
     */
    fun shortTermStability(s: Double, rating: Int, w: DoubleArray): Double {
        val sinc = exp(w[17] * (rating - GOOD + w[18])) * s.pow(-w[19])
        val factor = if (rating >= HARD) max(sinc, 1.0) else sinc
        return clampStability(s * factor)
    }

    /**
     * Applies one review.
     *
     * @param current state before the review, or null for a new card
     * @param elapsedDays days since the card's previous review (0 for new cards)
     */
    fun review(
        current: FsrsState?,
        rating: Int,
        elapsedDays: Double,
        desiredRetention: Double,
        w: DoubleArray,
    ): FsrsResult {
        require(rating in 1..4) { "rating must be 1..4" }
        if (current == null) {
            val s = initStability(rating, w)
            val d = initDifficulty(rating, w)
            return FsrsResult(FsrsState(s, d), nextInterval(s, desiredRetention, w))
        }

        val s0 = clampStability(current.stability)
        val d0 = clampDifficulty(current.difficulty)
        val r = retrievability(elapsedDays, s0, w)

        val s1 = when {
            elapsedDays < 1.0 -> shortTermStability(s0, rating, w)
            rating == 1 -> forgetStability(s0, d0, r, w)
            else -> recallStability(s0, d0, r, rating, w)
        }
        val d1 = nextDifficulty(d0, rating, w)
        return FsrsResult(FsrsState(s1, d1), nextInterval(s1, desiredRetention, w))
    }

    fun clampStability(s: Double): Double =
        if (s.isNaN()) MIN_STABILITY else s.coerceIn(MIN_STABILITY, MAX_STABILITY)

    fun clampDifficulty(d: Double): Double =
        if (d.isNaN()) 5.0 else d.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
}
