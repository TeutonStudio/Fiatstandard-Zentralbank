package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

const val AKTUELLE_KARTEN_FORMAT_VERSION = 2

/**
 * Wiederverwendbare, unbelegte Grundlage einer Spielkarte.
 *
 * Wasser ist die unendliche Grundebene und wird nicht gespeichert. Der beschriebene Bereich ist
 * der im Kartenbauer sichtbare Ausschnitt; nur gesetzte [gelaendefelder] belegen Speicherplatz.
 */
@Serializable
data class KartenVorlage(
    val formatVersion: Int = AKTUELLE_KARTEN_FORMAT_VERSION,
    val id: String,
    val name: String,
    val zeilen: Int,
    val spalten: Int,
    val startZeile: Int = 0,
    val startSpalte: Int = 0,
    @SerialName("landfelder")
    val gelaendefelder: List<GelaendeFeld> = emptyList(),
) {
    init {
        pruefeKartenGrundlage(
            formatVersion = formatVersion,
            id = id,
            name = name,
            zeilen = zeilen,
            spalten = spalten,
            startZeile = startZeile,
            startSpalte = startSpalte,
            gelaendefelder = gelaendefelder,
        )
    }

    val landfelder: List<GelaendeFeld> get() = gelaendefelder
    val landNachPosition: Map<KartenFeld, GelaendeTyp>
        get() = gelaendefelder.associate { feld -> feld.position to feld.gelaende }
    val endeZeileExklusiv: Long get() = startZeile.toLong() + zeilen
    val endeSpalteExklusiv: Long get() = startSpalte.toLong() + spalten

    fun alsSpielkarte(spielId: String = id): Spielkarte = Spielkarte(
        id = spielId,
        name = name,
        zeilen = zeilen,
        spalten = spalten,
        startZeile = startZeile,
        startSpalte = startSpalte,
        gelaendefelder = gelaendefelder,
    )
}

/**
 * Karte einer konkreten Partie. Ihre Geländedaten werden nach Spielbeginn nicht mehr verändert;
 * alle laufenden Änderungen liegen in [belegung].
 *
 * Format 1 wird nur angenommen, damit bestehende serialisierte Spielstände eingelesen und danach
 * mit [aufAktuellesFormat] normalisiert werden können. Neue Karten werden immer als Format 2
 * geschrieben.
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
    @SerialName("landfelder")
    val gelaendefelder: List<GelaendeFeld> = emptyList(),
    val belegung: KartenBelegung = KartenBelegung(),
) {
    init {
        pruefeKartenGrundlage(
            formatVersion = formatVersion,
            id = id,
            name = name,
            zeilen = zeilen,
            spalten = spalten,
            startZeile = startZeile,
            startSpalte = startSpalte,
            gelaendefelder = gelaendefelder,
        )
        belegung.pruefeFuer(this)
    }

    /** Übergangsname für bestehenden Darstellungscode. */
    val landfelder: List<GelaendeFeld> get() = gelaendefelder
    val landNachPosition: Map<KartenFeld, GelaendeTyp>
        get() = gelaendefelder.associate { feld -> feld.position to feld.gelaende }
    val endeZeileExklusiv: Long get() = startZeile.toLong() + zeilen
    val endeSpalteExklusiv: Long get() = startSpalte.toLong() + spalten

    fun alsVorlage(vorlagenId: String = id): KartenVorlage = KartenVorlage(
        id = vorlagenId,
        name = name,
        zeilen = zeilen,
        spalten = spalten,
        startZeile = startZeile,
        startSpalte = startSpalte,
        gelaendefelder = gelaendefelder,
    )

    fun aufAktuellesFormat(): Spielkarte = if (formatVersion == AKTUELLE_KARTEN_FORMAT_VERSION) {
        this
    } else {
        copy(formatVersion = AKTUELLE_KARTEN_FORMAT_VERSION)
    }
}

@Serializable
data class KartenFeld(
    val zeile: Int,
    val spalte: Int,
    val haelfte: DreieckHaelfte,
)

typealias KartenDreieck = KartenFeld

@Serializable
enum class DreieckHaelfte {
    OBEN,
    UNTEN,
}

@Serializable
data class GelaendeFeld(
    val position: KartenFeld,
    val gelaende: GelaendeTyp,
)

typealias Landfeld = GelaendeFeld

@Serializable
enum class GelaendeTyp {
    EBENE,
    WALD,
    GEBIRGE,
    WUESTE,
    SUMPF,
}

private fun pruefeKartenGrundlage(
    formatVersion: Int,
    id: String,
    name: String,
    zeilen: Int,
    spalten: Int,
    startZeile: Int,
    startSpalte: Int,
    gelaendefelder: List<GelaendeFeld>,
) {
    require(formatVersion in 1..AKTUELLE_KARTEN_FORMAT_VERSION) {
        "Nicht unterstützte Kartenformatversion: $formatVersion."
    }
    require(id.isNotBlank()) { "Karten-ID darf nicht leer sein." }
    require(name.isNotBlank()) { "Kartenname darf nicht leer sein." }
    require(zeilen > 0) { "Die Karte muss mindestens eine Zeile enthalten." }
    require(spalten > 0) { "Die Karte muss mindestens eine Spalte enthalten." }
    val endeZeile = startZeile.toLong() + zeilen
    val endeSpalte = startSpalte.toLong() + spalten
    require(endeZeile <= Int.MAX_VALUE.toLong() + 1L) {
        "Der Kartenbereich überschreitet den ganzzahligen Koordinatenraum."
    }
    require(endeSpalte <= Int.MAX_VALUE.toLong() + 1L) {
        "Der Kartenbereich überschreitet den ganzzahligen Koordinatenraum."
    }

    val positionen = gelaendefelder.map(GelaendeFeld::position)
    require(positionen.size == positionen.toSet().size) {
        "Jedes Dreieck darf höchstens ein Geländefeld tragen."
    }
    positionen.forEach { position ->
        require(
            position.zeile.toLong() in startZeile.toLong() until endeZeile &&
                position.spalte.toLong() in startSpalte.toLong() until endeSpalte,
        ) {
            "Dreieck liegt außerhalb der Karte: $position."
        }
    }
}
