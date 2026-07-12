package de.teutonstudio.zentralbank.datenbank

import de.teutonstudio.zentralbank.domain.BauteilTyp
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Rohstoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMappingTest {
    @Test
    fun zahlungsmittelWirdAlsCentbasiertesDomainGeldAbgebildet() {
        assertEquals(Geld.cent(12_300), 123.toZahlungsmittel().zuDomainGeld())
    }

    @Test
    fun testSpielWirdInDomainStateAbgebildet() {
        val state = TestSpiel.zuDomainGameState()

        assertEquals(TestSpiel.aktuelleRunde, state.rundenzähler)
        assertEquals(TestSpiel.spielerStringListe, state.spieler.map { it.name })
        assertTrue(state.marktpreise.keys.containsAll(Rohstoff.entries))
        assertTrue(state.spieler.all { BauteilTyp.EISENBAHNLINIE in it.bauteile.keys })
    }
}
