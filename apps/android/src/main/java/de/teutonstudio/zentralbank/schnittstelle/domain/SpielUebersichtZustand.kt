package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

data class SpielUebersichtZustand(
    val zug: ZugAnzeige,
    val spieler: List<SpielerAnzeige>,
)

fun SpielZustand.zuSpielUebersichtZustand(): SpielUebersichtZustand {
    return SpielUebersichtZustand(
        zug = zuZugAnzeige(),
        spieler = zuSpielerAnzeigen(),
    )
}
