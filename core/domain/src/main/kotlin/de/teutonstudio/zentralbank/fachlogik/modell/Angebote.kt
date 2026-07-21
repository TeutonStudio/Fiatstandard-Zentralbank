package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class HandelsAngebotId(val wert: Long)

@Serializable
enum class HandelsAngebotStatus {
    OFFEN,
    ANGENOMMEN,
    ABGELEHNT,
    ZURUECKGEZOGEN,
    ABGELAUFEN,
}

@Serializable
data class HandelsAngebot(
    val id: HandelsAngebotId,
    val anbieter: SpielerId,
    val empfaenger: SpielerId? = null,
    val angeboteneRohstoffe: Map<Rohstoff, Int> = emptyMap(),
    val geforderteRohstoffe: Map<Rohstoff, Int> = emptyMap(),
    val angebotenerGeldbetrag: Geld = Geld.NULL,
    val geforderterGeldbetrag: Geld = Geld.NULL,
    val erstelltInZug: Long,
    val erstelltInRunde: Int,
    val status: HandelsAngebotStatus = HandelsAngebotStatus.OFFEN,
)

@JvmInline
@Serializable
value class AnleihenAngebotId(val wert: Long)

@Serializable
data class AnleihenAngebot(
    val id: AnleihenAngebotId,
    val anbieter: SpielerId,
    val empfaenger: SpielerId? = null,
    val anleihe: AnleiheId,
    val preis: Geld,
    val erstelltInZug: Long,
    val erstelltInRunde: Int,
    val status: HandelsAngebotStatus = HandelsAngebotStatus.OFFEN,
)
