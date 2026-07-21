package de.teutonstudio.zentralbank.spielbrett

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielbrettTageszeitTest {
    @Test
    fun siebenSpielerTeilenDenSpieltagLueckenlosVonSechsBisAchtzehnUhr() {
        val fenster = (0 until 7).map { index -> spielzugZeitfenster(index, 7) }

        assertEquals("06:00–07:43", fenster.first().text)
        assertEquals("16:17–18:00", fenster.last().text)
        assertEquals(6f, fenster.first().beginnStunde, 0.0001f)
        assertEquals(18f, fenster.last().endeStunde, 0.0001f)
        fenster.zipWithNext().forEach { (vorher, nachher) ->
            assertEquals(vorher.endeStunde, nachher.beginnStunde, 0.0001f)
        }
    }

    @Test
    fun nachtAnimationLaeuftVonAbendUeberMitternachtZumMorgen() {
        val abend = HimmelsDarstellung.fuerNachtFortschritt(0f)
        val mitternacht = HimmelsDarstellung.fuerNachtFortschritt(0.5f)
        val morgen = HimmelsDarstellung.fuerNachtFortschritt(1f)

        assertEquals("18:00", abend.uhrzeitText)
        assertEquals("00:00", mitternacht.uhrzeitText)
        assertEquals("06:00", morgen.uhrzeitText)
        assertEquals(0f, abend.sterneSichtbarkeit, 0f)
        assertEquals(1f, mitternacht.sterneSichtbarkeit, 0.0001f)
        assertEquals(0f, morgen.sterneSichtbarkeit, 0f)
        assertTrue(mitternacht.mondSichtbarkeit > 0.99f)
        assertTrue(mitternacht.lichtHoeheGrad > abend.lichtHoeheGrad)
    }

    @Test
    fun zugstartBestimmtSonnenwinkelUndUhrzeit() {
        val erstesFenster = spielzugZeitfenster(0, 7)
        val mittleresFenster = spielzugZeitfenster(3, 7)

        val morgens = HimmelsDarstellung.fuerSpielzug(erstesFenster)
        val mittags = HimmelsDarstellung.fuerSpielzug(mittleresFenster)

        assertEquals("06:00", morgens.uhrzeitText)
        assertTrue(mittags.lichtAzimutGrad > morgens.lichtAzimutGrad)
        assertTrue(mittags.lichtHoeheGrad > morgens.lichtHoeheGrad)
        assertEquals(0f, mittags.nachtAnteil, 0f)
    }
}
