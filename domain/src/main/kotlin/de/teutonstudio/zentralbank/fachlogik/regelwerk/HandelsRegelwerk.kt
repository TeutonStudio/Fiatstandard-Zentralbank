package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung

internal object HandelsRegelwerk {
    fun rohstoffHandeln(
        zustand: SpielZustand,
        ereignis: SpielEreignis.RohstoffHandel,
    ): SpielZustand {
        require(ereignis.menge > 0) { "Rohstoffhandelsmenge muss positiv sein." }
        require(ereignis.preis > Geld.NULL) { "Rohstoffhandelspreis muss positiv sein." }

        val nachAbgabe = RohstoffRegelwerk.rohstoffeBuchen(
            zustand = zustand,
            spieler = ereignis.verkaeufer,
            mengen = mapOf(ereignis.rohstoff to ereignis.menge),
            faktor = -1,
        )
        val nachUebergabe = RohstoffRegelwerk.rohstoffeBuchen(
            zustand = nachAbgabe,
            spieler = ereignis.kaeufer,
            mengen = mapOf(ereignis.rohstoff to ereignis.menge),
            faktor = 1,
        )
        return FinanzRegelwerk.geldUebertragen(
            zustand = nachUebergabe,
            von = KontoId.Spieler(ereignis.kaeufer),
            an = KontoId.Spieler(ereignis.verkaeufer),
            betrag = ereignis.preis,
        )
    }

    fun mitAuslandHandeln(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AuslandsHandel,
    ): SpielZustand {
        require(ereignis.menge > 0) { "Außenhandelsmenge muss positiv sein." }
        require(ereignis.preis > Geld.NULL) { "Außenhandelspreis muss positiv sein." }
        val karte = requireNotNull(zustand.karte) { "Außenhandel benötigt eine Spielkarte." }
        require(KartenAuswertung.kannAussenhandelBetreiben(karte, ereignis.spieler)) {
            "Außenhandel benötigt einen aktiven Hafen mit Frachtschiffverbindung."
        }
        val mengen = mapOf(ereignis.rohstoff to ereignis.menge)
        return when (ereignis.art) {
            AussenhandelsArt.IMPORT -> {
                val nachZahlung = FinanzRegelwerk.geldUebertragen(
                    zustand,
                    KontoId.Spieler(ereignis.spieler),
                    KontoId.Ausland,
                    ereignis.preis,
                )
                RohstoffRegelwerk.rohstoffeBuchen(
                    nachZahlung,
                    ereignis.spieler,
                    mengen,
                    faktor = 1,
                )
            }
            AussenhandelsArt.EXPORT -> {
                val nachAbgabe = RohstoffRegelwerk.rohstoffeBuchen(
                    zustand,
                    ereignis.spieler,
                    mengen,
                    faktor = -1,
                )
                FinanzRegelwerk.geldUebertragen(
                    nachAbgabe,
                    KontoId.Ausland,
                    KontoId.Spieler(ereignis.spieler),
                    ereignis.preis,
                )
            }
        }
    }
}
