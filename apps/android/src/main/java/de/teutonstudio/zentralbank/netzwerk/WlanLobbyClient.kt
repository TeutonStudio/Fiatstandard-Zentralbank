package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.protokoll.FehlerAntwortDto
import de.teutonstudio.zentralbank.protokoll.LobbyDto
import de.teutonstudio.zentralbank.protokoll.LobbyGestartetDto
import de.teutonstudio.zentralbank.protokoll.LobbyKonfigurationDto
import de.teutonstudio.zentralbank.protokoll.LobbySitzungDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerAendernDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerRegistrierenDto
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class WlanLobbyEndpunkt(
    val host: String,
    val port: Int,
    val lobbyId: String,
    val name: String = "$host:$port",
) {
    val basisUrl: String
        get() {
            val urlHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
            return "http://$urlHost:$port"
        }
}

/** Client für die Spielgründung vor Entstehung eines echten Spielstands. */
class WlanLobbyClient(
    private val endpunkt: WlanLobbyEndpunkt,
) {
    private val json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    suspend fun lesen(): LobbyDto = withContext(Dispatchers.IO) {
        get("/api/v1/lobbies/${endpunkt.lobbyId}")
    }

    suspend fun registrieren(name: String, passwort: String, farbe: String): LobbySitzungDto =
        withContext(Dispatchers.IO) {
            post(
                "/api/v1/lobbies/${endpunkt.lobbyId}/players",
                json.encodeToString(
                    LobbySpielerRegistrierenDto(name = name, passwort = passwort, farbe = farbe),
                ),
            )
        }

    suspend fun spielerAendern(token: String, anfrage: LobbySpielerAendernDto): LobbyDto =
        withContext(Dispatchers.IO) {
            sende(
                methode = "PATCH",
                pfad = "/api/v1/lobbies/${endpunkt.lobbyId}/players/me",
                inhalt = json.encodeToString(anfrage),
                token = token,
            )
        }

    suspend fun konfigurationAendern(token: String, konfiguration: LobbyKonfigurationDto): LobbyDto =
        withContext(Dispatchers.IO) {
            sende(
                methode = "PUT",
                pfad = "/api/v1/lobbies/${endpunkt.lobbyId}/configuration",
                inhalt = json.encodeToString(konfiguration),
                token = token,
            )
        }

    suspend fun starten(token: String): LobbyGestartetDto = withContext(Dispatchers.IO) {
        post("/api/v1/lobbies/${endpunkt.lobbyId}/start", "{}", token)
    }

    private inline fun <reified T> get(pfad: String): T = sende("GET", pfad)

    private inline fun <reified T> post(pfad: String, inhalt: String, token: String? = null): T =
        sende("POST", pfad, inhalt, token)

    private inline fun <reified T> sende(
        methode: String,
        pfad: String,
        inhalt: String = "",
        token: String? = null,
    ): T {
        val verbindung = (URL(endpunkt.basisUrl + pfad).openConnection() as HttpURLConnection).apply {
            requestMethod = methode
            connectTimeout = 5_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (inhalt.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(inhalt.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val status = verbindung.responseCode
            val strom = if (status in 200..299) verbindung.inputStream else verbindung.errorStream
            val text = strom?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val fehler = runCatching { json.decodeFromString<FehlerAntwortDto>(text) }.getOrNull()
                throw IOException(fehler?.meldung ?: "WLAN-Lobbyanfrage wurde mit HTTP $status abgelehnt.")
            }
            return json.decodeFromString(text)
        } finally {
            verbindung.disconnect()
        }
    }
}
