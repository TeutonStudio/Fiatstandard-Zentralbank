package de.teutonstudio.zentralbank.simulation

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val argumente = SimulationsArgumente.lesen(args)
    val konfiguration = SimulationsKonfiguration(
        episoden = argumente.episoden,
        seed = argumente.seed,
        maximaleSchritte = argumente.maximaleSchritte,
    )
    val schritte = SimulationsLaeufer().ausfuehren(konfiguration)
    schreibeJsonl(argumente.ausgabe, schritte)
    println("Trainingsdaten geschrieben: ${argumente.ausgabe.toAbsolutePath()}")
}

fun schreibeJsonl(
    ausgabe: Path,
    schritte: Sequence<de.teutonstudio.zentralbank.protokoll.SimulationsschrittDto>,
) {
    ausgabe.parent?.let(Files::createDirectories)
    val json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
    }
    Files.newBufferedWriter(ausgabe).use { writer ->
        schritte.forEach { schritt ->
            writer.appendLine(json.encodeToString(schritt))
        }
    }
}

private data class SimulationsArgumente(
    val episoden: Int = 1,
    val seed: Long = 0,
    val maximaleSchritte: Int = 500,
    val ausgabe: Path = Path.of("build", "simulation", "episodes.jsonl"),
) {
    companion object {
        fun lesen(args: Array<String>): SimulationsArgumente {
            var episoden = 1
            var seed = 0L
            var maxSchritte = 500
            var ausgabe = Path.of("build", "simulation", "episodes.jsonl")
            var index = 0
            while (index < args.size) {
                val schluessel = args[index]
                val wert = args.getOrNull(index + 1)
                    ?: error("Wert nach $schluessel fehlt.")
                when (schluessel) {
                    "--episodes" -> episoden = wert.toInt()
                    "--seed" -> seed = wert.toLong()
                    "--max-steps" -> maxSchritte = wert.toInt()
                    "--output" -> ausgabe = Path.of(wert)
                    else -> error("Unbekanntes Argument: $schluessel")
                }
                index += 2
            }
            return SimulationsArgumente(episoden, seed, maxSchritte, ausgabe)
        }
    }
}
