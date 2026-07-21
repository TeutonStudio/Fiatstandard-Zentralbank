package de.teutonstudio.zentralbank.simulation

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationsLaeuferTest {
    @Test
    fun fuehrtMindestensZweiEpisodenHeadlessAus() {
        val schritte = SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(episoden = 2, seed = 42, maximaleSchritte = 12),
        ).toList()

        assertEquals(setOf("episode-0", "episode-1"), schritte.map { it.episodeId }.toSet())
        assertEquals(24, schritte.size)
    }

    @Test
    fun gleicherSeedUndGleicheAktionsfolgeErgebenGleicheZustandshashes() {
        val konfiguration = SimulationsKonfiguration(
            episoden = 2,
            seed = 42,
            maximaleSchritte = 20,
        )

        val links = SimulationsLaeufer().ausfuehren(konfiguration).toList()
        val rechts = SimulationsLaeufer().ausfuehren(konfiguration).toList()

        assertEquals(links.map { it.gewaehlteAktion }, rechts.map { it.gewaehlteAktion })
        assertEquals(
            links.map { it.naechsteBeobachtung.zustandsHash },
            rechts.map { it.naechsteBeobachtung.zustandsHash },
        )
    }

    @Test
    fun jsonlEnthaeltDieGefordertenTrainingsfelder() {
        val datei = Files.createTempFile("episoden", ".jsonl")
        schreibeJsonl(
            datei,
            SimulationsLaeufer().ausfuehren(
                SimulationsKonfiguration(episoden = 2, seed = 42, maximaleSchritte = 3),
            ),
        )

        val zeilen = Files.readAllLines(datei)
        val erstes = Json.parseToJsonElement(zeilen.first()).jsonObject
        assertTrue(zeilen.size >= 2)
        assertTrue(
            setOf(
                "episodeId",
                "engineVersion",
                "seed",
                "step",
                "actor",
                "observation",
                "legalActions",
                "chosenAction",
                "rewardComponents",
                "nextObservation",
                "terminated",
                "winner",
                "events",
            ).all(erstes::containsKey),
        )
    }
}
