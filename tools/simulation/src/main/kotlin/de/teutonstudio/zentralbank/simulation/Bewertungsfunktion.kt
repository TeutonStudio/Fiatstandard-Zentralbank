package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

interface Bewertungsfunktion {
    fun bewerte(
        vorher: SpielZustand,
        aktion: SpielAktion,
        nachher: SpielZustand,
    ): Belohnung
}

data class Belohnung(
    val ueberleben: Double = 0.0,
    val liquiditaet: Double = 0.0,
    val produktion: Double = 0.0,
    val kontrolle: Double = 0.0,
    val schuldenrisiko: Double = 0.0,
    val endergebnis: Double = 0.0,
) {
    fun alsKomponenten(): Map<String, Double> = linkedMapOf(
        "ueberleben" to ueberleben,
        "liquiditaet" to liquiditaet,
        "produktion" to produktion,
        "kontrolle" to kontrolle,
        "schuldenrisiko" to schuldenrisiko,
        "endergebnis" to endergebnis,
    )
}

/** Neutrale technische Baseline; ausdrücklich kein endgültiges KI-Belohnungssystem. */
class NeutraleBaselineBewertung : Bewertungsfunktion {
    override fun bewerte(
        vorher: SpielZustand,
        aktion: SpielAktion,
        nachher: SpielZustand,
    ): Belohnung = Belohnung()
}
