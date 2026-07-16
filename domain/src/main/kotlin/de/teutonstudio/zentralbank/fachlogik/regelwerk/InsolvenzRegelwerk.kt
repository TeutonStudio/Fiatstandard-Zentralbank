package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.MarktAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Schuldenstrich
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.UeberschuldungsStatus

internal object InsolvenzRegelwerk {
    private const val UEBERSCHULDUNG_WARNUNG_AB_ZUEGEN = 3

    fun schuldenstrichBuchen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.Schuldenstrich,
    ): SpielZustand {
        require(ereignis.entfernteBahnwege >= 0) {
            "Entfernte Bahnwege duerfen nicht negativ sein."
        }
        val schuldner = zustand.spieler.firstOrNull { spieler -> spieler.id == ereignis.spieler }
            ?: error("Unbekannter Spieler: ${ereignis.spieler.wert}")
        require(zustand.konflikte.none { konflikt ->
            konflikt.spielerA == ereignis.spieler || konflikt.spielerB == ereignis.spieler
        }) {
            "Schuldenstrich ist im Krieg nicht direkt verfuegbar."
        }
        val eigeneAnleihen = zustand.anleihen.values
            .filter { anleihe -> anleihe.emittent == ereignis.spieler }
        require(eigeneAnleihen.isNotEmpty()) {
            "Schuldenstrich benoetigt offene eigene Anleihen."
        }
        val vorhandeneBahnwege = schuldner.bauteile
            .getOrDefault(BauteilTyp.EISENBAHNLINIE, 0)
        require(ereignis.entfernteBahnwege <= vorhandeneBahnwege) {
            "Mehr Bahnwege entfernt als vorhanden."
        }

        var ausgezahlterBetrag = Geld.NULL
        var neuerZustand = zustand
        val geloeschteAnleihen = eigeneAnleihen.map { anleihe -> anleihe.id }
        eigeneAnleihen.forEach { anleihe ->
            val besitzer = AnleihenAuswertung.besitzer(neuerZustand, anleihe.id)
            if (besitzer is KontoId.Spieler && besitzer.id != ereignis.spieler) {
                ausgezahlterBetrag += anleihe.nennwert
                neuerZustand = FinanzRegelwerk.kontoAendern(
                    zustand = neuerZustand,
                    konto = besitzer,
                    aenderung = anleihe.nennwert,
                )
            }
        }

        neuerZustand = neuerZustand.copy(
            bankAnleihen = neuerZustand.bankAnleihen - geloeschteAnleihen.toSet(),
            anleihen = neuerZustand.anleihen - geloeschteAnleihen.toSet(),
            spieler = neuerZustand.spieler.map { spieler ->
                val ohneAnleihen = spieler.copy(
                    anleihen = spieler.anleihen - geloeschteAnleihen.toSet(),
                )
                if (spieler.id == ereignis.spieler) {
                    ohneAnleihen.copy(
                        bauteile = bauteileNachSchuldenstrich(
                            bauteile = spieler.bauteile,
                            entfernteBahnwege = ereignis.entfernteBahnwege,
                        ),
                    )
                } else {
                    ohneAnleihen
                }
            },
        )

        neuerZustand = neuerZustand.copy(
            schuldenstriche = neuerZustand.schuldenstriche + Schuldenstrich(
                spieler = ereignis.spieler,
                runde = zustand.rundenzähler,
                ausgezahlterBetrag = ausgezahlterBetrag,
                geloeschteAnleihen = geloeschteAnleihen,
                entfernteBahnwege = ereignis.entfernteBahnwege,
            ),
            ueberschuldungen = neuerZustand.ueberschuldungen
                .filterNot { status -> status.spieler == ereignis.spieler },
        )
        return if (zustand.zugStatus?.spieler == ereignis.spieler) {
            ZugRegelwerk.naechsterZug(neuerZustand, ereignis.spieler)
        } else {
            neuerZustand
        }
    }

    fun ueberschuldungAktualisieren(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): SpielZustand {
        val schuldensumme = AnleihenAuswertung.bankgehalteneSchuldensumme(zustand, spieler)
        val marktwert = MarktAuswertung.spielerMarktwert(zustand, spieler)
        val istImFrieden = zustand.konflikte.none { konflikt ->
            konflikt.spielerA == spieler || konflikt.spielerB == spieler
        }
        val istUeberschuldet = istImFrieden &&
            schuldensumme > marktwert &&
            schuldensumme > Geld.NULL
        val bestehend = zustand.ueberschuldungen
            .firstOrNull { status -> status.spieler == spieler }
        val neuerStatus = if (istUeberschuldet) {
            val neueSerie = (bestehend?.friedlicheUeberschuldeteZuege ?: 0) + 1
            UeberschuldungsStatus(
                spieler = spieler,
                friedlicheUeberschuldeteZuege = neueSerie,
                letztePruefungRunde = zustand.rundenzähler,
                schuldensumme = schuldensumme,
                marktwert = marktwert,
                warnungAktiv = neueSerie >= UEBERSCHULDUNG_WARNUNG_AB_ZUEGEN,
                schuldenstrichFaellig = neueSerie > UEBERSCHULDUNG_WARNUNG_AB_ZUEGEN,
            )
        } else {
            null
        }

        return zustand.copy(
            ueberschuldungen = zustand.ueberschuldungen
                .filterNot { status -> status.spieler == spieler } + listOfNotNull(neuerStatus),
        )
    }

    fun faelligerSchuldenstrichSpieler(zustand: SpielZustand): SpielerId? =
        zustand.ueberschuldungen
            .firstOrNull { status -> status.schuldenstrichFaellig }
            ?.spieler

    fun istSchuldenstrichFaellig(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): Boolean = zustand.ueberschuldungen.any { status ->
        status.spieler == spieler && status.schuldenstrichFaellig
    }

    private fun bauteileNachSchuldenstrich(
        bauteile: Map<BauteilTyp, Int>,
        entfernteBahnwege: Int,
    ): Map<BauteilTyp, Int> {
        val neueBauteile = bauteile.toMutableMap()
        neueBauteile.remove(BauteilTyp.BAHNHOF)
        neueBauteile.remove(BauteilTyp.HAFEN)

        val grossbahnhoefe = neueBauteile.remove(BauteilTyp.GROSSBAHNHOF) ?: 0
        if (grossbahnhoefe > 0) {
            neueBauteile[BauteilTyp.BAHNHOF] =
                neueBauteile.getOrDefault(BauteilTyp.BAHNHOF, 0) + grossbahnhoefe
        }

        val grosshaefen = neueBauteile.remove(BauteilTyp.GROSSHAFEN) ?: 0
        if (grosshaefen > 0) {
            neueBauteile[BauteilTyp.HAFEN] =
                neueBauteile.getOrDefault(BauteilTyp.HAFEN, 0) + grosshaefen
        }

        val bahnwege = neueBauteile.getOrDefault(BauteilTyp.EISENBAHNLINIE, 0) -
            entfernteBahnwege
        if (bahnwege > 0) {
            neueBauteile[BauteilTyp.EISENBAHNLINIE] = bahnwege
        } else {
            neueBauteile.remove(BauteilTyp.EISENBAHNLINIE)
        }

        return neueBauteile.filterValues { menge -> menge > 0 }
    }
}
