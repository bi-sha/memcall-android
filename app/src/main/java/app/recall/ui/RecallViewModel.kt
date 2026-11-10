package app.recall.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.recall.RecallApp
import app.recall.data.CardEntity
import app.recall.data.DeckEntity
import app.recall.data.RecallDao
import app.recall.scheduler.Sm2
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecallViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: RecallDao = (app as RecallApp).db.dao()

    val decks: StateFlow<List<DeckEntity>> = dao.decks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCards: StateFlow<List<CardEntity>> = dao.allCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addDeck(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { dao.insertDeck(DeckEntity(name = trimmed)) }
    }

    fun deleteDeck(id: Long) {
        viewModelScope.launch { dao.deleteDeck(id) }
    }

    fun addCard(deckId: Long, front: String, back: String) {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return
        viewModelScope.launch {
            // New cards are due immediately.
            dao.insertCard(CardEntity(deckId = deckId, front = f, back = b, dueDay = today()))
        }
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch { dao.deleteCard(id) }
    }

    suspend fun dueCards(deckId: Long): List<CardEntity> = dao.dueCards(deckId, today())

    fun grade(card: CardEntity, grade: Sm2.Grade) {
        viewModelScope.launch {
            val next = Sm2.grade(
                Sm2.Review(card.interval, card.reps, card.ease),
                grade,
            )
            dao.updateCard(
                card.copy(
                    interval = next.interval,
                    reps = next.reps,
                    ease = next.ease,
                    dueDay = today() + next.interval,
                ),
            )
        }
    }

    private fun today(): Long = LocalDate.now().toEpochDay()
}
