package app.recall.render

import android.media.MediaPlayer
import java.io.File

/**
 * Plays one clip at a time from the collection's media folder. Missing files are ignored.
 * FRAGILE: expects media files to be stored under their original names. genanki writes
 * numbered entries inside the .apkg, so the import step must rename them before this runs.
 */
class AudioPlayer(private val mediaDir: File) {
    private var player: MediaPlayer? = null

    fun play(name: String) {
        val file = File(mediaDir, name)
        if (!file.isFile) return
        release()
        player = MediaPlayer().apply {
            setDataSource(file.path)
            setOnCompletionListener { it.release() }
            prepare()
            start()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
