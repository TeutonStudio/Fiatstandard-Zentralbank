package de.teutonstudio.zentralbank.daten.karten

import android.content.Context
import de.teutonstudio.zentralbank.fachlogik.modell.AKTUELLE_KARTEN_FORMAT_VERSION
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    val migrationsHinweise: List<String> = emptyList(),
) {
    val mussAlsKopieGespeichertWerden: Boolean get() = migrationsHinweise.isNotEmpty()
}

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
            .map { datei -> datei.readText().zuKartenEintrag(KartenQuelle.EIGENE_KARTE) }

        (vorlagen + eigeneKarten).sortedWith(
            compareBy<KartenEintrag>(KartenEintrag::quelle)
                .thenBy { eintrag -> eintrag.vorlage.name.lowercase() },
        )
    }

    suspend fun eigeneKarteSpeichern(
        vorlage: KartenVorlage,
        alsNeueKopie: Boolean = false,
    ): KartenVorlage = withContext(Dispatchers.IO) {
        val gespeicherteVorlage = if (!alsNeueKopie && vorlage.id.startsWith(EIGENE_ID_PRAEFIX)) {
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
        val objekt = json.parseToJsonElement(this).jsonObject
        val version = objekt["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        return when (version) {
            1 -> {
                val alt = json.decodeFromString<AlteKartenVorlageV1>(this)
                KartenEintrag(
                    vorlage = KartenVorlage(
                        id = alt.id,
                        name = alt.name,
                        zeilen = alt.zeilen,
                        spalten = alt.spalten,
                        startZeile = alt.startZeile,
                        startSpalte = alt.startSpalte,
                        gelaendefelder = alt.landfelder,
                    ),
                    quelle = quelle,
                    migrationsHinweise = listOf(
                        "Kartenformat 1 wurde als Geländevorlage in Format 2 eingelesen.",
                    ) + alt.spezialfelder.map { spezialfeld ->
                        "${spezialfeld.name} (${spezialfeld.typ}) wurde nicht als Spielbelegung " +
                            "übernommen; bitte im Spielmodus regelkonform platzieren."
                    },
                )
            }
            AKTUELLE_KARTEN_FORMAT_VERSION -> KartenEintrag(
                vorlage = json.decodeFromString<KartenVorlage>(this),
                quelle = quelle,
            )
            else -> error("Nicht unterstützte Kartenformatversion: $version.")
        }
    }

    companion object {
        private const val VORLAGEN_PFAD = "karten/vorlagen"
        private const val JSON_ENDUNG = ".json"
        private const val EIGENE_ID_PRAEFIX = "eigene-"
    }
}

@Serializable
private data class AlteKartenVorlageV1(
    val id: String,
    val name: String,
    val zeilen: Int,
    val spalten: Int,
    val startZeile: Int = 0,
    val startSpalte: Int = 0,
    val landfelder: List<GelaendeFeld> = emptyList(),
    val spezialfelder: List<AltesSpezialfeld> = emptyList(),
)

@Serializable
private data class AltesSpezialfeld(
    val id: String,
    val name: String,
    val typ: String,
    val positionen: List<KartenFeld>,
)
