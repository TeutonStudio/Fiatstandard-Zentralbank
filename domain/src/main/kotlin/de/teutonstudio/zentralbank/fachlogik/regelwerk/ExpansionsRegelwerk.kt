package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

internal object ExpansionsRegelwerk {
    fun expandieren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.Expansion,
    ): SpielZustand {
        val nachKosten = RohstoffRegelwerk.rohstoffeBuchen(
            zustand = zustand,
            spieler = ereignis.spieler,
            mengen = ereignis.bauteil.kosten,
            faktor = -1,
        )
        return SpielerRegelwerk.aendereSpieler(nachKosten, ereignis.spieler) { spieler ->
            spieler.copy(
                bauteile = spieler.bauteile + (
                    ereignis.bauteil to (spieler.bauteile.getOrDefault(ereignis.bauteil, 0) + 1)
                ),
            )
        }
    }
}
