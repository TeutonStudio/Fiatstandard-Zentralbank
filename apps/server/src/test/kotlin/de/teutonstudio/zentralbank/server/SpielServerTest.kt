package de.teutonstudio.zentralbank.server

import de.teutonstudio.zentralbank.adapter.json.ArbeitsspeicherSpielAblage
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.SpielSchrittErgebnis
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.AktionErgebnisDto
import de.teutonstudio.zentralbank.protokoll.ErlaubteAktionenDto
import de.teutonstudio.zentralbank.protokoll.SpielAktionDto
import de.teutonstudio.zentralbank.protokoll.SpielErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielErstelltDto
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielServerTest {
    private val json = Json { classDiscriminator = "art" }

    @Test
    fun serveraktionVerwendetDieInjizierteGemeinsameEngine() = runBlocking {
        val zaehlend = ZaehlendeEngine()
        val dienst = SpielServerDienst(ArbeitsspeicherSpielAblage(), zaehlend)
        val erstellt = dienst.erstellen(SpielErstellenAnfrageDto(spielerNamen = listOf("Anna")))

        val ergebnis = dienst.aktionAusfuehren(
            erstellt.spielId.toLong(),
            AktionAusfuehrenAnfrageDto(aktion = SpielAktionDto.ProzugBeginnen(1L)),
        )

        assertEquals(true, ergebnis.zustand.zug?.prozugBegonnen)
        assertTrue(zaehlend.anwendenAufrufe > 0)
    }

    @Test
    fun healthRouteAntwortetUeberEchtenHttpServer() {
        SpielHttpServer(
            port = 0,
            dienst = SpielServerDienst(ArbeitsspeicherSpielAblage()),
        ).use { server ->
            server.starten()
            val antwort = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(
                    URI("http://127.0.0.1:${server.gebundenerPort}/health"),
                ).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, antwort.statusCode())
            assertTrue(antwort.body().contains("\"status\":\"ok\""))
        }
    }

    @Test
    fun echterHttpClientKannSpielErstellenAktionenLadenUndValidiertSenden() {
        SpielHttpServer(
            port = 0,
            dienst = SpielServerDienst(ArbeitsspeicherSpielAblage()),
        ).use { server ->
            server.starten()
            val basis = "http://127.0.0.1:${server.gebundenerPort}"
            val client = HttpClient.newHttpClient()
            val erstelltAntwort = client.send(
                jsonAnfrage(
                    "$basis/api/v1/games",
                    json.encodeToString(SpielErstellenAnfrageDto(spielerNamen = listOf("Anna"))),
                ),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(201, erstelltAntwort.statusCode())
            val erstellt = json.decodeFromString<SpielErstelltDto>(erstelltAntwort.body())

            val aktionenAntwort = client.send(
                HttpRequest.newBuilder(
                    URI("$basis/api/v1/games/${erstellt.spielId}/actions"),
                ).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, aktionenAntwort.statusCode())
            val aktionen = json.decodeFromString<ErlaubteAktionenDto>(aktionenAntwort.body())
            val prozugBeginnen = aktionen.aktionen.single {
                it is SpielAktionDto.ProzugBeginnen
            }

            val schrittAntwort = client.send(
                jsonAnfrage(
                    "$basis/api/v1/games/${erstellt.spielId}/actions",
                    json.encodeToString(AktionAusfuehrenAnfrageDto(aktion = prozugBeginnen)),
                ),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, schrittAntwort.statusCode())
            val schritt = json.decodeFromString<AktionErgebnisDto>(schrittAntwort.body())
            assertEquals(true, schritt.zustand.zug?.prozugBegonnen)
            assertTrue(schritt.ereignisse.isNotEmpty())
        }
    }

    private fun jsonAnfrage(uri: String, text: String): HttpRequest =
        HttpRequest.newBuilder(URI(uri))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(text))
            .build()

    private class ZaehlendeEngine : SpielEngine {
        private val delegate = StandardSpielEngine()
        var anwendenAufrufe: Int = 0

        override fun pruefe(zustand: SpielZustand, aktion: SpielAktion) =
            delegate.pruefe(zustand, aktion)

        override fun anwenden(
            zustand: SpielZustand,
            aktion: SpielAktion,
        ): Result<SpielSchrittErgebnis> {
            anwendenAufrufe++
            return delegate.anwenden(zustand, aktion)
        }

        override fun erlaubteAktionen(zustand: SpielZustand, spieler: SpielerId) =
            delegate.erlaubteAktionen(zustand, spieler)
    }
}
