package de.teutonstudio.zentralbank.fachlogik.ereignis

import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.serialization.Serializable

@Serializable
sealed interface SpielEreignis {
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
    data class RohstoffHandel(
        val kaeufer: SpielerId,
        val verkaeufer: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
    ) : SpielEreignis

    @Serializable
    data class Expansion(
        val spieler: SpielerId,
        val bauteil: BauteilTyp,
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
    data class SchrittAbgeschlossen(
        val schritt: SchrittTyp,
    ) : SpielEreignis

    @Serializable
    data class PhaseAbgeschlossen(
        val phase: Phase,
    ) : SpielEreignis

    @Serializable
    data object ZugBeendet : SpielEreignis
}

@Serializable
enum class TransaktionsGrund {
    ROHSTOFFHANDEL,
    ANLEIHENHANDEL,
    ZINS,
    RUECKZAHLUNG,
    SONSTIGES,
}
