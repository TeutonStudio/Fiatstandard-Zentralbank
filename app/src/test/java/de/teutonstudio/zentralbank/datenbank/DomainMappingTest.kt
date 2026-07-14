package de.teutonstudio.zentralbank.datenbank

import de.teutonstudio.zentralbank.domain.BauteilTyp
import de.teutonstudio.zentralbank.domain.Basispunkte
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

        assertEquals(TestSpiel.aktuelleRunde - 1, state.rundenzähler)
        assertEquals(TestSpiel.spielerStringListe, state.spieler.map { it.name })
        assertEquals(Basispunkte((TestSpiel.aktuellerLeitzinssatz * 100).toInt()), state.leitzins)
        assertTrue(state.marktpreise.keys.containsAll(Rohstoff.entries))
        assertEquals(
            setOf(
                Rohstoff.NAHRUNG,
                Rohstoff.LEHM,
                Rohstoff.ZIEGEL,
                Rohstoff.HOLZ,
                Rohstoff.ROHOEL,
                Rohstoff.DIESEL,
                Rohstoff.KOHLE,
                Rohstoff.STAHL,
                Rohstoff.EISEN,
            ),
            state.warenkorb.keys,
        )
        assertTrue(state.spieler.all { BauteilTyp.EISENBAHNLINIE in it.bauteile.keys })
    }
}
