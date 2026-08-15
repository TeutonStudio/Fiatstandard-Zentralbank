package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.protokoll.FehlerAntwortDto
import de.teutonstudio.zentralbank.protokoll.LobbyErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.LobbyKonfigurationDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerAendernDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerRegistrierenDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Router für die Vor-Spiel-Lobby. Nach dem Start delegiert derselbe WLAN-Endpunkt die
 * freigegebene Spiel-ID an den bestehenden [SpielNetzwerkRouter].
 */
class MehrspielerLobbyRouter(
    private val lobbyDienst: MehrspielerLobbyDienst,
    private val spielDienst: SpielNetzwerkDienst,
    private val freigegebeneLobbyId: String? = null,
) {
    private val json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun bearbeiten(anfrage: NetzwerkAnfrage): NetzwerkAntwort = try {
        val methode = anfrage.methode.uppercase()
        val teile = anfrage.pfad.substringBefore('?').trim('/').split('/').filter(String::isNotBlank)

        if (teile.take(3) == listOf("api", "v1", "games")) {
            val spielId = teile.getOrNull(3)?.toLongOrNull()
                ?: return fehler(404, "SPIEL_NICHT_GEFUNDEN", "Spiel nicht gefunden.")
            val lobbyId = freigegebeneLobbyId
                ?: return fehler(404, "SPIEL_NICHT_GEFUNDEN", "Spiel nicht gefunden.")
            val freigegeben = lobbyDienst.lesen(lobbyId).spielId?.toLongOrNull()
            if (freigegeben == null || freigegeben != spielId) {
                return fehler(404, "SPIEL_NICHT_GEFUNDEN", "Spiel nicht gefunden.")
            }
            return SpielNetzwerkRouter(
                dienst = spielDienst,
                freigegebenesSpielId = freigegeben,
                spielErstellenErlaubt = false,
            ).bearbeiten(anfrage)
        }

        when {
            methode == "POST" && teile == listOf("api", "v1", "lobbies") && freigegebeneLobbyId == null ->
                runBlocking {
                    val dto = json.decodeFromString<LobbyErstellenAnfrageDto>(anfrage.inhalt)
                    NetzwerkAntwort(201, json.encodeToString(lobbyDienst.erstellen(dto)))
                }

            teile.size >= 4 && teile.take(3) == listOf("api", "v1", "lobbies") -> {
                val lobbyId = lobbyId(teile[3])
                when {
                    methode == "GET" && teile.size == 4 ->
                        NetzwerkAntwort(200, json.encodeToString(lobbyDienst.lesen(lobbyId)))

                    methode == "POST" && teile.size == 5 && teile[4] == "players" -> runBlocking {
                        val dto = json.decodeFromString<LobbySpielerRegistrierenDto>(anfrage.inhalt)
                        NetzwerkAntwort(201, json.encodeToString(lobbyDienst.spielerRegistrieren(lobbyId, dto)))
                    }

                    methode == "PATCH" && teile.size == 6 && teile[4] == "players" && teile[5] == "me" -> runBlocking {
                        val dto = json.decodeFromString<LobbySpielerAendernDto>(anfrage.inhalt)
                        NetzwerkAntwort(
                            200,
                            json.encodeToString(lobbyDienst.spielerAendern(lobbyId, bearer(anfrage), dto)),
                        )
                    }

                    methode == "PUT" && teile.size == 5 && teile[4] == "configuration" -> runBlocking {
                        val dto = json.decodeFromString<LobbyKonfigurationDto>(anfrage.inhalt)
                        NetzwerkAntwort(
                            200,
                            json.encodeToString(lobbyDienst.konfigurationAendern(lobbyId, bearer(anfrage), dto)),
                        )
                    }

                    methode == "POST" && teile.size == 5 && teile[4] == "start" -> runBlocking {
                        NetzwerkAntwort(
                            200,
                            json.encodeToString(lobbyDienst.starten(lobbyId, bearer(anfrage))),
                        )
                    }

                    else -> fehler(404, "ROUTE_NICHT_GEFUNDEN", "Route nicht gefunden.")
                }
            }

            else -> fehler(404, "ROUTE_NICHT_GEFUNDEN", "Route nicht gefunden.")
        }
    } catch (ursache: NetzwerkLobbyNichtGefunden) {
        fehler(404, "LOBBY_NICHT_GEFUNDEN", ursache.message ?: "Lobby nicht gefunden.")
    } catch (ursache: UngueltigerLobbyHost) {
        fehler(401, "UNGUELTIGER_HOST", ursache.message ?: "Lobby-Hostschlüssel ungültig.")
    } catch (ursache: UngueltigeSpielSitzung) {
        fehler(401, "UNGUELTIGE_SITZUNG", ursache.message ?: "Ungültige Sitzung.")
    } catch (ursache: SerializationException) {
        fehler(400, "UNGUELTIGES_JSON", ursache.message ?: "JSON konnte nicht gelesen werden.")
    } catch (ursache: IllegalArgumentException) {
        fehler(422, "ANFRAGE_ABGELEHNT", ursache.message ?: "Anfrage wurde abgelehnt.")
    } catch (ursache: IllegalStateException) {
        fehler(409, "ZUSTANDSKONFLIKT", ursache.message ?: "Anfrage widerspricht dem Lobbyzustand.")
    } catch (ursache: Exception) {
        fehler(500, "INTERNER_FEHLER", ursache.message ?: "Interner Fehler.")
    }

    private fun lobbyId(id: String): String {
        if (freigegebeneLobbyId != null && id != freigegebeneLobbyId) throw NetzwerkLobbyNichtGefunden(id)
        return id
    }

    private fun bearer(anfrage: NetzwerkAnfrage): String {
        val wert = anfrage.kopfzeilen.entries
            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value
            ?.trim()
            .orEmpty()
        require(wert.startsWith("Bearer ", ignoreCase = true)) { "Authorization Bearer fehlt." }
        return wert.substringAfter(' ').trim().takeIf(String::isNotEmpty)
            ?: throw UngueltigeSpielSitzung()
    }

    private fun fehler(status: Int, code: String, meldung: String) = NetzwerkAntwort(
        status = status,
        inhalt = json.encodeToString(FehlerAntwortDto(code = code, meldung = meldung)),
    )
}
