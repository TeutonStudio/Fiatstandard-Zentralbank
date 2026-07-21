package de.teutonstudio.zentralbank.fachlogik.ereignis

import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund
import de.teutonstudio.zentralbank.fachlogik.modell.SpielErgebnis
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebot
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebot
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebotId
import kotlinx.serialization.Serializable

@Serializable
sealed interface SpielEreignis {
    @Serializable
    data class HandelsangebotErstellt(val angebot: HandelsAngebot) : SpielEreignis

    @Serializable
    data class HandelsangebotAngenommen(
        val angebot: HandelsAngebotId,
        val angenommenVon: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class HandelsangebotAbgelehnt(
        val angebot: HandelsAngebotId,
        val abgelehntVon: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class HandelsangebotZurueckgezogen(
        val angebot: HandelsAngebotId,
        val spieler: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class AnleihenangebotErstellt(val angebot: AnleihenAngebot) : SpielEreignis

    @Serializable
    data class AnleihenangebotAngenommen(
        val angebot: AnleihenAngebotId,
        val angenommenVon: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class AnleihenangebotAbgelehnt(
        val angebot: AnleihenAngebotId,
        val abgelehntVon: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class AnleihenangebotZurueckgezogen(
        val angebot: AnleihenAngebotId,
        val spieler: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class AngeboteAbgelaufen(
        val handelsangebote: List<HandelsAngebotId>,
        val anleihenangebote: List<AnleihenAngebotId>,
    ) : SpielEreignis

    @Serializable
    data class SpielerAusgeschieden(
        val spieler: SpielerId,
        val grund: AusscheidensGrund,
    ) : SpielEreignis

    @Serializable
    data class PartieBeendet(val ergebnis: SpielErgebnis) : SpielEreignis

    @Serializable
    data class ProzugBegonnen(
        val zugId: Long,
    ) : SpielEreignis

    @Serializable
    data class VerarbeitungAusgefuehrt(
        val zugId: Long,
        val feld: KartenFeld,
        val laeufe: Int,
    ) : SpielEreignis

    @Serializable
    data class VerwaltungsstandortVersorgt(
        val zugId: Long,
        val ecke: KartenEcke,
    ) : SpielEreignis

    @Serializable
    data class VerbindlichkeitBeglichen(
        val zugId: Long,
        val verbindlichkeit: VerbindlichkeitId,
    ) : SpielEreignis

    @Serializable
    data class ProzugErfolgreichAbgeschlossen(
        val zugId: Long,
    ) : SpielEreignis

    @Serializable
    data class WarenkorbGeaendert(
        val warenkorb: Map<Rohstoff, Int>,
    ) : SpielEreignis

    @Serializable
    data class RohstoffEinnahme(
        val spieler: SpielerId,
        val mengen: Map<Rohstoff, Int>,
    ) : SpielEreignis

    @Serializable
    data class RohstoffAusgabe(
        val spieler: SpielerId,
        val mengen: Map<Rohstoff, Int>,
    ) : SpielEreignis

    @Serializable
    data class Transaktion(
        val von: KontoId,
        val an: KontoId,
        val betrag: Geld,
        val grund: TransaktionsGrund,
    ) : SpielEreignis

    @Serializable
    data class AnleiheGekauft(
        val anleihe: AnleiheId,
        val kaeufer: SpielerId,
        val verkaeufer: KontoId,
        val preis: Geld,
    ) : SpielEreignis

    @Serializable
    data class AnleiheVerkauft(
        val anleihe: AnleiheId,
        val verkaeufer: SpielerId,
        val kaeufer: KontoId,
        val preis: Geld,
    ) : SpielEreignis

    @Serializable
    data class AnleiheFaellig(
        val anleihe: AnleiheId,
    ) : SpielEreignis

    @Serializable
    data class AnleiheEmittiert(
        val anleihe: Anleihe,
        val erwerber: KontoId,
        val erloes: Geld,
    ) : SpielEreignis

    @Serializable
    data class AnleiheFreiwilligZurueckgekauft(
        val anleihe: AnleiheId,
        val emittent: SpielerId,
        val preis: Geld,
    ) : SpielEreignis

    @Serializable
    data class RohstoffHandel(
        val kaeufer: SpielerId,
        val verkaeufer: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
    ) : SpielEreignis

    @Serializable
    data class AuslandsHandel(
        val spieler: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
        val art: AussenhandelsArt,
    ) : SpielEreignis

    @Serializable
    /** Kompatibilitätsereignis für bestehende Ereignisverläufe ohne Kartenort. */
    data class Expansion(
        val spieler: SpielerId,
        val bauteil: BauteilTyp,
    ) : SpielEreignis

    @Serializable
    data class HauptbahnhofPlatziert(
        val spieler: SpielerId,
        val ecke: KartenEcke,
    ) : SpielEreignis

    @Serializable
    data class EckGebaeudeGebaut(
        val spieler: SpielerId,
        val ecke: KartenEcke,
        val typ: EckGebaeudeTyp,
    ) : SpielEreignis

    @Serializable
    data class EckGebaeudeAufgewertet(
        val spieler: SpielerId,
        val ecke: KartenEcke,
        val zu: EckGebaeudeTyp,
    ) : SpielEreignis

    @Serializable
    data class SchieneGebaut(
        val spieler: SpielerId,
        val kante: KartenKante,
    ) : SpielEreignis

    @Serializable
    data class NeutraleAnlageErrichtet(
        val errichter: SpielerId,
        val feld: KartenFeld,
        val anlage: FeldAnlage,
    ) : SpielEreignis

    @Serializable
    data class KartenBelegungEntfernt(
        val spieler: SpielerId,
        val ort: KartenOrt,
        val grund: KartenAenderungsGrund = KartenAenderungsGrund.SPIELERAKTION,
    ) : SpielEreignis

    @Serializable
    data class KartenBauwerkZustandGeaendert(
        val spieler: SpielerId,
        val ort: KartenOrt,
        val zustand: BauwerkZustand,
        val grund: KartenAenderungsGrund,
    ) : SpielEreignis

    @Serializable
    data class FeldAnlagenZustandGeaendert(
        val spieler: SpielerId,
        val feld: KartenFeld,
        val zustand: AnlagenZustand,
        val grund: KartenAenderungsGrund,
    ) : SpielEreignis

    @Serializable
    data class SeewegEingerichtet(
        val id: String,
        val spieler: SpielerId,
        val hafenA: KartenEcke,
        val hafenB: KartenEcke,
        val richtung: FrachtRichtung,
    ) : SpielEreignis

    @Serializable
    data class SeewegEntfernt(
        val spieler: SpielerId,
        val id: String,
    ) : SpielEreignis

    @Serializable
    data class SeewegRouteGeaendert(
        val spieler: SpielerId,
        val id: String,
        val hafenA: KartenEcke,
        val hafenB: KartenEcke,
    ) : SpielEreignis

    @Serializable
    data class KriegsEinheitEingesetzt(
        val id: String,
        val spieler: SpielerId,
        val gegner: SpielerId,
        val typ: KriegsEinheitTyp,
        val ort: KartenOrt,
    ) : SpielEreignis

    @Serializable
    data class KriegsEinheitGebaut(
        val id: String,
        val spieler: SpielerId,
        val typ: KriegsEinheitTyp,
        val kante: KartenKante,
    ) : SpielEreignis

    @Serializable
    data class KriegsEinheitBewegt(
        val spieler: SpielerId,
        val id: String,
        /** Jede Kante ist ein einzelner Schritt nach der aktuellen Position. */
        val weg: List<KartenKante>,
    ) : SpielEreignis

    @Serializable
    data class KriegsEinheitenBewegt(
        val spieler: SpielerId,
        val ids: List<String>,
        /** Jede Kante ist ein gemeinsamer Schritt nach der aktuellen Position. */
        val weg: List<KartenKante>,
    ) : SpielEreignis

    @Serializable
    data class KriegsEinheitEntfernt(
        val spieler: SpielerId,
        val id: String,
    ) : SpielEreignis

    @Serializable
    data class KriegErklaert(
        val aggressor: SpielerId,
        val verteidiger: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class KriegBeendet(
        val spielerA: SpielerId,
        val spielerB: SpielerId,
    ) : SpielEreignis

    @Serializable
    data class Schuldenstrich(
        val spieler: SpielerId,
        val entfernteBahnwege: Int,
    ) : SpielEreignis

    @Serializable
    data class RundenwerteAktualisiert(
        val runde: Int,
        val marktpreise: Map<Rohstoff, Geld>,
        val leitzins: Basispunkte,
    ) : SpielEreignis

    /** Vollständig vom Domain-Kern berechneter Beginn einer neuen Runde. */
    @Serializable
    data class RundeBegonnen(
        val runde: Int,
        val marktpreise: Map<Rohstoff, Geld>,
        val leitzins: Basispunkte,
        val preisinflation: Basispunkte? = null,
    ) : SpielEreignis

    @Serializable
    data object ZugBeendet : SpielEreignis
}

@Serializable
enum class AussenhandelsArt {
    IMPORT,
    EXPORT,
}

@Serializable
enum class TransaktionsGrund {
    ROHSTOFFHANDEL,
    ANLEIHENHANDEL,
    ZINS,
    RUECKZAHLUNG,
    SONSTIGES,
}

@Serializable
enum class KartenAenderungsGrund {
    SPIELERAKTION,
    BELAGERUNG,
    SCHULDENSTRICH,
    REGELFOLGE,
}
