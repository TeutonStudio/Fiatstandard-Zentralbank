package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SpielerId(val wert: String)

@Serializable
sealed interface KontoId {
    @Serializable
    data class Spieler(val id: SpielerId) : KontoId

    @Serializable
    data object Bank : KontoId

    @Serializable
    data object Ausland : KontoId
}

@Serializable
enum class Rohstoff {
    NAHRUNG,
    LEHM,
    ZIEGEL,
    HOLZ,
    ROHOEL,
    SCHWEROEL,
    DIESEL,
    KOHLE,
    STAHL,
    EISEN,
}

@Serializable
data class RohstoffMenge(
    val rohstoff: Rohstoff,
    val menge: Int,
) {
    init {
        require(menge >= 0) { "Rohstoffmenge darf nicht negativ sein." }
    }
}

@Serializable
data class Spieler(
    val id: SpielerId,
    val name: String,
    val passwortHash: String = "",
    val rohstoffe: Map<Rohstoff, Int> = emptyMap(),
    val geldkonto: Geld = Geld.NULL,
    val anleihen: List<AnleiheId> = emptyList(),
    val bauteile: Map<BauteilTyp, Int> = emptyMap(),
)

@JvmInline
@Serializable
value class AnleiheId(val wert: String)

@Serializable
data class Anleihe(
    val id: AnleiheId,
    val emittent: SpielerId,
    val nennwert: Geld,
    val zinsBasispunkte: Int,
    val laufzeitRunden: Int,
    val zinsbetrag: Geld? = null,
    val emissionsRunde: Int = 0,
    val faelligkeitsRunde: Int = emissionsRunde + laufzeitRunden + 1,
) {
    init {
        require(laufzeitRunden > 0) { "Laufzeit muss positiv sein." }
        require(emissionsRunde >= 0) { "Emissionsrunde darf nicht negativ sein." }
        require(faelligkeitsRunde > emissionsRunde) {
            "Fälligkeitsrunde muss nach der Emissionsrunde liegen."
        }
    }
}

@Serializable
data class SpielZustand(
    val spieler: List<Spieler>,
    val karte: Spielkarte? = null,
    val spielabschnitt: Spielabschnitt = Spielabschnitt.REGULAER,
    /** Noch zu platzierende, bereits zum Startbestand der Spieler gehörende Bauwerke. */
    val rundeNullRestbestand: Map<SpielerId, Map<BauteilTyp, Int>>? = null,
    val bankkonto: Geld = Geld.NULL,
    val auslandskonto: Geld = Geld.NULL,
    val bankAnleihen: List<AnleiheId> = emptyList(),
    val warenkorb: Map<Rohstoff, Int> = emptyMap(),
    val anleihen: Map<AnleiheId, Anleihe> = emptyMap(),
    val konflikte: Set<Konflikt> = emptySet(),
    val schuldenstriche: List<Schuldenstrich> = emptyList(),
    val ueberschuldungen: List<UeberschuldungsStatus> = emptyList(),
    val marktpreise: Map<Rohstoff, Geld> = emptyMap(),
    val leitzins: Basispunkte = Basispunkte.NULL,
    val rundenzähler: Int = 0,
    val aktiverSpieler: SpielerId? = spieler.firstOrNull()?.id,
    val zugStatus: ZugStatus? = aktiverSpieler?.let {
        ZugStatus(zugId = 1L, spieler = it, phase = ZugPhase.Prozug)
    },
)

@Serializable
enum class Spielabschnitt {
    RUNDE_NULL,
    REGULAER,
}
