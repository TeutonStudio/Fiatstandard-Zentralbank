package de.teutonstudio.zentralbank.schnittstelle.kategorien

import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import org.junit.Assert.assertEquals
import org.junit.Test

class SpielErstellenStartrohstoffeTest {
    @Test
    fun gemeinsameAuswahlWirdAllenSpielernGleichZugeordnet() {
        val gemeinsam = mapOf(
            Rohstoffe.NAHRUNG to 4,
            Rohstoffe.KOHLE to 3,
            Rohstoffe.STAHL to 0,
        )

        val zugeordnet = startRohstoffeZuordnen(
            spielerNamen = listOf("Anna", "Bert"),
            unterschiedlich = false,
            proSpieler = emptyList(),
            gemeinsam = gemeinsam,
        )

        val erwartet = mapOf(Rohstoffe.NAHRUNG to 4, Rohstoffe.KOHLE to 3)
        assertEquals(erwartet, zugeordnet["Anna"])
        assertEquals(erwartet, zugeordnet["Bert"])
    }

    @Test
    fun unterschiedlicheAuswahlBleibtSpielerbezogen() {
        val zugeordnet = startRohstoffeZuordnen(
            spielerNamen = listOf("Anna", "Bert"),
            unterschiedlich = true,
            proSpieler = listOf(
                mapOf(Rohstoffe.NAHRUNG to 2),
                mapOf(Rohstoffe.SCHWERÖL to 1),
            ),
            gemeinsam = emptyMap(),
        )

        assertEquals(mapOf(Rohstoffe.NAHRUNG to 2), zugeordnet["Anna"])
        assertEquals(mapOf(Rohstoffe.SCHWERÖL to 1), zugeordnet["Bert"])
    }
}
