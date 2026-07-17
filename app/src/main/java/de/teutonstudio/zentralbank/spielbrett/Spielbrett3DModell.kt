package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Vollstaendige Beschreibung eines dreieckig tesselierten Spielbretts.
 *
 * Eine Spalte bezeichnet eine aus zwei Dreiecken bestehende Raute. Das sichtbare Raster enthaelt
 * daher [zeilen] * [spalten] * 2 Grunddreiecke und kann dank [startZeile]/[startSpalte] auch
 * negative Kartenkoordinaten darstellen. [auflagen] bestimmt, auf welchen Grunddreiecken ein
 * farbiges, dreiseitiges Prisma dargestellt wird; Wasser setzt sich ausserhalb des Rasters fort.
 */
@Immutable
data class Spielbrett3DModell(
    val zeilen: Int,
    val spalten: Int,
    val startZeile: Int = 0,
    val startSpalte: Int = 0,
    val auflagen: List<DreieckAuflage> = emptyList(),
    val zeigeBearbeitungsRaster: Boolean = false,
) {
    init {
        require(zeilen > 0) { "zeilen muss groesser als 0 sein." }
        require(spalten > 0) { "spalten muss groesser als 0 sein." }
        val endeZeileExklusiv = startZeile.toLong() + zeilen
        val endeSpalteExklusiv = startSpalte.toLong() + spalten
        require(endeZeileExklusiv <= Int.MAX_VALUE.toLong() + 1L) {
            "Der Zeilenbereich überschreitet den Koordinatenraum."
        }
        require(endeSpalteExklusiv <= Int.MAX_VALUE.toLong() + 1L) {
            "Der Spaltenbereich überschreitet den Koordinatenraum."
        }

        auflagen.forEach { auflage ->
            require(auflage.position.zeile.toLong() in startZeile.toLong() until endeZeileExklusiv) {
                "Auflage in Zeile ${auflage.position.zeile} liegt ausserhalb des Bretts."
            }
            require(auflage.position.spalte.toLong() in startSpalte.toLong() until endeSpalteExklusiv) {
                "Auflage in Spalte ${auflage.position.spalte} liegt ausserhalb des Bretts."
            }
        }

        val doppeltBelegteFelder = auflagen
            .groupingBy { auflage -> auflage.position to auflage.ebene }
            .eachCount()
            .filterValues { anzahl -> anzahl > 1 }
            .keys
        require(doppeltBelegteFelder.isEmpty()) {
            "Pro Grunddreieck und Ebene ist höchstens eine Auflage erlaubt: $doppeltBelegteFelder"
        }
    }
}

/** Position eines Dreiecks innerhalb der Tesselation. */
@Immutable
data class DreieckPosition(
    val zeile: Int,
    val spalte: Int,
    val ausrichtung: DreieckAusrichtung,
)

/** Ausrichtung in der Draufsicht entlang der vertikalen Brettachse. */
enum class DreieckAusrichtung {
    OBEN,
    UNTEN,
}

/**
 * Frei definierbarer Typ einer Auflage. Unterschiedliche Typen erhalten eigene
 * Filament-Materialinstanzen.
 */
@Immutable
data class DreieckTyp(
    val name: String,
    val farbe: Color,
    val metallisch: Float = 0.05f,
    val rauheit: Float = 0.62f,
) {
    init {
        require(name.isNotBlank()) { "Der Name eines DreieckTyps darf nicht leer sein." }
        require(metallisch in 0f..1f) { "metallisch muss zwischen 0 und 1 liegen." }
        require(rauheit in 0f..1f) { "rauheit muss zwischen 0 und 1 liegen." }
    }
}

/** Ein typisiertes Dreieck, das auf einem Grunddreieck des Bretts liegt. */
@Immutable
data class DreieckAuflage(
    val position: DreieckPosition,
    val typ: DreieckTyp,
    val ebene: AuflagenEbene = AuflagenEbene.LAND,
)

enum class AuflagenEbene {
    LAND,
    SPEZIAL,
}

data class DreieckTreffer(
    val position: DreieckPosition,
    val naechsteEcke: Int,
)
