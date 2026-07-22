package de.teutonstudio.zentralbank.simulation

import java.nio.file.Files
import java.nio.file.Path
import de.teutonstudio.zentralbank.fachlogik.engine.SeedZufallsquelle
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZahlungsfaehigkeitsAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.ProzugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentenUndEpisodenTest {
    @Test
    fun zufallsagentKannVollstaendigePartienSpielen() {
        val ergebnis = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(
                spiele = 20,
                seed = 42,
                maximaleEntscheidungen = 200,
                agenten = listOf("zufall"),
            ),
        )

        assertTrue(ergebnis.statistik.fehler.isEmpty())
        assertEquals(20, ergebnis.statistik.beendet)
        assertEquals(0, ergebnis.statistik.truncations)
        assertTrue(ergebnis.episoden.all { it.replay().ergebnis == it.ergebnis })
    }

    @Test
    fun sicherheitsagentWaehltNurGueltigeAktionenUndBeendetPartie() {
        val ergebnis = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(
                spiele = 2,
                seed = 11,
                maximaleEntscheidungen = 300,
                agenten = listOf("sicherheit"),
            ),
        )

        assertTrue(ergebnis.statistik.fehler.isEmpty())
        assertEquals(2, ergebnis.statistik.beendet)
    }

    @Test
    fun wirtschaftsagentWaehltNurGueltigeAktionenUndBeendetPartie() {
        val ergebnis = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(
                spiele = 2,
                seed = 21,
                maximaleEntscheidungen = 500,
                agenten = listOf("wirtschaft"),
            ),
        )

        assertTrue(ergebnis.statistik.fehler.isEmpty())
        assertEquals(2, ergebnis.statistik.beendet)
        assertEquals(0, ergebnis.statistik.truncations)
    }

    @Test
    fun episodenexportImportUndModellkodierungSindDeterministisch() {
        val ergebnis = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(2, 9, 200),
        )
        val datei = Files.createTempFile("fiat-episoden", ".jsonl")
        EpisodenJsonl.exportieren(datei, ergebnis.episoden.asSequence())
        val geladen = EpisodenJsonl.importieren(datei).toList()

        assertEquals(ergebnis.episoden, geladen)
        val text = Files.readString(datei)
        assertFalse(text.contains("passwort", ignoreCase = true))

        val umgebung = StandardTrainingsUmgebung()
        val punkt = umgebung.reset(KleineWirtschaftsBaseline(), 99)
        val links = BeobachtungsKodierung.kodiere(punkt)
        val rechts = BeobachtungsKodierung.kodiere(punkt)
        assertArrayEquals(links.globaleMerkmale, rechts.globaleMerkmale, 0f)
        links.spielerMerkmale.indices.forEach { index ->
            assertArrayEquals(links.spielerMerkmale[index], rechts.spielerMerkmale[index], 0f)
        }
        assertEquals(links.kandidaten, rechts.kandidaten)
        assertEquals(
            links.kandidaten.size,
            links.kandidaten.map { it.kanonischeSerialisierung }.distinct().size,
        )
    }

    @Test
    fun importLehntUnbekannteEpisodenversionAb() {
        val episode = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(1, 13, 200),
        ).episoden.single()
        val datei = Files.createTempFile("fiat-episode-version", ".jsonl")
        val ungueltig = EpisodenJsonl.json.encodeToString(episode)
            .replaceFirst("\"formatVersion\":2", "\"formatVersion\":99")
        Files.writeString(datei, "$ungueltig\n")

        assertTrue(runCatching { EpisodenJsonl.importieren(datei).toList() }.isFailure)
    }

    @Test
    fun parallelerLaufIstMitSequentiellemLaufIdentisch() {
        val basis = SimulationsKonfiguration(
            spiele = 4,
            seed = 77,
            maximaleEntscheidungen = 200,
            agenten = listOf("zufall", "sicherheit", "wirtschaft"),
        )

        val sequentiell = SimulationsLaeufer().ausfuehren(basis)
        val parallel = SimulationsLaeufer().ausfuehren(basis.copy(parallelitaet = 3))

        assertEquals(sequentiell, parallel)
    }

    @Test
    fun fehlendesOnnxModellFaelltAufRegelkonformenSicherheitsagentenZurueck() {
        val umgebung = StandardTrainingsUmgebung()
        val punkt = umgebung.reset(KleineWirtschaftsBaseline(), 123)
        OnnxModellAgent(
            Path.of("nicht-vorhanden.onnx"),
            Path.of("nicht-vorhandenes-manifest.json"),
        ).use { agent ->
            val aktion = agent.waehleAktion(punkt, SeedZufallsquelle(123))
            assertFalse(agent.status.modellGeladen)
            assertEquals("sicherheit", agent.status.verwendeterAgent)
            assertTrue(aktion in punkt.aktionsRaum.aktionen)
        }
    }

    @Test
    fun aktionsraumEnthaeltGruppenAllianzTransferUndFriedensaktionen() {
        val basis = SzenarioKatalog.szenario("generiert-vollstaendig-3").startzustand(71)
        val erster = basis.spieler[0].id
        val dritter = basis.spieler[2].id
        val krieg = basis.konflikte.single()
        val dritterImEpizug = basis.copy(
            aktiverSpieler = dritter,
            zugStatus = ZugStatus(
                71,
                dritter,
                ZugPhase.Epizug,
                ProzugStatus(begonnen = true, erfolgreichAbgeschlossen = true),
            ),
        )
        assertTrue(
            AktionsAuswertung.erlaubteAktionen(dritterImEpizug, dritter).aktionen.any {
                it is SpielAktion.KriegsAllianzBeitreten && it.krieg == krieg.id
            },
        )

        val basiskarte = requireNotNull(basis.karte)
        val panzer = basiskarte.belegung.kriegseinheiten.single {
            it.besitzer == erster && it.typ == KriegsEinheitTyp.PANZER
        }
        val mitGruppeUndAllianz = basis.copy(
            spieler = basis.spieler.map { spieler ->
                if (spieler.id == erster) spieler.copy(
                    rohstoffe = spieler.rohstoffe +
                        (Rohstoff.DIESEL to maxOf(100, spieler.rohstoffe.getOrDefault(Rohstoff.DIESEL, 0))),
                ) else spieler
            },
            karte = basiskarte.copy(
                belegung = basiskarte.belegung.copy(
                    kriegseinheiten = basiskarte.belegung.kriegseinheiten +
                        panzer.copy(id = "gruppen-panzer"),
                ),
            ),
            konflikte = setOf(krieg.copy(aggressoren = krieg.aggressoren + dritter)),
            aktiverSpieler = erster,
            zugStatus = ZugStatus(
                72,
                erster,
                ZugPhase.Epizug,
                ProzugStatus(begonnen = true, erfolgreichAbgeschlossen = true),
            ),
        )
        val aktionen = AktionsAuswertung.erlaubteAktionen(mitGruppeUndAllianz, erster).aktionen
        assertTrue(aktionen.any { it is SpielAktion.KriegsEinheitenBewegen && it.ids.size == 2 })
        assertTrue(aktionen.any { it is SpielAktion.RessourcenUebertragen && it.empfaenger == dritter })
        assertTrue(aktionen.any { it is SpielAktion.FriedensvertragVorschlagen })

        val frieden = SzenarioKatalog.szenario("generiert-frieden-3").startzustand(73)
        val annehmender = frieden.spieler[1].id
        val annahmeZustand = frieden.copy(
            aktiverSpieler = annehmender,
            zugStatus = ZugStatus(
                73,
                annehmender,
                ZugPhase.Epizug,
                ProzugStatus(begonnen = true, erfolgreichAbgeschlossen = true),
            ),
        )
        assertTrue(
            AktionsAuswertung.erlaubteAktionen(annahmeZustand, annehmender).aktionen.any {
                it is SpielAktion.FriedensvertragAnnehmen
            },
        )
    }

    @Test
    fun heuristikagentenBeendenVollstaendigesKriegsszenarioOhneAktionszyklus() {
        val umgebung = StandardTrainingsUmgebung(maximaleEntscheidungen = 10_000)
        var punkt: Entscheidungspunkt? = umgebung.reset(
            SzenarioKatalog.szenario("generiert-vollstaendig-3"),
            2_100_000_000L,
        )
        val agenten = listOf(
            AggressiverHeuristikAgent(),
            DefensiverHeuristikAgent(),
            SicherheitsAgent(),
        )
        val nachSpieler = umgebung.startzustand.spieler.mapIndexed { index, spieler ->
            spieler.id to agenten[index]
        }.toMap()
        val zufall = SeedZufallsquelle(2_100_000_000L)
        val typen = mutableListOf<String>()
        repeat(500) {
            val aktuell = punkt ?: return@repeat
            val aktion = nachSpieler.getValue(aktuell.spieler).waehleAktion(aktuell, zufall)
            typen += "${aktuell.spieler.wert}:${aktuell.beobachtung.zug?.phase}:" +
                aktion::class.simpleName.orEmpty()
            punkt = umgebung.step(aktion).naechsterPunkt
        }
        assertTrue(
            "Kriegsagenten blieben nach 500 Entscheidungen aktiv: " +
                typen.takeLast(30).joinToString() + "; legal=" +
                punkt?.aktionsRaum?.aktionen?.map { it::class.simpleName }?.distinct() +
                "; zahlung=" + punkt?.spieler?.let {
                    ZahlungsfaehigkeitsAuswertung.plan(umgebung.zustand, it)
                } + "; konflikte=" + umgebung.zustand.konflikte,
            punkt == null,
        )
    }
}
