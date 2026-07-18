package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KartenEcke(
    val x: Int,
    val y: Int,
) : Comparable<KartenEcke> {
    override fun compareTo(other: KartenEcke): Int =
        compareValuesBy(this, other, KartenEcke::y, KartenEcke::x)
}

@Serializable
data class KartenKante(
    val anfang: KartenEcke,
    val ende: KartenEcke,
) {
    init {
        require(anfang < ende) { "Eine Kartenkante braucht zwei kanonisch sortierte Ecken." }
    }

    companion object {
        fun zwischen(eckeA: KartenEcke, eckeB: KartenEcke): KartenKante {
            require(eckeA != eckeB) { "Eine Kartenkante braucht zwei verschiedene Ecken." }
            return if (eckeA < eckeB) KartenKante(eckeA, eckeB) else KartenKante(eckeB, eckeA)
        }
    }
}

@Serializable
sealed interface KartenOrt {
    @Serializable
    @SerialName("feld")
    data class Feld(val position: KartenFeld) : KartenOrt

    @Serializable
    @SerialName("ecke")
    data class Ecke(val position: KartenEcke) : KartenOrt

    @Serializable
    @SerialName("kante")
    data class Kante(val position: KartenKante) : KartenOrt
}

@Serializable
enum class EckGebaeudeTyp {
    HAUPTBAHNHOF,
    BAHNHOF,
    GROSSBAHNHOF,
    HAFEN,
    GROSSHAFEN,
}

@Serializable
enum class BauwerkZustand {
    INTAKT,
    BELAGERT,
    ZERSTOERT,
}

@Serializable
data class EckBelegung(
    val position: KartenEcke,
    val typ: EckGebaeudeTyp,
    val besitzer: SpielerId? = null,
    val zustand: BauwerkZustand = BauwerkZustand.INTAKT,
    val gebautInRunde: Int? = null,
) {
    init {
        require(gebautInRunde == null || gebautInRunde >= 0) {
            "Die Baurunde eines Eckgebäudes darf nicht negativ sein."
        }
        require(zustand == BauwerkZustand.ZERSTOERT || besitzer != null) {
            "Ein intaktes oder belagertes Eckgebäude braucht einen Besitzer."
        }
        require(zustand != BauwerkZustand.ZERSTOERT || besitzer == null) {
            "Ein zerstörtes Eckgebäude ist neutral."
        }
    }
}

@Serializable
data class KantenBelegung(
    val position: KartenKante,
    val zustand: BauwerkZustand = BauwerkZustand.INTAKT,
    val gebautInRunde: Int? = null,
) {
    init {
        require(gebautInRunde == null || gebautInRunde >= 0) {
            "Die Baurunde einer Handelslinie darf nicht negativ sein."
        }
        require(zustand != BauwerkZustand.BELAGERT) {
            "Eine Handelslinie kann nicht belagert sein."
        }
    }
}

@Serializable
sealed interface FeldAnlage {
    @Serializable
    @SerialName("abbaueinheit")
    data class Abbaueinheit(val rohstoff: Rohstoff) : FeldAnlage

    @Serializable
    @SerialName("geschaeftsbank")
    data object Geschaeftsbank : FeldAnlage

    @Serializable
    @SerialName("wirtschaftsregion")
    data class Wirtschaftsregion(val bauteil: BauteilTyp) : FeldAnlage {
        init {
            require(bauteil.art == BauteilArt.WIRTSCHAFTSREGION) {
                "Auf einem Dreieck kann nur eine Wirtschaftsregion platziert werden."
            }
        }
    }
}

@Serializable
enum class AnlagenZustand {
    AKTIV,
    VERLASSEN,
    ZERSTOERT,
}

@Serializable
data class FeldBelegung(
    val position: KartenFeld,
    val anlage: FeldAnlage,
    val zustand: AnlagenZustand = AnlagenZustand.AKTIV,
    val gebautInRunde: Int? = null,
) {
    init {
        require(gebautInRunde == null || gebautInRunde >= 0) {
            "Die Baurunde einer Feldanlage darf nicht negativ sein."
        }
    }
}

@Serializable
enum class FrachtRichtung {
    A_NACH_B,
    B_NACH_A,
}

@Serializable
data class SeewegBelegung(
    val id: String,
    val hafenA: KartenEcke,
    val hafenB: KartenEcke,
    val besitzer: SpielerId,
    val richtung: FrachtRichtung,
) {
    init {
        require(id.isNotBlank()) { "Eine Seeweg-ID darf nicht leer sein." }
        require(hafenA != hafenB) { "Ein Seeweg muss zwei verschiedene Häfen verbinden." }
    }
}

@Serializable
enum class KriegsEinheitTyp(
    val bewegungsRohstoff: Rohstoff,
) {
    PANZER(Rohstoff.DIESEL),
    KRIEGSSCHIFF(Rohstoff.SCHWEROEL),
}

@Serializable
data class KriegsEinheitBelegung(
    val id: String,
    val typ: KriegsEinheitTyp,
    val besitzer: SpielerId,
    /** Nur bei alten, unmittelbar für einen Konflikt eingesetzten Einheiten gesetzt. */
    val gegner: SpielerId? = null,
    val ort: KartenOrt,
) {
    init {
        require(id.isNotBlank()) { "Eine Kriegseinheiten-ID darf nicht leer sein." }
        require(besitzer != gegner) { "Besitzer und Gegner einer Kriegseinheit müssen verschieden sein." }
        require(ort is KartenOrt.Kante) { "Eine Kriegseinheit muss auf einer Kartenkante stehen." }
    }

    val position: KartenKante get() = (ort as KartenOrt.Kante).position
}

