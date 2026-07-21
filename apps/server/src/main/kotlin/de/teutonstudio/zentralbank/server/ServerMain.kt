package de.teutonstudio.zentralbank.server

import de.teutonstudio.zentralbank.adapter.json.ArbeitsspeicherSpielAblage
import de.teutonstudio.zentralbank.adapter.json.JsonDateiSpielAblage
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val konfiguration = ServerKonfiguration.lesen(args)
    val ablage = if (konfiguration.nurArbeitsspeicher) {
        ArbeitsspeicherSpielAblage()
    } else {
        JsonDateiSpielAblage(konfiguration.datenverzeichnis)
    }
    val server = SpielHttpServer(konfiguration.port, SpielServerDienst(ablage))
    Runtime.getRuntime().addShutdownHook(Thread(server::close))
    server.starten()
    println("Fiatstandard-Zentralbank-Server läuft auf http://127.0.0.1:${server.gebundenerPort}")
    CountDownLatch(1).await()
}

private data class ServerKonfiguration(
    val port: Int = 8080,
    val datenverzeichnis: Path = Path.of(".data", "games"),
    val nurArbeitsspeicher: Boolean = false,
) {
    companion object {
        fun lesen(args: Array<String>): ServerKonfiguration {
            var port = 8080
            var daten = Path.of(".data", "games")
            var arbeitsspeicher = false
            var index = 0
            while (index < args.size) {
                when (val argument = args[index]) {
                    "--port" -> port = args.wertNach(index++, argument).toInt()
                    "--data" -> daten = Path.of(args.wertNach(index++, argument))
                    "--memory" -> arbeitsspeicher = true
                    else -> error("Unbekanntes Argument: $argument")
                }
                index++
            }
            require(port in 0..65535) { "Port muss zwischen 0 und 65535 liegen." }
            return ServerKonfiguration(port, daten, arbeitsspeicher)
        }

        private fun Array<String>.wertNach(index: Int, argument: String): String =
            getOrNull(index + 1) ?: error("Wert nach $argument fehlt.")
    }
}
