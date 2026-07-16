package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Vollstaendige Beschreibung eines dreieckig tesselierten Spielbretts.
 *
 * Eine Spalte bezeichnet eine aus zwei Dreiecken bestehende Raute. Das Brett enthaelt daher
 * [zeilen] * [spalten] * 2 Grunddreiecke. [auflagen] bestimmt, auf welchen Grunddreiecken ein
 * farbiges, dreiseitiges Prisma dargestellt wird.
 */
@Immutable
data class Spielbrett3DModell(
    val zeilen: Int,
    val spalten: Int,
    val auflagen: List<DreieckAuflage> = emptyList(),
) {
    init {
        require(zeilen > 0) { "zeilen muss groesser als 0 sein." }
        require(spalten > 0) { "spalten muss groesser als 0 sein." }

        auflagen.forEach { auflage ->
            require(auflage.position.zeile < zeilen) {
                "Auflage in Zeile ${auflage.position.zeile} liegt ausserhalb des Bretts."
            }
            require(auflage.position.spalte < spalten) {
                "Auflage in Spalte ${auflage.position.spalte} liegt ausserhalb des Bretts."
            }
        }

        val doppeltBelegteFelder = auflagen
            .groupingBy(DreieckAuflage::position)
            .eachCount()
            .filterValues { anzahl -> anzahl > 1 }
            .keys
        require(doppeltBelegteFelder.isEmpty()) {
            "Pro Grunddreieck ist hoechstens eine Auflage erlaubt: $doppeltBelegteFelder"
        }
    }
}

/** Position eines Dreiecks innerhalb der Tesselation. */
@Immutable
data class DreieckPosition(
    val zeile: Int,
    val spalte: Int,
    val ausrichtung: DreieckAusrichtung,
) {
    init {
        require(zeile >= 0) { "zeile darf nicht negativ sein." }
        require(spalte >= 0) { "spalte darf nicht negativ sein." }
    }
}

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
)

