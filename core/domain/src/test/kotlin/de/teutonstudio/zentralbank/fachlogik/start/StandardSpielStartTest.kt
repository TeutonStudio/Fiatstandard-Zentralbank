package de.teutonstudio.zentralbank.fachlogik.start

import de.teutonstudio.zentralbank.fachlogik.auswertung.ZustandsInvarianten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardSpielStartTest {
    @Test
    fun `startzustand ist deterministisch und gueltig`() {
        val namen = listOf("Anna", "Bert", "Carla")
        val links = erstelleStandardSpiel(namen, seed = 42)
        val rechts = erstelleStandardSpiel(namen, seed = 42)

        assertEquals(links, rechts)
        assertEquals(3, links.karte?.belegung?.ecken?.size)
        assertTrue(ZustandsInvarianten.pruefe(links).isSuccess)
    }
}
