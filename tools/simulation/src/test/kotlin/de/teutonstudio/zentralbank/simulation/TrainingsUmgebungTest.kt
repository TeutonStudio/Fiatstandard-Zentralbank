package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingsUmgebungTest {
    private val szenario = KleineWirtschaftsBaseline()

    @Test
    fun resetUndStepSindMitGleichemSeedIdentisch() {
        val links = StandardTrainingsUmgebung(maximaleEntscheidungen = 20)
        val rechts = StandardTrainingsUmgebung(maximaleEntscheidungen = 20)
        val linksStart = links.reset(szenario, 42)
        val rechtsStart = rechts.reset(szenario, 42)

        assertEquals(linksStart, rechtsStart)
        val aktion = linksStart.aktionsRaum.aktionen
            .first { it is SpielAktion.ProzugAbschliessen }
        val linksSchritt = links.step(aktion)
        val rechtsSchritt = rechts.step(aktion)

        assertEquals(links.zustand, rechts.zustand)
        assertEquals(linksSchritt, rechtsSchritt)
    }

    @Test
    fun ungueltigeAktionWirdAbgelehntUndParalleleInstanzBleibtUnberuehrt() {
        val links = StandardTrainingsUmgebung(maximaleEntscheidungen = 20)
        val rechts = StandardTrainingsUmgebung(maximaleEntscheidungen = 20)
        val punkt = links.reset(szenario, 7)
        rechts.reset(szenario, 7)
        val rechtsVorher = rechts.zustand

        val fremdeAufgabe = SpielAktion.Aufgeben(SpielerId("nicht-aktiv"))
        assertTrue(runCatching { links.step(fremdeAufgabe) }.isFailure)
        links.step(punkt.aktionsRaum.aktionen.first { it is SpielAktion.ProzugAbschliessen })

        assertEquals(rechtsVorher, rechts.zustand)
        assertNotEquals(links.zustand, rechts.zustand)
    }

    @Test
    fun truncationIstKeinFachlichesSpielende() {
        val umgebung = StandardTrainingsUmgebung(maximaleEntscheidungen = 1)
        val punkt = umgebung.reset(szenario, 1)
        val uebergang = umgebung.step(
            punkt.aktionsRaum.aktionen.first { it is SpielAktion.ProzugAbschliessen },
        )

        assertTrue(uebergang.truncated)
        assertFalse(uebergang.terminated)
        assertNull(uebergang.ergebnis)
        assertTrue(runCatching { umgebung.step(SpielAktion.ZugBeenden) }.isFailure)
    }

    @Test
    fun nachFachlichemPartieendeIstKeinStepMehrMoeglich() {
        val umgebung = StandardTrainingsUmgebung(maximaleEntscheidungen = 20)
        var punkt = umgebung.reset(szenario, 1)
        var uebergang = umgebung.step(
            punkt.aktionsRaum.aktionen.first { it is SpielAktion.Aufgeben },
        )
        punkt = requireNotNull(uebergang.naechsterPunkt)
        uebergang = umgebung.step(
            punkt.aktionsRaum.aktionen.first { it is SpielAktion.Aufgeben },
        )

        assertTrue(uebergang.terminated)
        assertFalse(uebergang.truncated)
        assertTrue(uebergang.ergebnis?.gewinner != null)
        assertTrue(runCatching { umgebung.step(SpielAktion.ZugBeenden) }.isFailure)
    }
}
