package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

internal object SpielerRegelwerk {
    fun aendereSpieler(
        zustand: SpielZustand,
        spielerId: SpielerId,
        aenderung: (Spieler) -> Spieler,
    ): SpielZustand {
        var gefunden = false
        val neueSpieler = zustand.spieler.map { spieler ->
            if (spieler.id == spielerId) {
                gefunden = true
                aenderung(spieler)
            } else {
                spieler
            }
        }
        require(gefunden) { "Unbekannter Spieler: ${spielerId.wert}" }
        return zustand.copy(spieler = neueSpieler)
    }
}
