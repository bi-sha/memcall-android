package app.recall.render

import android.webkit.JavascriptInterface
import app.recall.fsrs.Grade

/**
 * Methods exposed to the page as `RecallNative`. recall_bridge.js wraps them as
 * `Recall.grade`, `Recall.getTyped` and `Recall.playAudio`.
 *
 * WebView calls these on a background thread. [onGrade] and [onPlayAudio] must hop to the
 * session's dispatcher before touching state.
 */
class RecallBridge(
    private val onGrade: (Grade) -> Unit,
    private val onPlayAudio: (String) -> Unit,
) {
    @JavascriptInterface
    fun grade(which: String) {
        when (which.lowercase()) {
            "again" -> onGrade(Grade.AGAIN)
            "good" -> onGrade(Grade.GOOD)
        }
    }

    @JavascriptInterface
    fun playAudio(name: String) {
        onPlayAudio(name)
    }
}
