package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val AKTUELLE_KARTEN_FORMAT_VERSION = 4
const val AELTESTE_UNTERSTUETZTE_KARTEN_FORMAT_VERSION = 3

/**
 * Hexagonaler Kartenausschnitt eines unbegrenzten Dreiecksgitters.
 *
 * Der Radius ist die Seitenlänge des Hexagons in Dreieckskanten. Ein Hexagon mit Radius 1
 * enthält sechs Dreiecke, eines mit Radius n genau 6 * n² Dreiecke. [zentrum] ist immer eine
 * echte Ecke des Dreiecksgitters.
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
    val spezialfelder: List<Spezialfeld> = emptyList(),
    val vorkommen: List<RohstoffVorkommen> = emptyList(),
) {
    init {
        pruefeKartenGrundlage(
            formatVersion,
            id,
            name,
            hexagon,
            gelaendefelder,
            spezialfelder,
            vorkommen,
        )
    }

    val landfelder: List<GelaendeFeld> get() = gelaendefelder
    val landNachPosition: Map<KartenFeld, GelaendeTyp>
        get() = gelaendefelder.associate { feld -> feld.position to feld.gelaende }
    val vorkommenNachPosition: Map<KartenFeld, VorkommensArt>
        get() = vorkommen.associate { eintrag -> eintrag.position to eintrag.art }

    fun vorkommenAn(position: KartenFeld): VorkommensArt? = vorkommenNachPosition[position]

    fun alsSpielkarte(spielId: String = id): Spielkarte = Spielkarte(
        formatVersion = formatVersion,
        id = spielId,
        name = name,
        hexagon = hexagon,
        gelaendefelder = gelaendefelder,
        spezialfelder = spezialfelder,
        vorkommen = vorkommen,
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
    val spezialfelder: List<Spezialfeld> = emptyList(),
    val vorkommen: List<RohstoffVorkommen> = emptyList(),
    val belegung: KartenBelegung = KartenBelegung(),
) {
    init {
        pruefeKartenGrundlage(
            formatVersion,
            id,
            name,
            hexagon,
            gelaendefelder,
            spezialfelder,
            vorkommen,
        )
        belegung.pruefeFuer(this)
        pruefeVorkommensBelegung()
    }

    val landfelder: List<GelaendeFeld> get() = gelaendefelder
    val landNachPosition: Map<KartenFeld, GelaendeTyp>
        get() = gelaendefelder.associate { feld -> feld.position to feld.gelaende }
    val vorkommenNachPosition: Map<KartenFeld, VorkommensArt>
        get() = vorkommen.associate { eintrag -> eintrag.position to eintrag.art }

    fun vorkommenAn(position: KartenFeld): VorkommensArt? = vorkommenNachPosition[position]

    fun alsVorlage(vorlagenId: String = id): KartenVorlage = KartenVorlage(
        formatVersion = formatVersion,
        id = vorlagenId,
        name = name,
        hexagon = hexagon,
        gelaendefelder = gelaendefelder,
        spezialfelder = spezialfelder,
        vorkommen = vorkommen,
    )

    private fun pruefeVorkommensBelegung() {
        if (formatVersion < 4) return
        belegung.felder.forEach { feldBelegung ->
            val wirtschaftsregion = feldBelegung.anlage as? FeldAnlage.Wirtschaftsregion
                ?: return@forEach
            val position = feldBelegung.position
            when (wirtschaftsregion.bauteil) {
                BauteilTyp.EISENMINE -> pruefeBergbauStandort(
                    position = position,
                    erwartet = VorkommensArt.EISENERZ,
                    bezeichnung = "Eisenmine",
                )
                BauteilTyp.KOHLEMINE -> pruefeBergbauStandort(
                    position = position,
                    erwartet = VorkommensArt.KOHLE,
                    bezeichnung = "Kohlemine",
                )
                BauteilTyp.LEHMINE -> pruefeBergbauStandort(
                    position = position,
                    erwartet = VorkommensArt.LEHM,
                    bezeichnung = "Lehmmine",
                )
                BauteilTyp.BOHRTURM -> require(vorkommenAn(position) == VorkommensArt.ROHOEL) {
                    "Ein Bohrturm braucht ein Rohölvorkommen: $position."
                }
                else -> Unit
            }
        }
    }

    private fun pruefeBergbauStandort(
        position: KartenFeld,
        erwartet: VorkommensArt,
        bezeichnung: String,
    ) {
        require(landNachPosition[position] == GelaendeTyp.GEBIRGE) {
            "Eine $bezeichnung darf nur im Gebirge gebaut werden: $position."
        }
        require(vorkommenAn(position) == erwartet) {
            "Eine $bezeichnung braucht ein passendes ${erwartet.anzeigeName}-Vorkommen: $position."
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

@Serializable
enum class VorkommensArt(val anzeigeName: String) {
    ROHOEL("Rohöl"),
    EISENERZ("Eisenerz"),
    KOHLE("Kohle"),
    LEHM("Lehm"),
}

@Serializable
data class RohstoffVorkommen(
    val position: KartenFeld,
    val art: VorkommensArt,
)

/** Ein aus den sechs Geländedreiecken um [mittelpunkt] gebildetes Sonderfeld. */
@Serializable
data class Spezialfeld(
    val typ: SpezialfeldTyp,
    val mittelpunkt: KartenEcke,
) {
    val positionen: List<KartenFeld> get() = angrenzendeFelder(mittelpunkt)
}

