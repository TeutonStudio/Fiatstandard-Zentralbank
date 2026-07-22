package de.teutonstudio.zentralbank.simulation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AKTUELLE_AKTIONS_SCHEMA_VERSION
import de.teutonstudio.zentralbank.fachlogik.beobachtung.AKTUELLE_BEOBACHTUNGS_VERSION
import de.teutonstudio.zentralbank.fachlogik.engine.Zufallsquelle
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class ModellManifest(
    val modelVersion: String,
    val observationSchemaVersion: Int,
    val actionSchemaVersion: Int,
    val episodeSchemaVersion: Int,
    val supportedStyles: List<SpielerStil>,
    val maximumPlayers: Int,
    val trainingCommit: String,
    val createdAt: String,
)

@Serializable
data class ModellAgentStatus(
    val modellGeladen: Boolean,
    val verwendeterAgent: String,
    val fallbackGrund: String? = null,
)

class OnnxModellAgent(
    modellPfad: Path,
    manifestPfad: Path,
    private val fallback: SpielAgent = SicherheitsAgent(),
) : SpielAgent, AutoCloseable {
    override val name: String = "onnx"
    private val umgebung = OrtEnvironment.getEnvironment()
    private var sitzung: OrtSession? = null
    private var manifest: ModellManifest? = null
    var status: ModellAgentStatus
        private set

    init {
        var geprueftesManifest: ModellManifest? = null
        val geladen = runCatching {
            require(Files.isRegularFile(modellPfad)) { "ONNX-Modell fehlt: $modellPfad" }
            require(Files.isRegularFile(manifestPfad)) { "Modellmanifest fehlt: $manifestPfad" }
            val manifest = manifestJson.decodeFromString<ModellManifest>(Files.readString(manifestPfad))
            require(manifest.modelVersion.isNotBlank())
            require(manifest.observationSchemaVersion == AKTUELLE_BEOBACHTUNGS_VERSION)
            require(manifest.actionSchemaVersion == AKTUELLE_AKTIONS_SCHEMA_VERSION)
            require(manifest.episodeSchemaVersion == AKTUELLE_EPISODEN_FORMAT_VERSION)
            require(manifest.maximumPlayers in 3..7)
            require(manifest.supportedStyles.containsAll(SpielerStil.entries))
            require(manifest.trainingCommit.isNotBlank() && manifest.createdAt.isNotBlank())
            geprueftesManifest = manifest
            umgebung.createSession(modellPfad.toString(), OrtSession.SessionOptions())
        }
        sitzung = geladen.getOrNull()
        manifest = geprueftesManifest.takeIf { geladen.isSuccess }
        status = if (geladen.isSuccess) {
            ModellAgentStatus(true, name)
        } else {
            ModellAgentStatus(false, fallback.name, geladen.exceptionOrNull()?.message)
        }
    }

    override fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion {
        val session = sitzung ?: return fallback.waehleAktion(entscheidungspunkt, zufall)
        return runCatching {
            val modellManifest = requireNotNull(manifest)
            require(entscheidungspunkt.beobachtung.beobachtungsVersion == AKTUELLE_BEOBACHTUNGS_VERSION)
            require(entscheidungspunkt.aktionsRaum.aktionsSchemaVersion == AKTUELLE_AKTIONS_SCHEMA_VERSION)
            require(entscheidungspunkt.beobachtung.spieler.size <= modellManifest.maximumPlayers) {
                "Das Modell unterstützt höchstens ${modellManifest.maximumPlayers} Spieler."
            }
            require(
                entscheidungspunkt.beobachtung.eigeneWirtschaft.spielstil in
                    modellManifest.supportedStyles,
            ) { "Der aktuelle Spielstil wird vom Modell nicht unterstützt." }
            val aktionen = entscheidungspunkt.aktionsRaum.aktionen
            require(aktionen.isNotEmpty())
            val zustand = arrayOf(OnnxMerkmalsKodierung.zustand(entscheidungspunkt))
            val kandidaten = arrayOf(aktionen.mapIndexed { index, aktion ->
                OnnxMerkmalsKodierung.aktion(aktion, index)
            }.toTypedArray())
            val stil = longArrayOf(entscheidungspunkt.beobachtung.eigeneWirtschaft.spielstil.ordinal.toLong())
            val maske = arrayOf(BooleanArray(aktionen.size) { true })
            OnnxTensor.createTensor(umgebung, zustand).use { stateTensor ->
                OnnxTensor.createTensor(umgebung, kandidaten).use { actionTensor ->
                    OnnxTensor.createTensor(umgebung, stil).use { styleTensor ->
                        OnnxTensor.createTensor(umgebung, maske).use { maskTensor ->
                            session.run(mapOf(
                                "state" to stateTensor,
                                "actions" to actionTensor,
                                "style" to styleTensor,
                                "legal_mask" to maskTensor,
                            )).use { result ->
                                @Suppress("UNCHECKED_CAST")
                                val scores = result[0].value as Array<FloatArray>
                                val zeile = scores.single()
                                require(zeile.size == aktionen.size && zeile.all(Float::isFinite)) {
                                    "ONNX lieferte ungültige Policy-Werte."
                                }
                                aktionen[zeile.indices.maxBy { zeile[it] }]
                            }
                        }
                    }
                }
            }
        }.getOrElse { fehler ->
            status = ModellAgentStatus(false, fallback.name, fehler.message)
            fallback.waehleAktion(entscheidungspunkt, zufall)
        }
    }

    override fun close() {
        sitzung?.close()
        sitzung = null
        manifest = null
    }

    private companion object {
        val manifestJson = Json { ignoreUnknownKeys = false }
    }
}

