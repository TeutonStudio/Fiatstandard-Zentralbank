package de.teutonstudio.zentralbank.protokoll

import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val API_VERSION = 1

@Serializable
data class LobbyKonfigurationDto(
    val name: String = "Fiatstandard WLAN-Spiel",
    val maximaleSpieler: Int = 7,
    val karte: KartenVorlage,
    val leitzinsBasispunkte: Int = 1_500,
    val inflationszielBasispunkte: Int = 200,
    val normaleAbweichungBasispunkte: Int = 50,
    val starkeAbweichungBasispunkte: Int = 200,
    val leitzinsSchrittBasispunkte: Int = 100,
    val warenkorb: Map<String, Int> = emptyMap(),
    val startGuthabenCent: Long = 10_000,
    val startRohstoffe: Map<String, Int> = emptyMap(),
    val startBauteile: Map<String, Int> = mapOf("HAUPTBAHNHOF" to 1),
    val seed: Long? = null,
)

@Serializable
data class LobbyErstellenAnfrageDto(
    val version: Int = API_VERSION,
    val konfiguration: LobbyKonfigurationDto,
)

@Serializable
data class LobbyErstelltDto(
    val version: Int = API_VERSION,
    val lobbyId: String,
    val hostToken: String,
    val lobby: LobbyDto,
)

@Serializable
data class LobbySpielerRegistrierenDto(
    val version: Int = API_VERSION,
    val name: String,
    val passwort: String,
    val farbe: String,
)

@Serializable
data class LobbySpielerAendernDto(
    val version: Int = API_VERSION,
    val name: String? = null,
    val farbe: String? = null,
    val bereit: Boolean? = null,
)

@Serializable
data class LobbySpielerDto(
    val id: String,
    val name: String,
    val farbe: String,
    val bereit: Boolean,
    val verbunden: Boolean,
)

@Serializable
data class LobbyDto(
    val version: Int = API_VERSION,
    val lobbyId: String,
    val revision: Long,
    val status: String,
    val konfiguration: LobbyKonfigurationDto,
    val spieler: List<LobbySpielerDto>,
    val spielId: String? = null,
)

@Serializable
data class LobbySitzungDto(
    val version: Int = API_VERSION,
    val lobbyId: String,
    val spielerId: String,
    val sessionToken: String,
    val lobby: LobbyDto,
)

@Serializable
data class LobbyGestartetDto(
    val version: Int = API_VERSION,
    val lobbyId: String,
    val spielId: String,
    val lobby: LobbyDto,
)

@Serializable
data class SpielErstellenAnfrageDto(
    val version: Int = API_VERSION,
    val spielerNamen: List<String>,
    val seed: Long? = null,
    val spielstile: List<String> = emptyList(),
)

@Serializable
data class SpielErstelltDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val zustand: SpielZustandDto,
    val revision: Long = 0,
)

@Serializable
data class SpielBeitretenAnfrageDto(
    val version: Int = API_VERSION,
    val spielerName: String,
    val passwort: String = "",
)

@Serializable
data class SpielSitzungDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val spielerId: String,
    val sessionToken: String,
    val revision: Long,
)

@Serializable
data class SpielBeobachtungAntwortDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val spieler: String,
    val revision: Long,
    val beobachtung: SpielBeobachtung,
)

@Serializable
data class SpielLadenAntwortDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val engineVersion: String,
    val zustand: SpielZustandDto,
)

@Serializable
data class SpielerDto(
    val id: String,
    val name: String,
    val rohstoffe: Map<String, Int>,
    val geldCent: Long,
    val anleihen: List<String>,
    val bauteile: Map<String, Int>,
    val spielstil: String,
)

@Serializable
data class ZugDto(
    val zugId: Long,
    val spieler: String,
    val phase: String,
    val prozugBegonnen: Boolean,
    val prozugAbgeschlossen: Boolean,
)

@Serializable
data class SpielZustandDto(
    val spieler: List<SpielerDto>,
    val spielabschnitt: String,
    val runde: Int,
    val aktiverSpieler: String?,
    val zug: ZugDto?,
    val bankkontoCent: Long,
    val auslandskontoCent: Long,
    val zustandsHash: String,
)

@Serializable
data class ErlaubteAktionenDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val spieler: String,
    val aktionen: List<SpielAktionDto>,
    val revision: Long = 0,
)

@Serializable
data class AktionAusfuehrenAnfrageDto(
    val version: Int = API_VERSION,
    val aktion: SpielAktionDto,
    val commandId: String = "",
    val expectedRevision: Long? = null,
)

@Serializable
data class AktionErgebnisDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val zustand: SpielZustandDto,
    val ereignisse: List<SpielEreignisDto>,
    val revision: Long = 0,
    val commandId: String? = null,
)

@Serializable
data class FehlerAntwortDto(
    val version: Int = API_VERSION,
    val code: String,
    val meldung: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class SpielEreignisDto(
    val typ: String,
    val daten: JsonElement,
)

@Serializable
data class EreignisstromDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val abPosition: Int,
    val ereignisse: List<SpielEreignisDto>,
)

@Serializable
data class SimulationsschrittDto(
    val version: Int = API_VERSION,
    val episodeId: String,
    val engineVersion: String,
    val seed: Long,
    @SerialName("step")
    val schritt: Int,
    @SerialName("actor")
    val akteur: String,
    @SerialName("observation")
    val beobachtung: SpielZustandDto,
    @SerialName("legalActions")
    val erlaubteAktionen: List<SpielAktionDto>,
    @SerialName("chosenAction")
    val gewaehlteAktion: SpielAktionDto,
    @SerialName("rewardComponents")
    val belohnungsKomponenten: Map<String, Double>,
    @SerialName("nextObservation")
    val naechsteBeobachtung: SpielZustandDto,
    @SerialName("terminated")
    val beendet: Boolean,
    @SerialName("winner")
    val gewinner: String? = null,
    @SerialName("events")
    val ereignisse: List<SpielEreignisDto>,
)
