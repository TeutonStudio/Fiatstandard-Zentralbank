package de.teutonstudio.zentralbank.simulation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.measureTimedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class MassensimulationsBericht(
    val schemaVersion: Int = 1,
    val spiele: Int,
    val seed: Long,
    val szenario: String,
    val beendet: Int,
    val truncations: Int,
    val fehler: List<String>,
    val siegeJeAgent: Map<String, Int>,
    val durchschnittlicheRunden: Double,
    val durchschnittlicheEntscheidungen: Double,
    val schritteProSekunde: Double,
    val dauerMillis: Long,
)

fun main(args: Array<String>) = massensimulationMain(args)

/** Speicherbegrenzter vollständiger Lauf; Episode 2 wird nach jedem Batch direkt geschrieben. */
fun massensimulationMain(args: Array<String>) {
    require(args.size in 4..6) {
        "Erwartet: <spiele> <seed> <szenario> <ausgabeordner> [batch] [parallelitaet]"
    }
    val spiele = args[0].toInt()
    val seed = args[1].toLong()
    val szenario = args[2]
    val ausgabe = Path.of(args[3])
    val batchGroesse = args.getOrNull(4)?.toInt() ?: 100
    val parallelitaet = args.getOrNull(5)?.toInt() ?: 4
    require(spiele > 0 && batchGroesse > 0 && parallelitaet > 0)
    Files.createDirectories(ausgabe)
    val episodenPfad = ausgabe.resolve("episoden.jsonl")

    var beendet = 0
    var truncations = 0
    var entscheidungen = 0L
    var rundenSumme = 0.0
    val fehler = mutableListOf<String>()
    val siege = mutableMapOf<String, Int>()
    val gemessen = measureTimedValue {
        Files.newBufferedWriter(episodenPfad).use { writer ->
            var start = 0
            while (start < spiele) {
                val anzahl = minOf(batchGroesse, spiele - start)
                val ergebnis = SimulationsLaeufer().ausfuehren(
                    SimulationsKonfiguration(
                        spiele = anzahl,
                        seed = seed + start,
                        maximaleEntscheidungen = 10_000,
                        agenten = if ("wirtschaft" in szenario) {
                            listOf("zufall", "sicherheit", "wirtschaft")
                        } else {
                            listOf("aggressiv", "defensiv", "sicherheit")
                        },
                        szenarioId = szenario,
                        parallelitaet = minOf(parallelitaet, anzahl),
                    ),
                )
                ergebnis.episoden.forEach { episode ->
                    writer.appendLine(EpisodenJsonl.json.encodeToString(episode))
                    val replay = episode.replay()
                    check(replay.ergebnis == episode.ergebnis) {
                        "Der gestreamte Episoden-Replay besitzt ein abweichendes Ergebnis."
                    }
                    entscheidungen += episode.entscheidungen.size
                    rundenSumme += episode.ergebnis?.endRunde ?: replay.rundenzähler
                }
                writer.flush()
                beendet += ergebnis.statistik.beendet
                truncations += ergebnis.statistik.truncations
                fehler += ergebnis.statistik.fehler
                ergebnis.statistik.siegeJeAgent.forEach { (agent, wert) ->
                    siege[agent] = siege.getOrDefault(agent, 0) + wert
                }
                start += anzahl
                System.err.println("Massensimulation $szenario: $start/$spiele")
            }
        }
    }
    val sekunden = gemessen.duration.inWholeNanoseconds / 1_000_000_000.0
    val bericht = MassensimulationsBericht(
        spiele = spiele,
        seed = seed,
        szenario = szenario,
        beendet = beendet,
        truncations = truncations,
        fehler = fehler,
        siegeJeAgent = siege.toSortedMap(),
        durchschnittlicheRunden = rundenSumme / spiele,
        durchschnittlicheEntscheidungen = entscheidungen.toDouble() / spiele,
        schritteProSekunde = entscheidungen / maxOf(0.001, sekunden),
        dauerMillis = gemessen.duration.inWholeMilliseconds,
    )
    Files.writeString(
        ausgabe.resolve("statistik.json"),
        EpisodenJsonl.json.encodeToString(bericht),
    )
    println(EpisodenJsonl.json.encodeToString(bericht))
    check(fehler.isEmpty()) { fehler.joinToString("; ") }
    check(beendet + truncations == spiele) {
        "Nicht alle Partien endeten regulär oder über den technischen Watchdog."
    }
}
