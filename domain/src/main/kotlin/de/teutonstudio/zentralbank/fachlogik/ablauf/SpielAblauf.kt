package de.teutonstudio.zentralbank.fachlogik.ablauf

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk

class SpielAblauf(
    val startzustand: SpielZustand,
    ereignisse: List<SpielEreignis> = emptyList(),
) {
    private val angewandteEvents = ereignisse.toMutableList()
    private val rueckgaengigGemacht = mutableListOf<SpielEreignis>()

    val eventLog: List<SpielEreignis>
        get() = angewandteEvents.toList()

    val redoLog: List<SpielEreignis>
        get() = rueckgaengigGemacht.toList()

    val state: SpielZustand
        get() = falte(angewandteEvents)

    fun apply(event: SpielEreignis): Result<SpielZustand> {
        return SpielRegelwerk.reduce(state, event).onSuccess {
            angewandteEvents += event
            rueckgaengigGemacht.clear()
        }
    }

    fun undo(): SpielZustand {
        require(angewandteEvents.isNotEmpty()) { "Es gibt kein Event zum Zuruecknehmen." }
        rueckgaengigGemacht += angewandteEvents.removeLast()
        return state
    }

    fun redo(): Result<SpielZustand> {
        val event = rueckgaengigGemacht.removeLastOrNull()
            ?: return Result.failure(IllegalStateException("Es gibt kein Event zum Wiederholen."))
        return SpielRegelwerk.reduce(state, event)
            .onSuccess { angewandteEvents += event }
            .onFailure { rueckgaengigGemacht += event }
    }

    private fun falte(events: List<SpielEreignis>): SpielZustand {
        return events.fold(startzustand) { state, event ->
            SpielRegelwerk.reduce(state, event).getOrThrow()
        }
    }
}
