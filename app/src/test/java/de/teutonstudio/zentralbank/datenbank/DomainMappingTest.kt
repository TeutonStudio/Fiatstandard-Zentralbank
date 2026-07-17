package de.teutonstudio.zentralbank.datenbank

import de.teutonstudio.zentralbank.daten.zuordnung.zuGeld
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMappingTest {
    @Test
    fun zahlungsmittelWirdAlsCentbasiertesDomainGeldAbgebildet() {
        assertEquals(Geld.cent(12_300), 123.toZahlungsmittel().zuGeld())
    }

    @Test
    fun testSpielWirdInDomainStateAbgebildet() {
        val state = TestSpiel.zuSpielZustand()

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

    @Test
    fun neuesKartenspielBeginntBeiErstemSpielerOhneHauptbahnhofInRundeNull() {
        val anna = Spieler("Anna", mapOf(Verwaltungsstandort.HAUPTBAHNHOF to 1))
        val bert = Spieler("Bert", mapOf(Verwaltungsstandort.HAUPTBAHNHOF to 1))
        val annaId = SpielerId("Anna")
        val karte = Spielkarte(
            id = "runde-null",
            name = "Runde Null",
            hexagon = KartenHexagon(radius = 6),
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(
                        KartenFeld(0, 0, DreieckHaelfte.UNTEN).ecken().first(),
                        EckGebaeudeTyp.HAUPTBAHNHOF,
                        annaId,
                    ),
                ),
            ),
        )
        val spiel = Spiel(
            leitzinssatz = 0f,
            spieler = linkedMapOf(
                anna to Zahlungsmittel(),
                bert to Zahlungsmittel(),
            ),
            warenkorb = emptyMap(),
            inflationsziel = 2f,
            normaleAbweichung = 0.5f,
            starkeAbweichung = 2f,
            karte = karte,
        )

        val zustand = spiel.zuSpielZustand()

        assertEquals(Spielabschnitt.RUNDE_NULL, zustand.spielabschnitt)
        assertEquals(SpielerId("Bert"), zustand.aktiverSpieler)
        assertEquals(
            mapOf(BauteilTyp.HAUPTBAHNHOF to 1),
            zustand.rundeNullRestbestand?.get(SpielerId("Bert")),
        )
    }
}
