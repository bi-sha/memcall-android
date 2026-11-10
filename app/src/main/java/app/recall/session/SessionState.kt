package app.recall.session

/** Where the session is: between cards, showing the question, showing the answer, or finished. */
enum class SessionPhase { IDLE, QUESTION, ANSWER, DONE }

/**
 * What the UI renders. [front] and [back] are full HTML documents for the WebView.
 * [typedCorrect] is set after [SessionLoop.submitTyped]; null when nothing was typed.
 * [message] carries a non-fatal problem, such as a skipped card or a failed grade.
 */
data class SessionState(
    val phase: SessionPhase,
    val cardId: Long? = null,
    val front: String? = null,
    val back: String? = null,
    val position: Int = 0,
    val total: Int = 0,
    val typedCorrect: Boolean? = null,
    val message: String? = null,
) {
    companion object {
        val IDLE = SessionState(SessionPhase.IDLE)
        fun done(total: Int) = SessionState(SessionPhase.DONE, total = total, position = total)
    }
}
