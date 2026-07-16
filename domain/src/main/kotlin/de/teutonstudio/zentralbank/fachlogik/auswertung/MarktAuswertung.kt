package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

object MarktAuswertung {
    fun aktuellerPreis(
        zustand: SpielZustand,
        rohstoff: Rohstoff,
    ): Geld = zustand.marktpreise[rohstoff] ?: Geld.NULL

    fun bauteilMarktwert(
        zustand: SpielZustand,
        bauteil: BauteilTyp,
    ): Geld = bauteil.kosten.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
        summe + aktuellerPreis(zustand, rohstoff) * menge
    }

    fun spielerMarktwert(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): Geld {
        val bestand = zustand.spieler.firstOrNull { kandidat -> kandidat.id == spieler }
            ?: error("Unbekannter Spieler: ${spieler.wert}")
        return bestand.bauteile.entries.fold(Geld.NULL) { summe, (bauteil, menge) ->
            summe + bauteilMarktwert(zustand, bauteil) * menge
        }
    }
}
