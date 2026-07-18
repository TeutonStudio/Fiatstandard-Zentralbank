package de.teutonstudio.zentralbank.fachlogik.ablauf

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielAblaufTest {
    private val annaId = SpielerId("Anna")
    private val startzustand = SpielZustand(
        spieler = listOf(
            Spieler(
                id = annaId,
                name = "Anna",
                geldkonto = Geld.mark(1),
            ),
        ),
    )
    private val prozugBegonnen = SpielEreignis.ProzugBegonnen(1L)

    @Test
    fun anwendenAktualisiertDenGecachtenZustandUndEreignisverlauf() {
        val ablauf = SpielAblauf(startzustand)

        val folgezustand = ablauf.ereignisAnwenden(prozugBegonnen).getOrThrow()

        assertSame(folgezustand, ablauf.zustand)
        assertTrue(ablauf.zustand.zugStatus?.prozug?.begonnen == true)
        assertEquals(listOf(prozugBegonnen), ablauf.ereignisVerlauf.angewandteEreignisse)
        assertTrue(ablauf.ereignisVerlauf.wiederholbareEreignisse.isEmpty())
    }

    @Test
    fun ladenRueckgaengigUndWiederholenRekonstruierenKonsistent() {
        val ablauf = SpielAblauf(startzustand, listOf(prozugBegonnen))
        assertTrue(ablauf.zustand.zugStatus?.prozug?.begonnen == true)

        ablauf.rueckgaengig()
        assertFalse(ablauf.zustand.zugStatus?.prozug?.begonnen == true)
        assertEquals(listOf(prozugBegonnen), ablauf.ereignisVerlauf.wiederholbareEreignisse)

        ablauf.wiederholen().getOrThrow()
        assertTrue(ablauf.zustand.zugStatus?.prozug?.begonnen == true)
        assertTrue(ablauf.integritaetPruefen().isSuccess)
    }

    @Test
    fun neuesEreignisVerwirftWiederholbarenVerlauf() {
        val ablauf = SpielAblauf(startzustand, listOf(prozugBegonnen))
        ablauf.rueckgaengig()

        ablauf.ereignisAnwenden(
            SpielEreignis.WarenkorbGeaendert(emptyMap()),
        ).getOrThrow()

        assertTrue(ablauf.ereignisVerlauf.wiederholbareEreignisse.isEmpty())
        assertTrue(ablauf.wiederholen().isFailure)
    }
}
