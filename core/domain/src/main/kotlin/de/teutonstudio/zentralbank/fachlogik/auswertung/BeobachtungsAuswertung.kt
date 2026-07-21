package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.beobachtung.AngebotBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.BauteilBestandBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.EckBauwerkBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.FeldAnlageBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.GegnerBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.HandelslinieBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.KartenBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.KriegsEinheitBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.MarktBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.RohstoffBestandBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.RohstoffPreisBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SeewegBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielerBeobachtung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.ZugBeobachtung
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

object BeobachtungsAuswertung {
    fun fuerSpieler(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): SpielBeobachtung {
        val eigen = zustand.spieler.firstOrNull { it.id == spieler }
            ?: error("Unbekannter Spieler: ${spieler.wert}.")
        val karte = zustand.karte
        return SpielBeobachtung(
            betrachtenderSpieler = spieler,
            runde = zustand.rundenzähler,
            zug = zustand.zugStatus?.let { zug ->
                ZugBeobachtung(
                    zugId = zug.zugId,
                    aktiverSpieler = zug.spieler,
                    phase = zug.phase,
                    prozugBegonnen = zug.prozug.begonnen,
                    prozugAbgeschlossen = zug.prozug.erfolgreichAbgeschlossen,
                )
            },
            eigeneWirtschaft = SpielerBeobachtung(
                id = eigen.id,
                name = eigen.name,
                rohstoffe = eigen.rohstoffe.entries
                    .sortedBy { it.key.name }
                    .map { RohstoffBestandBeobachtung(it.key, it.value) },
                geld = eigen.geldkonto,
                anleihen = eigen.anleihen.sortedBy { it.wert },
                bauteile = eigen.bauteile.entries
                    .sortedBy { it.key.name }
                    .map { BauteilBestandBeobachtung(it.key, it.value) },
                ausgeschieden = eigen.id in zustand.ausgeschiedeneSpieler,
            ),
            gegner = zustand.spieler.filter { it.id != spieler }
                .sortedBy { it.id.wert }
                .map { gegner ->
                    GegnerBeobachtung(
                        id = gegner.id,
                        name = gegner.name,
                        ausgeschieden = gegner.id in zustand.ausgeschiedeneSpieler,
                        oeffentlicheBauwerke = karte?.let { spielkarte ->
                            spielkarte.belegung.ecken.count { it.besitzer == gegner.id } +
                                spielkarte.belegung.seewege.count { it.besitzer == gegner.id } +
                                spielkarte.belegung.kriegseinheiten.count { it.besitzer == gegner.id }
                        } ?: 0,
                        emittierteAnleihen = zustand.anleihen.values.count {
                            it.emittent == gegner.id
                        },
                    )
                },
            markt = MarktBeobachtung(
                preise = Rohstoff.entries.map { rohstoff ->
                    RohstoffPreisBeobachtung(rohstoff, zustand.marktpreise[rohstoff]
                        ?: de.teutonstudio.zentralbank.fachlogik.modell.Geld.NULL)
                },
                leitzinsBasispunkte = zustand.leitzins.wert,
            ),
            karte = karte?.let { spielkarte ->
                KartenBeobachtung(
                    id = spielkarte.id,
                    name = spielkarte.name,
                    gelaendefelder = spielkarte.gelaendefelder.sortedWith(
                        compareBy({ it.position.zeile }, { it.position.spalte }, { it.position.haelfte.name }),
                    ),
                    eckBauwerke = spielkarte.belegung.ecken.sortedBy { it.position }.map {
                        EckBauwerkBeobachtung(it.position, it.typ, it.besitzer, it.zustand)
                    },
                    handelslinien = spielkarte.belegung.kanten.sortedWith(
                        compareBy({ it.position.anfang.y }, { it.position.anfang.x },
                            { it.position.ende.y }, { it.position.ende.x }),
                    ).map { HandelslinieBeobachtung(it.position, it.erbautVon, it.zustand) },
                    feldAnlagen = spielkarte.belegung.felder.sortedWith(
                        compareBy({ it.position.zeile }, { it.position.spalte }, { it.position.haelfte.name }),
                    ).map { FeldAnlageBeobachtung(it.position, it.anlage, it.zustand) },
                    seewege = spielkarte.belegung.seewege.sortedBy { it.id }.map {
                        SeewegBeobachtung(it.id, it.hafenA, it.hafenB, it.besitzer, it.richtung)
                    },
                    kriegseinheiten = spielkarte.belegung.kriegseinheiten.sortedBy { it.id }.map {
                        KriegsEinheitBeobachtung(it.id, it.typ, it.besitzer, it.ort)
                    },
                )
            },
            angebote = sichtbareAngebote(zustand, spieler),
            ergebnis = zustand.ergebnis,
        )
    }

    private fun sichtbareAngebote(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<AngebotBeobachtung> = buildList {
        zustand.handelsAngebote.filter { angebot ->
            angebot.empfaenger == null || angebot.empfaenger == spieler || angebot.anbieter == spieler
        }.forEach { angebot ->
            add(
                AngebotBeobachtung.RohstoffHandel(
                    id = angebot.id,
                    anbieter = angebot.anbieter,
                    empfaenger = angebot.empfaenger,
                    angeboteneRohstoffe = angebot.angeboteneRohstoffe.entries.sortedBy { it.key.name }
                        .map { RohstoffBestandBeobachtung(it.key, it.value) },
                    geforderteRohstoffe = angebot.geforderteRohstoffe.entries.sortedBy { it.key.name }
                        .map { RohstoffBestandBeobachtung(it.key, it.value) },
                    angebotenerGeldbetrag = angebot.angebotenerGeldbetrag,
                    geforderterGeldbetrag = angebot.geforderterGeldbetrag,
                    status = angebot.status.name,
                ),
            )
        }
        zustand.anleihenAngebote.filter { angebot ->
            angebot.empfaenger == null || angebot.empfaenger == spieler || angebot.anbieter == spieler
        }.forEach { angebot ->
            add(
                AngebotBeobachtung.AnleihenHandel(
                    id = angebot.id,
                    anbieter = angebot.anbieter,
                    empfaenger = angebot.empfaenger,
                    anleihe = angebot.anleihe,
                    preis = angebot.preis,
                    status = angebot.status.name,
                ),
            )
        }
    }.sortedBy { angebot ->
        when (angebot) {
            is AngebotBeobachtung.RohstoffHandel -> "H-${angebot.id.wert}"
            is AngebotBeobachtung.AnleihenHandel -> "A-${angebot.id.wert}"
        }
    }
}
