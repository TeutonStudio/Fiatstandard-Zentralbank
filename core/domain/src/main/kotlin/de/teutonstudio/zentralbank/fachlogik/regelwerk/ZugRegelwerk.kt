package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus

internal object ZugRegelwerk {
    fun pruefeFreigabe(
        zustand: SpielZustand,
        ereignis: SpielEreignis,
    ) {
        if (zustand.ergebnis != null) {
            error("Die Partie ist bereits beendet; weitere Aktionen sind nicht zulässig.")
        }
        if (ereignis is SpielEreignis.PartieBeendet) return
        if (ereignis is SpielEreignis.AngeboteAbgelaufen) return
        if (ereignis is SpielEreignis.SpielerAusgeschieden) {
            require(ereignis.spieler == zustand.aktiverSpieler) {
                "Nur der aktive Spieler kann ausscheiden."
            }
            return
        }
        if (ereignis is SpielEreignis.WarenkorbGeaendert) return
        if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
            require(ereignis.istRundeNullPlatzierung()) {
                "Prozug und Epizug beginnen erst nach Abschluss von Runde 0."
            }
            return
        }

        val zug = requireNotNull(zustand.zugStatus) { "Es ist kein Zug aktiv." }
        val faelligerSchuldenstrich = InsolvenzRegelwerk.faelligerSchuldenstrichSpieler(zustand)
        if (faelligerSchuldenstrich != null) {
            require(
                ereignis is SpielEreignis.Schuldenstrich &&
                    ereignis.spieler == faelligerSchuldenstrich,
            ) {
                "Zuerst muss der fällige Schuldenstrich für " +
                    "${faelligerSchuldenstrich.wert} gebucht werden."
            }
            return
        }

