package de.teutonstudio.zentralbank.web

import de.teutonstudio.zentralbank.anwendung.SpielSitzung
import de.teutonstudio.zentralbank.anwendung.stabilerHash
import de.teutonstudio.zentralbank.fachlogik.start.erstelleStandardSpiel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WebEngineParityTest {
    @Test
    fun browserVerwendetDeterministischenGemeinsamenRegelkern() {
        val startA = erstelleStandardSpiel(listOf("Anna", "Bert", "Carla"), seed = 42)
        val startB = erstelleStandardSpiel(listOf("Anna", "Bert", "Carla"), seed = 42)
        assertEquals(startA.stabilerHash(), startB.stabilerHash())

        val sitzung = SpielSitzung(startA)
        val spieler = startA.aktiverSpieler ?: error("Aktiver Spieler fehlt.")
        val aktionen = sitzung.erlaubteAktionen(spieler)
        assertTrue(aktionen.isNotEmpty())
        sitzung.aktionAnwenden(aktionen.first()).getOrThrow()
        assertNotEquals(startA.stabilerHash(), sitzung.zustand.stabilerHash())
    }
}
