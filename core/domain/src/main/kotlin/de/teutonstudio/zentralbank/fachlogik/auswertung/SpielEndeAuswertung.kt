package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.SpielEndeGrund
import de.teutonstudio.zentralbank.fachlogik.modell.SpielErgebnis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

object SpielEndeAuswertung {
    fun ergebnisFallsBeendet(zustand: SpielZustand): SpielErgebnis? {
        if (zustand.ergebnis != null) return zustand.ergebnis
        val verbleibend = zustand.spieler
            .map { it.id }
            .filterNot(zustand.ausgeschiedeneSpieler::contains)
        if (verbleibend.size > 1) return null
        val gewinner = verbleibend.singleOrNull()
        val platzierungen = listOfNotNull(gewinner) +
            zustand.ausscheidensReihenfolge.asReversed().filterNot { it == gewinner }
        return SpielErgebnis(
            gewinner = gewinner,
            platzierungen = platzierungen,
            ausgeschiedeneSpieler = zustand.ausgeschiedeneSpieler,
            grund = if (gewinner == null) {
                SpielEndeGrund.ALLE_AUSGESCHIEDEN
            } else {
                SpielEndeGrund.LETZTER_SPIELFAEHIGER_SPIELER
            },
            endRunde = zustand.rundenzähler,
        )
    }
}
