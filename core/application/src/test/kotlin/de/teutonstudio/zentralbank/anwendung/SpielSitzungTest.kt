package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielSitzungTest {
    private val anna = SpielerId("Anna")
    private val start = SpielZustand(spieler = listOf(Spieler(anna, "Anna")))

    @Test
    fun atomareAktionenWerdenVollstaendigOderGarNichtUebernommen() {
        val sitzung = SpielSitzung(start)

        val ergebnis = sitzung.aktionenAtomarAnwenden(
            listOf(
                SpielAktion.ProzugBeginnen(1L),
                SpielAktion.ProzugAbschliessen(999L),
            ),
        )

        assertTrue(ergebnis.isFailure)
        assertEquals(start, sitzung.zustand)
        assertTrue(sitzung.fachEreignisse.isEmpty())
    }

    @Test
    fun gleicherStartzustandUndGleicheAktionenErgebenDenselbenHash() {
        fun ausfuehren(): String {
            val sitzung = SpielSitzung(start)
            sitzung.aktionAnwenden(SpielAktion.ProzugBeginnen(1L)).getOrThrow()
            sitzung.aktionAnwenden(SpielAktion.ProzugAbschliessen(1L)).getOrThrow()
            sitzung.aktionAnwenden(SpielAktion.ZugBeenden).getOrThrow()
            return sitzung.zustand.stabilerHash()
        }

        assertEquals(ausfuehren(), ausfuehren())
    }

    @Test
    fun undoUndRedoRekonstruierenAusDemEreignisverlauf() {
        val sitzung = SpielSitzung(start)
        sitzung.aktionAnwenden(SpielAktion.ProzugBeginnen(1L)).getOrThrow()
        val begonnen = sitzung.zustand

        sitzung.rueckgaengig()
        assertEquals(start, sitzung.zustand)
        sitzung.wiederholen().getOrThrow()

        assertEquals(begonnen, sitzung.zustand)
    }
}
