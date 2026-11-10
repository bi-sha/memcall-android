package app.recall.review

import java.text.Normalizer
import java.util.Locale

/**
 * Forgiving match for typed answers. Same rules as the card templates' JS:
 *  - ignore case, accents, repeated and surrounding spaces
 *  - the first character must match
 *  - edit distance threshold: 1 for targets under 5 characters, 2 otherwise
 */
object TypedAnswer {

    private val combining = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(combining, "")
            .lowercase(Locale.ROOT)
            .replace(spaces, " ")
            .trim()

    fun matches(typed: String, target: String): Boolean {
        val t = normalize(typed)
        val g = normalize(target)
        if (t.isEmpty() || g.isEmpty()) return false
        if (t[0] != g[0]) return false
        val threshold = if (g.length < 5) 1 else 2
        return t == g || levenshtein(t, g) <= threshold
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
