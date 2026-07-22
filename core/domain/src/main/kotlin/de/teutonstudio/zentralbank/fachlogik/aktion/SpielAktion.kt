package de.teutonstudio.zentralbank.fachlogik.aktion

import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsSeite
import de.teutonstudio.zentralbank.fachlogik.modell.Friedensvertrag
import de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Eine Absicht des Benutzers oder eines Agenten. Erst die [SpielEngine] entscheidet, ob daraus
 * akzeptierte [SpielEreignis]-Objekte entstehen. Aktionen werden deshalb nie als Verlauf
 * gespeichert.
 */
@Serializable
sealed interface SpielAktion {
    @Serializable
    data class HauptbahnhofPlatzieren(
        val spieler: SpielerId,
        val ecke: KartenEcke,
    ) : SpielAktion

    @Serializable
    data class EckGebaeudeBauen(
        val spieler: SpielerId,
        val ecke: KartenEcke,
        val typ: EckGebaeudeTyp,
    ) : SpielAktion

    @Serializable
    data class EckGebaeudeAufwerten(
        val spieler: SpielerId,
        val ecke: KartenEcke,
        val zu: EckGebaeudeTyp,
    ) : SpielAktion

    @Serializable
    data class SchieneBauen(
        val spieler: SpielerId,
        val kante: KartenKante,
    ) : SpielAktion

    @Serializable
    data class AnlageErrichten(
        val spieler: SpielerId,
        val feld: KartenFeld,
        val anlage: FeldAnlage,
    ) : SpielAktion

    @Serializable
    data class BelegungAbreissen(
        val spieler: SpielerId,
        val ort: KartenOrt,
    ) : SpielAktion

    /** Die Kennung wird vom Spielkern vergeben. */
    @Serializable
    data class SeewegEinrichten(
        val spieler: SpielerId,
        val hafenA: KartenEcke,
        val hafenB: KartenEcke,
        val richtung: FrachtRichtung,
    ) : SpielAktion

    @Serializable
    data class SeewegEntfernen(
        val spieler: SpielerId,
        val id: String,
    ) : SpielAktion

    /** Die Kennung wird vom Spielkern vergeben. */
    @Serializable
    data class KriegsEinheitBauen(
        val spieler: SpielerId,
        val typ: KriegsEinheitTyp,
        val kante: KartenKante,
    ) : SpielAktion

    /** Kompatible Einsetzentscheidung; die Kennung wird vom Spielkern vergeben. */
    @Serializable
    data class KriegsEinheitEinsetzen(
        val spieler: SpielerId,
        val gegner: SpielerId,
        val typ: KriegsEinheitTyp,
        val ort: KartenOrt,
    ) : SpielAktion

    @Serializable
    data class KriegsEinheitBewegen(
        val spieler: SpielerId,
        val id: String,
        val naechsteKante: KartenKante,
    ) : SpielAktion

    @Serializable
    data class KriegsEinheitenBewegen(
        val spieler: SpielerId,
        val ids: List<String>,
        val naechsteKante: KartenKante,
    ) : SpielAktion

    @Serializable
    data class VerwaltungsruineReparieren(
        val spieler: SpielerId,
        val ecke: KartenEcke,
    ) : SpielAktion

    @Serializable
    data class VerwaltungsruineAbreissen(
        val spieler: SpielerId,
        val ecke: KartenEcke,
    ) : SpielAktion

    /** Die Anleihe-ID und die Emissionsrunde bestimmt ausschließlich der Spielkern. */
    @Serializable
    data class AnleiheEmittieren(
        val spieler: SpielerId,
        val nennwert: Geld,
        val zinsBasispunkte: Int,
        val laufzeitRunden: Int,
        val zinsbetrag: Geld? = null,
        val erwerber: KontoId = KontoId.Bank,
        val erloes: Geld = nennwert,
    ) : SpielAktion

    @Serializable
    data class AnleiheFreiwilligZurueckkaufen(
        val spieler: SpielerId,
        val anleihe: AnleiheId,
        val preis: Geld,
    ) : SpielAktion

    /** Ersetzt [alteAnleihe] vollständig; nur die Nennwertdifferenz wird ausgezahlt. */
    @Serializable
    data class AnleiheAufstocken(
        val spieler: SpielerId,
        val alteAnleihe: AnleiheId,
        val neuerNennwert: Geld,
        val zinsBasispunkte: Int,
        val laufzeitRunden: Int,
    ) : SpielAktion

    @Serializable
    data class SchuldenstrichDurchfuehren(
        val spieler: SpielerId,
        val entfernteBahnwege: Int = 0,
    ) : SpielAktion

