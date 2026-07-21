package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsRaum
import de.teutonstudio.zentralbank.fachlogik.beobachtung.AKTUELLE_BEOBACHTUNGS_VERSION
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.engine.AKTUELLE_REGEL_VERSION
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielErgebnis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val AKTUELLE_EPISODEN_FORMAT_VERSION = 2
const val AKTUELLE_AKTIONS_VERSION = 1

@Serializable
data class EntscheidungsDatensatz(
    val formatVersion: Int = AKTUELLE_EPISODEN_FORMAT_VERSION,
    val regelVersion: String = AKTUELLE_REGEL_VERSION,
    val beobachtungsVersion: Int = AKTUELLE_BEOBACHTUNGS_VERSION,
    val aktionsVersion: Int = AKTUELLE_AKTIONS_VERSION,
    val spielId: String,
    val entscheidungsNummer: Long,
    val spieler: SpielerId,
    val beobachtung: SpielBeobachtung,
    val erlaubteAktionen: AktionsRaum,
    val gewaehlteAktion: SpielAktion,
    val belohnung: Float,
    val ergebnis: SpielErgebnis?,
) {
    init {
        require(formatVersion == AKTUELLE_EPISODEN_FORMAT_VERSION) {
            "Nicht unterstützte Entscheidungsformatversion: $formatVersion."
        }
        require(regelVersion == AKTUELLE_REGEL_VERSION) {
            "Nicht unterstützte Regelversion: $regelVersion."
        }
        require(beobachtungsVersion == AKTUELLE_BEOBACHTUNGS_VERSION) {
            "Nicht unterstützte Beobachtungsversion: $beobachtungsVersion."
        }
        require(aktionsVersion == AKTUELLE_AKTIONS_VERSION) {
            "Nicht unterstützte Aktionsversion: $aktionsVersion."
        }
        require(entscheidungsNummer >= 0L) { "Entscheidungsnummer darf nicht negativ sein." }
        require(belohnung.isFinite()) { "Belohnung muss eine endliche Zahl sein." }
    }
}

@Serializable
data class SpielEpisode(
    val formatVersion: Int = AKTUELLE_EPISODEN_FORMAT_VERSION,
    val regelVersion: String = AKTUELLE_REGEL_VERSION,
    val beobachtungsVersion: Int = AKTUELLE_BEOBACHTUNGS_VERSION,
    val aktionsVersion: Int = AKTUELLE_AKTIONS_VERSION,
    val spielId: String,
    val seed: Long,
    val szenarioId: String,
    val startzustand: SpielZustand,
    val entscheidungen: List<EntscheidungsDatensatz>,
    val ereignisse: List<SpielEreignis>,
    val ergebnis: SpielErgebnis?,
    val truncated: Boolean,
) {
    init {
        require(formatVersion == AKTUELLE_EPISODEN_FORMAT_VERSION) {
            "Nicht unterstützte Episodenformatversion: $formatVersion."
        }
        require(regelVersion == AKTUELLE_REGEL_VERSION) {
            "Nicht unterstützte Regelversion: $regelVersion."
        }
        require(beobachtungsVersion == AKTUELLE_BEOBACHTUNGS_VERSION) {
            "Nicht unterstützte Beobachtungsversion: $beobachtungsVersion."
        }
        require(aktionsVersion == AKTUELLE_AKTIONS_VERSION) {
            "Nicht unterstützte Aktionsversion: $aktionsVersion."
        }
        require(startzustand.spieler.all { spieler -> spieler.passwortHash.isBlank() }) {
            "Eine Trainingsepisode darf keine Passwort-Hashes enthalten."
        }
        require(entscheidungen.withIndex().all { (index, entscheidung) ->
            entscheidung.spielId == spielId &&
                entscheidung.entscheidungsNummer == index.toLong() &&
                entscheidung.formatVersion == formatVersion &&
                entscheidung.regelVersion == regelVersion &&
                entscheidung.beobachtungsVersion == beobachtungsVersion &&
                entscheidung.aktionsVersion == aktionsVersion
        }) {
            "Entscheidungen müssen lückenlos nummeriert sein und zum Episodenkopf gehören."
        }
        require(!(truncated && ergebnis != null)) {
            "Eine fachlich beendete Episode darf nicht zugleich trunciert sein."
        }
    }

    fun replay(): SpielZustand = SpielAblauf(startzustand, ereignisse).zustand
}

object EpisodenJsonl {
    val json: Json = Json {
        classDiscriminator = "art"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun exportieren(datei: Path, episoden: Sequence<SpielEpisode>) {
        datei.parent?.let(Files::createDirectories)
        Files.newBufferedWriter(datei).use { writer ->
            episoden.forEach { episode ->
                val text = json.encodeToString(episode)
                check(!text.contains("passwort", ignoreCase = true)) {
                    "Trainingsdaten dürfen keine Passwortfelder enthalten."
                }
                writer.appendLine(text)
            }
        }
    }

    fun importieren(datei: Path): Sequence<SpielEpisode> = Files.newBufferedReader(datei).useLines {
        it.filter(String::isNotBlank)
            .map { zeile -> json.decodeFromString<SpielEpisode>(zeile) }
            .toList()
            .asSequence()
    }
}

fun SpielZustand.ohnePasswoerter(): SpielZustand = copy(
    spieler = spieler.map { it.copy(passwortHash = "") },
)
