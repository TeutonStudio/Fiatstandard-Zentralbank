package de.teutonstudio.zentralbank.simulation

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString

fun main(args: Array<String>) {
    val argumente = SimulationsArgumente.lesen(args)
    val ergebnis = SimulationsLaeufer().ausfuehren(
        SimulationsKonfiguration(
            spiele = argumente.spiele,
            seed = argumente.seed,
            maximaleEntscheidungen = argumente.maximaleEntscheidungen,
            agenten = argumente.agenten,
            szenarioId = argumente.karte,
        ),
    )
    Files.createDirectories(argumente.ausgabeOrdner)
    val episodenDatei = argumente.expliziteJsonl
        ?: argumente.ausgabeOrdner.resolve("episoden.jsonl")
    EpisodenJsonl.exportieren(episodenDatei, ergebnis.episoden.asSequence())
    val statistikDatei = argumente.ausgabeOrdner.resolve("statistik.json")
    Files.writeString(statistikDatei, EpisodenJsonl.json.encodeToString(ergebnis.statistik))
    println(
        "Simulation: ${ergebnis.statistik.beendet}/${ergebnis.statistik.spiele} beendet, " +
            "${ergebnis.statistik.truncations} trunciert, " +
            "${ergebnis.statistik.fehler.size} Fehler",
    )
    println("Episoden: ${episodenDatei.toAbsolutePath()}")
    println("Statistik: ${statistikDatei.toAbsolutePath()}")
    check(ergebnis.statistik.fehler.isEmpty()) {
        ergebnis.statistik.fehler.joinToString("; ")
    }
}

private data class SimulationsArgumente(
    val spiele: Int = 1,
    val seed: Long = 0L,
    val maximaleEntscheidungen: Int = 500,
    val agenten: List<String> = listOf("zufall"),
    val karte: String = "kleine-wirtschaft-v1",
    val ausgabeOrdner: Path = Path.of("build", "simulationen"),
    val expliziteJsonl: Path? = null,
) {
    companion object {
        fun lesen(args: Array<String>): SimulationsArgumente {
            var wert = SimulationsArgumente()
            var index = 0
            while (index < args.size) {
                val schluessel = args[index]
                val argument = args.getOrNull(index + 1)
                    ?: error("Wert nach $schluessel fehlt.")
                wert = when (schluessel) {
                    "--spiele", "--episodes" -> wert.copy(spiele = argument.toInt())
                    "--seed" -> wert.copy(seed = argument.toLong())
                    "--max-entscheidungen", "--max-steps" ->
                        wert.copy(maximaleEntscheidungen = argument.toInt())
                    "--spieler" -> wert.copy(
                        agenten = argument.split(',').map(String::trim).filter(String::isNotEmpty),
                    )
                    "--karte" -> wert.copy(karte = argument)
                    "--ausgabe" -> wert.copy(ausgabeOrdner = Path.of(argument), expliziteJsonl = null)
                    "--output" -> {
                        val datei = Path.of(argument)
                        wert.copy(
                            ausgabeOrdner = datei.parent ?: Path.of("."),
                            expliziteJsonl = datei,
                        )
                    }
                    else -> error("Unbekanntes Argument: $schluessel")
                }
                index += 2
            }
            return wert
        }
    }
}
