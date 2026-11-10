package app.recall

import android.app.Application
import androidx.room.Room
import app.recall.data.RecallDb

class RecallApp : Application() {
    val db: RecallDb by lazy {
        Room.databaseBuilder(this, RecallDb::class.java, "recall.db").build()
    }
}
