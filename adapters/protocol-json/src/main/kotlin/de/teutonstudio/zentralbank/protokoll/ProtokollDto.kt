package de.teutonstudio.zentralbank.protokoll

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val API_VERSION = 1

@Serializable
data class SpielErstellenAnfrageDto(
    val version: Int = API_VERSION,
    val spielerNamen: List<String>,
    val seed: Long? = null,
)

@Serializable
data class SpielErstelltDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val zustand: SpielZustandDto,
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
)

@Serializable
data class AktionAusfuehrenAnfrageDto(
    val version: Int = API_VERSION,
    val aktion: SpielAktionDto,
)

@Serializable
data class AktionErgebnisDto(
    val version: Int = API_VERSION,
    val spielId: String,
    val zustand: SpielZustandDto,
    val ereignisse: List<SpielEreignisDto>,
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
