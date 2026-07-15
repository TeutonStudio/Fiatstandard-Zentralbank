package de.teutonstudio.zentralbank.schnittstelle.kategorien

import org.junit.Assert.assertEquals
import org.junit.Test

class SchuldenGraphPrognoseTest {
    @Test
    fun schreibtDenLetztenBekanntenWertBisZurLetztenGraphRundeFort() {
        assertEquals(
            listOf(10, 20, 30, 30, 30),
            listOf(10, 20, 30).bisRundeFortgeschrieben(letzteRunde = 4),
        )
    }

    @Test
    fun veraendertBereitsVollstaendigeOderLeereReihenNicht() {
        assertEquals(
            listOf(10, 20, 30),
            listOf(10, 20, 30).bisRundeFortgeschrieben(letzteRunde = 2),
        )
        assertEquals(
            emptyList<Int>(),
            emptyList<Int>().bisRundeFortgeschrieben(letzteRunde = 4),
        )
    }
}
