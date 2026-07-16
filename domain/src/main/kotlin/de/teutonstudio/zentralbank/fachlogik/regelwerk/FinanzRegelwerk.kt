package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

internal object FinanzRegelwerk {
    fun geldUebertragen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.Transaktion,
    ): SpielZustand = geldUebertragen(
        zustand = zustand,
        von = ereignis.von,
        an = ereignis.an,
        betrag = ereignis.betrag,
    )

    fun geldUebertragen(
        zustand: SpielZustand,
        von: KontoId,
        an: KontoId,
        betrag: Geld,
    ): SpielZustand {
        require(betrag > Geld.NULL) { "Transaktionsbetrag muss positiv sein." }
        require(von != an) { "Sender und Empfaenger muessen verschieden sein." }

        val nachAbzug = kontoAendern(zustand, von, -betrag)
        return kontoAendern(nachAbzug, an, betrag)
    }

    fun kontoAendern(
        zustand: SpielZustand,
        konto: KontoId,
        aenderung: Geld,
    ): SpielZustand = when (konto) {
        KontoId.Bank -> {
            val neuerStand = zustand.bankkonto + aenderung
            require(neuerStand >= Geld.NULL) { "Bankkonto darf nicht negativ werden." }
            zustand.copy(bankkonto = neuerStand)
        }
        is KontoId.Spieler -> SpielerRegelwerk.aendereSpieler(zustand, konto.id) { spieler ->
            val neuerStand = spieler.geldkonto + aenderung
            require(neuerStand >= Geld.NULL) { "${spieler.name} hat nicht genug Geld." }
            spieler.copy(geldkonto = neuerStand)
        }
    }
}
