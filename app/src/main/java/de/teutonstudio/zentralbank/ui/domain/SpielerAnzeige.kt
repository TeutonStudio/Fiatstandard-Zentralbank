package de.teutonstudio.zentralbank.ui.domain

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Spieler

data class SpielerAnzeige(
    val name: String,
    val geld: String,
    val anleihen: Int,
    val bauteile: Int,
)

fun GameState.zuSpielerAnzeigen(): List<SpielerAnzeige> {
    return spieler.map { it.zuSpielerAnzeige() }
}

private fun Spieler.zuSpielerAnzeige(): SpielerAnzeige {
    return SpielerAnzeige(
        name = name,
        geld = geldkonto.zuMarkString(),
        anleihen = anleihen.size,
        bauteile = bauteile.values.sum(),
    )
}
