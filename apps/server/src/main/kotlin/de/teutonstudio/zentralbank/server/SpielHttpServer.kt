package de.teutonstudio.zentralbank.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.teutonstudio.zentralbank.netzwerk.NetzwerkAnfrage
import de.teutonstudio.zentralbank.netzwerk.SpielNetzwerkRouter
import de.teutonstudio.zentralbank.protokoll.FehlerAntwortDto
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SimulationStartAnfrage(
    val spiele: Int = 1,
    val seed: Long = 0L,
    val watchdogEntscheidungen: Int = 10_000,
    val agenten: List<String> = listOf("sicherheit"),
    val szenarioId: String = "kleine-wirtschaft-v2",
    val parallelitaet: Int = 1,
)

@Serializable
data class LigaStartAnfrage(
    val spiele: Int = 6,
    val seed: Long = 2_000_000_000L,
    val agenten: List<String> = listOf(
        "zufall", "sicherheit", "wirtschaft", "aggressiv", "defensiv", "onnx",
    ),
)

class SpielHttpServer(
    port: Int,
    private val dienst: SpielServerDienst,
) : AutoCloseable {
    private val json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val spielRouter = SpielNetzwerkRouter(dienst.netzwerkDienst)
    private val worker = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress(port), 0).apply {
        executor = worker
        createContext("/") { austausch -> behandle(austausch) }
    }

    val gebundenerPort: Int
        get() = server.address.port

    fun starten() = server.start()

    override fun close() {
        server.stop(0)
        worker.shutdownNow()
    }

    private fun behandle(austausch: HttpExchange) {
        try {
            setzeCors(austausch)
            if (austausch.requestMethod == "OPTIONS") {
                antworteLeer(austausch, 204)
                return
            }
            val pfad = austausch.requestURI.path
            val teile = pfad.trim('/').split('/').filter(String::isNotBlank)
            when {
                teile == listOf("health") || teile.take(3) == listOf("api", "v1", "games") -> {
                    val antwort = spielRouter.bearbeiten(
                        NetzwerkAnfrage(
                            methode = austausch.requestMethod,
                            pfad = pfad,
                            kopfzeilen = austausch.requestHeaders.entries.associate { (name, werte) ->
                                name to werte.joinToString(",")
                            },
                            inhalt = if (austausch.requestMethod in setOf("POST", "PUT", "PATCH")) {
                                leseText(austausch)
                            } else {
                                ""
                            },
                        ),
                    )
                    antworte(austausch, antwort.status, antwort.inhalt, antwort.inhaltstyp)
                }

                austausch.requestMethod == "POST" && teile == listOf("api", "v1", "simulations") -> {
                    val anfrage = json.decodeFromString<SimulationStartAnfrage>(leseText(austausch))
                    antworteJson(austausch, 200, json.encodeToString(dienst.simulationStarten(anfrage)))
                }

                austausch.requestMethod == "POST" && teile == listOf("api", "v1", "league") -> {
                    val anfrage = json.decodeFromString<LigaStartAnfrage>(leseText(austausch))
                    antworteJson(austausch, 200, json.encodeToString(dienst.ligaStarten(anfrage)))
                }

                austausch.requestMethod == "GET" && teile == listOf("api", "v1", "league", "latest") ->
                    antworteJson(austausch, 200, json.encodeToString(dienst.letzterLigaBericht()))

                else -> antworteFehler(austausch, 404, "ROUTE_NICHT_GEFUNDEN", "Route nicht gefunden.")
            }
        } catch (fehler: SerializationException) {
            antworteFehler(
                austausch,
                400,
                "UNGUELTIGES_JSON",
                fehler.message ?: "JSON konnte nicht gelesen werden.",
            )
        } catch (fehler: IllegalArgumentException) {
            antworteFehler(
                austausch,
                422,
                "AKTION_ABGELEHNT",
                fehler.message ?: "Anfrage wurde fachlich abgelehnt.",
            )
        } catch (fehler: IllegalStateException) {
            antworteFehler(
                austausch,
                409,
                "ZUSTANDSKONFLIKT",
                fehler.message ?: "Anfrage widerspricht dem aktuellen Zustand.",
            )
        } catch (fehler: Exception) {
            antworteFehler(austausch, 500, "INTERNER_FEHLER", fehler.message ?: "Interner Fehler.")
        } finally {
            austausch.close()
        }
    }

    private fun leseText(austausch: HttpExchange): String {
        val bytes = austausch.requestBody.readNBytes(MAXIMALE_ANFRAGE_BYTES + 1)
        require(bytes.size <= MAXIMALE_ANFRAGE_BYTES) { "Anfrage ist zu groß." }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun antworteFehler(
        austausch: HttpExchange,
        status: Int,
        code: String,
        meldung: String,
    ) = antworteJson(
        austausch,
        status,
        json.encodeToString(FehlerAntwortDto(code = code, meldung = meldung)),
    )

    private fun antworteJson(austausch: HttpExchange, status: Int, text: String) =
        antworte(austausch, status, text, "application/json; charset=utf-8")

    private fun antworte(
        austausch: HttpExchange,
        status: Int,
        text: String,
        inhaltstyp: String,
    ) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        austausch.responseHeaders.set("Content-Type", inhaltstyp)
        austausch.sendResponseHeaders(status, bytes.size.toLong())
        austausch.responseBody.write(bytes)
    }

    private fun antworteLeer(austausch: HttpExchange, status: Int) {
        austausch.sendResponseHeaders(status, -1)
    }

    private fun setzeCors(austausch: HttpExchange) {
        austausch.responseHeaders.set("Access-Control-Allow-Origin", "*")
        austausch.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
        austausch.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    private companion object {
        const val MAXIMALE_ANFRAGE_BYTES = 1_048_576
    }
}
