package de.teutonstudio.zentralbank.simulation

import java.nio.file.Files
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
        assertTrue(links.aktionsMaske.contentEquals(rechts.aktionsMaske))
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
}
