package app.recall.session

import app.recall.collection.CardDataCodec
import app.recall.collection.CardRow
import app.recall.collection.CollectionRepo
import app.recall.review.RecallStateStore

/**
 * Level unlock rule (locked): level N unlocks when at least 80% of the cards in level N-1
 * have FSRS stability >= 7 days. Unlocks are sticky.
 *
 * FRAGILE: stability comes from cards.data. Cards without FSRS state use their interval
 * as a stand-in, so unlocks computed that way are approximate.
 */
object LevelUnlock {
    const val THRESHOLD = 0.8
    const val MIN_STABILITY_DAYS = 7.0

    /** Recomputes unlock state for every course and stores any newly reached level. */
    suspend fun refresh(repo: CollectionRepo, store: RecallStateStore): List<String> {
        val byCourse = mutableMapOf<String, MutableMap<Int, MutableList<CardRow>>>()
        for ((card, tags) in repo.allCardsWithTags()) {
            val course = tagValue(tags, "course::") ?: continue
            val level = tagValue(tags, "level::")?.toIntOrNull() ?: continue
            byCourse.getOrPut(course) { mutableMapOf() }.getOrPut(level) { mutableListOf() }.add(card)
        }

        val newlyUnlocked = mutableListOf<String>()
        for ((course, levels) in byCourse) {
            for (level in levels.keys.sorted()) {
                if (store.isUnlocked(course, level)) continue
                if (level == 1 || levelReady(levels[level - 1].orEmpty())) {
                    store.markUnlocked(course, level)
                    newlyUnlocked += "$course:$level"
                }
            }
        }
        return newlyUnlocked
    }

    private fun levelReady(prevCards: List<CardRow>): Boolean {
        if (prevCards.isEmpty()) return true
        val passing = prevCards.count { stability(it) >= MIN_STABILITY_DAYS }
        return passing.toDouble() / prevCards.size >= THRESHOLD
    }

    private fun stability(card: CardRow): Double =
        CardDataCodec.parseState(card.data)?.stability ?: card.ivl.toDouble()

    private fun tagValue(tags: List<String>, prefix: String): String? =
        tags.firstOrNull { it.lowercase().startsWith(prefix) }?.substring(prefix.length)
}
