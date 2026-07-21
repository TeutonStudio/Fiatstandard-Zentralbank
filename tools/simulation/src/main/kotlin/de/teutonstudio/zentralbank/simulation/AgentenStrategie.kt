package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.Zufallsquelle
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

interface AgentenStrategie {
    fun waehleAktion(
        zustand: SpielZustand,
        erlaubteAktionen: List<SpielAktion>,
        zufallsquelle: Zufallsquelle,
    ): SpielAktion
}

class DeterministischeZufallsStrategie : AgentenStrategie {
    override fun waehleAktion(
        zustand: SpielZustand,
        erlaubteAktionen: List<SpielAktion>,
        zufallsquelle: Zufallsquelle,
    ): SpielAktion {
        require(erlaubteAktionen.isNotEmpty()) { "Es gibt keine auswählbare Aktion." }
        return erlaubteAktionen[zufallsquelle.naechsteGanzzahl(erlaubteAktionen.size)]
    }
}
