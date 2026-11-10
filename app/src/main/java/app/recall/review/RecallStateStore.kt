package app.recall.review

import java.io.File
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * App-owned state in recall_state.json: which cards have been missed (their mem is shown
 * from then on) and which course levels are unlocked (sticky once reached).
 *
 * Kept outside cards.data so the collection stays readable by Anki-format tools.
 * Thread-safe through `synchronized`; writes go through a temp file and rename.
 */
class RecallStateStore(private val file: File) {

    private val memSeen = mutableSetOf<Long>()
    private val unlocked = mutableMapOf<String, MutableSet<Int>>()

    init {
        load()
    }

    @Synchronized
    fun shouldShowMem(cardId: Long): Boolean = cardId in memSeen

    /** Call on Again. The card's mem is shown on every later presentation. */
    @Synchronized
    fun markMissed(cardId: Long) {
        if (memSeen.add(cardId)) save()
    }

    @Synchronized
    fun isUnlocked(course: String, level: Int): Boolean =
        level == 1 || unlocked[course]?.contains(level) == true

    /** Unlocks are never revoked. */
    @Synchronized
    fun markUnlocked(course: String, level: Int) {
        if (unlocked.getOrPut(course) { mutableSetOf() }.add(level)) save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            val seen = o.optJSONArray("mem_seen") ?: JSONArray()
            for (i in 0 until seen.length()) memSeen += seen.getLong(i)
            val levels = o.optJSONObject("unlocked") ?: JSONObject()
            val keys = levels.keys()
            while (keys.hasNext()) {
                val course = keys.next()
                val arr = levels.getJSONArray(course)
                unlocked[course] = MutableList(arr.length()) { arr.getInt(it) }.toMutableSet()
            }
        } catch (e: JSONException) {
            System.err.println("recall: recall_state.json unreadable, starting empty: ${e.message}")
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("mem_seen", JSONArray(memSeen.toList()))
        val levels = JSONObject()
        for ((course, set) in unlocked) levels.put(course, JSONArray(set.sorted()))
        o.put("unlocked", levels)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
