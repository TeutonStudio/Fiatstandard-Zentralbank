package de.teutonstudio.zentralbank.domain

import kotlinx.serialization.Serializable
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.ZugStatus

@JvmInline
@Serializable
value class SpielerId(val wert: String)

@Serializable
sealed interface KontoId {
    @Serializable
    data class Spieler(val id: SpielerId) : KontoId

    @Serializable
    data object Bank : KontoId
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
) {
    init {
        require(laufzeitRunden > 0) { "Laufzeit muss positiv sein." }
    }
}

@Serializable
data class GameState(
    val spieler: List<Spieler>,
    val bankkonto: Geld = Geld.NULL,
    val bankAnleihen: List<AnleiheId> = emptyList(),
    val warenkorb: Map<Rohstoff, Int> = emptyMap(),
    val anleihen: Map<AnleiheId, Anleihe> = emptyMap(),
    val konflikte: Set<Konflikt> = emptySet(),
    val schuldenstriche: List<Schuldenstrich> = emptyList(),
    val marktpreise: Map<Rohstoff, Geld> = emptyMap(),
    val leitzins: Basispunkte = Basispunkte.NULL,
    val rundenzähler: Int = 0,
    val aktiverSpieler: SpielerId? = spieler.firstOrNull()?.id,
    val zugStatus: ZugStatus? = aktiverSpieler?.let { ZugStatus(it, Phase.Einnahmen) },
)
