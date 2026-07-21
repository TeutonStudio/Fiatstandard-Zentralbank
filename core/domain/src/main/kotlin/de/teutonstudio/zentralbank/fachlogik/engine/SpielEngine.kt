package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

interface SpielEngine {
    fun pruefe(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): Result<Unit>

    fun anwenden(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): Result<SpielSchrittErgebnis>

    fun erlaubteAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion>
}

data class SpielSchrittErgebnis(
    val zustand: SpielZustand,
    val ereignisse: List<SpielEreignis>,
)
