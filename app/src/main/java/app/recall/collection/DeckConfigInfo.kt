package app.recall.collection

/**
 * One entry of `col.dconf`. Both keys are FRAGILE: `desiredRetention` and `fsrsWeights`
 * are the names this app reads. Anki's own key names may differ between versions.
 */
data class DeckConfigInfo(
    val id: Long,
    val desiredRetention: Double?,
    val fsrsWeights: List<Double>?,
)