    /** Erstellt nur ein Angebot; Bestände werden dabei nicht reserviert oder übertragen. */
    @Serializable
    data class HandelsangebotErstellen(
        val spieler: SpielerId,
        val empfaenger: SpielerId? = null,
        val angeboteneRohstoffe: Map<Rohstoff, Int> = emptyMap(),
        val geforderteRohstoffe: Map<Rohstoff, Int> = emptyMap(),
        val angebotenerGeldbetrag: Geld = Geld.NULL,
        val geforderterGeldbetrag: Geld = Geld.NULL,
    ) : SpielAktion

    @Serializable
    data class HandelsangebotAnnehmen(
        val spieler: SpielerId,
        val angebot: HandelsAngebotId,
    ) : SpielAktion

    @Serializable
    data class HandelsangebotAblehnen(
        val spieler: SpielerId,
        val angebot: HandelsAngebotId,
    ) : SpielAktion

    @Serializable
    data class HandelsangebotZurueckziehen(
        val spieler: SpielerId,
        val angebot: HandelsAngebotId,
    ) : SpielAktion

    @Serializable
    data class AnleihenangebotErstellen(
        val spieler: SpielerId,
        val empfaenger: SpielerId? = null,
        val anleihe: AnleiheId,
        val preis: Geld,
    ) : SpielAktion

    @Serializable
    data class AnleihenangebotAnnehmen(
        val spieler: SpielerId,
        val angebot: AnleihenAngebotId,
    ) : SpielAktion

    @Serializable
    data class AnleihenangebotAblehnen(
        val spieler: SpielerId,
        val angebot: AnleihenAngebotId,
    ) : SpielAktion

    @Serializable
    data class AnleihenangebotZurueckziehen(
        val spieler: SpielerId,
        val angebot: AnleihenAngebotId,
    ) : SpielAktion

    @Serializable
    data class ProzugBeginnen(val zugId: Long) : SpielAktion

    @Serializable
    data class VerarbeitungAusfuehren(
        val zugId: Long,
        val feld: KartenFeld,
        val laeufe: Int = 1,
    ) : SpielAktion

    @Serializable
    data class VerwaltungsstandortVersorgen(
        val zugId: Long,
        val ecke: KartenEcke,
    ) : SpielAktion

    @Serializable
    data class VerbindlichkeitBegleichen(
        val zugId: Long,
        val verbindlichkeit: VerbindlichkeitId,
    ) : SpielAktion

    @Serializable
    data class ProzugAbschliessen(val zugId: Long) : SpielAktion

    /** Autoritative letzte Prüfung, nachdem sämtliche normalen Rettungswege ausgeschöpft sind. */
    @Serializable
    data class ZahlungsunfaehigkeitFeststellen(
        val spieler: SpielerId,
        val zugId: Long,
    ) : SpielAktion

    @Serializable
    data object ZugBeenden : SpielAktion

    @Serializable
    data class WarenkorbAendern(val warenkorb: Map<Rohstoff, Int>) : SpielAktion

    @Serializable
    data class RohstoffHandeln(
        val kaeufer: SpielerId,
        val verkaeufer: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
    ) : SpielAktion

    @Serializable
    data class MitAuslandHandeln(
        val spieler: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
        @SerialName("handelsart") val art: AussenhandelsArt,
    ) : SpielAktion

    @Serializable
    data class KriegErklaeren(
        val aggressor: SpielerId,
        val verteidiger: SpielerId,
    ) : SpielAktion

    @Serializable
    data class KriegsAllianzBeitreten(
        val spieler: SpielerId,
        val krieg: KriegId,
        val seite: KriegsSeite,
    ) : SpielAktion

    @Serializable
    data class WaffenstillstandAnbieten(
        val spieler: SpielerId,
        val krieg: KriegId,
        val gegner: SpielerId,
    ) : SpielAktion

    @Serializable
    data class WaffenstillstandAnnehmen(
        val spieler: SpielerId,
        val krieg: KriegId,
        val von: SpielerId,
    ) : SpielAktion

    @Serializable
    data class KriegKapitulieren(
        val spieler: SpielerId,
        val krieg: KriegId,
    ) : SpielAktion

    @Serializable
    data class FriedensvertragVorschlagen(
        val spieler: SpielerId,
        val vertrag: Friedensvertrag,
    ) : SpielAktion

    @Serializable
    data class FriedensvertragAnnehmen(
        val spieler: SpielerId,
        val vertrag: FriedensvertragId,
    ) : SpielAktion

    @Serializable
    data class UnabhaengigenFriedenSchliessen(
        val spieler: SpielerId,
        val krieg: KriegId,
        val gegner: SpielerId,
    ) : SpielAktion

    @Serializable
    data class RessourcenUebertragen(
        val spieler: SpielerId,
        val empfaenger: SpielerId,
        val rohstoffe: Map<Rohstoff, Int> = emptyMap(),
        val geld: Geld = Geld.NULL,
    ) : SpielAktion

}
