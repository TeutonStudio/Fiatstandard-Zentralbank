package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielbrettGeometrieTest {
    @Test
    fun `jede Zelle erzeugt zwei Grunddreiecke`() {
        val geometrie = berechneSpielbrettGeometrie(zeilen = 3, spalten = 4)

        assertEquals(24, geometrie.dreiecke.size)
        assertEquals(24, geometrie.dreiecke.map(GrundDreieck::position).distinct().size)
    }

    @Test
    fun `Grunddreiecke sind gleichseitig und haben Hoehe zwei`() {
        val dreieck = berechneSpielbrettGeometrie(1, 1).dreieck(
            DreieckPosition(0, 0, DreieckAusrichtung.UNTEN),
        )
        val kanten = listOf(
            abstand(dreieck.ecken[0], dreieck.ecken[1]),
            abstand(dreieck.ecken[1], dreieck.ecken[2]),
            abstand(dreieck.ecken[2], dreieck.ecken[0]),
        )

        assertTrue(kanten.all { kante -> abs(kante - kanten.first()) < 0.0001f })
        val zWerte = dreieck.ecken.map(BrettPunkt::z)
        assertEquals(GRUNDDREIECK_HOEHE, zWerte.max() - zWerte.min(), 0.0001f)
    }

    @Test
    fun `Auflagenradius deckt das ganze Grunddreieck ab`() {
        assertEquals(GRUNDDREIECK_HOEHE, AUFLAGEN_RADIUS * 1.5f, 0.0001f)
    }

    @Test
    fun `mehrere unterschiedliche Auflagen werden akzeptiert`() {
        val gold = DreieckTyp("Gold", Color.Yellow)
        val wasser = DreieckTyp("Wasser", Color.Blue)

        val modell = Spielbrett3DModell(
            zeilen = 2,
            spalten = 2,
            auflagen = listOf(
                DreieckAuflage(DreieckPosition(0, 0, DreieckAusrichtung.OBEN), gold),
                DreieckAuflage(DreieckPosition(1, 1, DreieckAusrichtung.UNTEN), wasser),
            ),
        )

        assertEquals(listOf(gold, wasser), modell.auflagen.map(DreieckAuflage::typ))
    }

    @Test
    fun `Land und Spezialauflage duerfen dasselbe Dreieck belegen`() {
        val position = DreieckPosition(0, 0, DreieckAusrichtung.OBEN)

        val modell = Spielbrett3DModell(
            zeilen = 1,
            spalten = 1,
            auflagen = listOf(
                DreieckAuflage(position, DreieckTyp("Land", Color.Green)),
                DreieckAuflage(
                    position,
                    DreieckTyp("Spezial", Color.Magenta),
                    AuflagenEbene.SPEZIAL,
                ),
            ),
        )

        assertEquals(2, modell.auflagen.size)
    }

    @Test
    fun `Treffer findet Dreieck und naechste Ecke`() {
        val geometrie = berechneSpielbrettGeometrie(3, 3)
        val dreieck = geometrie.dreiecke.first()

        val treffer = geometrie.treffer(dreieck.mittelpunkt)

        assertEquals(dreieck.position, treffer?.position)
        assertTrue(treffer?.naechsteEcke in 0..2)
    }

    @Test
    fun `innerer Eckpunkt bildet ein Hexagon aus sechs Dreiecken`() {
        val geometrie = berechneSpielbrettGeometrie(4, 4)
        val innererTreffer = geometrie.dreiecke.asSequence()
            .flatMap { dreieck ->
                dreieck.ecken.indices.asSequence().map { ecke ->
                    DreieckTreffer(dreieck.position, ecke)
                }
            }
            .first { treffer -> geometrie.hexagonUm(treffer).size == 6 }

        assertEquals(6, geometrie.hexagonUm(innererTreffer).distinct().size)
    }

    @Test
    fun `Geometrie behaelt negative Kartenkoordinaten bei`() {
        val geometrie = berechneSpielbrettGeometrie(
            zeilen = 3,
            spalten = 4,
            startZeile = -8,
            startSpalte = -12,
        )

        assertTrue(
            DreieckPosition(-8, -12, DreieckAusrichtung.UNTEN) in
                geometrie.dreiecke.map(GrundDreieck::position),
        )
        assertTrue(
            DreieckPosition(-6, -9, DreieckAusrichtung.OBEN) in
                geometrie.dreiecke.map(GrundDreieck::position),
        )
    }

    @Test
    fun `Positionen ausserhalb des Bretts werden abgelehnt`() {
        val fehler = assertThrows(IllegalArgumentException::class.java) {
            Spielbrett3DModell(
                zeilen = 1,
                spalten = 1,
                auflagen = listOf(
                    DreieckAuflage(
                        DreieckPosition(1, 0, DreieckAusrichtung.OBEN),
                        DreieckTyp("Ungueltig", Color.Red),
                    ),
                ),
            )
        }

        assertTrue(fehler.message.orEmpty().contains("ausserhalb"))
    }

    private fun abstand(a: BrettPunkt, b: BrettPunkt): Float =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
}
