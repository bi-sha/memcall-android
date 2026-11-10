package app.recall.session

import app.recall.collection.CardRow
import app.recall.collection.CollectionRepo
import app.recall.collection.GradeWriter
import app.recall.collection.ModelInfo
import app.recall.collection.NoteRow
import app.recall.fsrs.Grade
import app.recall.render.CardRenderer
import app.recall.review.RecallStateStore
import app.recall.review.TypedAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives one review session: builds the queue, renders each card, runs the speed timer,
 * records grades, and tracks mem reveal.
 *
 * Threading: all state changes happen on [scope], which must use Dispatchers.Main. DB work
 * is moved to CollectionRepo.dispatcher by the repo and writer.
 *
 * Speed mode: after [speedSeconds] without a reveal, the answer is shown for [holdMillis]
 * and the card is graded Again. A manual grade cancels the timer.
 */
class SessionLoop(
    private val repo: CollectionRepo,
    private val writer: GradeWriter,
    private val store: RecallStateStore,
    private val renderer: CardRenderer,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val speedSeconds: Int = 3,
    private val holdMillis: Long = 1200L,
) {
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state

    private var mode = SessionMode.LEARN
    private var queue: List<CardRow> = emptyList()
    private var index = 0
    private var shownAtMs = 0L
    private var timerJob: Job? = null
    private var busy = false
    private var current: Presented? = null

    private class Presented(
        val card: CardRow,
        val model: ModelInfo,
        val template: ModelInfo.TemplateInfo,
        val fields: Map<String, String>,
        val mem: String?,
    )

    fun start(mode: SessionMode, newPerDay: Int = 20, reviewLimit: Int = 200) {
        scope.launch {
            timerJob?.cancel()
            this@SessionLoop.mode = mode
            val nowSecs = clock() / 1000
            val today = repo.meta.todayDay(nowSecs)
            queue = when (mode) {
                SessionMode.LEARN -> repo.learning(nowSecs, today, reviewLimit) + repo.newCards(newPerDay)
                SessionMode.REVIEW, SessionMode.SPEED -> repo.dueReview(today, reviewLimit)
            }
            index = 0
            show()
        }
    }

    /** Shows the answer side. Also called by the speed timer. */
    fun reveal() {
        scope.launch { revealNow() }
    }

    /** Records a grade for the card on screen. Ignored unless the answer is showing. */
    fun grade(grade: Grade) {
        scope.launch { gradeCurrent(grade, fromTimer = false) }
    }

    /** Checks a typed answer against the term. The page also shows feedback; this is the native copy. */
    fun submitTyped(text: String) {
        val p = current ?: return
        val correct = TypedAnswer.matches(text, p.fields["term"].orEmpty())
        _state.value = _state.value.copy(typedCorrect = correct)
    }

    /** Recomputes level unlocks. Returns "course:level" entries that were newly unlocked. */
    suspend fun refreshUnlocks(): List<String> = LevelUnlock.refresh(repo, store)

    private suspend fun show() {
        busy = false
        if (index >= queue.size) {
            current = null
            _state.value = SessionState.done(queue.size)
            return
        }
        val card = queue[index]
        val note: NoteRow? = repo.note(card.noteId)
        val model = note?.let { repo.models[it.modelId] }
        val template = model?.templates?.getOrNull(card.ord)
        if (note == null || model == null || template == null) {
            index++
            _state.value = _state.value.copy(message = "Card ${card.id} has no template and was skipped.")
            show()
            return
        }
        val fields = repo.fieldMap(note)
        val mem = if (mode != SessionMode.SPEED && store.shouldShowMem(card.id)) {
            fields["mem"]?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        current = Presented(card, model, template, fields, mem)
        shownAtMs = clock()
        _state.value = SessionState(
            phase = SessionPhase.QUESTION,
            cardId = card.id,
            front = renderer.front(model, template, fields, mem),
            position = index + 1,
            total = queue.size,
        )
        if (mode == SessionMode.SPEED) {
            timerJob = scope.launch {
                delay(speedSeconds * 1000L)
                revealNow()
                delay(holdMillis)
                gradeCurrent(Grade.AGAIN, fromTimer = true)
            }
        }
    }

    private fun revealNow() {
        val p = current ?: return
        if (_state.value.phase != SessionPhase.QUESTION) return
        val back = renderer.back(p.model, p.template, p.fields, p.mem)
        _state.value = _state.value.copy(phase = SessionPhase.ANSWER, back = back)
    }

    private suspend fun gradeCurrent(grade: Grade, fromTimer: Boolean) {
        val p = current ?: return
        if (busy || _state.value.phase != SessionPhase.ANSWER) return
        busy = true
        if (!fromTimer) timerJob?.cancel()

        if (grade == Grade.AGAIN) store.markMissed(p.card.id)
        val elapsed = (clock() - shownAtMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        try {
            writer.grade(p.card.id, grade, clock(), elapsed)
        } catch (e: Exception) {
            busy = false
            _state.value = _state.value.copy(message = "Grade not saved: ${e.message}")
            return
        }
        index++
        show()
    }
}
