package app.recall.collection

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recall.fsrs.Grade
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the device against a copy of anki/sample.apkg's collection.anki2 (bundled as a test asset).
 * Each test gets a fresh copy, so tests do not affect each other.
 */
@RunWith(AndroidJUnit4::class)
class CollectionSmokeTest {

    private lateinit var file: File

    @Before
    fun copyCollection() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        file = File(target.cacheDir, "collection.anki2")
        listOf(file, File(file.path + "-journal"), File(file.path + "-wal"), File(file.path + "-shm"))
            .forEach { it.delete() }
        assets.open("collection.anki2").use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        }
    }

    @Test
    fun opensCollectionAndReadsNewQueue() = runBlocking {
        CollectionRepo.open(file).use { repo ->
            assertEquals(1, repo.models.size)
            val model = repo.models.values.first()
            assertTrue(model.fieldIndex("term") >= 0)

            val newCards = repo.newCards(50)
            // 4 notes; TypeIn, MultipleChoice and Speed each make a card. Audio needs audio.
            assertEquals(12, newCards.size)
            assertTrue(newCards.all { it.queue == 0 && it.type == 0 })

            val note = repo.note(newCards.first().noteId)!!
            val fields = repo.fieldMap(note)
            assertTrue(fields["term"].orEmpty().isNotBlank())
            assertTrue(note.tags.any { it.startsWith("course::") })
        }
    }

    @Test
    fun goodGradeMovesNewCardToReviewAndWritesRevlog() = runBlocking {
        val now = System.currentTimeMillis()
        CollectionRepo.open(file).use { repo ->
            val writer = GradeWriter(repo)
            val card = repo.newCards(1).first()

            val out = writer.grade(card.id, Grade.GOOD, now, 1200)

            val after = repo.card(card.id)!!
            assertEquals(2, after.queue)
            assertEquals(2, after.type)
            assertEquals(out.intervalDays, after.ivl)
            assertEquals(1, after.reps)
            assertEquals(0, after.lapses)
            assertTrue("state written to data", after.data.contains("\"s\""))
            assertEquals(repo.meta.todayDay(now / 1000) + out.intervalDays, after.due)
        }

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*), MIN(ease), MAX(ease) FROM revlog", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(3, c.getInt(1))
                assertEquals(3, c.getInt(2))
            }
        }
    }

    @Test
    fun againOnReviewedCardCountsLapseAndAllowsSameMillisecondRevlogs() = runBlocking {
        val now = System.currentTimeMillis()
        CollectionRepo.open(file).use { repo ->
            val writer = GradeWriter(repo)
            val card = repo.newCards(1).first()

            writer.grade(card.id, Grade.GOOD, now, 500)
            // Same millisecond on purpose: the revlog id must be made unique, not rejected.
            val out = writer.grade(card.id, Grade.AGAIN, now, 500)

            val after = repo.card(card.id)!!
            assertEquals(1, after.lapses)
            assertEquals(out.intervalDays, after.ivl)
            assertTrue(out.intervalDays >= 1)
        }

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM revlog", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
        }
    }

    @Test
    fun failedGradeRollsBackAndLeavesDatabaseUsable() = runBlocking {
        val now = System.currentTimeMillis()
        CollectionRepo.open(file).use { repo ->
            val writer = GradeWriter(repo)
            val card = repo.newCards(1).first()

            try {
                writer.grade(999_999_999L, Grade.GOOD, now, 0)
                fail("grading a missing card should throw")
            } catch (expected: IllegalArgumentException) {
                // expected
            }

            // If the failed transaction were still open, BEGIN IMMEDIATE would fail here.
            writer.grade(card.id, Grade.GOOD, now, 0)
            assertEquals(1, repo.card(card.id)!!.reps)
        }
    }
}
