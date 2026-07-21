package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AktionsAuswertungTest {
    private val anna = SpielerId("Anna")
    private val bert = SpielerId("Bert")
    private val engine = StandardSpielEngine()

    @Test
    fun aktionsraumIstDeterministischSortiertUndNurGueltig() {
        val start = SpielZustand(
            spieler = listOf(
                Spieler(
                    anna,
                    "Anna",
                    rohstoffe = mapOf(Rohstoff.HOLZ to 2, Rohstoff.KOHLE to 1),
                    geldkonto = Geld.mark(100),
                ),
                Spieler(bert, "Bert", geldkonto = Geld.mark(100)),
            ),
            marktpreise = mapOf(Rohstoff.HOLZ to Geld.mark(4), Rohstoff.KOHLE to Geld.mark(3)),
        )
        val begonnen = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand

        val links = AktionsAuswertung.erlaubteAktionen(begonnen, anna)
        val rechts = AktionsAuswertung.erlaubteAktionen(begonnen, anna)

        assertEquals(links, rechts)
        assertFalse(links.aktionen.isEmpty())
        assertEquals(
            links.aktionen.sortedBy(AktionsAuswertung::aktionsSchluessel),
            links.aktionen,
        )
        assertTrue(links.aktionen.all { engine.pruefe(begonnen, it).isSuccess })
        assertTrue(links.aktionen.any { it is SpielAktion.HandelsangebotErstellen })
        assertTrue(links.aktionen.any { it is SpielAktion.ProzugAbschliessen })
    }

    @Test
    fun nichtAktiverUndAusgeschiedenerSpielerErhaltenKeineAktionen() {
        val start = SpielZustand(
            spieler = listOf(Spieler(anna, "Anna"), Spieler(bert, "Bert")),
        )

        assertTrue(AktionsAuswertung.erlaubteAktionen(start, bert).aktionen.isEmpty())
        val nachAufgabe = engine.anwenden(start, SpielAktion.Aufgeben(anna)).getOrThrow().zustand
        assertTrue(AktionsAuswertung.erlaubteAktionen(nachAufgabe, anna).aktionen.isEmpty())
    }
}