        when (ereignis) {
            is SpielEreignis.HandelsangebotErstellt -> {
                pruefeHandelsphase(zug)
                require(ereignis.angebot.anbieter == zug.spieler) {
                    "Nur der aktive Spieler darf ein Angebot erstellen."
                }
            }
            is SpielEreignis.HandelsangebotAngenommen -> {
                pruefeHandelsphase(zug)
                require(ereignis.angenommenVon == zug.spieler) {
                    "Nur der aktive Spieler darf ein Angebot annehmen."
                }
            }
            is SpielEreignis.HandelsangebotAbgelehnt -> {
                pruefeHandelsphase(zug)
                require(ereignis.abgelehntVon == zug.spieler) {
                    "Nur der aktive Spieler darf ein Angebot ablehnen."
                }
            }
            is SpielEreignis.HandelsangebotZurueckgezogen -> {
                pruefeHandelsphase(zug)
                require(ereignis.spieler == zug.spieler) {
                    "Nur der aktive Spieler darf sein Angebot zurückziehen."
                }
            }
            is SpielEreignis.AnleihenangebotErstellt -> {
                pruefeHandelsphase(zug)
                require(ereignis.angebot.anbieter == zug.spieler) {
                    "Nur der aktive Spieler darf ein Anleihenangebot erstellen."
                }
            }
            is SpielEreignis.AnleihenangebotAngenommen -> {
                pruefeHandelsphase(zug)
                require(ereignis.angenommenVon == zug.spieler) {
                    "Nur der aktive Spieler darf ein Anleihenangebot annehmen."
                }
            }
            is SpielEreignis.AnleihenangebotAbgelehnt -> {
                pruefeHandelsphase(zug)
                require(ereignis.abgelehntVon == zug.spieler) {
                    "Nur der aktive Spieler darf ein Anleihenangebot ablehnen."
                }
            }
            is SpielEreignis.AnleihenangebotZurueckgezogen -> {
                pruefeHandelsphase(zug)
                require(ereignis.spieler == zug.spieler) {
                    "Nur der aktive Spieler darf sein Anleihenangebot zurückziehen."
                }
            }
            is SpielEreignis.AngeboteAbgelaufen -> Unit
            is SpielEreignis.SpielerAusgeschieden,
            is SpielEreignis.PartieBeendet -> Unit // vor dem when abschließend geprüft
            is SpielEreignis.ProzugBegonnen -> {
                require(zug.phase == ZugPhase.Prozug && !zug.prozug.begonnen) {
                    "Der Prozug wurde bereits begonnen oder ist nicht aktiv."
                }
            }
            is SpielEreignis.VerarbeitungAusgefuehrt,
            is SpielEreignis.VerwaltungsstandortVersorgt,
            is SpielEreignis.VerbindlichkeitBeglichen,
            is SpielEreignis.ProzugErfolgreichAbgeschlossen -> pruefeBegonnenenProzug(zug)

            is SpielEreignis.RohstoffHandel -> {
                pruefeHandelsphase(zug)
                require(zug.spieler == ereignis.kaeufer || zug.spieler == ereignis.verkaeufer) {
                    "Der aktive Spieler muss am Handel beteiligt sein."
                }
            }
            is SpielEreignis.AuslandsHandel -> {
                pruefeHandelsphase(zug)
                require(ereignis.spieler == zug.spieler) { "Nur der aktive Spieler darf handeln." }
            }
            is SpielEreignis.AnleiheEmittiert -> {
                pruefeHandelsphase(zug)
                require(ereignis.anleihe.emittent == zug.spieler) {
                    "Nur der aktive Spieler darf eine Anleihe emittieren."
                }
            }
            is SpielEreignis.AnleiheFreiwilligZurueckgekauft -> {
                pruefeHandelsphase(zug)
                require(ereignis.emittent == zug.spieler) {
                    "Nur der aktive Spieler darf seine Anleihe zurückkaufen."
                }
                pruefeNichtFaellig(zug, ereignis.anleihe)
            }
            is SpielEreignis.AnleiheGekauft -> {
                pruefeHandelsphase(zug)
                require(
                    ereignis.kaeufer == zug.spieler ||
                        ereignis.verkaeufer == KontoId.Spieler(zug.spieler),
                ) { "Der aktive Spieler muss am Anleihenhandel beteiligt sein." }
                pruefeNichtFaellig(zug, ereignis.anleihe)
            }
            is SpielEreignis.AnleiheVerkauft -> {
                pruefeHandelsphase(zug)
                require(
                    ereignis.verkaeufer == zug.spieler ||
                        ereignis.kaeufer == KontoId.Spieler(zug.spieler),
                ) { "Der aktive Spieler muss am Anleihenhandel beteiligt sein." }
                pruefeNichtFaellig(zug, ereignis.anleihe)
            }

            is SpielEreignis.Expansion,
            is SpielEreignis.EckGebaeudeGebaut,
            is SpielEreignis.EckGebaeudeAufgewertet,
            is SpielEreignis.SchieneGebaut,
            is SpielEreignis.NeutraleAnlageErrichtet,
            is SpielEreignis.KartenBelegungEntfernt,
            is SpielEreignis.KartenBauwerkZustandGeaendert,
            is SpielEreignis.FeldAnlagenZustandGeaendert,
            is SpielEreignis.SeewegEingerichtet,
            is SpielEreignis.SeewegEntfernt,
            is SpielEreignis.SeewegRouteGeaendert -> {
                pruefeEpizug(zug)
                require(ereignis.primaererSpieler() == zug.spieler) {
                    "Nur der aktive Spieler darf bauen oder die Karte ändern."
                }
            }
            is SpielEreignis.KriegsEinheitEingesetzt,
            is SpielEreignis.KriegsEinheitGebaut,
            is SpielEreignis.KriegsEinheitBewegt,
            is SpielEreignis.KriegsEinheitenBewegt,
            is SpielEreignis.KriegsEinheitEntfernt,
            is SpielEreignis.KriegErklaert,
            is SpielEreignis.KriegBeendet -> {
                pruefeEpizug(zug)
                require(ereignis.primaererSpieler() == zug.spieler) {
                    "Nur der aktive Spieler darf eine Konfliktaktion ausführen."
                }
            }
            SpielEreignis.ZugBeendet -> pruefeEpizug(zug)
            is SpielEreignis.RundenwerteAktualisiert -> {
                require(
                    zug.phase == ZugPhase.Prozug && !zug.prozug.begonnen &&
                        ereignis.runde == zustand.rundenzähler,
                ) { "Rundenwerte werden genau vor Beginn des neuen Prozuges aktualisiert." }
            }
            is SpielEreignis.RundeBegonnen -> {
                require(
                    zug.phase == ZugPhase.Prozug && !zug.prozug.begonnen &&
                        ereignis.runde == zustand.rundenzähler,
                ) { "Eine neue Runde wird genau vor dem ersten Prozug begonnen." }
            }
            is SpielEreignis.Schuldenstrich -> Unit
            is SpielEreignis.HauptbahnhofPlatziert ->
                error("Ein Hauptbahnhof kann nur in Runde 0 platziert werden.")
            is SpielEreignis.RohstoffEinnahme,
            is SpielEreignis.RohstoffAusgabe,
            is SpielEreignis.Transaktion,
            is SpielEreignis.AnleiheFaellig -> error(
                "Diese allgemeine Buchung ist im regulären Zug nicht zulässig; " +
                    "verwende ein konkretes Prozug- oder Handelsereignis.",
            )
            is SpielEreignis.WarenkorbGeaendert -> Unit
        }
    }

    fun zugBeenden(zustand: SpielZustand): SpielZustand {
        val zug = requireNotNull(zustand.zugStatus) { "Es ist kein Zug aktiv." }
        pruefeEpizug(zug)
        val nachPruefung = InsolvenzRegelwerk.ueberschuldungAktualisieren(zustand, zug.spieler)
        return if (InsolvenzRegelwerk.istSchuldenstrichFaellig(nachPruefung, zug.spieler)) {
            nachPruefung
        } else {
            naechsterZug(nachPruefung, zug.spieler)
        }
    }

    fun naechsterZug(
        zustand: SpielZustand,
        aktuellerSpieler: SpielerId,
    ): SpielZustand {
        val aktuellerIndex = zustand.spieler.indexOfFirst { it.id == aktuellerSpieler }
        require(aktuellerIndex >= 0) { "Aktiver Spieler ist unbekannt." }
        val naechsterIndex = (1..zustand.spieler.size)
            .asSequence()
            .map { versatz -> (aktuellerIndex + versatz) % zustand.spieler.size }
            .firstOrNull { index ->
                zustand.spieler[index].id !in zustand.ausgeschiedeneSpieler
            } ?: error("Es ist kein spielfähiger Spieler mehr vorhanden.")
        val naechsterSpieler = zustand.spieler[naechsterIndex]
        val neueRunde = if (naechsterIndex <= aktuellerIndex) {
            zustand.rundenzähler + 1
        } else {
            zustand.rundenzähler
        }
        val naechsteZugId = (zustand.zugStatus?.zugId ?: 0L) + 1L
        return zustand.copy(
            aktiverSpieler = naechsterSpieler.id,
            rundenzähler = neueRunde,
            zugStatus = ZugStatus(
                zugId = naechsteZugId,
                spieler = naechsterSpieler.id,
                phase = ZugPhase.Prozug,
            ),
        )
    }

    private fun pruefeHandelsphase(zug: ZugStatus) {
        if (zug.phase == ZugPhase.Prozug) pruefeBegonnenenProzug(zug)
    }

    private fun pruefeBegonnenenProzug(zug: ZugStatus) {
        require(zug.phase == ZugPhase.Prozug && zug.prozug.begonnen) {
            "Diese Aktion ist nur im begonnenen Prozug erlaubt."
        }
    }

    private fun pruefeEpizug(zug: ZugStatus) {
        require(zug.phase == ZugPhase.Epizug && zug.prozug.erfolgreichAbgeschlossen) {
            "Diese Aktion ist erst im Epizug nach erfolgreichem Prozug erlaubt."
        }
    }

    private fun pruefeNichtFaellig(
        zug: ZugStatus,
        anleihe: de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId,
    ) {
        require(zug.prozug.verbindlichkeiten.none { verbindlichkeit ->
            verbindlichkeit.id.anleihe == anleihe &&
                verbindlichkeit.id !in zug.prozug.beglicheneVerbindlichkeiten
        }) { "Eine im Prozug fällige Anleihe darf nicht mehr gehandelt werden." }
    }

    private fun SpielEreignis.istRundeNullPlatzierung(): Boolean =
        this is SpielEreignis.HauptbahnhofPlatziert ||
            this is SpielEreignis.EckGebaeudeGebaut ||
            this is SpielEreignis.SchieneGebaut ||
            this is SpielEreignis.NeutraleAnlageErrichtet

    private fun SpielEreignis.primaererSpieler(): SpielerId? = when (this) {
        is SpielEreignis.Expansion -> spieler
        is SpielEreignis.HauptbahnhofPlatziert -> spieler
        is SpielEreignis.EckGebaeudeGebaut -> spieler
        is SpielEreignis.EckGebaeudeAufgewertet -> spieler
        is SpielEreignis.SchieneGebaut -> spieler
        is SpielEreignis.NeutraleAnlageErrichtet -> errichter
        is SpielEreignis.KartenBelegungEntfernt -> spieler
        is SpielEreignis.KartenBauwerkZustandGeaendert -> spieler
        is SpielEreignis.FeldAnlagenZustandGeaendert -> spieler
        is SpielEreignis.SeewegEingerichtet -> spieler
        is SpielEreignis.SeewegEntfernt -> spieler
        is SpielEreignis.SeewegRouteGeaendert -> spieler
        is SpielEreignis.KriegsEinheitEingesetzt -> spieler
        is SpielEreignis.KriegsEinheitGebaut -> spieler
        is SpielEreignis.KriegsEinheitBewegt -> spieler
        is SpielEreignis.KriegsEinheitenBewegt -> spieler
        is SpielEreignis.KriegsEinheitEntfernt -> spieler
        is SpielEreignis.KriegErklaert -> aggressor
        is SpielEreignis.KriegBeendet -> spielerA
        is SpielEreignis.SpielerAusgeschieden -> spieler
        is SpielEreignis.HandelsangebotErstellt -> angebot.anbieter
        is SpielEreignis.HandelsangebotAngenommen -> angenommenVon
        is SpielEreignis.HandelsangebotAbgelehnt -> abgelehntVon
        is SpielEreignis.HandelsangebotZurueckgezogen -> spieler
        is SpielEreignis.AnleihenangebotErstellt -> angebot.anbieter
        is SpielEreignis.AnleihenangebotAngenommen -> angenommenVon
        is SpielEreignis.AnleihenangebotAbgelehnt -> abgelehntVon
        is SpielEreignis.AnleihenangebotZurueckgezogen -> spieler
        else -> null
    }
}
