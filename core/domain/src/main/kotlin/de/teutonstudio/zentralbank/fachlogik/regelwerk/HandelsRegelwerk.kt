package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung

internal object HandelsRegelwerk {
    fun ressourcenUebertragen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.RessourcenUebertragen,
    ): SpielZustand {
        require(ereignis.von != ereignis.an) { "Absender und Empfänger müssen verschieden sein." }
        require(ereignis.rohstoffe.values.all { it >= 0 }) {
            "Übertragene Rohstoffmengen dürfen nicht negativ sein."
        }
        require(ereignis.geld >= Geld.NULL) { "Der übertragene Geldbetrag darf nicht negativ sein." }
        require(ereignis.rohstoffe.values.any { it > 0 } || ereignis.geld > Geld.NULL) {
            "Eine Ressourcenübertragung darf nicht leer sein."
        }
        require(zustand.konflikte.any { konflikt ->
            val seiteVon = konflikt.seiteVon(ereignis.von)
            seiteVon != null && seiteVon == konflikt.seiteVon(ereignis.an)
        }) {
            "Direkte Ressourcenübertragung ist nur innerhalb einer Kriegsallianz erlaubt."
        }
        var neu = zustand
        if (ereignis.rohstoffe.isNotEmpty()) {
            neu = RohstoffRegelwerk.rohstoffeBuchen(neu, ereignis.von, ereignis.rohstoffe, -1)
            neu = RohstoffRegelwerk.rohstoffeBuchen(neu, ereignis.an, ereignis.rohstoffe, 1)
        }
        if (ereignis.geld > Geld.NULL) {
            neu = FinanzRegelwerk.geldUebertragen(
                neu,
                KontoId.Spieler(ereignis.von),
                KontoId.Spieler(ereignis.an),
                ereignis.geld,
            )
        }
        return neu
    }

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
        ).marktpreisBeobachten(ereignis.rohstoff, ereignis.preis, ereignis.menge)
    }

    fun mitAuslandHandeln(
        zustand: SpielZustand,
        ereignis: SpielEreignis.AuslandsHandel,
    ): SpielZustand {
        require(ereignis.menge > 0) { "Außenhandelsmenge muss positiv sein." }
        require(ereignis.preis > Geld.NULL) { "Außenhandelspreis muss positiv sein." }
        val karte = requireNotNull(zustand.karte) { "Außenhandel benötigt eine Spielkarte." }
        require(
            KartenAuswertung.kannAussenhandelBetreiben(
                karte,
                ereignis.spieler,
                zustand.konflikte,
            ),
        ) {
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
        }.marktpreisBeobachten(ereignis.rohstoff, ereignis.preis, ereignis.menge)
    }

    private fun SpielZustand.marktpreisBeobachten(
        rohstoff: de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff,
        gesamtpreis: Geld,
        menge: Int,
    ): SpielZustand {
        val einzelpreis = Geld.cent(gesamtpreis.cent / menge)
        return copy(
            marktpreisBeobachtungen = marktpreisBeobachtungen +
                (rohstoff to (marktpreisBeobachtungen[rohstoff].orEmpty() + einzelpreis)),
        )
    }
}
