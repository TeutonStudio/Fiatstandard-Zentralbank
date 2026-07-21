package de.teutonstudio.zentralbank.schnittstelle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagrammLegendenStatusTest {
    @Test
    fun umschalten_blendetNurDenGewaehltenEintragAusUndWiederEin() {
        val status = DiagrammLegendenStatus(listOf("a", "b", "c"))

        status.umschalten("b")

        assertTrue(status.istSichtbar("a"))
        assertFalse(status.istSichtbar("b"))
        assertTrue(status.istSichtbar("c"))

        status.umschalten("b")

        assertTrue(status.istSichtbar("b"))
    }

    @Test
    fun nurAnzeigen_blendetAlleAnderenEintraegeAus() {
        val status = DiagrammLegendenStatus(listOf("a", "b", "c"))

        status.nurAnzeigen("b")

        assertEquals(setOf("b"), status.sichtbareIds)
    }

    @Test
    fun sichtbarkeitSetzen_schaltetEineGanzeGruppeGemeinsam() {
        val status = DiagrammLegendenStatus(listOf("a", "b", "c", "d"))

        status.sichtbarkeitSetzen(listOf("b", "c"), sichtbar = false)

        assertEquals(setOf("a", "d"), status.sichtbareIds)

        status.sichtbarkeitSetzen(listOf("b", "c"), sichtbar = true)

        assertEquals(setOf("a", "b", "c", "d"), status.sichtbareIds)
    }
}
