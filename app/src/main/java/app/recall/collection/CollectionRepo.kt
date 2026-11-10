package app.recall.collection

import android.database.sqlite.SQLiteDatabase
import app.recall.fsrs.FsrsScheduler
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read access to an existing collection.anki2, plus the shared connection that
 * [GradeWriter] uses for writes. Opened with raw SQLiteDatabase; no Room, no
 * SQLiteOpenHelper.onCreate. The database must already exist.
 *
 * FRAGILE: targets the legacy schema (col.models, col.decks, col.dconf as JSON), which
 * genanki produces. Modern Anki collections will fail here with a clear error.
 */
class CollectionRepo private constructor(
    internal val db: SQLiteDatabase,
    val meta: CollectionMeta,
    val models: Map<Long, ModelInfo>,
    private val deckConf: Map<Long, Long>,
    private val confs: Map<Long, DeckConfigInfo>,
) : AutoCloseable {

    companion object {
        /** Every DB read and write runs on this single thread. */
        val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
            Thread(r, "recall-db").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        suspend fun open(file: File): CollectionRepo = withContext(dispatcher) {
            require(file.exists()) { "collection not found: ${file.path}" }
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                load(db)
            } catch (e: Exception) {
                db.close()
                throw e
            }
        }

        private fun load(db: SQLiteDatabase): CollectionRepo {
            db.rawQuery("SELECT crt, models, decks, dconf FROM col LIMIT 1", null).use { c ->
                check(c.moveToFirst()) { "col table is empty" }
                return CollectionRepo(
                    db = db,
                    meta = CollectionMeta(c.getLong(0)),
                    models = parseModels(c.getString(1)),
                    deckConf = parseDecks(c.getString(2)),
                    confs = parseConfs(c.getString(3)),
                )
            }
        }

        private fun keysOf(o: JSONObject): List<String> {
            val out = mutableListOf<String>()
            val it = o.keys()
            while (it.hasNext()) out += it.next()
            return out
        }

        private fun parseModels(json: String): Map<Long, ModelInfo> {
            val root = JSONObject(json)
            return keysOf(root).associate { key ->
                val m = root.getJSONObject(key)
                val fields = m.getJSONArray("flds").objects().map {
                    ModelInfo.FieldInfo(it.getString("name"), it.getInt("ord"))
                }.sortedBy { it.ord }
                val templates = m.getJSONArray("tmpls").objects().map {
                    ModelInfo.TemplateInfo(
                        name = it.getString("name"),
                        ord = it.getInt("ord"),
                        qfmt = it.getString("qfmt"),
                        afmt = it.getString("afmt"),
                    )
                }.sortedBy { it.ord }
                key.toLong() to ModelInfo(
                    id = key.toLong(),
                    name = m.getString("name"),
                    fields = fields,
                    templates = templates,
                    css = m.optString("css", ""),
                )
            }
        }

        private fun parseDecks(json: String): Map<Long, Long> {
            val root = JSONObject(json)
            return keysOf(root).associate { did ->
                did.toLong() to root.getJSONObject(did).optLong("conf", 1L)
            }
        }

        private fun parseConfs(json: String): Map<Long, DeckConfigInfo> {
            val root = JSONObject(json)
            return keysOf(root).associate { id ->
                val c = root.getJSONObject(id)
                val weights = c.optJSONArray("fsrsWeights")?.let { arr ->
                    List(arr.length()) { i -> arr.getDouble(i) }
                }
                id.toLong() to DeckConfigInfo(
                    id = id.toLong(),
                    desiredRetention = if (c.has("desiredRetention")) c.getDouble("desiredRetention") else null,
                    fsrsWeights = weights,
                )
            }
        }

        private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    }

    // ---- configuration lookups -------------------------------------------------

    fun weightsFor(deckId: Long): DoubleArray = FsrsScheduler.loadWeights(confFor(deckId)?.fsrsWeights)

    fun desiredRetentionFor(deckId: Long): Double =
        confFor(deckId)?.desiredRetention ?: FsrsScheduler.DEFAULT_DESIRED_RETENTION

    private fun confFor(deckId: Long): DeckConfigInfo? =
        deckConf[deckId]?.let { confs[it] }

    // ---- queues -----------------------------------------------------------------

    suspend fun dueReview(today: Long, limit: Int): List<CardRow> = withContext(dispatcher) {
        cards(
            "SELECT id, nid, did, ord, type, queue, due, ivl, reps, lapses, data " +
                "FROM cards WHERE queue = 2 AND due <= ? ORDER BY due, id LIMIT ?",
            arrayOf(today.toString(), limit.toString()),
        )
    }

    /** Learning cards that are due. Queue 1 uses epoch seconds; queue 3 uses day numbers. */
    suspend fun learning(nowSecs: Long, today: Long, limit: Int): List<CardRow> = withContext(dispatcher) {
        cards(
            "SELECT id, nid, did, ord, type, queue, due, ivl, reps, lapses, data FROM cards " +
                "WHERE queue IN (1, 3) AND ((queue = 1 AND due <= ?) OR (queue = 3 AND due <= ?)) " +
                "ORDER BY due, id LIMIT ?",
            arrayOf(nowSecs.toString(), today.toString(), limit.toString()),
        )
    }

    suspend fun newCards(limit: Int): List<CardRow> = withContext(dispatcher) {
        cards(
            "SELECT id, nid, did, ord, type, queue, due, ivl, reps, lapses, data " +
                "FROM cards WHERE queue = 0 ORDER BY due, id LIMIT ?",
            arrayOf(limit.toString()),
        )
    }

    suspend fun card(id: Long): CardRow? = withContext(dispatcher) { cardLocked(id) }

    /** Every card with its note's tags. Used to group cards by course and level. */
    suspend fun allCardsWithTags(): List<Pair<CardRow, List<String>>> = withContext(dispatcher) {
        db.rawQuery(
            "SELECT c.id, c.nid, c.did, c.ord, c.type, c.queue, c.due, c.ivl, c.reps, c.lapses, " +
                "c.data, n.tags FROM cards c JOIN notes n ON c.nid = n.id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val card = CardRow(
                        id = c.getLong(0),
                        noteId = c.getLong(1),
                        deckId = c.getLong(2),
                        ord = c.getInt(3),
                        type = c.getInt(4),
                        queue = c.getInt(5),
                        due = c.getLong(6),
                        ivl = c.getInt(7),
                        reps = c.getInt(8),
                        lapses = c.getInt(9),
                        data = c.getString(10) ?: "",
                    )
                    add(card to c.getString(11).split(' ').filter { it.isNotBlank() })
                }
            }
        }
    }

    suspend fun note(id: Long): NoteRow? = withContext(dispatcher) {
        db.rawQuery("SELECT id, mid, flds, tags FROM notes WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return@use null
            NoteRow(
                id = c.getLong(0),
                modelId = c.getLong(1),
                fields = c.getString(2).split('\u001F'),
                tags = c.getString(3).split(' ').filter { it.isNotBlank() },
            )
        }
    }

    /** Field name → value for a note, using the note type's field order. */
    fun fieldMap(note: NoteRow): Map<String, String> {
        val model = models[note.modelId] ?: return emptyMap()
        return model.fields.associate { f ->
            f.name to note.fields.getOrElse(f.ord) { "" }
        }
    }

    // ---- internal helpers (called on the DB thread) -----------------------------

    internal fun cardLocked(id: Long): CardRow? =
        cards(
            "SELECT id, nid, did, ord, type, queue, due, ivl, reps, lapses, data FROM cards WHERE id = ?",
            arrayOf(id.toString()),
        ).firstOrNull()

    private fun cards(sql: String, args: Array<String>): List<CardRow> =
        db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CardRow(
                            id = c.getLong(0),
                            noteId = c.getLong(1),
                            deckId = c.getLong(2),
                            ord = c.getInt(3),
                            type = c.getInt(4),
                            queue = c.getInt(5),
                            due = c.getLong(6),
                            ivl = c.getInt(7),
                            reps = c.getInt(8),
                            lapses = c.getInt(9),
                            data = c.getString(10) ?: "",
                        ),
                    )
                }
            }
        }

    override fun close() {
        db.close()
    }
}
