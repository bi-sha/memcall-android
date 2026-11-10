package app.recall.fsrs

/** Per-card memory state. Stability is in days; difficulty is in the range 1..10. */
data class FsrsState(
    val stability: Double,
    val difficulty: Double,
)
