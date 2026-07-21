package de.teutonstudio.zentralbank.adapter.json

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ArbeitsspeicherSpielAblage : SpielAblage {
    private val sperre = Mutex()
    private val spiele = MutableStateFlow<Map<Long, GespeichertesSpiel>>(emptyMap())

    override fun spielstaendeBeobachten(): Flow<List<SpielstandUebersicht>> =
        spiele.map { bestand ->
            bestand.values.sortedBy(GespeichertesSpiel::id).map(GespeichertesSpiel::zuUebersicht)
        }

    override suspend fun spielLaden(id: Long): GespeichertesSpiel? = sperre.withLock {
        spiele.value[id]
    }

    override suspend fun spielSpeichern(spiel: GespeichertesSpiel) {
        require(spiel.id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        sperre.withLock { spiele.value = spiele.value + (spiel.id to spiel) }
    }

    override suspend fun spielLoeschen(id: Long) {
        require(id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        sperre.withLock { spiele.value = spiele.value - id }
    }
}
