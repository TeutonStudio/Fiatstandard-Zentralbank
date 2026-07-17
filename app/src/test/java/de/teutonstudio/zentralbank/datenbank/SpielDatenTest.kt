package de.teutonstudio.zentralbank.datenbank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpielDatenTest {
    @Test
    fun neuesSpielFordertAutomatischGenerierteIdAn() {
        val daten = SpielDaten(
            warenkorb = emptyMap(),
            spieler = listOf(Spieler("Anna", emptyMap())),
            inflationswerte = Triple(2f, 1f, 3f),
        )

        assertEquals(0L, daten.spielID)
    }

    @Test
    fun warenkorbWirdStabilUndOhneNullmengenGespeichert() {
        val warenkorb = mapOf(
            Rohstoffe.HOLZ to 3,
            Rohstoffe.NAHRUNG to 2,
            Rohstoffe.STAHL to 0,
        )

        assertEquals("NAHRUNG#2/HOLZ#3", warenkorb.zuSpeicherWarenkorb())
    }

    @Test
    fun negativeWarenkorbmengeWirdNichtGespeichert() {
        assertThrows(IllegalArgumentException::class.java) {
            mapOf(Rohstoffe.HOLZ to -1).zuSpeicherWarenkorb()
        }
    }
}
