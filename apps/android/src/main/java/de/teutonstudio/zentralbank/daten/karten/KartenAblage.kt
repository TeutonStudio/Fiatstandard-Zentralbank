package de.teutonstudio.zentralbank.daten.karten

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import de.teutonstudio.zentralbank.fachlogik.modell.AKTUELLE_KARTEN_FORMAT_VERSION
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import java.io.File
import java.io.FileOutputStream
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
    val hatReferenzbild: Boolean = false,
)

class KartenAblage(context: Context) {
    private val anwendungskontext = context.applicationContext
    private val eigeneKartenVerzeichnis = File(anwendungskontext.filesDir, "karten/eigene")
    private val referenzenVerzeichnis = File(anwendungskontext.filesDir, "karten/referenzen")
    private val referenzEntwuerfeVerzeichnis =
        File(anwendungskontext.cacheDir, "karten/referenz-entwuerfe")
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
                    ?.let { eintrag ->
                        eintrag.copy(hatReferenzbild = referenzMetadatenDatei(eintrag.vorlage.id).isFile)
                    }
            }

        (vorlagen + eigeneKarten).sortedWith(
            compareBy<KartenEintrag>(KartenEintrag::quelle)
                .thenBy { eintrag -> eintrag.vorlage.name.lowercase() },
        )
    }

    suspend fun eigeneKarteSpeichern(
        vorlage: KartenVorlage,
        referenz: KartenReferenz?,
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
        var referenzTemporaer: Pair<File, File>? = null
        try {
            referenzTemporaer = referenz?.let {
                bereiteReferenzSpeicherungVor(gespeicherteVorlage.id, it)
            }
            verschiebeErsetzend(temporaer, ziel)
            if (referenzTemporaer == null) {
                referenzBildDatei(gespeicherteVorlage.id).delete()
                referenzMetadatenDatei(gespeicherteVorlage.id).delete()
            } else {
                verschiebeErsetzend(
                    referenzTemporaer.first,
                    referenzBildDatei(gespeicherteVorlage.id),
                )
                verschiebeErsetzend(
                    referenzTemporaer.second,
                    referenzMetadatenDatei(gespeicherteVorlage.id),
                )
            }
        } finally {
            temporaer.delete()
            referenzTemporaer?.first?.delete()
            referenzTemporaer?.second?.delete()
        }
        referenz?.takeIf(KartenReferenz::temporaer)?.bildDatei?.delete()
        gespeicherteVorlage
    }

    suspend fun referenzLaden(kartenId: String): KartenReferenz? = withContext(Dispatchers.IO) {
        val bild = referenzBildDatei(kartenId)
        val metadaten = referenzMetadatenDatei(kartenId)
        if (!bild.isFile || !metadaten.isFile) return@withContext null

        KartenReferenz(
            bildDatei = bild,
            metadaten = json.decodeFromString<KartenReferenzMetadaten>(metadaten.readText()),
        )
    }

    suspend fun referenzImportieren(
        uri: Uri,
        initialeBreiteInBrettEinheiten: Float,
    ): KartenReferenz = withContext(Dispatchers.IO) {
        require(
            anwendungskontext.contentResolver.getType(uri)?.startsWith("image/") != false,
        ) { "Die ausgewählte Datei ist kein Bild." }
        require(
            referenzEntwuerfeVerzeichnis.exists() || referenzEntwuerfeVerzeichnis.mkdirs(),
        ) { "Temporäres Verzeichnis für das Referenzbild konnte nicht angelegt werden." }

        val ziel = File(referenzEntwuerfeVerzeichnis, "${UUID.randomUUID()}.bild")
        try {
            anwendungskontext.contentResolver.openInputStream(uri).use { eingabe ->
                requireNotNull(eingabe) { "Das ausgewählte Bild konnte nicht geöffnet werden." }
                FileOutputStream(ziel).use { ausgabe ->
                    val puffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var gesamt = 0L
                    while (true) {
                        val gelesen = eingabe.read(puffer)
                        if (gelesen < 0) break
                        gesamt += gelesen
                        require(gesamt <= MAX_REFERENZ_DATEIGROESSE) {
                            "Das Referenzbild darf höchstens 25 MiB groß sein."
                        }
                        ausgabe.write(puffer, 0, gelesen)
                    }
                }
            }
            val optionen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ziel.absolutePath, optionen)
            require(optionen.outWidth > 0 && optionen.outHeight > 0) {
                "Die ausgewählte Datei konnte nicht als Bild gelesen werden."
            }
            require(optionen.outWidth.toLong() * optionen.outHeight <= MAX_REFERENZ_PIXEL) {
                "Das Referenzbild besitzt zu viele Bildpunkte."
            }
            KartenReferenz(
                bildDatei = ziel,
                metadaten = KartenReferenzMetadaten(
                    breiteInBrettEinheiten = initialeBreiteInBrettEinheiten
                        .coerceIn(MIN_REFERENZ_BREITE, MAX_REFERENZ_BREITE),
                ),
                temporaer = true,
            )
        } catch (fehler: Throwable) {
            ziel.delete()
            throw fehler
        }
    }

    suspend fun eigeneKarteLoeschen(kartenId: String): Boolean = withContext(Dispatchers.IO) {
        require(kartenId.startsWith(EIGENE_ID_PRAEFIX)) {
            "Gebündelte Kartenvorlagen können nicht gelöscht werden."
        }
        val geloescht = File(eigeneKartenVerzeichnis, "$kartenId.json").delete()
        referenzBildDatei(kartenId).delete()
        referenzMetadatenDatei(kartenId).delete()
        geloescht
    }

    fun referenzEntwurfVerwerfen(referenz: KartenReferenz?) {
        if (referenz?.temporaer == true) referenz.bildDatei.delete()
    }

    private fun bereiteReferenzSpeicherungVor(
        kartenId: String,
        referenz: KartenReferenz,
    ): Pair<File, File> {
        require(referenz.bildDatei.isFile) { "Das Referenzbild ist nicht mehr verfügbar." }
        require(referenzenVerzeichnis.exists() || referenzenVerzeichnis.mkdirs()) {
            "Verzeichnis für Referenzbilder konnte nicht angelegt werden."
        }
        val kennung = "$kartenId.${UUID.randomUUID()}.tmp"
        val bildTemporaer = File(referenzenVerzeichnis, "$kennung.bild")
        val metadatenTemporaer = File(referenzenVerzeichnis, "$kennung.json")
        try {
            referenz.bildDatei.copyTo(bildTemporaer, overwrite = true)
            metadatenTemporaer.writeText(json.encodeToString(referenz.metadaten))
            return bildTemporaer to metadatenTemporaer
        } catch (fehler: Throwable) {
            bildTemporaer.delete()
            metadatenTemporaer.delete()
            throw fehler
        }
    }

    private fun verschiebeErsetzend(quelle: File, ziel: File) {
        try {
            Files.move(quelle.toPath(), ziel.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(quelle.toPath(), ziel.toPath(), REPLACE_EXISTING)
        }
    }

    private fun referenzBildDatei(kartenId: String) = File(referenzenVerzeichnis, "$kartenId.bild")

    private fun referenzMetadatenDatei(kartenId: String) =
        File(referenzenVerzeichnis, "$kartenId.json")

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
        private const val MAX_REFERENZ_DATEIGROESSE = 25L * 1024L * 1024L
        private const val MAX_REFERENZ_PIXEL = 80_000_000L
    }
}
