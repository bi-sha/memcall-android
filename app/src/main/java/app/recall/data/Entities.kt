package app.recall.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * [dueDay] is the epoch day (days since 1970-01-01) on which the card is next due.
 * [interval], [reps] and [ease] are the SM-2 scheduling state.
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val interval: Int = 0,
    val reps: Int = 0,
    val ease: Double = 2.5,
    val dueDay: Long = 0,
)

@Dao
interface RecallDao {
    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    fun decks(): Flow<List<DeckEntity>>

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)

    @Query("SELECT * FROM cards")
    fun allCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueDay <= :today ORDER BY dueDay, id")
    suspend fun dueCards(deckId: Long, today: Long): List<CardEntity>

    @Insert
    suspend fun insertCard(card: CardEntity): Long

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: Long)
}

@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RecallDb : RoomDatabase() {
    abstract fun dao(): RecallDao
}
