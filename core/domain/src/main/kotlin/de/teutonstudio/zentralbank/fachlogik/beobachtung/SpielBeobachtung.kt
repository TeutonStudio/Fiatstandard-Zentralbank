package de.teutonstudio.zentralbank.fachlogik.beobachtung

import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielErgebnis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import kotlinx.serialization.Serializable

const val AKTUELLE_BEOBACHTUNGS_VERSION = 1

@Serializable
data class SpielBeobachtung(
    val beobachtungsVersion: Int = AKTUELLE_BEOBACHTUNGS_VERSION,
    val betrachtenderSpieler: SpielerId,
    val runde: Int,
    val zug: ZugBeobachtung?,
    val eigeneWirtschaft: SpielerBeobachtung,
    val gegner: List<GegnerBeobachtung>,
    val markt: MarktBeobachtung,
    val karte: KartenBeobachtung?,
    val angebote: List<AngebotBeobachtung>,
    val ergebnis: SpielErgebnis?,
)

@Serializable
data class ZugBeobachtung(
    val zugId: Long,
    val aktiverSpieler: SpielerId,
    val phase: ZugPhase,
    val prozugBegonnen: Boolean,
    val prozugAbgeschlossen: Boolean,
)

@Serializable
data class RohstoffBestandBeobachtung(val rohstoff: Rohstoff, val menge: Int)

@Serializable
data class BauteilBestandBeobachtung(val bauteil: BauteilTyp, val menge: Int)

@Serializable
data class SpielerBeobachtung(
    val id: SpielerId,
    val name: String,
    val rohstoffe: List<RohstoffBestandBeobachtung>,
    val geld: Geld,
    val anleihen: List<AnleiheId>,
    val bauteile: List<BauteilBestandBeobachtung>,
    val ausgeschieden: Boolean,
)

/**
 * Gegnerische Lagerbestände, Geldkonten und Passwortdaten sind nicht sichtbar.
 * Sichtbar bleiben nur Identität, Zugfähigkeit und auf der öffentlichen Karte
 * beziehungsweise im Anleihenregister ohnehin erkennbare Summen.
 */
@Serializable
data class GegnerBeobachtung(
    val id: SpielerId,
    val name: String,
    val ausgeschieden: Boolean,
    val oeffentlicheBauwerke: Int,
    val emittierteAnleihen: Int,
)

@Serializable
data class RohstoffPreisBeobachtung(val rohstoff: Rohstoff, val preis: Geld)

@Serializable
data class MarktBeobachtung(
    val preise: List<RohstoffPreisBeobachtung>,
    val leitzinsBasispunkte: Int,
)

@Serializable
sealed interface AngebotBeobachtung {
    val status: String

    @Serializable
    data class RohstoffHandel(
        val id: HandelsAngebotId,
        val anbieter: SpielerId,
        val empfaenger: SpielerId?,
        val angeboteneRohstoffe: List<RohstoffBestandBeobachtung>,
        val geforderteRohstoffe: List<RohstoffBestandBeobachtung>,
        val angebotenerGeldbetrag: Geld,
        val geforderterGeldbetrag: Geld,
        override val status: String,
    ) : AngebotBeobachtung

    @Serializable
    data class AnleihenHandel(
        val id: AnleihenAngebotId,
        val anbieter: SpielerId,
        val empfaenger: SpielerId?,
        val anleihe: AnleiheId,
        val preis: Geld,
        override val status: String,
    ) : AngebotBeobachtung
}

@Serializable
data class KartenBeobachtung(
    val id: String,
    val name: String,
    val gelaendefelder: List<GelaendeFeld>,
    val eckBauwerke: List<EckBauwerkBeobachtung>,
    val handelslinien: List<HandelslinieBeobachtung>,
    val feldAnlagen: List<FeldAnlageBeobachtung>,
    val seewege: List<SeewegBeobachtung>,
    val kriegseinheiten: List<KriegsEinheitBeobachtung>,
)

@Serializable
data class EckBauwerkBeobachtung(
    val position: KartenEcke,
    val typ: EckGebaeudeTyp,
    val besitzer: SpielerId?,
    val zustand: BauwerkZustand,
)

@Serializable
data class HandelslinieBeobachtung(
    val position: KartenKante,
    val erbautVon: SpielerId?,
    val zustand: BauwerkZustand,
)

@Serializable
data class FeldAnlageBeobachtung(
    val position: KartenFeld,
    val anlage: FeldAnlage,
    val zustand: AnlagenZustand,
)

@Serializable
data class SeewegBeobachtung(
    val id: String,
    val hafenA: KartenEcke,
    val hafenB: KartenEcke,
    val besitzer: SpielerId,
    val richtung: FrachtRichtung,
)

@Serializable
data class KriegsEinheitBeobachtung(
    val id: String,
    val typ: KriegsEinheitTyp,
    val besitzer: SpielerId,
    val ort: KartenOrt,
)
