package de.teutonstudio.zentralbank.domain

import kotlinx.serialization.Serializable

@Serializable
data class Schuldenstrich(
    val spieler: SpielerId,
    val runde: Int,
    val ausgezahlterBetrag: Geld,
    val geloeschteAnleihen: List<AnleiheId>,
    val entfernteBahnwege: Int,
)
