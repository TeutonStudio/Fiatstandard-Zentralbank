package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

internal object RohstoffRegelwerk {
    fun warenkorbAendern(
        zustand: SpielZustand,
        ereignis: SpielEreignis.WarenkorbGeaendert,
    ): SpielZustand {
        require(ereignis.warenkorb.values.all { menge -> menge >= 0 }) {
            "Warenkorbmengen duerfen nicht negativ sein."
        }
        return zustand.copy(
            warenkorb = ereignis.warenkorb.filterValues { menge -> menge > 0 },
        )
    }

    fun rohstoffeEinnehmen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.RohstoffEinnahme,
    ): SpielZustand = rohstoffeBuchen(
        zustand = zustand,
        spieler = ereignis.spieler,
        mengen = ereignis.mengen,
        faktor = 1,
    )

    fun rohstoffeAusgeben(
        zustand: SpielZustand,
        ereignis: SpielEreignis.RohstoffAusgabe,
    ): SpielZustand = rohstoffeBuchen(
        zustand = zustand,
        spieler = ereignis.spieler,
        mengen = ereignis.mengen,
        faktor = -1,
    )

    fun rohstoffeBuchen(
        zustand: SpielZustand,
        spieler: SpielerId,
        mengen: Map<Rohstoff, Int>,
        faktor: Int,
    ): SpielZustand {
        require(faktor == 1 || faktor == -1) { "Buchungsfaktor muss 1 oder -1 sein." }
        require(mengen.isNotEmpty()) { "Rohstoffbuchung darf nicht leer sein." }
        require(mengen.values.all { menge -> menge > 0 }) {
            "Rohstoffmengen muessen positiv sein."
        }

        return SpielerRegelwerk.aendereSpieler(zustand, spieler) { bestand ->
            val neueRohstoffe = bestand.rohstoffe.toMutableMap()
            mengen.forEach { (rohstoff, menge) ->
                val neuerWert = neueRohstoffe.getOrDefault(rohstoff, 0) + menge * faktor
                require(neuerWert >= 0) {
                    "${bestand.name} hat nicht genug ${rohstoff.name}."
                }
                if (neuerWert == 0) {
                    neueRohstoffe.remove(rohstoff)
                } else {
                    neueRohstoffe[rohstoff] = neuerWert
                }
            }
            bestand.copy(rohstoffe = neueRohstoffe.toMap())
        }
    }
}