@Serializable
enum class SpezialfeldTyp {
    TEICH,
}

private fun pruefeKartenGrundlage(
    formatVersion: Int,
    id: String,
    name: String,
    hexagon: KartenHexagon,
    gelaendefelder: List<GelaendeFeld>,
    spezialfelder: List<Spezialfeld>,
    vorkommen: List<RohstoffVorkommen>,
) {
    require(formatVersion in AELTESTE_UNTERSTUETZTE_KARTEN_FORMAT_VERSION..AKTUELLE_KARTEN_FORMAT_VERSION) {
        "Nicht unterstützte Kartenformatversion: $formatVersion."
    }
    require(formatVersion >= 4 || vorkommen.isEmpty()) {
        "Rohstoffvorkommen benötigen Kartenformatversion 4 oder neuer."
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

    val landPositionen = positionen.toSet()
    val landNachPosition = gelaendefelder.associate { feld -> feld.position to feld.gelaende }
    val vonSpezialfeldernBelegt = mutableSetOf<KartenFeld>()
    spezialfelder.forEach { spezialfeld ->
        val spezialPositionen = spezialfeld.positionen
        require(spezialPositionen.size == 6) {
            "Ein Spezialfeld muss aus genau sechs Dreiecken bestehen."
        }
        spezialPositionen.forEach { position ->
            require(position in landPositionen) {
                "Ein Spezialfeld darf nur auf Gelände liegen: $position."
            }
            require(hexagon.enthaelt(position)) {
                "Spezialfeld-Dreieck liegt außerhalb des Kartenhexagons: $position."
            }
            require(vonSpezialfeldernBelegt.add(position)) {
                "Spezialfelder dürfen sich nicht überlagern: $position."
            }
        }
    }

    val vorkommensPositionen = vorkommen.map(RohstoffVorkommen::position)
    require(vorkommensPositionen.size == vorkommensPositionen.toSet().size) {
        "Jedes Feld darf höchstens ein Rohstoffvorkommen tragen."
    }
    vorkommen.forEach { eintrag ->
        require(eintrag.position in landPositionen) {
            "Ein Rohstoffvorkommen darf nur auf Land liegen: ${eintrag.position}."
        }
        require(eintrag.position !in vonSpezialfeldernBelegt) {
            "Auf einem Spezialfeld darf kein Rohstoffvorkommen liegen: ${eintrag.position}."
        }
        if (eintrag.art in setOf(VorkommensArt.EISENERZ, VorkommensArt.KOHLE, VorkommensArt.LEHM)) {
            require(landNachPosition[eintrag.position] == GelaendeTyp.GEBIRGE) {
                "${eintrag.art.anzeigeName} darf nur im Gebirge vorkommen: ${eintrag.position}."
            }
        }
    }
}
