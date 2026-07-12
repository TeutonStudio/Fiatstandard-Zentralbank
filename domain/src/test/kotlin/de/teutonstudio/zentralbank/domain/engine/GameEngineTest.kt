package de.teutonstudio.zentralbank.domain.engine

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.events.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class GameEngineTest {
    private val annaId = SpielerId("Anna")

    private fun engine(): GameEngine = GameEngine(
        GameState(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    geldkonto = Geld.mark(1),
                ),
            ),
        ),
    )

    @Test
    fun applyUndoRedoFaltenEventLog() {
        val engine = engine()
        val event = GameEvent.RohstoffEinnahme(annaId, mapOf(Rohstoff.HOLZ to 2))

        engine.apply(event).getOrThrow()
        assertEquals(2, engine.state.spieler.single().rohstoffe[Rohstoff.HOLZ])

        engine.undo()
        assertEquals(null, engine.state.spieler.single().rohstoffe[Rohstoff.HOLZ])

        engine.redo().getOrThrow()
        assertEquals(2, engine.state.spieler.single().rohstoffe[Rohstoff.HOLZ])
    }
}
