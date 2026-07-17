package de.teutonstudio.zentralbank.daten.karten

import android.content.Context
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class KartenQuelle {
    VORLAGE,
    EIGENE_KARTE,
}

data class KartenEintrag(
    val karte: Spielkarte,
    val quelle: KartenQuelle,
)

class KartenAblage(context: Context) {
    private val anwendungskontext = context.applicationContext
    private val eigeneKartenVerzeichnis = File(anwendungskontext.filesDir, "karten/eigene")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun alleKartenLaden(): List<KartenEintrag> = withContext(Dispatchers.IO) {
        val vorlagen = anwendungskontext.assets
            .list(VORLAGEN_PFAD)
            .orEmpty()
            .filter { dateiname -> dateiname.endsWith(JSON_ENDUNG) }
            .sorted()
            .map { dateiname ->
                val text = anwendungskontext.assets
                    .open("$VORLAGEN_PFAD/$dateiname")
                    .bufferedReader()
                    .use { leser -> leser.readText() }
                KartenEintrag(
                    karte = json.decodeFromString<Spielkarte>(text),
                    quelle = KartenQuelle.VORLAGE,
                )
            }

        val eigeneKarten = eigeneKartenVerzeichnis
            .takeIf(File::isDirectory)
            ?.listFiles { datei -> datei.isFile && datei.extension == "json" }
            .orEmpty()
            .sortedBy { datei -> datei.name }
            .map { datei ->
                KartenEintrag(
                    karte = json.decodeFromString<Spielkarte>(datei.readText()),
                    quelle = KartenQuelle.EIGENE_KARTE,
                )
            }

        (vorlagen + eigeneKarten).sortedWith(
            compareBy<KartenEintrag>(KartenEintrag::quelle)
                .thenBy { eintrag -> eintrag.karte.name.lowercase() },
        )
    }

    suspend fun eigeneKarteSpeichern(karte: Spielkarte): Spielkarte = withContext(Dispatchers.IO) {
        val gespeicherteKarte = if (karte.id.startsWith(EIGENE_ID_PRAEFIX)) {
            karte
        } else {
            karte.copy(id = "$EIGENE_ID_PRAEFIX${UUID.randomUUID()}")
        }
        require(eigeneKartenVerzeichnis.exists() || eigeneKartenVerzeichnis.mkdirs()) {
            "Verzeichnis für eigene Karten konnte nicht angelegt werden."
        }
        val ziel = File(eigeneKartenVerzeichnis, "${gespeicherteKarte.id}.json")
        val temporaer = File(eigeneKartenVerzeichnis, "${gespeicherteKarte.id}.json.tmp")
        temporaer.writeText(json.encodeToString(gespeicherteKarte))
        try {
            Files.move(temporaer.toPath(), ziel.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaer.toPath(), ziel.toPath(), REPLACE_EXISTING)
        }
        gespeicherteKarte
    }

    companion object {
        private const val VORLAGEN_PFAD = "karten/vorlagen"
        private const val JSON_ENDUNG = ".json"
        private const val EIGENE_ID_PRAEFIX = "eigene-"
    }
}
