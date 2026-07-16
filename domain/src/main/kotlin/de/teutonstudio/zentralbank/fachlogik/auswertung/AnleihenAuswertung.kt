package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

object AnleihenAuswertung {
    fun besitzer(
        zustand: SpielZustand,
        anleihe: AnleiheId,
    ): KontoId? {
        if (anleihe in zustand.bankAnleihen) return KontoId.Bank
        return zustand.spieler
            .firstOrNull { spieler -> anleihe in spieler.anleihen }
            ?.let { spieler -> KontoId.Spieler(spieler.id) }
    }

    fun zinszahlung(anleihe: Anleihe): Geld =
        Geld.cent(anleihe.nennwert.cent * anleihe.zinsBasispunkte / 10_000L)

    fun gesamtschuld(anleihe: Anleihe): Geld =
        anleihe.nennwert + zinszahlung(anleihe) * anleihe.laufzeitRunden

    fun bankgehalteneSchuldensumme(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): Geld = zustand.anleihen.values
        .filter { anleihe ->
            anleihe.emittent == spieler && anleihe.id in zustand.bankAnleihen
        }
        .fold(Geld.NULL) { summe, anleihe -> summe + gesamtschuld(anleihe) }
}
