package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val AKTUELLE_KARTEN_FORMAT_VERSION = 3

/**
 * Hexagonaler Kartenausschnitt eines unbegrenzten Dreiecksgitters.
 *
 * Der Radius ist die Seitenlänge des Hexagons in Dreieckskanten. Ein Hexagon mit Radius 1
 * enthält sechs Dreiecke, eines mit Radius n genau 6 * n² Dreiecke. [zentrum] ist immer eine
 * echte Ecke des Dreiecksgitters und erlaubt verlustfreie Migration älterer Kartenkoordinaten.
 */
@Serializable
data class KartenHexagon(
    val zentrum: KartenEcke = KartenEcke(0, 0),
    val radius: Int = 1,
) {
    init {
        require(radius > 0) { "Der Kartenradius muss mindestens 1 betragen." }
        require(zentrum.y % 2 == 0) { "Das Hexagonzentrum muss eine Ecke des Dreiecksgitters sein." }
    }

    val anzahlFelder: Long get() = 6L * radius * radius

    fun mitMindestradiusFuer(positionen: Iterable<KartenFeld>): KartenHexagon = copy(
        radius = positionen.fold(1) { bisher, position ->
            maxOf(bisher, benoetigterRadius(position))
        },
    )
}

/** Wiederverwendbare, unbelegte Grundlage einer Spielkarte. */
@Serializable
data class KartenVorlage(
    val formatVersion: Int = AKTUELLE_KARTEN_FORMAT_VERSION,
    val id: String,
    val name: String,
    val hexagon: KartenHexagon = KartenHexagon(),
    @SerialName("landfelder")
    val gelaendefelder: List<GelaendeFeld> = emptyList(),
) {
    init {
        pruefeKartenGrundlage(formatVersion, id, name, hexagon, gelaendefelder)
    }

    val landfelder: List<GelaendeFeld> get() = gelaendefelder
    val landNachPosition: Map<KartenFeld, GelaendeTyp>
        get() = gelaendefelder.associate { feld -> feld.position to feld.gelaende }

    fun alsSpielkarte(spielId: String = id): Spielkarte = Spielkarte(
        id = spielId,
        name = name,
        hexagon = hexagon,
        gelaendefelder = gelaendefelder,
    )
}

/** Karte einer Partie: unveränderliches Hexagon mit veränderlicher Spielbelegung. */
@Serializable
data class Spielkarte(
    val formatVersion: Int = AKTUELLE_KARTEN_FORMAT_VERSION,
    val id: String,
    val name: String,
    val hexagon: KartenHexagon = KartenHexagon(),
    @SerialName("landfelder")
    val gelaendefelder: List<GelaendeFeld> = emptyList(),
    val belegung: KartenBelegung = KartenBelegung(),
) {
    init {
        pruefeKartenGrundlage(formatVersion, id, name, hexagon, gelaendefelder)
        belegung.pruefeFuer(this)
    }

    val landfelder: List<GelaendeFeld> get() = gelaendefelder
    val landNachPosition: Map<KartenFeld, GelaendeTyp>
        get() = gelaendefelder.associate { feld -> feld.position to feld.gelaende }

    fun alsVorlage(vorlagenId: String = id): KartenVorlage = KartenVorlage(
        id = vorlagenId,
        name = name,
        hexagon = hexagon,
        gelaendefelder = gelaendefelder,
    )

    fun aufAktuellesFormat(): Spielkarte = also {
        require(formatVersion == AKTUELLE_KARTEN_FORMAT_VERSION) {
            "Karten vor Format 3 müssen vor dem Domain-Deserialisieren migriert werden."
        }
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
    hexagon: KartenHexagon,
    gelaendefelder: List<GelaendeFeld>,
) {
    require(formatVersion == AKTUELLE_KARTEN_FORMAT_VERSION) {
        "Nicht unterstützte Kartenformatversion: $formatVersion."
    }
    require(id.isNotBlank()) { "Karten-ID darf nicht leer sein." }
    require(name.isNotBlank()) { "Kartenname darf nicht leer sein." }

    val positionen = gelaendefelder.map(GelaendeFeld::position)
    require(positionen.size == positionen.toSet().size) {
        "Jedes Dreieck darf höchstens ein Geländefeld tragen."
    }
    positionen.forEach { position ->
        require(hexagon.enthaelt(position)) {
            "Dreieck liegt außerhalb des Kartenhexagons: $position."
        }
    }
}
