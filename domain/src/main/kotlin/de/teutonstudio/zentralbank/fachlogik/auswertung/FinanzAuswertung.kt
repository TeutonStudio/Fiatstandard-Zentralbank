package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

object FinanzAuswertung {
    fun geldsumme(zustand: SpielZustand): Geld =
        zustand.spieler.fold(zustand.bankkonto) { summe, spieler ->
            summe + spieler.geldkonto
        }
}
