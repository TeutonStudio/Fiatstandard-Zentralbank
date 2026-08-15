package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.protokoll.API_VERSION
import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.FehlerAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielBeitretenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielErstellenAnfrageDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class NetzwerkAnfrage(
    val methode: String,
    val pfad: String,
    val kopfzeilen: Map<String, String> = emptyMap(),
    val inhalt: String = "",
)

data class NetzwerkAntwort(
    val status: Int,
    val inhalt: String,
    val inhaltstyp: String = "application/json; charset=utf-8",
)

/** Gemeinsamer HTTP-unabhängiger Router für JVM-Server und Android-WLAN-Host. */
class SpielNetzwerkRouter(
    private val dienst: SpielNetzwerkDienst,
) {
    private val json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun bearbeiten(anfrage: NetzwerkAnfrage): NetzwerkAntwort = try {
        val methode = anfrage.methode.uppercase()
        val teile = anfrage.pfad.substringBefore('?').trim('/').split('/').filter(String::isNotBlank)
        when {
            methode == "GET" && teile == listOf("health") ->
                jsonAntwort(
                    200,
                    buildJsonObject {
                        put("status", "ok")
                        put("version", API_VERSION)
                    }.toString(),
                )

            methode == "POST" && teile == listOf("api", "v1", "games") -> runBlocking {
                val dto = json.decodeFromString<SpielErstellenAnfrageDto>(anfrage.inhalt)
                jsonAntwort(201, json.encodeToString(dienst.erstellen(dto)))
            }

            methode == "POST" && teile.size == 5 &&
                teile.take(3) == listOf("api", "v1", "games") && teile[4] == "join" -> runBlocking {
                val id = spielId(teile[3])
                val dto = json.decodeFromString<SpielBeitretenAnfrageDto>(anfrage.inhalt)
                jsonAntwort(200, json.encodeToString(dienst.beitreten(id, dto)))
            }

            methode == "GET" && teile.size == 4 &&
                teile.take(3) == listOf("api", "v1", "games") -> runBlocking {
                jsonAntwort(200, json.encodeToString(dienst.laden(spielId(teile[3]))))
            }

            methode == "GET" && teile.size == 5 &&
                teile.take(3) == listOf("api", "v1", "games") && teile[4] == "actions" -> runBlocking {
                val id = spielId(teile[3])
                jsonAntwort(200, json.encodeToString(dienst.erlaubteAktionen(id, bearer(anfrage))))
            }

            methode == "GET" && teile.size == 5 &&
                teile.take(3) == listOf("api", "v1", "games") && teile[4] == "observation" -> runBlocking {
                val id = spielId(teile[3])
                jsonAntwort(200, json.encodeToString(dienst.beobachten(id, bearer(anfrage))))
            }

            methode == "POST" && teile.size == 5 &&
                teile.take(3) == listOf("api", "v1", "games") && teile[4] == "actions" -> runBlocking {
                val id = spielId(teile[3])
                val dto = json.decodeFromString<AktionAusfuehrenAnfrageDto>(anfrage.inhalt)
                jsonAntwort(200, json.encodeToString(dienst.aktionAusfuehren(id, bearer(anfrage), dto)))
            }

            else -> fehlerAntwort(404, "ROUTE_NICHT_GEFUNDEN", "Route nicht gefunden.")
        }
    } catch (ursache: NetzwerkSpielNichtGefunden) {
        fehlerAntwort(404, "SPIEL_NICHT_GEFUNDEN", ursache.message ?: "Spiel nicht gefunden.")
    } catch (ursache: UngueltigeSpielSitzung) {
        fehlerAntwort(401, "UNGUELTIGE_SITZUNG", ursache.message ?: "Ungültige Spielsitzung.")
    } catch (ursache: RevisionKonflikt) {
        NetzwerkAntwort(
            status = 409,
            inhalt = json.encodeToString(
                FehlerAntwortDto(
                    code = "REVISION_KONFLIKT",
                    meldung = ursache.message ?: "Der Clientzustand ist veraltet.",
                    details = mapOf(
                        "erwartet" to ursache.erwartet.toString(),
                        "aktuell" to ursache.aktuell.toString(),
                    ),
                ),
            ),
        )
    } catch (ursache: SerializationException) {
        fehlerAntwort(400, "UNGUELTIGES_JSON", ursache.message ?: "JSON konnte nicht gelesen werden.")
    } catch (ursache: IllegalArgumentException) {
        fehlerAntwort(422, "AKTION_ABGELEHNT", ursache.message ?: "Anfrage wurde fachlich abgelehnt.")
    } catch (ursache: IllegalStateException) {
        fehlerAntwort(409, "ZUSTANDSKONFLIKT", ursache.message ?: "Anfrage widerspricht dem aktuellen Zustand.")
    } catch (ursache: Exception) {
        fehlerAntwort(500, "INTERNER_FEHLER", ursache.message ?: "Interner Fehler.")
    }

    private fun bearer(anfrage: NetzwerkAnfrage): String {
        val wert = anfrage.kopfzeilen.entries
            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value
            ?.trim()
            .orEmpty()
        if (!wert.startsWith("Bearer ", ignoreCase = true)) throw UngueltigeSpielSitzung()
        return wert.substringAfter(' ').trim().takeIf(String::isNotEmpty)
            ?: throw UngueltigeSpielSitzung()
    }

    private fun spielId(text: String): Long = text.toLongOrNull()
        ?.takeIf { it >= 0 }
        ?: throw IllegalArgumentException("Ungültige Spiel-ID '$text'.")

    private fun jsonAntwort(status: Int, text: String) = NetzwerkAntwort(status, text)

    private fun fehlerAntwort(status: Int, code: String, meldung: String) = NetzwerkAntwort(
        status = status,
        inhalt = json.encodeToString(FehlerAntwortDto(code = code, meldung = meldung)),
    )
}
