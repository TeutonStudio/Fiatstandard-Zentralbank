package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenAuswertungTest {
    private val anna = SpielerId("anna")
    private val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
    private val kante = feld.kanten().first()
    private val land = angrenzendeFelder(kante).map { GelaendeFeld(it, GelaendeTyp.EBENE) }

    @Test
    fun besteAnschlussartBestimmtErtrag() {
        val karte = karte(
            ecken = listOf(
                EckBelegung(feld.ecken().first(), EckGebaeudeTyp.GROSSBAHNHOF, anna),
            ),
            kanten = listOf(KantenBelegung(kante, anna)),
        )

        assertEquals(mapOf(anna to 3), KartenAuswertung.ertrag(karte, feld))
        assertEquals(mapOf(Rohstoff.NAHRUNG to 3), KartenAuswertung.rohstoffErtrag(karte, anna))
    }

    @Test
    fun anlageOhneAnschlussIstVerlassenUndErtraglos() {
        val karte = karte()
        val belegung = karte.belegung.felder.single()

        assertEquals(AnlagenZustand.VERLASSEN, KartenAuswertung.effektiverZustand(karte, belegung))
        assertTrue(KartenAuswertung.ertrag(karte, feld).isEmpty())
    }

    @Test
    fun geschaeftsbankWirdNurMitAnschlussKontrolliert() {
        val ohne = karte(anlage = FeldAnlage.Geschaeftsbank)
        val mit = karte(
            anlage = FeldAnlage.Geschaeftsbank,
            kanten = listOf(KantenBelegung(kante, anna)),
        )

        assertFalse(KartenAuswertung.kontrolliertGeschaeftsbank(ohne, feld, anna))
        assertTrue(KartenAuswertung.kontrolliertGeschaeftsbank(mit, feld, anna))
    }

    private fun karte(
        anlage: FeldAnlage = FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG),
        ecken: List<EckBelegung> = emptyList(),
        kanten: List<KantenBelegung> = emptyList(),
    ) = Spielkarte(
        id = "auswertung",
        name = "Auswertung",
        zeilen = 6,
        spalten = 6,
        gelaendefelder = land,
        belegung = KartenBelegung(
            ecken = ecken,
            kanten = kanten,
            felder = listOf(FeldBelegung(feld, anlage)),
        ),
    )
}
