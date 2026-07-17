package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenEditorTest {
    @Test
    fun gelaendewerkzeugSpeichertNurDasBearbeiteteDreieck() {
        val karte = leereKarte()
        val treffer = DreieckTreffer(
            DreieckPosition(1, 1, DreieckAusrichtung.UNTEN),
            naechsteEcke = 0,
        )

        val bearbeitet = karte.wendeWerkzeugAn(treffer, KartenWerkzeug.WALD)

        assertEquals(1, bearbeitet.gelaendefelder.size)
        assertEquals(GelaendeTyp.WALD, bearbeitet.gelaendefelder.single().gelaende)
    }

    @Test
    fun wasserEntferntNurDasGetroffeneGelaendefeld() {
        val trefferA = DreieckTreffer(DreieckPosition(1, 1, DreieckAusrichtung.UNTEN), 0)
        val trefferB = DreieckTreffer(DreieckPosition(1, 1, DreieckAusrichtung.OBEN), 0)
        val karte = leereKarte()
            .wendeWerkzeugAn(trefferA, KartenWerkzeug.WALD)
            .wendeWerkzeugAn(trefferB, KartenWerkzeug.GEBIRGE)

        val bearbeitet = karte.wendeWerkzeugAn(trefferA, KartenWerkzeug.WASSER)

        assertEquals(1, bearbeitet.gelaendefelder.size)
        assertEquals(GelaendeTyp.GEBIRGE, bearbeitet.gelaendefelder.single().gelaende)
    }

    @Test
    fun verkleinernMeldetUndEntferntFelderAusserhalb() {
        val karte = leereKarte()
            .wendeWerkzeugAn(
                DreieckTreffer(DreieckPosition(3, 3, DreieckAusrichtung.OBEN), 0),
                KartenWerkzeug.EBENE,
            )

        assertEquals(1, karte.anzahlEntfallenderFelder(2, 2))
        assertTrue(karte.mitAusdehnung(2, 2).gelaendefelder.isEmpty())
    }

    @Test
    fun bearbeitungsbereichWaechstOhneLimitInAlleVierRichtungen() {
        val karte = leereKarte()
            .erweitert(KartenRichtung.NORDEN, 80)
            .erweitert(KartenRichtung.SUEDEN, 90)
            .erweitert(KartenRichtung.WESTEN, 100)
            .erweitert(KartenRichtung.OSTEN, 110)

        assertEquals(-80, karte.startZeile)
        assertEquals(-100, karte.startSpalte)
        assertEquals(174, karte.zeilen)
        assertEquals(214, karte.spalten)
        assertEquals(94L, karte.endeZeileExklusiv)
        assertEquals(114L, karte.endeSpalteExklusiv)
    }

    private fun leereKarte() = KartenVorlage(
        id = "testkarte",
        name = "Testkarte",
        zeilen = 4,
        spalten = 4,
    )
}
