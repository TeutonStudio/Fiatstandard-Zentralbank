package de.teutonstudio.zentralbank.fachlogik.modell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenTopologieTest {
    @Test
    fun gemeinsameEckeHatAusAllenNachbarfeldernDieselbeKennung() {
        val feld = KartenFeld(-2, 3, DreieckHaelfte.OBEN)
        val ecke = feld.ecken().first()
        val nachbarn = angrenzendeFelder(ecke)

        assertEquals(6, nachbarn.size)
        assertTrue(nachbarn.all { ecke in it.ecken() })
    }

    @Test
    fun gemeinsameKanteHatAufBeidenFeldernDieselbeKennung() {
        val feld = KartenFeld(2, -3, DreieckHaelfte.UNTEN)
        val kante = feld.kanten()[1]
        val nachbarn = angrenzendeFelder(kante)

        assertEquals(2, nachbarn.size)
        assertTrue(nachbarn.all { kante in it.kanten() })
    }

    @Test
    fun kantenSindUnabhaengigVonDerEckenreihenfolge() {
        val a = KartenEcke(-4, 8)
        val b = KartenEcke(-2, 8)

        assertEquals(KartenKante.zwischen(a, b), KartenKante.zwischen(b, a))
    }

    @Test
    fun kantenAbstandFunktioniertAuchMitNegativenKoordinaten() {
        val start = KartenFeld(-4, -5, DreieckHaelfte.UNTEN).ecken().first()
        val eins = benachbarteEcken(start).first()
        val zwei = benachbarteEcken(eins).first { it != start && it !in benachbarteEcken(start) }

        assertEquals(1, kantenAbstand(start, eins, maximal = 2))
        assertEquals(2, kantenAbstand(start, zwei, maximal = 2))
        assertNotEquals(start, zwei)
    }
}
