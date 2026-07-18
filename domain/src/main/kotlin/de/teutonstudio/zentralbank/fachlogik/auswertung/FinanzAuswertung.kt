package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

object FinanzAuswertung {
    fun geldsumme(zustand: SpielZustand): Geld =
        zustand.spieler.fold(zustand.bankkonto + zustand.auslandskonto) { summe, spieler ->
            summe + spieler.geldkonto
        }
}
