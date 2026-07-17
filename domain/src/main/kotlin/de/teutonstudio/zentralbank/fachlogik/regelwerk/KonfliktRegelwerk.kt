package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

internal object KonfliktRegelwerk {
    fun kriegErklaeren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegErklaert,
    ): SpielZustand {
        require(ereignis.aggressor != ereignis.verteidiger) {
            "Ein Spieler kann sich nicht selbst Krieg erklaeren."
        }
        require(zustand.spieler.any { spieler -> spieler.id == ereignis.aggressor }) {
            "Unbekannter Spieler: ${ereignis.aggressor.wert}"
        }
        require(zustand.spieler.any { spieler -> spieler.id == ereignis.verteidiger }) {
            "Unbekannter Spieler: ${ereignis.verteidiger.wert}"
        }
        require(zustand.konflikte.none { konflikt ->
            konflikt.betrifft(ereignis.aggressor, ereignis.verteidiger)
        }) {
            "Zwischen diesen Spielern besteht bereits Krieg."
        }
        return zustand.copy(
            konflikte = zustand.konflikte + Konflikt(ereignis.aggressor, ereignis.verteidiger),
        )
    }

    fun kriegBeenden(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegBeendet,
    ): SpielZustand {
        val konflikt = zustand.konflikte.firstOrNull { bestehend ->
            bestehend.betrifft(ereignis.spielerA, ereignis.spielerB)
        } ?: error("Zwischen diesen Spielern besteht kein Krieg.")
        return zustand.copy(
            konflikte = zustand.konflikte - konflikt,
            karte = zustand.karte?.let { karte ->
                karte.copy(
                    belegung = karte.belegung.copy(
                        kriegseinheiten = karte.belegung.kriegseinheiten.filterNot { einheit ->
                            konflikt.betrifft(einheit.besitzer, einheit.gegner)
                        },
                    ),
                )
            },
        )
    }
}
