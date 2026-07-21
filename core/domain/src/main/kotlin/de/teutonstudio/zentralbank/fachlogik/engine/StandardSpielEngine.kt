package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.ProzugAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.RundenAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.SpielEndeAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZustandsInvarianten
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.ereignis.KartenAenderungsGrund
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebot
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebot
import de.teutonstudio.zentralbank.fachlogik.modell.AnleihenAngebotId
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotStatus
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk

class StandardSpielEngine : SpielEngine {
    override fun pruefe(zustand: SpielZustand, aktion: SpielAktion): Result<Unit> =
        anwenden(zustand, aktion).map { }

    override fun anwenden(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): Result<SpielSchrittErgebnis> = runCatching {
        ZustandsInvarianten.pruefe(zustand).getOrThrow()
        val ereignisse = ereignisseFuer(zustand, aktion)
        val folgezustand = ereignisse.fold(zustand) { zwischenzustand, ereignis ->
            SpielRegelwerk.wendeAn(zwischenzustand, ereignis).getOrThrow()
        }
        ZustandsInvarianten.pruefe(folgezustand).getOrThrow()
        SpielSchrittErgebnis(folgezustand, ereignisse)
    }

    override fun erlaubteAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        return AktionsAuswertung.erlaubteAktionen(zustand, spieler).aktionen
    }

    private fun ereignisseFuer(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): List<SpielEreignis> {
        if (aktion == SpielAktion.ZugBeenden) return zugBeendenEreignisse(zustand)
        if (aktion is SpielAktion.Aufgeben) return aufgebenEreignisse(zustand, aktion)
        return listOf(when (aktion) {
            is SpielAktion.Aufgeben -> error("Wird als mehrstufige Regelfolge aufgelöst.")
            is SpielAktion.HauptbahnhofPlatzieren -> SpielEreignis.HauptbahnhofPlatziert(
                aktion.spieler,
                aktion.ecke,
            )
            is SpielAktion.EckGebaeudeBauen -> SpielEreignis.EckGebaeudeGebaut(
                aktion.spieler,
                aktion.ecke,
                aktion.typ,
            )
            is SpielAktion.EckGebaeudeAufwerten -> SpielEreignis.EckGebaeudeAufgewertet(
                aktion.spieler,
                aktion.ecke,
                aktion.zu,
            )
            is SpielAktion.SchieneBauen -> SpielEreignis.SchieneGebaut(
                aktion.spieler,
                aktion.kante,
            )
            is SpielAktion.AnlageErrichten -> SpielEreignis.NeutraleAnlageErrichtet(
                aktion.spieler,
                aktion.feld,
                aktion.anlage,
            )
            is SpielAktion.BelegungAbreissen -> SpielEreignis.KartenBelegungEntfernt(
                aktion.spieler,
                aktion.ort,
                KartenAenderungsGrund.SPIELERAKTION,
            )
            is SpielAktion.SeewegEinrichten -> SpielEreignis.SeewegEingerichtet(
                id = "seeweg-${zustand.naechsteSeewegNummer}",
                spieler = aktion.spieler,
                hafenA = aktion.hafenA,
                hafenB = aktion.hafenB,
                richtung = aktion.richtung,
            )
            is SpielAktion.SeewegEntfernen -> SpielEreignis.SeewegEntfernt(
                aktion.spieler,
                aktion.id,
            )
            is SpielAktion.KriegsEinheitBauen -> SpielEreignis.KriegsEinheitGebaut(
                id = "einheit-${zustand.naechsteEinheitenNummer}",
                spieler = aktion.spieler,
                typ = aktion.typ,
                kante = aktion.kante,
            )
            is SpielAktion.KriegsEinheitEinsetzen -> SpielEreignis.KriegsEinheitEingesetzt(
                id = "einheit-${zustand.naechsteEinheitenNummer}",
                spieler = aktion.spieler,
                gegner = aktion.gegner,
                typ = aktion.typ,
                ort = aktion.ort,
            )
            is SpielAktion.KriegsEinheitBewegen -> SpielEreignis.KriegsEinheitBewegt(
                spieler = aktion.spieler,
                id = aktion.id,
                weg = listOf(aktion.naechsteKante),
            )
            is SpielAktion.AnleiheEmittieren -> SpielEreignis.AnleiheEmittiert(
                anleihe = Anleihe(
                    id = AnleiheId("anleihe-${zustand.naechsteAnleiheNummer}"),
                    emittent = aktion.spieler,
                    nennwert = aktion.nennwert,
                    zinsBasispunkte = aktion.zinsBasispunkte,
                    laufzeitRunden = aktion.laufzeitRunden,
                    zinsbetrag = aktion.zinsbetrag,
                    emissionsRunde = zustand.rundenzähler,
                ),
                erwerber = aktion.erwerber,
                erloes = aktion.erloes,
            )
            is SpielAktion.AnleiheFreiwilligZurueckkaufen ->
                SpielEreignis.AnleiheFreiwilligZurueckgekauft(
                    aktion.anleihe,
                    aktion.spieler,
                    aktion.preis,
                )
            is SpielAktion.SchuldenstrichDurchfuehren -> SpielEreignis.Schuldenstrich(
                aktion.spieler,
                aktion.entfernteBahnwege,
            )
            is SpielAktion.HandelsangebotErstellen -> SpielEreignis.HandelsangebotErstellt(
                HandelsAngebot(
                    id = HandelsAngebotId(zustand.naechsteAngebotsNummer),
                    anbieter = aktion.spieler,
                    empfaenger = aktion.empfaenger,
                    angeboteneRohstoffe = aktion.angeboteneRohstoffe,
                    geforderteRohstoffe = aktion.geforderteRohstoffe,
                    angebotenerGeldbetrag = aktion.angebotenerGeldbetrag,
                    geforderterGeldbetrag = aktion.geforderterGeldbetrag,
                    erstelltInZug = requireNotNull(zustand.zugStatus).zugId,
                    erstelltInRunde = zustand.rundenzähler,
                ),
            )
            is SpielAktion.HandelsangebotAnnehmen -> SpielEreignis.HandelsangebotAngenommen(
                aktion.angebot,
                aktion.spieler,
            )
            is SpielAktion.HandelsangebotAblehnen -> SpielEreignis.HandelsangebotAbgelehnt(
                aktion.angebot,
                aktion.spieler,
            )
            is SpielAktion.HandelsangebotZurueckziehen ->
                SpielEreignis.HandelsangebotZurueckgezogen(aktion.angebot, aktion.spieler)
            is SpielAktion.AnleihenangebotErstellen -> SpielEreignis.AnleihenangebotErstellt(
                AnleihenAngebot(
                    id = AnleihenAngebotId(zustand.naechsteAngebotsNummer),
                    anbieter = aktion.spieler,
                    empfaenger = aktion.empfaenger,
                    anleihe = aktion.anleihe,
                    preis = aktion.preis,
                    erstelltInZug = requireNotNull(zustand.zugStatus).zugId,
                    erstelltInRunde = zustand.rundenzähler,
                ),
            )
            is SpielAktion.AnleihenangebotAnnehmen -> SpielEreignis.AnleihenangebotAngenommen(
                aktion.angebot,
                aktion.spieler,
            )
            is SpielAktion.AnleihenangebotAblehnen -> SpielEreignis.AnleihenangebotAbgelehnt(
                aktion.angebot,
                aktion.spieler,
            )
            is SpielAktion.AnleihenangebotZurueckziehen ->
                SpielEreignis.AnleihenangebotZurueckgezogen(aktion.angebot, aktion.spieler)
            is SpielAktion.ProzugBeginnen -> SpielEreignis.ProzugBegonnen(aktion.zugId)
            is SpielAktion.VerarbeitungAusfuehren -> SpielEreignis.VerarbeitungAusgefuehrt(
                zugId = aktion.zugId,
                feld = aktion.feld,
                laeufe = aktion.laeufe,
            )
            is SpielAktion.VerwaltungsstandortVersorgen ->
                SpielEreignis.VerwaltungsstandortVersorgt(aktion.zugId, aktion.ecke)
            is SpielAktion.VerbindlichkeitBegleichen ->
                SpielEreignis.VerbindlichkeitBeglichen(aktion.zugId, aktion.verbindlichkeit)
            is SpielAktion.ProzugAbschliessen ->
                SpielEreignis.ProzugErfolgreichAbgeschlossen(aktion.zugId)
            SpielAktion.ZugBeenden -> error("Wird als mehrstufige Regelfolge aufgelöst.")
            is SpielAktion.WarenkorbAendern -> SpielEreignis.WarenkorbGeaendert(aktion.warenkorb)
            is SpielAktion.RohstoffHandeln -> SpielEreignis.RohstoffHandel(
                kaeufer = aktion.kaeufer,
                verkaeufer = aktion.verkaeufer,
                rohstoff = aktion.rohstoff,
                menge = aktion.menge,
                preis = aktion.preis,
            )
            is SpielAktion.MitAuslandHandeln -> SpielEreignis.AuslandsHandel(
                spieler = aktion.spieler,
                rohstoff = aktion.rohstoff,
                menge = aktion.menge,
                preis = aktion.preis,
                art = aktion.art,
            )
            is SpielAktion.KriegErklaeren -> SpielEreignis.KriegErklaert(
                aggressor = aktion.aggressor,
                verteidiger = aktion.verteidiger,
            )
            is SpielAktion.FriedenSchliessen -> SpielEreignis.KriegBeendet(
                spielerA = aktion.spielerA,
                spielerB = aktion.spielerB,
            )
        })
    }

    private fun zugBeendenEreignisse(zustand: SpielZustand): List<SpielEreignis> {
        val vorherigeRunde = zustand.rundenzähler
        val vorherigeZugId = zustand.zugStatus?.zugId
        val ereignisse = mutableListOf<SpielEreignis>(SpielEreignis.ZugBeendet)
        var zwischenzustand = SpielRegelwerk.wendeAn(zustand, ereignisse.last()).getOrThrow()
        val zugHatGewechselt = zwischenzustand.zugStatus?.zugId != vorherigeZugId
        if (!zugHatGewechselt) return ereignisse

        if (zwischenzustand.rundenzähler > vorherigeRunde) {
            val ablauf = ablaufereignis(zwischenzustand)
            if (ablauf != null) {
                ereignisse += ablauf
                zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ablauf).getOrThrow()
            }
            val werte = RundenAuswertung.naechsteRundenwerte(zwischenzustand)
            val rundenereignis = SpielEreignis.RundeBegonnen(
                runde = werte.runde,
                marktpreise = werte.marktpreise,
                leitzins = werte.leitzins,
                preisinflation = werte.preisinflation,
            )
            ereignisse += rundenereignis
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, rundenereignis).getOrThrow()
        }
        val naechsterZug = requireNotNull(zwischenzustand.zugStatus)
        ereignisse += SpielEreignis.ProzugBegonnen(naechsterZug.zugId)
        return ereignisse
    }

    private fun aufgebenEreignisse(
        zustand: SpielZustand,
        aktion: SpielAktion.Aufgeben,
    ): List<SpielEreignis> {
        val vorherigeRunde = zustand.rundenzähler
        val ereignisse = mutableListOf<SpielEreignis>(
            SpielEreignis.SpielerAusgeschieden(
                spieler = aktion.spieler,
                grund = de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund.AUFGABE,
            ),
        )
        var zwischenzustand = SpielRegelwerk.wendeAn(zustand, ereignisse.last()).getOrThrow()
        val ergebnis = SpielEndeAuswertung.ergebnisFallsBeendet(zwischenzustand)
        if (ergebnis != null) {
            ereignisse += SpielEreignis.PartieBeendet(ergebnis)
            return ereignisse
        }
        if (zwischenzustand.rundenzähler > vorherigeRunde) {
            val ablauf = ablaufereignis(zwischenzustand)
            if (ablauf != null) {
                ereignisse += ablauf
                zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ablauf).getOrThrow()
            }
            val werte = RundenAuswertung.naechsteRundenwerte(zwischenzustand)
            val rundenereignis = SpielEreignis.RundeBegonnen(
                werte.runde,
                werte.marktpreise,
                werte.leitzins,
                werte.preisinflation,
            )
            ereignisse += rundenereignis
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, rundenereignis).getOrThrow()
        }
        ereignisse += SpielEreignis.ProzugBegonnen(requireNotNull(zwischenzustand.zugStatus).zugId)
        return ereignisse
    }

    private fun ablaufereignis(zustand: SpielZustand): SpielEreignis.AngeboteAbgelaufen? {
        val handel = zustand.handelsAngebote.filter {
            it.status == HandelsAngebotStatus.OFFEN && it.erstelltInRunde < zustand.rundenzähler
        }.map { it.id }
        val anleihen = zustand.anleihenAngebote.filter {
            it.status == HandelsAngebotStatus.OFFEN && it.erstelltInRunde < zustand.rundenzähler
        }.map { it.id }
        return if (handel.isEmpty() && anleihen.isEmpty()) {
            null
        } else {
            SpielEreignis.AngeboteAbgelaufen(
                handelsangebote = handel,
                anleihenangebote = anleihen,
            )
        }
    }
}
