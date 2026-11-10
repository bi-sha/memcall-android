package app.recall.session

/**
 * LEARN: new cards plus learning cards that are due.
 * REVIEW: graduated review cards that are due.
 * SPEED: same queue as REVIEW, with a 3 s timer. Timeout grades Again.
 */
enum class SessionMode { LEARN, REVIEW, SPEED }
