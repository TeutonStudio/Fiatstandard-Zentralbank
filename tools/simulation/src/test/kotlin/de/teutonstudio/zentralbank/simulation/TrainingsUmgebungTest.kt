package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
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
        val aktion = linksStart.aktionsRaum.aktionen.first()
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

        val fremderProzug = SpielAktion.ProzugBeginnen(Long.MAX_VALUE)
        assertTrue(runCatching { links.step(fremderProzug) }.isFailure)
        links.step(punkt.aktionsRaum.aktionen.first())

        assertEquals(rechtsVorher, rechts.zustand)
        assertNotEquals(links.zustand, rechts.zustand)
    }

    @Test
    fun truncationIstKeinFachlichesSpielende() {
        val umgebung = StandardTrainingsUmgebung(maximaleEntscheidungen = 1)
        val punkt = umgebung.reset(szenario, 1)
        val uebergang = umgebung.step(
            punkt.aktionsRaum.aktionen.first(),
        )

        assertTrue(uebergang.truncated)
        assertFalse(uebergang.terminated)
        assertNull(uebergang.ergebnis)
        assertTrue(runCatching { umgebung.step(SpielAktion.ZugBeenden) }.isFailure)
    }

    @Test
    fun nachFachlichemPartieendeIstKeinStepMehrMoeglich() {
        val umgebung = StandardTrainingsUmgebung(maximaleEntscheidungen = 2_000)
        val agent = SicherheitsAgent()
        val zufall = de.teutonstudio.zentralbank.fachlogik.engine.SeedZufallsquelle(1)
        var punkt = umgebung.reset(szenario, 1)
        var uebergang: TrainingsUebergang
        do {
            uebergang = umgebung.step(agent.waehleAktion(punkt, zufall))
            punkt = uebergang.naechsterPunkt ?: break
        } while (true)

        assertTrue(uebergang.terminated)
        assertFalse(uebergang.truncated)
        assertTrue(uebergang.ergebnis?.gewinner != null)
        assertTrue(runCatching { umgebung.step(SpielAktion.ZugBeenden) }.isFailure)
    }

    @Test
    fun baselineEnthaeltEineDeterministischeKarteMitStartstandorten() {
        val links = szenario.startzustand(42)
        val rechts = szenario.startzustand(42)

        assertEquals(links, rechts)
        assertEquals(18, links.karte?.gelaendefelder?.size)
        assertEquals(3, links.karte?.belegung?.ecken?.size)
        assertTrue(links.karte?.belegung?.ecken?.all {
            it.typ == de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.HAUPTBAHNHOF
        } == true)
    }

    @Test
    fun szenariokatalogEnthaeltSchuldenLandkriegSeekriegBlockadeBelagerungUndFrieden() {
        val schulden = SzenarioKatalog.szenario("generiert-schulden-3").startzustand(1)
        assertTrue(schulden.anleihen.isNotEmpty())

        val land = SzenarioKatalog.szenario("generiert-landkrieg-4").startzustand(2)
        assertEquals(4, land.spieler.size)
        assertTrue(land.karte!!.belegung.kriegseinheiten.any { it.typ == KriegsEinheitTyp.PANZER })

        val see = SzenarioKatalog.szenario("generiert-seekrieg-5").startzustand(3)
        val seekarte = requireNotNull(see.karte)
        assertTrue(seekarte.belegung.seewege.isNotEmpty())
        assertTrue(seekarte.belegung.kriegseinheiten.any { it.typ == KriegsEinheitTyp.KRIEGSSCHIFF })

        val blockade = SzenarioKatalog.szenario("generiert-blockade-6").startzustand(4)
        assertTrue(blockade.karte!!.belegung.seewege.isNotEmpty())

        val belagerung = SzenarioKatalog.szenario("generiert-belagerung-7").startzustand(5)
        assertEquals(7, belagerung.spieler.size)
        assertTrue(belagerung.belagerungen.isNotEmpty())
        assertTrue(belagerung.karte!!.belegung.ecken.any { it.zustand == BauwerkZustand.BELAGERT })

        val frieden = SzenarioKatalog.szenario("generiert-frieden-3").startzustand(6)
        assertTrue(frieden.friedensvertraege.isNotEmpty())
    }

    @Test
    fun kiSpieltRundeNullUeberDenAutoritativenAktionsraum() {
        val szenario = SzenarioKatalog.szenario("generiert-startaufstellung-3")
        val umgebung = StandardTrainingsUmgebung()

        val ersterPunkt = umgebung.reset(szenario, 77L)

        assertEquals(Spielabschnitt.RUNDE_NULL, umgebung.zustand.spielabschnitt)
        assertTrue(ersterPunkt.aktionsRaum.aktionen.all {
            it is SpielAktion.HauptbahnhofPlatzieren
        })
        val lauf = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(
                spiele = 1,
                seed = 77L,
                maximaleEntscheidungen = 10_000,
                agenten = listOf("sicherheit"),
                szenarioId = "generiert-startaufstellung-3",
            ),
        )
        assertTrue(lauf.statistik.fehler.isEmpty())
        assertEquals(1, lauf.statistik.beendet)
        assertTrue(lauf.episoden.single().entscheidungen.any {
            it.gewaehlteAktion is SpielAktion.HauptbahnhofPlatzieren
        })
    }

    @Test
    fun echteKartenvorlagenUnterstuetzenDreiBisSiebenSpieler() {
        SzenarioKatalog.echteKarten.forEach { datei ->
            val basis = "vorlage-${datei.substringBeforeLast('.')}"
            assertEquals(3, SzenarioKatalog.szenario(basis).startzustand(10).spieler.size)
            assertEquals(7, SzenarioKatalog.szenario("$basis-7").startzustand(11).spieler.size)
        }
    }
}
