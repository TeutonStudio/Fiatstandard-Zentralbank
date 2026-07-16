package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

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
}
