package de.teutonstudio.zentralbank.adapter.json

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class JsonDateiSpielAblage(
    private val verzeichnis: Path,
) : SpielAblage {
    private val sperre = Mutex()
    private val uebersichten = MutableStateFlow<List<SpielstandUebersicht>>(emptyList())

    init {
        Files.createDirectories(verzeichnis)
        uebersichten.value = ladeAlleSynchron().map(GespeichertesSpiel::zuUebersicht)
    }

    override fun spielstaendeBeobachten(): Flow<List<SpielstandUebersicht>> =
        uebersichten.asStateFlow()

    override suspend fun spielLaden(id: Long): GespeichertesSpiel? = withContext(Dispatchers.IO) {
        sperre.withLock { ladeSynchron(id) }
    }

    override suspend fun spielSpeichern(spiel: GespeichertesSpiel) = withContext(Dispatchers.IO) {
        require(spiel.id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        sperre.withLock {
            val ziel = dateiFuer(spiel.id)
            val temporaer = Files.createTempFile(verzeichnis, ".spiel-${spiel.id}-", ".tmp")
            try {
                Files.writeString(temporaer, spielstandJson.encodeToString(spiel.zuJsonFormat()))
                try {
                    Files.move(temporaer, ziel, ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporaer, ziel, REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporaer)
            }
            aktualisiereUebersichten()
        }
    }

    override suspend fun spielLoeschen(id: Long) = withContext(Dispatchers.IO) {
        require(id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        sperre.withLock {
            Files.deleteIfExists(dateiFuer(id))
            aktualisiereUebersichten()
        }
    }

    private fun ladeSynchron(id: Long): GespeichertesSpiel? {
        val datei = dateiFuer(id)
        if (!Files.exists(datei)) return null
        return spielstandJson.decodeFromString<GespeichertesSpielFormat>(Files.readString(datei))
            .zuGespeichertemSpiel()
            .also { require(it.id == id) { "Spiel-ID in ${datei.fileName} stimmt nicht überein." } }
    }

    private fun ladeAlleSynchron(): List<GespeichertesSpiel> {
        if (!Files.exists(verzeichnis)) return emptyList()
        return Files.list(verzeichnis).use { dateien ->
            dateien
                .filter { datei -> datei.fileName.toString().matches(SPIEL_DATEI_REGEX) }
                .map { datei ->
                    spielstandJson.decodeFromString<GespeichertesSpielFormat>(Files.readString(datei))
                        .zuGespeichertemSpiel()
                }
                .sorted { links, rechts -> links.id.compareTo(rechts.id) }
                .toList()
        }
    }

    private fun aktualisiereUebersichten() {
        uebersichten.value = ladeAlleSynchron().map(GespeichertesSpiel::zuUebersicht)
    }

    private fun dateiFuer(id: Long): Path = verzeichnis.resolve("spiel-$id.json")

    private companion object {
        val SPIEL_DATEI_REGEX = Regex("spiel-[0-9]+\\.json")
    }
}
