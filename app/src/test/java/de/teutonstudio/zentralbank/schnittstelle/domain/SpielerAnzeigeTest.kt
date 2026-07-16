package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Test

class SpielerAnzeigeTest {
    @Test
    fun spielerAnzeigeVerdichtetDomainSpieler() {
        val state = SpielZustand(
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
