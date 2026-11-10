package app.recall.fsrs

/**
 * Turns an older 17- or 19-weight vector into the 21-weight FSRS-6 layout.
 *
 * Policy: the stored values overwrite the FSRS-6 defaults at the same indices; any index
 * the old vector does not have keeps its FSRS-6 default. So w[20] (decay) becomes 0.1542
 * unless the vector supplied its own value.
 *
 * ASSUMPTION: the index meanings of FSRS-4/5 weights are treated as the same as FSRS-6 for
 * indices they share. This is a policy, not a verified conversion; fitted values from an
 * older version will not reproduce that version's exact behaviour.
 */
object WeightMigration {
    private val ACCEPTED_LENGTHS = setOf(17, 19)

    fun migrate(source: List<Double>): DoubleArray {
        require(source.size in ACCEPTED_LENGTHS) {
            "migration accepts 17 or 19 weights, got ${source.size}"
        }
        val out = FsrsScheduler.DEFAULT_WEIGHTS.copyOf()
        for (i in source.indices) out[i] = source[i]
        return out
    }
}
