package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AKTUELLE_AKTIONS_SCHEMA_VERSION
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsRaum
import de.teutonstudio.zentralbank.fachlogik.beobachtung.AKTUELLE_BEOBACHTUNGS_VERSION
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.engine.AKTUELLE_REGEL_VERSION
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val AKTUELLE_EPISODEN_FORMAT_VERSION = 2
const val AKTUELLE_AKTIONS_VERSION = AKTUELLE_AKTIONS_SCHEMA_VERSION

@Serializable
data class SpielerUebergang(
    val spieler: SpielerId,
    val startEntscheidung: Long,
    val endEntscheidungExklusiv: Long,
    val akkumulierteBelohnung: Float,
    val beendetDurch: String,
)

@Serializable
data class TechnischeAbbruchDiagnose(
    val grund: String,
    val entscheidungenOhneMarktwertAenderung: Int,
    val letzteAktionen: List<SpielAktion>,
    val letzterZustand: SpielZustand,
)

@Serializable
data class EntscheidungsDatensatz(
    val formatVersion: Int = AKTUELLE_EPISODEN_FORMAT_VERSION,
    val regelVersion: String = AKTUELLE_REGEL_VERSION,
    val beobachtungsVersion: Int = AKTUELLE_BEOBACHTUNGS_VERSION,
    val aktionsVersion: Int = AKTUELLE_AKTIONS_VERSION,
    val spielId: String,
    val entscheidungsNummer: Long,
    val spieler: SpielerId,
    val spielstil: SpielerStil,
    val beobachtung: SpielBeobachtung,
    val erlaubteAktionen: AktionsRaum,
    val gewaehlteAktion: SpielAktion,
    val belohnungen: Map<SpielerId, Float>,
    val terminated: Boolean,
    val truncated: Boolean,
    val ausscheidensGruende: Map<SpielerId, AusscheidensGrund>,
    val naechsterAktiverSpieler: SpielerId?,
    val ereignisse: List<SpielEreignis>,
    val marktwerteVorher: Map<SpielerId, Geld>,
    val marktwerteNachher: Map<SpielerId, Geld>,
    val ergebnis: SpielErgebnis?,
) {
    init {
        require(formatVersion == AKTUELLE_EPISODEN_FORMAT_VERSION)
        require(regelVersion == AKTUELLE_REGEL_VERSION)
        require(beobachtungsVersion == AKTUELLE_BEOBACHTUNGS_VERSION)
        require(aktionsVersion == AKTUELLE_AKTIONS_VERSION)
        require(entscheidungsNummer >= 0L)
        require(belohnungen.values.all(Float::isFinite)) { "Belohnungen müssen endlich sein." }
        require(!(terminated && truncated)) { "Ein Schritt kann nicht beendet und trunciert sein." }
        require(gewaehlteAktion in erlaubteAktionen.aktionen) {
            "Die gewählte Aktion muss in der vollständigen legalen Kandidatenliste stehen."
        }
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
    val spielerUebergaenge: List<SpielerUebergang>,
    val ereignisse: List<SpielEreignis>,
    val ergebnis: SpielErgebnis?,
    val truncated: Boolean,
    val abbruchDiagnose: TechnischeAbbruchDiagnose? = null,
) {
    init {
        require(formatVersion == AKTUELLE_EPISODEN_FORMAT_VERSION)
        require(regelVersion == AKTUELLE_REGEL_VERSION)
        require(beobachtungsVersion == AKTUELLE_BEOBACHTUNGS_VERSION)
        require(aktionsVersion == AKTUELLE_AKTIONS_VERSION)
        require(startzustand.spieler.all { it.passwortHash.isBlank() }) {
            "Eine Trainingsepisode darf keine Passwort-Hashes enthalten."
        }
        require(entscheidungen.withIndex().all { (index, entscheidung) ->
            entscheidung.spielId == spielId && entscheidung.entscheidungsNummer == index.toLong() &&
                entscheidung.formatVersion == formatVersion &&
                entscheidung.regelVersion == regelVersion &&
                entscheidung.beobachtungsVersion == beobachtungsVersion &&
                entscheidung.aktionsVersion == aktionsVersion
        }) { "Entscheidungen müssen lückenlos nummeriert sein und zum Episodenkopf gehören." }
        require(!(truncated && ergebnis != null))
        require((abbruchDiagnose != null) == truncated) {
            "Eine truncierte Episode braucht genau eine technische Diagnose."
        }
    }

    fun replay(): SpielZustand = SpielAblauf(startzustand, ereignisse).zustand

    companion object {
        fun spielerUebergaenge(entscheidungen: List<EntscheidungsDatensatz>): List<SpielerUebergang> =
            entscheidungen.flatMapIndexed { index, start ->
                val spieler = start.spieler
                val naechsterEigenerIndex = ((index + 1) until entscheidungen.size).firstOrNull {
                    entscheidungen[it].spieler == spieler
                }
                val ausscheidensIndex = (index until entscheidungen.size).firstOrNull {
                    spieler in entscheidungen[it].ausscheidensGruende
                }
                val endIndex = listOfNotNull(naechsterEigenerIndex, ausscheidensIndex?.plus(1))
                    .minOrNull() ?: entscheidungen.size
                val rewards = entscheidungen.subList(index, endIndex).sumOf {
                    it.belohnungen.getOrDefault(spieler, 0f).toDouble()
                }.toFloat()
                listOf(
                    SpielerUebergang(
                        spieler = spieler,
                        startEntscheidung = index.toLong(),
                        endEntscheidungExklusiv = endIndex.toLong(),
                        akkumulierteBelohnung = rewards,
                        beendetDurch = when {
                            ausscheidensIndex != null && ausscheidensIndex < endIndex -> "AUSGESCHIEDEN"
                            naechsterEigenerIndex != null && naechsterEigenerIndex == endIndex -> "NAECHSTE_AKTION"
                            entscheidungen.lastOrNull()?.truncated == true -> "TRUNCATED"
                            else -> "PARTIEENDE"
                        },
                    ),
                )
            }
    }
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
        it.filter(String::isNotBlank).map { zeile -> json.decodeFromString<SpielEpisode>(zeile) }
            .toList().asSequence()
    }
}

fun SpielZustand.ohnePasswoerter(): SpielZustand = copy(
    spieler = spieler.map { it.copy(passwortHash = "") },
)
