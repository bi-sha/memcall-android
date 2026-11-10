package app.recall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.recall.data.CardEntity
import app.recall.data.DeckEntity
import app.recall.scheduler.Sm2
import app.recall.ui.theme.RecallTheme

@Composable
fun RecallRoot(vm: RecallViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "decks") {
        composable("decks") {
            DecksScreen(vm = vm, onOpen = { id -> nav.navigate("deck/$id") })
        }
        composable(
            "deck/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            DeckScreen(
                vm = vm,
                deckId = entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onStudy = { id -> nav.navigate("review/$id") },
            )
        }
        composable(
            "review/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            ReviewScreen(
                vm = vm,
                deckId = entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(vm: RecallViewModel, onOpen: (Long) -> Unit) {
    val decks by vm.decks.collectAsState()
    val cards by vm.allCards.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recall") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New deck")
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No decks yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap + to create your first deck.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(decks, key = { it.id }) { deck ->
                    DeckRow(deck, cards, onClick = { onOpen(deck.id) })
                }
            }
        }
    }

    if (showAdd) {
        NameDialog(
            title = "New deck",
            onDismiss = { showAdd = false },
            onConfirm = { name ->
                vm.addDeck(name)
                showAdd = false
            },
        )
    }
}

@Composable
private fun DeckRow(deck: DeckEntity, cards: List<CardEntity>, onClick: () -> Unit) {
    val today = java.time.LocalDate.now().toEpochDay()
    val mine = cards.filter { it.deckId == deck.id }
    val due = mine.count { it.dueDay <= today }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(deck.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "$due due · ${mine.size} cards",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    vm: RecallViewModel,
    deckId: Long,
    onBack: () -> Unit,
    onStudy: (Long) -> Unit,
) {
    val decks by vm.decks.collectAsState()
    val cards by vm.allCards.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId }
    val deckCards = cards.filter { it.deckId == deckId }
    val today = java.time.LocalDate.now().toEpochDay()
    val dueCount = deckCards.count { it.dueDay <= today }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New card")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = { onStudy(deckId) },
                    enabled = dueCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (dueCount > 0) "Study $dueCount due" else "Nothing due")
                }
            }
            items(deckCards, key = { it.id }) { card ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                card.front,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.deleteCard(card.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete card")
                            }
                        }
                        Text(
                            card.back,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { vm.deleteDeck(deckId); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete deck")
                }
            }
        }
    }

    if (showAdd) {
        AddCardDialog(
            onDismiss = { showAdd = false },
            onConfirm = { front, back ->
                vm.addCard(deckId, front, back)
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: RecallViewModel, deckId: Long, onBack: () -> Unit) {
    var queue by remember { mutableStateOf<List<CardEntity>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val advance: () -> Unit = {
        index += 1
        revealed = false
    }

    LaunchedEffect(deckId) {
        queue = vm.dueCards(deckId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val cards = queue
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                cards == null -> Text("Loading…")
                cards.isEmpty() -> {
                    Text("All caught up", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "No cards are due right now.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                index >= cards.size -> {
                    Text("Session complete", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${cards.size} cards reviewed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val card = cards[index]
                    Text(
                        "${index + 1} / ${cards.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        card.front,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    if (!revealed) {
                        Button(onClick = { revealed = true }) { Text("Show answer") }
                    } else {
                        Text(card.back, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradeButton("Again", Sm2.Grade.AGAIN, card, vm, advance)
                            GradeButton("Hard", Sm2.Grade.HARD, card, vm, advance)
                            GradeButton("Good", Sm2.Grade.GOOD, card, vm, advance)
                            GradeButton("Easy", Sm2.Grade.EASY, card, vm, advance)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: Sm2.Grade,
    card: CardEntity,
    vm: RecallViewModel,
    onAnswered: () -> Unit,
) {
    OutlinedButton(onClick = {
        vm.grade(card, grade)
        onAnswered()
    }) {
        Text(label)
    }
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddCardDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text("Front") },
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text("Back") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(front, back) },
                enabled = front.isNotBlank() && back.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
