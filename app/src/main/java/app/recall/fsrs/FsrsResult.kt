package app.recall.fsrs

/** Next memory state and the interval (whole days) until the card is due again. */
data class FsrsResult(
    val state: FsrsState,
    val intervalDays: Int,
)
