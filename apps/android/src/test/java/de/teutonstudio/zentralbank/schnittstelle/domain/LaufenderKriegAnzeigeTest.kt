package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsSeite
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsStatus
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaufenderKriegAnzeigeTest {
    private val a = SpielerId("a")
    private val b = SpielerId("b")
    private val c = SpielerId("c")
    private val d = SpielerId("d")

    @Test
    fun mehrparteienkriegWirdProKriegGenauEinmalMitGegnerseiteAngezeigt() {
        val zustand = basis().copy(
            konflikte = setOf(
                Konflikt(
                    spielerA = a,
                    spielerB = c,
                    id = KriegId("krieg-mehrparteien"),
                    aggressoren = setOf(a, b),
                    verteidiger = setOf(c, d),
                    begonnenInRunde = 4,
                ),
            ),
        )

        val anzeige = zustand.laufendeKriegeFuer("A").single()

        assertEquals(KriegsSeite.AGGRESSOREN, anzeige.eigeneSeite)
        assertEquals(listOf("C", "D"), anzeige.gegnerNamen)
        assertEquals(4, anzeige.begonnenInRunde)
        assertTrue(anzeige.betrifftGeschaeftspartner("C"))
    }

    @Test
    fun friedenAngebotenBleibtSichtbarBeendeterKriegNicht() {
        val zustand = basis().copy(
            konflikte = setOf(
                Konflikt(
                    a,
                    b,
                    KriegId("offen"),
                    status = KriegsStatus.FRIEDEN_ANGEBOTEN,
                ),
                Konflikt(
                    a,
                    c,
                    KriegId("beendet"),
                    status = KriegsStatus.BEENDET,
                ),
            ),
        )

        val anzeigen = zustand.laufendeKriegeFuer("A")

        assertEquals(listOf("offen"), anzeigen.map { it.id.wert })
        assertTrue(anzeigen.single().vorgangText.contains("Frieden angeboten"))
    }

    private fun basis() = SpielZustand(
        spieler = listOf(
            Spieler(a, "A"),
            Spieler(b, "B"),
            Spieler(c, "C"),
            Spieler(d, "D"),
        ),
    )
}
