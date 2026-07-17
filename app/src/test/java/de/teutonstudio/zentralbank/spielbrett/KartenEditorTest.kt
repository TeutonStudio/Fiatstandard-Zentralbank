package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenEditorTest {
    @Test
    fun `Gelaendewerkzeug speichert nur das bearbeitete Landdreieck`() {
        val karte = leereKarte()
        val treffer = DreieckTreffer(
            DreieckPosition(1, 1, DreieckAusrichtung.UNTEN),
            naechsteEcke = 0,
        )

        val bearbeitet = karte.wendeWerkzeugAn(treffer, KartenWerkzeug.WALD)

        assertEquals(1, bearbeitet.landfelder.size)
        assertEquals(GelaendeTyp.WALD, bearbeitet.landfelder.single().gelaende)
        assertTrue(bearbeitet.spezialfelder.isEmpty())
    }

    @Test
    fun `Spezialwerkzeug legt genau sechs Landdreiecke als Gruppe an`() {
        val karte = leereKarte()
        val treffer = innererTreffer(karte)

        val bearbeitet = karte.wendeWerkzeugAn(
            treffer = treffer,
            werkzeug = KartenWerkzeug.STADT,
            neueSpezialId = { "stadt-1" },
        )

        assertEquals(6, bearbeitet.landfelder.size)
        assertEquals(1, bearbeitet.spezialfelder.size)
        assertEquals(SpezialfeldTyp.STADT, bearbeitet.spezialfelder.single().typ)
        assertEquals(6, bearbeitet.spezialfelder.single().positionen.distinct().size)
    }

    @Test
    fun `Wasser entfernt Land und das darauf liegende Spezialfeld`() {
        val karte = leereKarte().wendeWerkzeugAn(
            treffer = innererTreffer(leereKarte()),
            werkzeug = KartenWerkzeug.HAFEN,
            neueSpezialId = { "hafen-1" },
        )
        val position = karte.spezialfelder.single().positionen.first()
        val treffer = DreieckTreffer(position.zu3DPosition(), 0)

        val bearbeitet = karte.wendeWerkzeugAn(treffer, KartenWerkzeug.WASSER)

        assertFalse(position in bearbeitet.landNachPosition)
        assertTrue(bearbeitet.spezialfelder.isEmpty())
    }

    @Test
    fun `Verkleinern entfernt Felder ausserhalb und unvollstaendige Spezialgruppen`() {
        val ausgang = leereKarte().wendeWerkzeugAn(
            treffer = innererTreffer(leereKarte()),
            werkzeug = KartenWerkzeug.HEXAGON,
            neueSpezialId = { "hex-1" },
        )

        val verkleinert = ausgang.mitAusdehnung(1, 1)

        assertTrue(verkleinert.landfelder.all { it.position.zeile == 0 && it.position.spalte == 0 })
        assertTrue(verkleinert.spezialfelder.isEmpty())
    }

    private fun leereKarte() = Spielkarte(
        id = "testkarte",
        name = "Testkarte",
        zeilen = 4,
        spalten = 4,
    )

    private fun innererTreffer(karte: Spielkarte): DreieckTreffer {
        val geometrie = berechneSpielbrettGeometrie(karte.zeilen, karte.spalten)
        return geometrie.dreiecke.asSequence()
            .flatMap { dreieck ->
                dreieck.ecken.indices.asSequence().map { ecke ->
                    DreieckTreffer(dreieck.position, ecke)
                }
            }
            .first { treffer -> geometrie.hexagonUm(treffer).size == 6 }
    }
}
