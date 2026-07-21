package de.teutonstudio.zentralbank.adapter.json

import de.teutonstudio.zentralbank.anwendung.AKTUELLE_ENGINE_VERSION
import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import kotlinx.serialization.Serializable

const val AKTUELLE_JSON_SCHEMA_VERSION = 1

@Serializable
data class GespeichertesSpielFormat(
    val schemaVersion: Int = AKTUELLE_JSON_SCHEMA_VERSION,
    val engineVersion: String = AKTUELLE_ENGINE_VERSION,
    val spielId: String,
    val startzustand: SpielZustand,
    val ereignisse: List<SpielEreignis>,
    val seed: Long? = null,
)

internal fun GespeichertesSpiel.zuJsonFormat(): GespeichertesSpielFormat =
    GespeichertesSpielFormat(
        engineVersion = engineVersion,
        spielId = id.toString(),
        startzustand = startzustand,
        ereignisse = ereignisse,
        seed = seed,
    )

internal fun GespeichertesSpielFormat.zuGespeichertemSpiel(): GespeichertesSpiel {
    require(schemaVersion == AKTUELLE_JSON_SCHEMA_VERSION) {
        "Nicht unterstützte JSON-Schema-Version $schemaVersion."
    }
    val id = spielId.toLongOrNull()
        ?: error("Die Spiel-ID '$spielId' ist für diesen Ablage-Port ungültig.")
    return GespeichertesSpiel(
        id = id,
        startzustand = startzustand,
        ereignisse = ereignisse,
        schemaVersion = schemaVersion,
        engineVersion = engineVersion,
        seed = seed,
    )
}
