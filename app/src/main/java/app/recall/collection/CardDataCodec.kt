package app.recall.collection

import app.recall.fsrs.FsrsState
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads and writes FSRS state in `cards.data`.
 *
 * FRAGILE: the keys "s" (stability, days), "d" (difficulty, 1..10) and "dr" (desired
 * retention) are this app's keys. A card whose data is missing or unparsable returns null,
 * and the caller falls back to the interval.
 */
object CardDataCodec {

    fun parseState(data: String): FsrsState? {
        if (data.isBlank()) return null
        return try {
            val o = JSONObject(data)
            if (!o.has("s") || !o.has("d")) return null
            val s = o.getDouble("s")
            val d = o.getDouble("d")
            if (!s.isFinite() || !d.isFinite()) null else FsrsState(s, d)
        } catch (e: JSONException) {
            null
        }
    }

    /** Keeps any keys already in [existing] and sets s, d and dr. */
    fun encode(existing: String, state: FsrsState, desiredRetention: Double): String {
        val o = try {
            if (existing.isBlank()) JSONObject() else JSONObject(existing)
        } catch (e: JSONException) {
            JSONObject()
        }
        o.put("s", state.stability)
        o.put("d", state.difficulty)
        o.put("dr", desiredRetention)
        return o.toString()
    }
}
