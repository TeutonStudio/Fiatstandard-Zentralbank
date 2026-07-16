package de.teutonstudio.zentralbank.fachlogik.ablauf

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
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
    private val holzEinnahme = SpielEreignis.RohstoffEinnahme(
        spieler = annaId,
        mengen = mapOf(Rohstoff.HOLZ to 2),
    )

    @Test
    fun anwendenAktualisiertDenGecachtenZustandUndEreignisverlauf() {
        val ablauf = SpielAblauf(startzustand)

        val folgezustand = ablauf.ereignisAnwenden(holzEinnahme).getOrThrow()

        assertSame(folgezustand, ablauf.zustand)
        assertEquals(2, ablauf.zustand.spieler.single().rohstoffe[Rohstoff.HOLZ])
        assertEquals(listOf(holzEinnahme), ablauf.ereignisVerlauf.angewandteEreignisse)
        assertTrue(ablauf.ereignisVerlauf.wiederholbareEreignisse.isEmpty())
    }

    @Test
    fun ladenRueckgaengigUndWiederholenRekonstruierenKonsistent() {
        val ablauf = SpielAblauf(startzustand, listOf(holzEinnahme))
        assertEquals(2, ablauf.zustand.spieler.single().rohstoffe[Rohstoff.HOLZ])

        ablauf.rueckgaengig()
        assertEquals(null, ablauf.zustand.spieler.single().rohstoffe[Rohstoff.HOLZ])
        assertEquals(listOf(holzEinnahme), ablauf.ereignisVerlauf.wiederholbareEreignisse)

        ablauf.wiederholen().getOrThrow()
        assertEquals(2, ablauf.zustand.spieler.single().rohstoffe[Rohstoff.HOLZ])
        assertTrue(ablauf.integritaetPruefen().isSuccess)
    }

    @Test
    fun neuesEreignisVerwirftWiederholbarenVerlauf() {
        val ablauf = SpielAblauf(startzustand, listOf(holzEinnahme))
        ablauf.rueckgaengig()

        ablauf.ereignisAnwenden(
            SpielEreignis.RohstoffEinnahme(
                spieler = annaId,
                mengen = mapOf(Rohstoff.KOHLE to 1),
            ),
        ).getOrThrow()

        assertTrue(ablauf.ereignisVerlauf.wiederholbareEreignisse.isEmpty())
        assertTrue(ablauf.wiederholen().isFailure)
    }
}
