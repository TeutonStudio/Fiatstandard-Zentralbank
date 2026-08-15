package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KriegserklaerungsAutorisierungTest {
    private val a = SpielerId("A")
    private val b = SpielerId("B")
    private val c = SpielerId("C")
    private val engine = StandardSpielEngine()

    @Test
    fun nurAktiverSpielerDarfImEpizugAggressorSein() {
        val epizug = epizugMitDreiSpielern()

        val erlaubt = engine.anwenden(epizug, SpielAktion.KriegErklaeren(a, b))
        val fremderAggressor = engine.anwenden(epizug, SpielAktion.KriegErklaeren(b, c))

        assertTrue(erlaubt.isSuccess)
        assertTrue(fremderAggressor.isFailure)
        assertEquals(a, erlaubt.getOrThrow().zustand.konflikte.single().aggressoren.single())
    }

    @Test
    fun naechsterSpielerKannSichNichtVorSeinemZugDurchKriegserklaerungVerteidigen() {
        val epizug = epizugMitDreiSpielern()

        val ergebnis = engine.anwenden(epizug, SpielAktion.KriegErklaeren(b, c))

        assertTrue(ergebnis.isFailure)
        assertTrue(epizug.konflikte.isEmpty())
        assertEquals(a, epizug.aktiverSpieler)
    }

    @Test
    fun auchAktiverSpielerDarfImProzugNochKeinenKriegErklaeren() {
        val start = basis()
        val prozug = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand

        val ergebnis = engine.anwenden(prozug, SpielAktion.KriegErklaeren(a, b))

        assertTrue(ergebnis.isFailure)
        assertEquals(ZugPhase.Prozug, prozug.zugStatus?.phase)
    }

    private fun epizugMitDreiSpielern(): SpielZustand {
        val prozug = engine.anwenden(basis(), SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand
        return engine.anwenden(prozug, SpielAktion.ProzugAbschliessen(1L)).getOrThrow().zustand
    }

    private fun basis() = SpielZustand(
        spieler = listOf(
            Spieler(a, "A"),
            Spieler(b, "B"),
            Spieler(c, "C"),
        ),
    )
}
