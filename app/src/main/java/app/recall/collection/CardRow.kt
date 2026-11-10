package app.recall.collection

/**
 * One row of the `cards` table.
 * `due` is a day number for review cards (queue 2), epoch seconds for learning (queue 1), and
 * the new-card position for new cards (queue 0). FRAGILE: Anki uses these encodings.
 */
data class CardRow(
    val id: Long,
    val noteId: Long,
    val deckId: Long,
    val ord: Int,
    val type: Int,
    val queue: Int,
    val due: Long,
    val ivl: Int,
    val reps: Int,
    val lapses: Int,
    val data: String,
)
