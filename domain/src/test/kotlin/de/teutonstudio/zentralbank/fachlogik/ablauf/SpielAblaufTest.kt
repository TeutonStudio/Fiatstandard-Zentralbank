package de.teutonstudio.zentralbank.fachlogik.ablauf

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import org.junit.Assert.assertEquals
import org.junit.Test

class SpielAblaufTest {
    private val annaId = SpielerId("Anna")

    private fun spielAblauf(): SpielAblauf = SpielAblauf(
        SpielZustand(
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
    fun anwendenRueckgaengigUndWiederholenPflegenEreignisverlauf() {
        val engine = spielAblauf()
        val event = SpielEreignis.RohstoffEinnahme(annaId, mapOf(Rohstoff.HOLZ to 2))

        engine.apply(event).getOrThrow()
        assertEquals(2, engine.state.spieler.single().rohstoffe[Rohstoff.HOLZ])

        engine.undo()
        assertEquals(null, engine.state.spieler.single().rohstoffe[Rohstoff.HOLZ])

        engine.redo().getOrThrow()
        assertEquals(2, engine.state.spieler.single().rohstoffe[Rohstoff.HOLZ])
    }
}
