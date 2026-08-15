package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.AktionErgebnisDto
import de.teutonstudio.zentralbank.protokoll.ErlaubteAktionenDto
import de.teutonstudio.zentralbank.protokoll.FehlerAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielBeitretenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielBeobachtungAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielSitzungDto
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class WlanEndpunkt(
    val host: String,
    val port: Int,
    val spielId: Long,
    val name: String = "$host:$port",
) {
    val basisUrl: String
        get() {
            val urlHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
            return "http://$urlHost:$port"
        }
}

class WlanSpielClient(
    private val endpunkt: WlanEndpunkt,
) {
    private val json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    suspend fun beitreten(spielerName: String, passwort: String = ""): SpielSitzungDto =
        withContext(Dispatchers.IO) {
            post(
                pfad = "/api/v1/games/${endpunkt.spielId}/join",
                inhalt = json.encodeToString(
                    SpielBeitretenAnfrageDto(spielerName = spielerName, passwort = passwort),
                ),
            )
        }

    suspend fun beobachten(token: String): SpielBeobachtungAntwortDto = withContext(Dispatchers.IO) {
        get("/api/v1/games/${endpunkt.spielId}/observation", token)
    }

    suspend fun erlaubteAktionen(token: String): ErlaubteAktionenDto = withContext(Dispatchers.IO) {
        get("/api/v1/games/${endpunkt.spielId}/actions", token)
    }

    suspend fun aktionAusfuehren(
        token: String,
        anfrage: AktionAusfuehrenAnfrageDto,
    ): AktionErgebnisDto = withContext(Dispatchers.IO) {
        post(
            pfad = "/api/v1/games/${endpunkt.spielId}/actions",
            inhalt = json.encodeToString(anfrage),
            token = token,
        )
    }

    private inline fun <reified T> get(pfad: String, token: String): T =
        sende("GET", pfad, token = token)

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
                throw IOException(fehler?.meldung ?: "WLAN-Anfrage wurde mit HTTP $status abgelehnt.")
            }
            return json.decodeFromString(text)
        } finally {
            verbindung.disconnect()
        }
    }
}
