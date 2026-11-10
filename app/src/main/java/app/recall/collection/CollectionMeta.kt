package app.recall.collection

/**
 * Collection-wide timing. Day numbers count from `col.crt` (creation time, epoch seconds).
 * FRAGILE: Anki rolls the day over at a configured hour (default 4 AM). This uses plain
 * midnight-free floor division, so cards near the rollover can be due a day early or late.
 */
data class CollectionMeta(val crtSecs: Long) {
    fun todayDay(nowSecs: Long): Long = Math.floorDiv(nowSecs - crtSecs, SECONDS_PER_DAY)

    companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
