package de.teutonstudio.zentralbank.fachlogik.schnittstelle

import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

data class SpielstandUebersicht(
    val id: Long,
    val spielerNamen: List<String>,
    val runde: Int,
    val ausLegacyDatenImportiert: Boolean,
)

@Serializable
data class GespeichertesSpiel(
    val id: Long,
    val startzustand: SpielZustand,
    val ereignisse: List<SpielEreignis> = emptyList(),
    val ausLegacyDatenImportiert: Boolean = false,
) {
    fun aktuellerZustand(): SpielZustand = SpielAblauf(startzustand, ereignisse).zustand

    fun zuUebersicht(): SpielstandUebersicht {
        val zustand = aktuellerZustand()
        return SpielstandUebersicht(
            id = id,
            spielerNamen = zustand.spieler.map { spieler -> spieler.name },
            runde = zustand.rundenzähler,
            ausLegacyDatenImportiert = ausLegacyDatenImportiert,
        )
    }
}

interface SpielAblage {
    fun spielstaendeBeobachten(): Flow<List<SpielstandUebersicht>>

    suspend fun spielLaden(id: Long): GespeichertesSpiel?

    suspend fun spielSpeichern(spiel: GespeichertesSpiel)

    suspend fun spielLoeschen(id: Long)
}
