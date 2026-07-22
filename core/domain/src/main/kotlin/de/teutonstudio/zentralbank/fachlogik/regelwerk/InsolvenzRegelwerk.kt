package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Schuldenstrich
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZentralbankGeldschoepfung

internal object InsolvenzRegelwerk {
    fun zentralbankgeldProtokollieren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.ZentralbankgeldGeschoepft,
    ): SpielZustand {
        require(ereignis.betrag > Geld.NULL) { "Geldschöpfung muss positiv sein." }
        require(ereignis.spieler in zustand.spieler.map { it.id }) {
            "Die Geldschöpfung verweist auf einen unbekannten Spieler."
        }
        return zustand.copy(
            zentralbankGeldschoepfungen = zustand.zentralbankGeldschoepfungen +
                ZentralbankGeldschoepfung(
                    ereignis.spieler,
                    ereignis.betrag,
                    zustand.rundenzähler,
                    ereignis.grund,
                ),
        )
    }

    fun schuldenstrichBuchen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.Schuldenstrich,
    ): SpielZustand {
        require(ereignis.entfernteBahnwege == 0) {
            "Schuldenstrich entfernt keine Handelslinien; sie werden neutral."
        }
        val schuldner = zustand.spieler.firstOrNull { it.id == ereignis.spieler }
            ?: error("Unbekannter Spieler: ${ereignis.spieler.wert}")
        require(zustand.konflikte.none { ereignis.spieler in it.teilnehmer }) {
            "Schuldenstrich ist während eines Krieges nicht erlaubt."
        }
        val karte = requireNotNull(zustand.karte) {
            "Ein Schuldenstrich benötigt Verwaltungsstandorte auf einer Karte."
        }
        val eigeneStandorte = karte.belegung.ecken.filter { it.besitzer == ereignis.spieler }
        require(eigeneStandorte.any { it.typ != EckGebaeudeTyp.HAUPTBAHNHOF }) {
            "Es ist kein Verwaltungsstandort mehr herabstufbar."
        }

        val eigeneAnleihen = zustand.anleihen.values
            .filter { it.emittent == ereignis.spieler }
            .sortedBy { it.id.wert }
        var ausgezahlterBetrag = Geld.NULL
        var neuerZustand = zustand
        eigeneAnleihen.forEach { anleihe ->
            val besitzer = AnleihenAuswertung.besitzer(neuerZustand, anleihe.id)
                ?: error("Anleihe ${anleihe.id.wert} besitzt keinen Gläubiger.")
            if (besitzer != KontoId.Spieler(ereignis.spieler)) {
                ausgezahlterBetrag += anleihe.nennwert
                neuerZustand = FinanzRegelwerk.kontoAendern(
                    neuerZustand,
                    besitzer,
                    anleihe.nennwert,
                )
            }
            neuerZustand = AnleihenRegelwerk.anleiheAusloesen(neuerZustand, anleihe.id)
        }

        val entfernteEinheiten = karte.belegung.kriegseinheiten
            .filter { it.besitzer == ereignis.spieler }
            .map { it.id }
            .sorted()
        val neueEcken = karte.belegung.ecken.mapNotNull { standort ->
            if (standort.besitzer != ereignis.spieler) return@mapNotNull standort
            when (standort.typ) {
                EckGebaeudeTyp.HAUPTBAHNHOF -> standort
                EckGebaeudeTyp.GROSSBAHNHOF -> standort.copy(typ = EckGebaeudeTyp.BAHNHOF)
                EckGebaeudeTyp.GROSSHAFEN -> standort.copy(typ = EckGebaeudeTyp.HAFEN)
                EckGebaeudeTyp.BAHNHOF, EckGebaeudeTyp.HAFEN -> null
            }
        }
        val herabgestuft = eigeneStandorte.count { it.typ != EckGebaeudeTyp.HAUPTBAHNHOF }
        val neueKarte = karte.copy(
            belegung = karte.belegung.copy(
                ecken = neueEcken,
                kanten = karte.belegung.kanten.map { handelslinie ->
                    if (handelslinie.erbautVon == ereignis.spieler) {
                        handelslinie.copy(erbautVon = null)
                    } else {
                        handelslinie
                    }
                },
                kriegseinheiten = karte.belegung.kriegseinheiten.filterNot {
                    it.besitzer == ereignis.spieler &&
                        it.typ in setOf(KriegsEinheitTyp.PANZER, KriegsEinheitTyp.KRIEGSSCHIFF)
                },
            ),
        )
        neuerZustand = neuerZustand.copy(
            karte = neueKarte,
            spieler = neuerZustand.spieler.map { spieler ->
                if (spieler.id == ereignis.spieler) spieler.copy(geldkonto = Geld.NULL) else spieler
            },
            schuldenstriche = neuerZustand.schuldenstriche + Schuldenstrich(
                spieler = ereignis.spieler,
                runde = zustand.rundenzähler,
                ausgezahlterBetrag = ausgezahlterBetrag,
                geloeschteAnleihen = eigeneAnleihen.map { it.id },
                entfernteBahnwege = 0,
                entfernteEinheiten = entfernteEinheiten,
                herabgestufteStandorte = herabgestuft,
                geldschoepfung = ausgezahlterBetrag,
            ),
            belagerungen = neuerZustand.belagerungen.filterNot {
                it.verteidiger == ereignis.spieler || it.fuehrenderBelagerer == ereignis.spieler
            },
        )
        return if (zustand.zugStatus?.spieler == ereignis.spieler) {
            ZugRegelwerk.naechsterZug(neuerZustand, ereignis.spieler)
        } else {
            neuerZustand
        }
    }
}
