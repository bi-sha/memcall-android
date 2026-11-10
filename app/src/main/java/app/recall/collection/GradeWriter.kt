package app.recall.collection

import app.recall.fsrs.CardSnapshot
import app.recall.fsrs.Grade
import app.recall.fsrs.SchedulingPlanner
import app.recall.fsrs.UpdatePlan
import kotlinx.coroutines.withContext

/** What one grade produced, for the caller to show or log. */
data class GradeOutcome(
    val intervalDays: Int,
    val dueDay: Long,
    val queue: Int,
)

/**
 * Applies one grade to a card: plans it with [SchedulingPlanner], writes cards and revlog in a
 * single BEGIN IMMEDIATE transaction, and rolls back on any failure.
 *
 * FRAGILE: the revlog columns (ease, ivl, lastIvl, factor, time, type) and the cards columns
 * (type, queue, due, ivl, reps, lapses, usn, mod) follow Anki's legacy schema. The revlog
 * `factor` is written as 0 because FSRS has no ease factor.
 */
class GradeWriter(private val repo: CollectionRepo) {

    /**
     * @param nowMs wall-clock time in milliseconds. Also used as the revlog id.
     * @param timeTakenMs time spent on the card, stored in the revlog.
     */
    suspend fun grade(cardId: Long, grade: Grade, nowMs: Long, timeTakenMs: Int): GradeOutcome =
        withContext(CollectionRepo.dispatcher) {
            val db = repo.db
            val nowSecs = nowMs / 1000
            db.execSQL("BEGIN IMMEDIATE")
            try {
                val card = repo.cardLocked(cardId)
                    ?: throw IllegalArgumentException("card $cardId not found")
                val today = repo.meta.todayDay(nowSecs)
                val plan = SchedulingPlanner.plan(
                    card = CardSnapshot(
                        isNew = card.type == 0,
                        due = card.due,
                        ivl = card.ivl,
                        reps = card.reps,
                        lapses = card.lapses,
                        priorState = CardDataCodec.parseState(card.data) ?: fallbackState(card),
                    ),
                    grade = grade,
                    today = today,
                    desiredRetention = repo.desiredRetentionFor(card.deckId),
                    weights = repo.weightsFor(card.deckId),
                )
                val data = CardDataCodec.encode(card.data, plan.state, repo.desiredRetentionFor(card.deckId))

                db.execSQL(
                    "UPDATE cards SET type = ?, queue = ?, due = ?, ivl = ?, reps = ?, " +
                        "lapses = ?, data = ?, usn = -1, mod = ? WHERE id = ?",
                    arrayOf<Any?>(
                        plan.type, plan.queue, plan.due, plan.ivl, plan.reps,
                        plan.lapses, data, nowSecs, cardId,
                    ),
                )

                insertRevlog(uniqueRevlogId(nowMs), cardId, grade, plan, timeTakenMs)

                db.execSQL("COMMIT")
                GradeOutcome(plan.intervalDays, plan.due, plan.queue)
            } catch (e: Throwable) {
                runCatching { db.execSQL("ROLLBACK") }
                throw e
            }
        }

    private fun fallbackState(card: CardRow): app.recall.fsrs.FsrsState? {
        // Cards with no stored state are new unless their type says otherwise.
        if (card.type == 0) return null
        // FRAGILE fallback: no usable FSRS state on a review card. Use the interval.
        System.err.println("recall: card ${card.id} has no FSRS state; falling back to ivl=${card.ivl}")
        return app.recall.fsrs.FsrsState(stability = maxOf(card.ivl.toDouble(), 1.0), difficulty = 5.0)
    }

    private fun uniqueRevlogId(startMs: Long): Long {
        var id = startMs
        while (revlogExists(id)) id++
        return id
    }

    private fun revlogExists(id: Long): Boolean =
        repo.db.rawQuery("SELECT 1 FROM revlog WHERE id = ?", arrayOf(id.toString())).use { it.moveToFirst() }

    private fun insertRevlog(revId: Long, cardId: Long, grade: Grade, plan: UpdatePlan, timeTakenMs: Int) {
        repo.db.execSQL(
            "INSERT INTO revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) " +
                "VALUES (?, ?, -1, ?, ?, ?, 0, ?, ?)",
            arrayOf<Any?>(revId, cardId, grade.rating, plan.intervalDays, plan.lastIvl, timeTakenMs, plan.revlogType),
        )
    }
}
