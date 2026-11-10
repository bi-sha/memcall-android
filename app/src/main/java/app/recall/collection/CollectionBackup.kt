package app.recall.collection

import java.io.File

/** Copies the collection to `collection.anki2.bak` once, on the first launch of the app. */
object CollectionBackup {
    private const val MARKER = "collection_backup_done"

    fun backupOnFirstLaunch(collection: File, appFilesDir: File) {
        val marker = File(appFilesDir, MARKER)
        if (marker.exists()) return
        val bak = File(collection.parentFile, collection.name + ".bak")
        if (collection.exists() && !bak.exists()) {
            collection.copyTo(bak, overwrite = false)
        }
        marker.writeText(System.currentTimeMillis().toString())
    }
}
