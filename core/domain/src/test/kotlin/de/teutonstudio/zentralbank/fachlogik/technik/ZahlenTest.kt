package de.teutonstudio.zentralbank.fachlogik.technik

import org.junit.Assert.assertEquals
import org.junit.Test

class ZahlenTest {
    @Test
    fun `bodenDivision entspricht mathematischem Abrunden`() {
        assertEquals(-2, bodenDivision(-3, 2))
        assertEquals(-2, bodenDivision(3, -2))
        assertEquals(1, bodenDivision(3, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subtraktion erkennt Int MinValue Ueberlauf`() {
        subtrahiereExakt(0, Int.MIN_VALUE)
    }
}
