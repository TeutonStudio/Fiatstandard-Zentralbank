package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

object MarktAuswertung {
    fun aktuellerPreis(
        zustand: SpielZustand,
        rohstoff: Rohstoff,
    ): Geld = zustand.marktpreise[rohstoff] ?: Geld.NULL

    fun bauteilMarktwert(
        zustand: SpielZustand,
        bauteil: BauteilTyp,
    ): Geld = bauteil.kosten.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
        summe + aktuellerPreis(zustand, rohstoff) * menge
    }

    fun spielerMarktwert(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): Geld {
        val bestand = zustand.spieler.firstOrNull { kandidat -> kandidat.id == spieler }
            ?: error("Unbekannter Spieler: ${spieler.wert}")
        val rohstoffwert = bestand.rohstoffe.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
            summe + aktuellerPreis(zustand, rohstoff) * menge
        }
        val bauteilwert = bestand.bauteile.entries.fold(Geld.NULL) { summe, (bauteil, menge) ->
            summe + bauteilMarktwert(zustand, bauteil) * menge
        }
        val kartenwert = zustand.karte?.let { karte ->
            val verwaltung = karte.belegung.ecken.filter { it.besitzer == spieler }
                .fold(Geld.NULL) { summe, standort ->
                    val typ = when (standort.typ) {
                        de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.HAUPTBAHNHOF -> BauteilTyp.HAUPTBAHNHOF
                        de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.BAHNHOF -> BauteilTyp.BAHNHOF
                        de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
                        de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.HAFEN -> BauteilTyp.HAFEN
                        de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
                    }
                    summe + bauteilMarktwert(zustand, typ)
                }
            val fracht = karte.belegung.seewege.count { it.besitzer == spieler }
            val einheiten = karte.belegung.kriegseinheiten.filter { it.besitzer == spieler }
                .fold(Geld.NULL) { summe, einheit ->
                    val kosten = when (einheit.typ) {
                        de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp.PANZER ->
                            mapOf(Rohstoff.STAHL to 3, Rohstoff.DIESEL to 2)
                        de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp.KRIEGSSCHIFF ->
                            mapOf(Rohstoff.STAHL to 3, Rohstoff.SCHWEROEL to 2)
                    }
                    summe + kosten.entries.fold(Geld.NULL) { teil, (rohstoff, menge) ->
                        teil + aktuellerPreis(zustand, rohstoff) * menge
                    }
                }
            verwaltung + bauteilMarktwert(zustand, BauteilTyp.FRACHTSCHIFF) * fracht + einheiten
        } ?: Geld.NULL
        val anleiheVermoegen = bestand.anleihen.fold(Geld.NULL) { summe, id ->
            summe + (zustand.anleihen[id]?.nennwert ?: Geld.NULL)
        }
        val schulden = zustand.anleihen.values.filter { it.emittent == spieler }
            .fold(Geld.NULL) { summe, anleihe -> summe + anleihe.nennwert }
        val brutto = bestand.geldkonto + rohstoffwert + bauteilwert + kartenwert + anleiheVermoegen
        return if (brutto >= schulden) brutto - schulden else Geld.NULL
    }
}
