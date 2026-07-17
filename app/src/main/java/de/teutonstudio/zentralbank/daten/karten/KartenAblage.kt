package de.teutonstudio.zentralbank.daten.karten

import android.content.Context
import de.teutonstudio.zentralbank.fachlogik.modell.AKTUELLE_KARTEN_FORMAT_VERSION
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class KartenQuelle {
    VORLAGE,
    EIGENE_KARTE,
}

data class KartenEintrag(
    val vorlage: KartenVorlage,
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
                text.zuKartenEintrag(KartenQuelle.VORLAGE)
            }

        val eigeneKarten = eigeneKartenVerzeichnis
            .takeIf(File::isDirectory)
            ?.listFiles { datei -> datei.isFile && datei.extension == "json" }
            .orEmpty()
            .sortedBy { datei -> datei.name }
            .mapNotNull { datei ->
                val text = datei.readText()
                text.takeIf { it.kartenFormatVersion() == AKTUELLE_KARTEN_FORMAT_VERSION }
                    ?.zuKartenEintrag(KartenQuelle.EIGENE_KARTE)
            }

        (vorlagen + eigeneKarten).sortedWith(
            compareBy<KartenEintrag>(KartenEintrag::quelle)
                .thenBy { eintrag -> eintrag.vorlage.name.lowercase() },
        )
    }

    suspend fun eigeneKarteSpeichern(
        vorlage: KartenVorlage,
    ): KartenVorlage = withContext(Dispatchers.IO) {
        val gespeicherteVorlage = if (vorlage.id.startsWith(EIGENE_ID_PRAEFIX)) {
            vorlage.copy(formatVersion = AKTUELLE_KARTEN_FORMAT_VERSION)
        } else {
            vorlage.copy(
                formatVersion = AKTUELLE_KARTEN_FORMAT_VERSION,
                id = "$EIGENE_ID_PRAEFIX${UUID.randomUUID()}",
            )
        }
        require(eigeneKartenVerzeichnis.exists() || eigeneKartenVerzeichnis.mkdirs()) {
            "Verzeichnis für eigene Karten konnte nicht angelegt werden."
        }
        val ziel = File(eigeneKartenVerzeichnis, "${gespeicherteVorlage.id}.json")
        val temporaer = File(eigeneKartenVerzeichnis, "${gespeicherteVorlage.id}.json.tmp")
        temporaer.writeText(json.encodeToString(gespeicherteVorlage))
        try {
            Files.move(temporaer.toPath(), ziel.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaer.toPath(), ziel.toPath(), REPLACE_EXISTING)
        }
        gespeicherteVorlage
    }

    private fun String.zuKartenEintrag(quelle: KartenQuelle): KartenEintrag {
        val version = kartenFormatVersion()
        require(version == AKTUELLE_KARTEN_FORMAT_VERSION) {
            "Nicht unterstützte Kartenformatversion: ${version ?: "nicht angegeben"}."
        }
        return KartenEintrag(
            vorlage = json.decodeFromString<KartenVorlage>(this),
            quelle = quelle,
        )
    }

    private fun String.kartenFormatVersion(): Int? =
        json.parseToJsonElement(this)
            .jsonObject["formatVersion"]
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()

    companion object {
        private const val VORLAGEN_PFAD = "karten/vorlagen"
        private const val JSON_ENDUNG = ".json"
        private const val EIGENE_ID_PRAEFIX = "eigene-"
    }
}
