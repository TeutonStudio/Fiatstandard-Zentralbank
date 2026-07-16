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
    fun `Auflagenradius ergibt eine Dreieckshoehe von eins`() {
        assertEquals(AUFLAGEN_HOEHE, AUFLAGEN_RADIUS * 1.5f, 0.0001f)
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
