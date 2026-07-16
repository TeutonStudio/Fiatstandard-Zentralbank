package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.domain.AnleiheId
import de.teutonstudio.zentralbank.domain.BauteilTyp
import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Test

class SpielerAnzeigeTest {
    @Test
    fun spielerAnzeigeVerdichtetDomainSpieler() {
        val state = GameState(
            spieler = listOf(
                Spieler(
                    id = SpielerId("anna"),
                    name = "Anna",
                    geldkonto = Geld.mark(12),
                    anleihen = listOf(AnleiheId("a1"), AnleiheId("a2")),
                    bauteile = mapOf(BauteilTyp.BAHNHOF to 1, BauteilTyp.EISENBAHNLINIE to 2),
                ),
            ),
        )

        val anzeige = state.zuSpielerAnzeigen().single()

        assertEquals("Anna", anzeige.name)
        assertEquals("12,00 ℳ", anzeige.geld)
        assertEquals(2, anzeige.anleihen)
        assertEquals(3, anzeige.bauteile)
    }
}
