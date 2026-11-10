package app.recall.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2Test {

    @Test
    fun firstGoodAnswerSchedulesOneDay() {
        val r = Sm2.grade(Sm2.Review.NEW, Sm2.Grade.GOOD)
        assertEquals(1, r.interval)
        assertEquals(1, r.reps)
    }

    @Test
    fun secondGoodAnswerSchedulesSixDays() {
        val first = Sm2.grade(Sm2.Review.NEW, Sm2.Grade.GOOD)
        val second = Sm2.grade(first, Sm2.Grade.GOOD)
        assertEquals(6, second.interval)
        assertEquals(2, second.reps)
    }

    @Test
    fun laterReviewsGrowByEase() {
        val first = Sm2.grade(Sm2.Review.NEW, Sm2.Grade.GOOD)
        val second = Sm2.grade(first, Sm2.Grade.GOOD)
        val third = Sm2.grade(second, Sm2.Grade.GOOD)
        // 6 * 2.5 = 15
        assertEquals(15, third.interval)
    }

    @Test
    fun againResetsProgressAndSchedulesTomorrow() {
        val learned = Sm2.Review(interval = 30, reps = 5, ease = 2.5)
        val r = Sm2.grade(learned, Sm2.Grade.AGAIN)
        assertEquals(1, r.interval)
        assertEquals(0, r.reps)
    }

    @Test
    fun easeNeverDropsBelowFloor() {
        var r = Sm2.Review.NEW
        repeat(50) { r = Sm2.grade(r, Sm2.Grade.AGAIN) }
        assertTrue(r.ease >= 1.3)
        assertEquals(1.3, r.ease, 1e-9)
    }

    @Test
    fun easyRaisesEase() {
        val r = Sm2.grade(Sm2.Review.NEW, Sm2.Grade.EASY)
        assertTrue(r.ease > Sm2.Review.NEW.ease)
    }
}
