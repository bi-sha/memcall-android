package app.recall.collection

/** One row of the `notes` table, with fields split on 0x1F and tags split on spaces. */
data class NoteRow(
    val id: Long,
    val modelId: Long,
    val fields: List<String>,
    val tags: List<String>,
)
