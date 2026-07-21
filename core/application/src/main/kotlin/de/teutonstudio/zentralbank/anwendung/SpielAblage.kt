package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import de.teutonstudio.zentralbank.fachlogik.engine.AKTUELLE_REGEL_VERSION

const val AKTUELLE_ENGINE_VERSION = AKTUELLE_REGEL_VERSION
const val AKTUELLE_SPIELSTAND_SCHEMA_VERSION = 1

@Serializable
data class SpielstandUebersicht(
    val id: Long,
    val spielerNamen: List<String>,
    val runde: Int,
)

@Serializable
data class GespeichertesSpiel(
    val id: Long,
    val startzustand: SpielZustand,
    val ereignisse: List<SpielEreignis> = emptyList(),
    val schemaVersion: Int = AKTUELLE_SPIELSTAND_SCHEMA_VERSION,
    val engineVersion: String = AKTUELLE_ENGINE_VERSION,
    val seed: Long? = null,
) {
    fun aktuellerZustand(): SpielZustand = SpielAblauf(startzustand, ereignisse).zustand

    fun zuUebersicht(): SpielstandUebersicht {
        val zustand = aktuellerZustand()
        return SpielstandUebersicht(
            id = id,
            spielerNamen = zustand.spieler.map { spieler -> spieler.name },
            runde = zustand.rundenzähler,
        )
    }
}

interface SpielAblage {
    fun spielstaendeBeobachten(): Flow<List<SpielstandUebersicht>>

    suspend fun spielLaden(id: Long): GespeichertesSpiel?

    suspend fun spielSpeichern(spiel: GespeichertesSpiel)

    suspend fun spielLoeschen(id: Long)
}
