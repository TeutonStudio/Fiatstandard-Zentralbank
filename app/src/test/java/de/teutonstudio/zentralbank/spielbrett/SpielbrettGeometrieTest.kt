package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielbrettGeometrieTest {
    @Test
    fun `Hexagonradius zwei erzeugt vierundzwanzig Grunddreiecke`() {
        val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 2))

        assertEquals(24, geometrie.dreiecke.size)
        assertEquals(24, geometrie.dreiecke.map(GrundDreieck::position).distinct().size)
    }

    @Test
    fun `Grunddreiecke sind gleichseitig und haben Hoehe zwei`() {
        val dreieck = berechneSpielbrettGeometrie(KartenHexagon()).dreieck(
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
            hexagon = KartenHexagon(radius = 4),
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
            hexagon = KartenHexagon(radius = 2),
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
        val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 3))
        val dreieck = geometrie.dreiecke.first()

        val treffer = geometrie.treffer(dreieck.mittelpunkt)

        assertEquals(dreieck.position, treffer?.position)
        assertTrue(treffer?.naechsteEcke in 0..2)
    }

    @Test
    fun `Eck- und Kantenwerkzeuge verlangen einen Treffer nahe am Ziel`() {
        val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 3))
        val dreieck = geometrie.dreiecke.first()
        val mittenTreffer = requireNotNull(geometrie.treffer(dreieck.mittelpunkt))
        val kantenMitte = BrettPunkt(
            x = (dreieck.ecken[0].x + dreieck.ecken[1].x) / 2f,
            z = (dreieck.ecken[0].z + dreieck.ecken[1].z) / 2f,
        )
        val kantenTreffer = requireNotNull(geometrie.treffer(kantenMitte))
        val eckTreffer = requireNotNull(geometrie.treffer(dreieck.ecken[0]))

        assertNull(mittenTreffer.zuKartenOrt(KartenZielModus.ECKE))
        assertNull(mittenTreffer.zuKartenOrt(KartenZielModus.KANTE))
        assertTrue(kantenTreffer.zuKartenOrt(KartenZielModus.KANTE) != null)
        assertTrue(eckTreffer.zuKartenOrt(KartenZielModus.ECKE) != null)
    }

    @Test
    fun `innerer Eckpunkt bildet ein Hexagon aus sechs Dreiecken`() {
        val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 4))
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
            KartenHexagon(
                zentrum = KartenFeld(-7, -10, DreieckHaelfte.UNTEN).ecken().first(),
                radius = 4,
            ),
        )

        assertTrue(
            DreieckPosition(-7, -10, DreieckAusrichtung.UNTEN) in
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
                hexagon = KartenHexagon(),
                auflagen = listOf(
                    DreieckAuflage(
                        DreieckPosition(10, 10, DreieckAusrichtung.OBEN),
                        DreieckTyp("Ungueltig", Color.Red),
                    ),
                ),
            )
        }

        assertTrue(fehler.message.orEmpty().contains("außerhalb"))
    }

    private fun abstand(a: BrettPunkt, b: BrettPunkt): Float =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
}