@Serializable
data class KartenBelegung(
    val ecken: List<EckBelegung> = emptyList(),
    val kanten: List<KantenBelegung> = emptyList(),
    val felder: List<FeldBelegung> = emptyList(),
    val seewege: List<SeewegBelegung> = emptyList(),
    val kriegseinheiten: List<KriegsEinheitBelegung> = emptyList(),
) {
    val eckenNachPosition: Map<KartenEcke, EckBelegung>
        get() = ecken.associateBy(EckBelegung::position)
    val kantenNachPosition: Map<KartenKante, KantenBelegung>
        get() = kanten.associateBy(KantenBelegung::position)
    val felderNachPosition: Map<KartenFeld, FeldBelegung>
        get() = felder.associateBy(FeldBelegung::position)

    internal fun pruefeFuer(karte: Spielkarte) {
        require(ecken.size == ecken.map(EckBelegung::position).toSet().size) {
            "Jede Ecke darf höchstens einmal belegt sein."
        }
        require(kanten.size == kanten.map(KantenBelegung::position).toSet().size) {
            "Jede Kante darf höchstens einmal belegt sein."
        }
        require(felder.size == felder.map(FeldBelegung::position).toSet().size) {
            "Jedes Feld darf höchstens einmal belegt sein."
        }
        require(seewege.size == seewege.map(SeewegBelegung::id).toSet().size) {
            "Seeweg-IDs müssen eindeutig sein."
        }
        require(kriegseinheiten.size == kriegseinheiten.map(KriegsEinheitBelegung::id).toSet().size) {
            "Kriegseinheiten-IDs müssen eindeutig sein."
        }
        require(
            kriegseinheiten.size == kriegseinheiten.map(KriegsEinheitBelegung::position).toSet().size,
        ) {
            "Auf jeder Kante darf höchstens eine Kriegseinheit stehen."
        }
        val hauptbahnhoefeNachSpieler = ecken
            .asSequence()
            .filter { belegung -> belegung.typ == EckGebaeudeTyp.HAUPTBAHNHOF }
            .mapNotNull(EckBelegung::besitzer)
            .groupingBy { spieler -> spieler }
            .eachCount()
        require(hauptbahnhoefeNachSpieler.values.none { anzahl -> anzahl > 1 }) {
            "Jeder Spieler darf höchstens einen Hauptbahnhof besitzen."
        }

        seewege.forEach { seeweg ->
            val hafenA = eckenNachPosition[seeweg.hafenA]
            val hafenB = eckenNachPosition[seeweg.hafenB]
            require(
                hafenA?.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN) &&
                    hafenB?.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN),
            ) {
                "Ein Seeweg muss zwei Häfen verbinden."
            }
            require(
                hafenA?.besitzer == seeweg.besitzer && hafenB?.besitzer == seeweg.besitzer &&
                    hafenA.zustand == BauwerkZustand.INTAKT &&
                    hafenB.zustand == BauwerkZustand.INTAKT,
            ) {
                "Ein Frachtschiff verbindet zwei eigene, intakte Häfen."
            }
            require(karte.kuerzesterWasserweg(seeweg.hafenA, seeweg.hafenB) != null) {
                "Zwischen den Häfen existiert keine Route aus Wasser-Wasser-Kanten."
            }
        }
        ecken.filter { it.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN) }
            .forEach { hafen ->
                val belegt = seewege.count { it.hafenA == hafen.position || it.hafenB == hafen.position }
                val kapazitaet = if (hafen.typ == EckGebaeudeTyp.GROSSHAFEN) 2 else 1
                require(belegt <= kapazitaet) {
                    "Die Frachtschiffkapazität des Hafens ${hafen.position} ist überschritten."
                }
            }
        kriegseinheiten.forEach { einheit ->
            require(karte.istBefahrbar(einheit.typ, einheit.position)) {
                when (einheit.typ) {
                    KriegsEinheitTyp.PANZER ->
                        "Ein Panzer muss auf einer an Gelände grenzenden Kartenkante stehen."
                    KriegsEinheitTyp.KRIEGSSCHIFF ->
                        "Ein Kriegsschiff muss auf einer an Wasser grenzenden Kartenkante stehen."
                }
            }
        }

        felder.forEach { belegung ->
            require(belegung.position in karte.landNachPosition) {
                "Eine Feldanlage darf nur auf Gelände stehen: ${belegung.position}."
            }
        }
        ecken.forEach { belegung ->
            require(karte.liegtImBearbeitungsUmfeld(belegung.position)) {
                "Eckbelegung liegt außerhalb der Karte: ${belegung.position}."
            }
            require(!karte.istSpezialfeldMittelpunkt(belegung.position)) {
                "Die Teichmitte eines Spezialfelds darf nicht bebaut werden: " +
                    "${belegung.position}."
            }
        }
        kanten.forEach { belegung ->
            require(
                karte.liegtImBearbeitungsUmfeld(belegung.position.anfang) &&
                    karte.liegtImBearbeitungsUmfeld(belegung.position.ende),
            ) {
                "Kantenbelegung liegt außerhalb der Karte: ${belegung.position}."
            }
            val nachbarn = angrenzendeFelder(belegung.position)
            require(nachbarn.size == 2 && nachbarn.all { it in karte.landNachPosition }) {
                "Eine Handelslinie darf nur zwischen zwei Geländefeldern liegen: " +
                    "${belegung.position}."
            }
            require(!karte.istSpezialfeldInnenkante(belegung.position)) {
                "Eine zur Teichmitte führende Kante darf nicht bebaut werden: " +
                    "${belegung.position}."
            }
        }
    }
}

private fun Spielkarte.liegtImBearbeitungsUmfeld(ecke: KartenEcke): Boolean =
    angrenzendeFelder(ecke).any(::enthaeltFeld)
