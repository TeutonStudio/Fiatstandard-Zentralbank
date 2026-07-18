package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import de.teutonstudio.zentralbank.fachlogik.modell.enthaeltFeld
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
    fun weitEntferntesGelaendeVergroessertDenHexagonradiusAutomatisch() {
        val weitWeg = DreieckTreffer(DreieckPosition(30, -40, DreieckAusrichtung.OBEN), 0)

        val karte = leereKarte().wendeWerkzeugAn(weitWeg, KartenWerkzeug.EBENE)

        assertTrue(karte.hexagon.radius > 30)
        assertTrue(karte.enthaeltFeld(weitWeg.position.zuKartenFeld()))
    }

    @Test
    fun entfernenDesAeusserstenFeldesVerkleinertDenGespeichertenRadius() {
        val weitWeg = DreieckTreffer(DreieckPosition(-25, 35, DreieckAusrichtung.UNTEN), 0)
        val gross = leereKarte().wendeWerkzeugAn(weitWeg, KartenWerkzeug.WALD)

        val wiederLeer = gross.wendeWerkzeugAn(weitWeg, KartenWerkzeug.WASSER)

        assertTrue(gross.hexagon.radius > 1)
        assertEquals(1, wiederLeer.hexagon.radius)
    }

    @Test
    fun teichWerkzeugErzeugtEinHexagonAusSechsEbenenMitGemeinsamerMitte() {
        val treffer = DreieckTreffer(
            DreieckPosition(2, 2, DreieckAusrichtung.UNTEN),
            naechsteEcke = 0,
        )

        val bearbeitet = leereKarte().wendeWerkzeugAn(treffer, KartenWerkzeug.TEICH)
        val teich = bearbeitet.spezialfelder.single()

        assertEquals(SpezialfeldTyp.TEICH, teich.typ)
        assertEquals(6, teich.positionen.distinct().size)
        assertTrue(teich.positionen.all { position -> position in bearbeitet.landNachPosition })
        assertTrue(teich.positionen.all { position -> bearbeitet.enthaeltFeld(position) })
        assertTrue(
            teich.positionen.all { position ->
                bearbeitet.landNachPosition[position] == GelaendeTyp.EBENE
            },
        )
    }

    @Test
    fun spezialfeldEntfernenLaesstDieSechsGelaendedreieckeStehen() {
        val treffer = DreieckTreffer(
            DreieckPosition(2, 2, DreieckAusrichtung.UNTEN),
            naechsteEcke = 0,
        )
        val mitTeich = leereKarte().wendeWerkzeugAn(treffer, KartenWerkzeug.TEICH)

        val entfernt = mitTeich.wendeWerkzeugAn(treffer, KartenWerkzeug.SPEZIAL_ENTFERNEN)

        assertTrue(entfernt.spezialfelder.isEmpty())
        assertEquals(6, entfernt.gelaendefelder.size)
    }

    @Test
    fun wasserAufEinemTeichdreieckEntferntAuchDasSpezialfeld() {
        val treffer = DreieckTreffer(
            DreieckPosition(2, 2, DreieckAusrichtung.UNTEN),
            naechsteEcke = 0,
        )
        val mitTeich = leereKarte().wendeWerkzeugAn(treffer, KartenWerkzeug.TEICH)
        val teichDreieck = mitTeich.spezialfelder.single().positionen.first()

        val bearbeitet = mitTeich.wendeWerkzeugAn(
            DreieckTreffer(teichDreieck.zu3DPosition(), naechsteEcke = 0),
            KartenWerkzeug.WASSER,
        )

        assertTrue(bearbeitet.spezialfelder.isEmpty())
        assertEquals(5, bearbeitet.gelaendefelder.size)
    }

    private fun leereKarte() = KartenVorlage(
        id = "testkarte",
        name = "Testkarte",
    )
}
