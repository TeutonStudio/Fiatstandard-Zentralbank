package de.teutonstudio.zentralbank.domain.engine

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.events.GameEvent

class GameEngine(
    val startzustand: GameState,
    events: List<GameEvent> = emptyList(),
) {
    private val angewandteEvents = events.toMutableList()
    private val rueckgaengigGemacht = mutableListOf<GameEvent>()

    val eventLog: List<GameEvent>
        get() = angewandteEvents.toList()

    val redoLog: List<GameEvent>
        get() = rueckgaengigGemacht.toList()

    val state: GameState
        get() = falte(angewandteEvents)

    fun apply(event: GameEvent): Result<GameState> {
        return Reducer.reduce(state, event).onSuccess {
            angewandteEvents += event
            rueckgaengigGemacht.clear()
        }
    }

    fun undo(): GameState {
        require(angewandteEvents.isNotEmpty()) { "Es gibt kein Event zum Zuruecknehmen." }
        rueckgaengigGemacht += angewandteEvents.removeLast()
        return state
    }

    fun redo(): Result<GameState> {
        val event = rueckgaengigGemacht.removeLastOrNull()
            ?: return Result.failure(IllegalStateException("Es gibt kein Event zum Wiederholen."))
        return Reducer.reduce(state, event)
            .onSuccess { angewandteEvents += event }
            .onFailure { rueckgaengigGemacht += event }
    }

    private fun falte(events: List<GameEvent>): GameState {
        return events.fold(startzustand) { state, event ->
            Reducer.reduce(state, event).getOrThrow()
        }
    }
}
