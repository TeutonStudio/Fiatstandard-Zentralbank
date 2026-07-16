package de.teutonstudio.zentralbank.fachlogik.modell

import org.junit.Assert.assertEquals
import org.junit.Test

class GeldTest {
    @Test
    fun markWirdInternAlsCentGespeichert() {
        assertEquals(100L, Geld.mark(1).cent)
        assertEquals(12345L, Geld.cent(12345).cent)
    }

    @Test
    fun rechnetCentbasiert() {
        val start = Geld.mark(10)
        val gebuehr = Geld.cent(25)

        assertEquals(Geld.cent(1025), start + gebuehr)
        assertEquals(Geld.cent(975), start - gebuehr)
        assertEquals(Geld.mark(30), start * 3)
    }

    @Test
    fun formatiertAlsMarkMitZweiCentstellen() {
        assertEquals("12,34 ℳ", Geld.cent(1234).zuMarkString())
        assertEquals("-12,34 ℳ", Geld.cent(-1234).zuMarkString())
    }
}
