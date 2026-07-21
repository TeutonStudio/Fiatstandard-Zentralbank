package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

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
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
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
    /** Einzelpreise der laufenden Runde; daraus entstehen die Preise der Folgerunde. */
    val marktpreisBeobachtungen: Map<Rohstoff, List<Geld>> = emptyMap(),
    val geldpolitik: Geldpolitik = Geldpolitik(),
    val rundenwerte: List<Rundenwerte> = emptyList(),
    val ausgeschiedeneSpieler: Set<SpielerId> = emptySet(),
    val ausscheidensReihenfolge: List<SpielerId> = emptyList(),
    val ergebnis: SpielErgebnis? = null,
    val naechsteAnleiheNummer: Long = 1L,
    val naechsteSeewegNummer: Long = 1L,
    val naechsteEinheitenNummer: Long = 1L,
    val handelsAngebote: List<HandelsAngebot> = emptyList(),
    val anleihenAngebote: List<AnleihenAngebot> = emptyList(),
    val naechsteAngebotsNummer: Long = 1L,
    val rundenzähler: Int = 0,
    val aktiverSpieler: SpielerId? = spieler.firstOrNull()?.id,
    val zugStatus: ZugStatus? = aktiverSpieler?.let {
        ZugStatus(zugId = 1L, spieler = it, phase = ZugPhase.Prozug)
    },
)

/**
 * Ganzzahlige Entsprechung der bisherigen Android-Geldpolitik. Alle Werte sind
 * Basispunkte, damit die Domain weder Float noch Double für Geldregeln benötigt.
 */
@Serializable
data class Geldpolitik(
    val inflationsziel: Basispunkte = Basispunkte.prozent(2),
    val normaleAbweichung: Basispunkte = Basispunkte.prozent(1),
    val starkeAbweichung: Basispunkte = Basispunkte.prozent(3),
    val leitzinsSchritt: Basispunkte = Basispunkte.prozent(1),
) {
    init {
        require(normaleAbweichung.wert >= 0) { "Normale Abweichung darf nicht negativ sein." }
        require(starkeAbweichung.wert >= normaleAbweichung.wert) {
            "Starke Abweichung muss mindestens so groß wie die normale Abweichung sein."
        }
        require(leitzinsSchritt.wert >= 0) { "Leitzinsschritt darf nicht negativ sein." }
    }
}

@Serializable
data class Rundenwerte(
    val runde: Int,
    val marktpreise: Map<Rohstoff, Geld>,
    val leitzins: Basispunkte,
    val preisinflation: Basispunkte? = null,
)

@Serializable
enum class Spielabschnitt {
    RUNDE_NULL,
    REGULAER,
}
