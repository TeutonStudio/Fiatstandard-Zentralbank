package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler

data class SpielerAnzeige(
    val name: String,
    val geld: String,
    val anleihen: Int,
    val bauteile: Int,
)

fun SpielZustand.zuSpielerAnzeigen(): List<SpielerAnzeige> {
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