private object OnnxMerkmalsKodierung {
    private const val ZUSTAND = 64
    private const val AKTION = 64
    private val aktionsJson = Json { classDiscriminator = "art"; encodeDefaults = true }

    fun zustand(punkt: Entscheidungspunkt): FloatArray {
        val beobachtung = punkt.beobachtung
        val merkmale = FloatArray(ZUSTAND)
        merkmale[0] = beobachtung.runde / 100f
        merkmale[1] = beobachtung.markt.leitzinsBasispunkte / 10_000f
        merkmale[2] = beobachtung.spieler.size / 7f
        beobachtung.spieler.take(7).forEachIndexed { index, spieler ->
            val offset = 4 + index * 7
            merkmale[offset] = spieler.geld.cent.toFloat()
            merkmale[offset + 1] = spieler.marktwert.cent.toFloat()
            merkmale[offset + 2] = spieler.rohstoffe.sumOf { it.menge } / 100f
            merkmale[offset + 3] = spieler.offeneEigeneAnleihen.size / 10f
            merkmale[offset + 4] = spieler.erreichbareWirtschaftsstandorte.size / 100f
            merkmale[offset + 5] = spieler.einheiten.size / 50f
            merkmale[offset + 6] = if (spieler.ausgeschieden) 1f else 0f
        }
        val karte = beobachtung.karte
        merkmale[55] = (karte?.gelaendefelder?.size ?: 0) / 1000f
        merkmale[56] = (karte?.eckBauwerke?.size ?: 0) / 100f
        merkmale[57] = (karte?.handelslinien?.size ?: 0) / 500f
        merkmale[58] = (karte?.feldAnlagen?.size ?: 0) / 500f
        merkmale[59] = (karte?.kriegseinheiten?.size ?: 0) / 100f
        merkmale[60] = beobachtung.kriege.size / 10f
        merkmale[61] = beobachtung.belagerungen.size / 20f
        merkmale[62] = beobachtung.friedensvertraege.size / 20f
        merkmale[63] = if (beobachtung.ergebnis != null) 1f else 0f
        return merkmale
    }

    fun aktion(aktion: SpielAktion, kandidatenIndex: Int): FloatArray {
        require(kandidatenIndex >= 0)
        val element = aktionsJson.encodeToJsonElement(SpielAktion.serializer(), aktion).jsonObject
        val merkmale = FloatArray(AKTION)
        val typ = element["art"]?.jsonPrimitive?.content.orEmpty().encodeToByteArray().take(31)
        merkmale[0] = typ.size / 31f
        typ.forEachIndexed { index, byte -> merkmale[index + 1] = (byte.toInt() and 0xff) / 255f }
        val zahlen = mutableListOf<Float>()
        fun sammeln(wert: JsonElement) {
            when (wert) {
                is JsonObject -> wert.entries.sortedBy { it.key }.filterNot { it.key == "art" }
                    .forEach { sammeln(it.value) }
                is JsonArray -> wert.forEach(::sammeln)
                is JsonPrimitive -> if (!wert.isString) wert.floatOrNull?.let(zahlen::add)
                JsonNull -> Unit
            }
        }
        sammeln(element)
        zahlen.take(28).forEachIndexed { index, wert ->
            merkmale[index + 32] = wert.coerceIn(-10f, 10f)
        }
        repeat(4) { byteIndex ->
            merkmale[60 + byteIndex] =
                ((kandidatenIndex ushr (byteIndex * 8)) and 0xff) / 255f
        }
        return merkmale
    }
}
