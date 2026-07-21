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
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import kotlinx.serialization.Serializable

/**
 * Eine Absicht des Benutzers oder eines Agenten. Erst die [SpielEngine] entscheidet, ob daraus
 * akzeptierte [SpielEreignis]-Objekte entstehen. Aktionen werden deshalb nie als Verlauf
 * gespeichert.
 */
@Serializable
sealed interface SpielAktion {
    /** Aufgabe ist eine ausdrückliche Spielerentscheidung und keine Zeitstrafe. */
    @Serializable
    data class Aufgeben(val spieler: SpielerId) : SpielAktion

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

    @Serializable
    data class SchuldenstrichDurchfuehren(
        val spieler: SpielerId,
        val entfernteBahnwege: Int,
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
        val art: AussenhandelsArt,
    ) : SpielAktion

    @Serializable
    data class KriegErklaeren(
        val aggressor: SpielerId,
        val verteidiger: SpielerId,
    ) : SpielAktion

    @Serializable
    data class FriedenSchliessen(
        val spielerA: SpielerId,
        val spielerB: SpielerId,
    ) : SpielAktion
}
