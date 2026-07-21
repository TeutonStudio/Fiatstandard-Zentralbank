package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardSpielEngineTest {
    private val anna = SpielerId("Anna")
    private val start = SpielZustand(
        spieler = listOf(Spieler(anna, "Anna", geldkonto = Geld.mark(10))),
    )
    private val engine = StandardSpielEngine()

    @Test
    fun ungueltigeAktionVeraendertDenEingangszustandNicht() {
        val vorher = start.copy()

        val ergebnis = engine.anwenden(start, SpielAktion.ProzugAbschliessen(999L))

        assertTrue(ergebnis.isFailure)
        assertEquals(vorher, start)
    }

    @Test
    fun erlaubteAktionenFuehrenMitDerselbenEngineDurchEinenZug() {
        val begonnen = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow()
        val abschliessen = engine.erlaubteAktionen(begonnen.zustand, anna).single()
        val epizug = engine.anwenden(begonnen.zustand, abschliessen).getOrThrow()

        assertEquals(listOf(SpielAktion.ZugBeenden), engine.erlaubteAktionen(epizug.zustand, anna))
        assertEquals(1, begonnen.ereignisse.size)
    }

    @Test
    fun gleicherSeedLiefertGleicheAuswahlfolge() {
        val links = SeedZufallsquelle(42)
        val rechts = SeedZufallsquelle(42)

        assertEquals(
            List(100) { links.naechsteGanzzahl(17) },
            List(100) { rechts.naechsteGanzzahl(17) },
        )
    }
}
