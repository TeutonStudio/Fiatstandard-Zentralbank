package de.teutonstudio.zentralbank.domain.events

import de.teutonstudio.zentralbank.domain.AnleiheId
import de.teutonstudio.zentralbank.domain.BauteilTyp
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.KontoId
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.SchrittTyp
import kotlinx.serialization.Serializable

@Serializable
sealed interface GameEvent {
    @Serializable
    data class WarenkorbGeaendert(
        val warenkorb: Map<Rohstoff, Int>,
    ) : GameEvent

    @Serializable
    data class RohstoffEinnahme(
        val spieler: SpielerId,
        val mengen: Map<Rohstoff, Int>,
    ) : GameEvent

    @Serializable
    data class RohstoffAusgabe(
        val spieler: SpielerId,
        val mengen: Map<Rohstoff, Int>,
    ) : GameEvent

    @Serializable
    data class Transaktion(
        val von: KontoId,
        val an: KontoId,
        val betrag: Geld,
        val grund: TransaktionsGrund,
    ) : GameEvent

    @Serializable
    data class AnleiheGekauft(
        val anleihe: AnleiheId,
        val kaeufer: SpielerId,
        val verkaeufer: KontoId,
        val preis: Geld,
    ) : GameEvent

    @Serializable
    data class AnleiheVerkauft(
        val anleihe: AnleiheId,
        val verkaeufer: SpielerId,
        val kaeufer: KontoId,
        val preis: Geld,
    ) : GameEvent

    @Serializable
    data class AnleiheFaellig(
        val anleihe: AnleiheId,
    ) : GameEvent

    @Serializable
    data class RohstoffHandel(
        val kaeufer: SpielerId,
        val verkaeufer: SpielerId,
        val rohstoff: Rohstoff,
        val menge: Int,
        val preis: Geld,
    ) : GameEvent

    @Serializable
    data class Expansion(
        val spieler: SpielerId,
        val bauteil: BauteilTyp,
    ) : GameEvent

    @Serializable
    data class KriegErklaert(
        val aggressor: SpielerId,
        val verteidiger: SpielerId,
    ) : GameEvent

    @Serializable
    data class KriegBeendet(
        val spielerA: SpielerId,
        val spielerB: SpielerId,
    ) : GameEvent

    @Serializable
    data class Schuldenstrich(
        val spieler: SpielerId,
        val entfernteBahnwege: Int,
    ) : GameEvent

    @Serializable
    data class SchrittAbgeschlossen(
        val schritt: SchrittTyp,
    ) : GameEvent

    @Serializable
    data class PhaseAbgeschlossen(
        val phase: Phase,
    ) : GameEvent

    @Serializable
    data object ZugBeendet : GameEvent
}

@Serializable
enum class TransaktionsGrund {
    ROHSTOFFHANDEL,
    ANLEIHENHANDEL,
    ZINS,
    RUECKZAHLUNG,
    SONSTIGES,
}
