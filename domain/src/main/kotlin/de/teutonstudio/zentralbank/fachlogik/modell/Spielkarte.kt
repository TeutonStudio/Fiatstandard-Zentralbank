package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

const val AKTUELLE_KARTEN_FORMAT_VERSION = 1

/**
 * Dünn besetztes Kartenmodell: Wasser ist die unendliche Grundebene und wird nicht gespeichert.
 * Nur Dreiecke mit einer Landauflage sowie mehrteilige Spezialfelder belegen Speicherplatz.
 * [startZeile]/[startSpalte] und die Ausdehnung beschreiben lediglich den aktuell im Editor
 * sichtbaren Bereich. Dieser kann ohne fachliche Obergrenze in jede Richtung erweitert werden.
 */
@Serializable
data class Spielkarte(
    val formatVersion: Int = AKTUELLE_KARTEN_FORMAT_VERSION,
    val id: String,
    val name: String,
    val zeilen: Int,
    val spalten: Int,
    val startZeile: Int = 0,
    val startSpalte: Int = 0,
    val landfelder: List<Landfeld> = emptyList(),
    val spezialfelder: List<Spezialfeld> = emptyList(),
) {
    init {
        require(formatVersion == AKTUELLE_KARTEN_FORMAT_VERSION) {
            "Nicht unterstützte Kartenformatversion: $formatVersion."
        }
        require(id.isNotBlank()) { "Karten-ID darf nicht leer sein." }
        require(name.isNotBlank()) { "Kartenname darf nicht leer sein." }
        require(zeilen > 0) { "Die Karte muss mindestens eine Zeile enthalten." }
        require(spalten > 0) { "Die Karte muss mindestens eine Spalte enthalten." }
        require(startZeile.toLong() + zeilen <= Int.MAX_VALUE.toLong() + 1L) {
            "Der Kartenbereich überschreitet den ganzzahligen Koordinatenraum."
        }
        require(startSpalte.toLong() + spalten <= Int.MAX_VALUE.toLong() + 1L) {
            "Der Kartenbereich überschreitet den ganzzahligen Koordinatenraum."
        }

        val landPositionsListe = landfelder.map(Landfeld::position)
        val landPositionen = landPositionsListe.toSet()
        require(landPositionsListe.size == landPositionen.size) {
            "Jedes Dreieck darf höchstens ein Landfeld tragen."
        }
        landPositionsListe.forEach(::pruefePosition)

        val spezialIds = spezialfelder.map(Spezialfeld::id)
        require(spezialIds.size == spezialIds.toSet().size) {
            "Spezialfeld-IDs müssen innerhalb einer Karte eindeutig sein."
        }
        val belegteSpezialPositionen = mutableSetOf<KartenDreieck>()
        spezialfelder.forEach { spezialfeld ->
            require(spezialfeld.positionen.size == 6) {
                "Spezialfeld ${spezialfeld.name} muss aus genau sechs Dreiecken bestehen."
            }
            require(spezialfeld.positionen.size == spezialfeld.positionen.toSet().size) {
                "Spezialfeld ${spezialfeld.name} enthält ein Dreieck mehrfach."
            }
            spezialfeld.positionen.forEach { position ->
                pruefePosition(position)
                require(position in landPositionen) {
                    "Spezialfelder dürfen nur auf Land liegen: $position."
                }
                require(belegteSpezialPositionen.add(position)) {
                    "Spezialfelder dürfen sich nicht überlagern: $position."
                }
            }
        }
    }

    val landNachPosition: Map<KartenDreieck, GelaendeTyp>
        get() = landfelder.associate { feld -> feld.position to feld.gelaende }

    val endeZeileExklusiv: Long get() = startZeile.toLong() + zeilen
    val endeSpalteExklusiv: Long get() = startSpalte.toLong() + spalten

    private fun pruefePosition(position: KartenDreieck) {
        require(
            position.zeile.toLong() in startZeile.toLong() until endeZeileExklusiv &&
                position.spalte.toLong() in startSpalte.toLong() until endeSpalteExklusiv,
        ) {
            "Dreieck liegt außerhalb der Karte: $position."
        }
    }
}

@Serializable
data class KartenDreieck(
    val zeile: Int,
    val spalte: Int,
    val haelfte: DreieckHaelfte,
)

@Serializable
enum class DreieckHaelfte {
    OBEN,
    UNTEN,
}

@Serializable
data class Landfeld(
    val position: KartenDreieck,
    val gelaende: GelaendeTyp,
)

@Serializable
enum class GelaendeTyp {
    EBENE,
    WALD,
    GEBIRGE,
    WUESTE,
    SUMPF,
}

@Serializable
data class Spezialfeld(
    val id: String,
    val name: String,
    val typ: SpezialfeldTyp,
    val positionen: List<KartenDreieck>,
) {
    init {
        require(id.isNotBlank()) { "Spezialfeld-ID darf nicht leer sein." }
        require(name.isNotBlank()) { "Spezialfeldname darf nicht leer sein." }
    }
}

@Serializable
enum class SpezialfeldTyp {
    HEXAGON,
    STADT,
    HAFEN,
}
