package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
data class Konflikt(
    val spielerA: SpielerId,
    val spielerB: SpielerId,
) {
    init {
        require(spielerA != spielerB) { "Konfliktparteien muessen verschieden sein." }
    }

    fun betrifft(a: SpielerId, b: SpielerId): Boolean {
        return (spielerA == a && spielerB == b) || (spielerA == b && spielerB == a)
    }
}
