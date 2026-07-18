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
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenAuswertungTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")
    private val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
    private val kante = feld.kanten().first()
    private val land = angrenzendeFelder(kante).map { GelaendeFeld(it, GelaendeTyp.EBENE) }

    @Test
    fun besteAnschlussartBestimmtErtrag() {
        val karte = karte(
            ecken = listOf(
                EckBelegung(feld.ecken().last(), EckGebaeudeTyp.GROSSBAHNHOF, anna),
            ),
            kanten = listOf(KantenBelegung(kante)),
        )

        assertEquals(mapOf(anna to 3), KartenAuswertung.ertrag(karte, feld))
        assertEquals(mapOf(Rohstoff.NAHRUNG to 3), KartenAuswertung.abbauErtrag(karte, anna))
    }

    @Test
    fun anlageOhneAnschlussIstVerlassenUndErtraglos() {
        val karte = karte()
        val belegung = karte.belegung.felder.single()

        assertEquals(AnlagenZustand.VERLASSEN, KartenAuswertung.effektiverZustand(karte, belegung))
        assertTrue(KartenAuswertung.ertrag(karte, feld).isEmpty())
    }

    @Test
    fun verarbeitungWirdNichtAlsAutomatischerAbbauGewertet() {
        val karte = karte(
            anlage = FeldAnlage.Wirtschaftsregion(BauteilTyp.ZIEGELBRENNER),
            kanten = listOf(KantenBelegung(kante)),
        )

        assertTrue(KartenAuswertung.abbauErtrag(karte, anna).isEmpty())
        val standort = KartenAuswertung.verarbeitungsStandorte(karte, anna).single()
        assertEquals(1, standort.maximaleLaeufe)
        assertEquals(mapOf(Rohstoff.LEHM to 1), standort.einsatzJeLauf)
        assertEquals(mapOf(Rohstoff.ZIEGEL to 1), standort.ertragJeLauf)
    }

    @Test
    fun geschaeftsbankWirdNurMitAnschlussKontrolliert() {
        val ohne = karte(anlage = FeldAnlage.Geschaeftsbank)
        val mit = karte(
            anlage = FeldAnlage.Geschaeftsbank,
            kanten = listOf(KantenBelegung(kante)),
        )

        assertFalse(KartenAuswertung.kontrolliertGeschaeftsbank(ohne, feld, anna))
        assertTrue(KartenAuswertung.kontrolliertGeschaeftsbank(mit, feld, anna))
    }

    @Test
    fun handelslinieHatNurBeiGenauEinemVerbundenenSpielerEinenGewalthaber() {
        val ecken = listOf(
            KartenEcke(6, 4),
            KartenEcke(8, 4),
            KartenEcke(10, 4),
            KartenEcke(12, 4),
        )
        val linien = ecken.zipWithNext(KartenKante::zwischen)
        val netz = Spielkarte(
            id = "gemeinsames-netz",
            name = "Gemeinsames Netz",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = linien
                .flatMap(::angrenzendeFelder)
                .distinct()
                .map { GelaendeFeld(it, GelaendeTyp.EBENE) },
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(ecken.first(), EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    EckBelegung(ecken.last(), EckGebaeudeTyp.HAUPTBAHNHOF, bert),
                ),
                kanten = linien.map(::KantenBelegung),
            ),
        )

        linien.forEach { linie ->
            assertEquals(setOf(anna, bert), KartenAuswertung.verbundeneSpieler(netz, linie))
            assertNull(KartenAuswertung.gewalthaber(netz, linie))
        }

        val nurAnna = netz.copy(
            belegung = netz.belegung.copy(ecken = netz.belegung.ecken.take(1)),
        )
        linien.forEach { linie ->
            assertEquals(anna, KartenAuswertung.gewalthaber(nurAnna, linie))
        }
    }

    private fun karte(
        anlage: FeldAnlage = FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG),
        ecken: List<EckBelegung> = emptyList(),
        kanten: List<KantenBelegung> = emptyList(),
    ) = Spielkarte(
        id = "auswertung",
        name = "Auswertung",
        hexagon = KartenHexagon(radius = 8),
        gelaendefelder = land,
        belegung = KartenBelegung(
            ecken = if (kanten.isEmpty()) ecken else {
                ecken + EckBelegung(
                    kante.anfang,
                    EckGebaeudeTyp.HAUPTBAHNHOF,
                    anna,
                )
            },
            kanten = kanten,
            felder = listOf(FeldBelegung(feld, anlage)),
        ),
    )
}
