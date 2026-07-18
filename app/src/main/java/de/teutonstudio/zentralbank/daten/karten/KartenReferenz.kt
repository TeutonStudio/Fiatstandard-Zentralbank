package de.teutonstudio.zentralbank.daten.karten

import java.io.File
import kotlinx.serialization.Serializable

internal const val MIN_REFERENZ_BREITE = 0.1f
internal const val MAX_REFERENZ_BREITE = 100_000f

/** Dauerhaft speicherbare Ausrichtung eines Referenzbildes auf der Kartenebene. */
@Serializable
data class KartenReferenzMetadaten(
    val formatVersion: Int = AKTUELLE_REFERENZ_FORMAT_VERSION,
    val zentrumX: Float = 0f,
    val zentrumZ: Float = 0f,
    val breiteInBrettEinheiten: Float,
    val deckkraft: Float = 0.4f,
    val sichtbar: Boolean = true,
) {
    init {
        require(formatVersion == AKTUELLE_REFERENZ_FORMAT_VERSION) {
            "Nicht unterstützte Referenzbildversion: $formatVersion."
        }
        require(zentrumX.isFinite() && zentrumZ.isFinite()) {
            "Die Position des Referenzbildes muss endlich sein."
        }
        require(
            breiteInBrettEinheiten.isFinite() &&
                breiteInBrettEinheiten in MIN_REFERENZ_BREITE..MAX_REFERENZ_BREITE,
        ) {
            "Die Breite des Referenzbildes liegt außerhalb des gültigen Bereichs."
        }
        require(deckkraft.isFinite() && deckkraft in 0f..1f) {
            "Die Deckkraft des Referenzbildes muss zwischen 0 und 1 liegen."
        }
    }

    fun nachAusrichtungsGeste(
        verschiebungX: Float,
        verschiebungZ: Float,
        zoomFaktor: Float,
    ): KartenReferenzMetadaten {
        if (
            !verschiebungX.isFinite() || !verschiebungZ.isFinite() ||
            !zoomFaktor.isFinite() || zoomFaktor <= 0f
        ) {
            return this
        }
        return copy(
            zentrumX = zentrumX + verschiebungX,
            zentrumZ = zentrumZ + verschiebungZ,
            breiteInBrettEinheiten = (breiteInBrettEinheiten * zoomFaktor)
                .coerceIn(MIN_REFERENZ_BREITE, MAX_REFERENZ_BREITE),
        )
    }
}

/** Bilddatei und Ausrichtung eines gespeicherten oder noch temporären Editorbildes. */
data class KartenReferenz(
    val bildDatei: File,
    val metadaten: KartenReferenzMetadaten,
    val temporaer: Boolean = false,
)

internal const val AKTUELLE_REFERENZ_FORMAT_VERSION = 1
