package de.teutonstudio.zentralbank.fachlogik.beobachtung

import de.teutonstudio.zentralbank.fachlogik.modell.*
import kotlinx.serialization.Serializable

const val AKTUELLE_BEOBACHTUNGS_VERSION = 2

@Serializable
data class SpielBeobachtung(
    val beobachtungsVersion: Int = AKTUELLE_BEOBACHTUNGS_VERSION,
    val betrachtenderSpieler: SpielerId,
    val runde: Int,
    val zug: ZugBeobachtung?,
    /** Vollständige öffentliche Daten aller Spieler in Sitzreihenfolge. */
    val spieler: List<SpielerBeobachtung>,
    val markt: MarktBeobachtung,
    val karte: KartenBeobachtung?,
    val angebote: List<AngebotBeobachtung>,
    val kriege: List<Konflikt>,
    val friedensvertraege: List<Friedensvertrag>,
    val belagerungen: List<Belagerung>,
    val schuldenstriche: List<Schuldenstrich>,
    val zentralbankGeldschoepfungen: List<ZentralbankGeldschoepfung>,
    val ergebnis: SpielErgebnis?,
) {
    val eigeneWirtschaft: SpielerBeobachtung
        get() = spieler.single { it.id == betrachtenderSpieler }
    val gegner: List<SpielerBeobachtung>
        get() = spieler.filter { it.id != betrachtenderSpieler }
}

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
data class AnleiheBeobachtung(
    val id: AnleiheId,
    val emittent: SpielerId,
    val glaeubiger: KontoId,
    val nennwert: Geld,
    val rueckkaufsbetrag: Geld,
    val zinsBasispunkte: Int,
    val laufzeitRunden: Int,
    val emissionsRunde: Int,
    val faelligkeitsRunde: Int,
    val geleisteteZinszahlungen: Int,
)

@Serializable
data class AbgeschlossenesHandelsgeschaeftBeobachtung(
    val id: String,
    val art: String,
    val gegenpartei: SpielerId?,
    val status: String,
)

@Serializable
data class SpielerBeobachtung(
    val sitzPosition: Int,
    val id: SpielerId,
    val name: String,
    val spielstil: SpielerStil,
    val aktiv: Boolean,
    val ausgeschieden: Boolean,
    val amZug: Boolean,
    val marktwert: Geld,
    val rohstoffe: List<RohstoffBestandBeobachtung>,
    val geld: Geld,
    val anleihenImBesitz: List<AnleiheId>,
    val offeneEigeneAnleihen: List<AnleiheBeobachtung>,
    val bauteile: List<BauteilBestandBeobachtung>,
    val gesamtertragJeRunde: List<RohstoffBestandBeobachtung>,
    val produktionsmengenJeRohstoff: List<RohstoffBestandBeobachtung>,
    val gesamteVersorgungskosten: List<RohstoffBestandBeobachtung>,
    val abgeschlosseneHandelsgeschaefte: List<AbgeschlossenesHandelsgeschaeftBeobachtung>,
    val kontrollierteVerwaltungsstandorte: List<KartenEcke>,
    val erreichbareWirtschaftsstandorte: List<KartenFeld>,
    val einheiten: List<KriegsEinheitBeobachtung>,
    val kriege: List<KriegId>,
    val allianzen: List<KriegId>,
    val waffenstillstaende: List<SpielerPaar>,
    val kapitulationen: List<FriedensvertragId>,
    val friedensvertraege: List<FriedensvertragId>,
)

@Serializable
data class RohstoffPreisBeobachtung(val rohstoff: Rohstoff, val preis: Geld)

@Serializable
data class MarktBeobachtung(
    val preise: List<RohstoffPreisBeobachtung>,
    val leitzinsBasispunkte: Int,
    val bankkonto: Geld,
    val auslandskonto: Geld,
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
data class ErreichbarkeitBeobachtung(
    val spieler: SpielerId,
    val ecken: List<KartenEcke>,
    val wirtschaftsstandorte: List<KartenFeld>,
)

@Serializable
data class BlockadeBeobachtung(
    val blockierterSpieler: SpielerId,
    val kante: KartenKante,
    val durchSpieler: List<SpielerId>,
    val art: String,
)

@Serializable
data class KartenBeobachtung(
    val id: String,
    val name: String,
    val hexagon: KartenHexagon,
    /** Sämtliche Felder einschließlich Wasser in kanonischer Reihenfolge. */
    val felder: List<TopologieFeldBeobachtung>,
    /** Vollständige Gitterknoten, ohne dass ein Client Topologieregeln nachbilden muss. */
    val knoten: List<KartenEcke>,
    /** Vollständige Gitterkanten einschließlich Land- und Seekanten. */
    val kanten: List<TopologieKanteBeobachtung>,
    val gelaendefelder: List<GelaendeFeld>,
    val spezialfelder: List<Spezialfeld>,
    val eckBauwerke: List<EckBauwerkBeobachtung>,
    val handelslinien: List<HandelslinieBeobachtung>,
    val feldAnlagen: List<FeldAnlageBeobachtung>,
    val seewege: List<SeewegBeobachtung>,
    val kriegseinheiten: List<KriegsEinheitBeobachtung>,
    val erreichbarkeit: List<ErreichbarkeitBeobachtung>,
    val blockaden: List<BlockadeBeobachtung>,
)

@Serializable
data class TopologieFeldBeobachtung(
    val position: KartenFeld,
    val gelaende: GelaendeTyp?,
    val wasser: Boolean,
    val spezialfeld: SpezialfeldTyp?,
)

@Serializable
data class TopologieKanteBeobachtung(
    val position: KartenKante,
    val landkante: Boolean,
    val seekante: Boolean,
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
    val kontrolliertVon: List<SpielerId>,
)

@Serializable
data class SeewegBeobachtung(
    val id: String,
    val hafenA: KartenEcke,
    val hafenB: KartenEcke,
    val besitzer: SpielerId,
    val richtung: FrachtRichtung,
    val frachtschiffVorhanden: Boolean = true,
    val aktiv: Boolean,
)

@Serializable
data class KriegsEinheitBeobachtung(
    val id: String,
    val typ: KriegsEinheitTyp,
    val besitzer: SpielerId,
    val ort: KartenOrt,
)
