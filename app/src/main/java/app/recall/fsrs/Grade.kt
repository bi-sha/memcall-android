package app.recall.fsrs

/**
 * Grades the app offers. Values match FSRS ratings: 1 = Again, 3 = Good.
 * Hard (2) and Easy (4) are not offered in the UI but FsrsScheduler supports them.
 */
enum class Grade(val rating: Int) {
    AGAIN(1),
    GOOD(3),
}
