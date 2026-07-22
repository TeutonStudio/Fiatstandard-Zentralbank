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
import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerPaar

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
        if (aktion is SpielAktion.SchuldenstrichDurchfuehren) {
            return schuldenstrichEreignisse(zustand, aktion)
        }
        if (aktion is SpielAktion.KriegsEinheitBewegen ||
            aktion is SpielAktion.KriegsEinheitenBewegen
        ) {
            return bewegungsEreignisse(zustand, aktion)
        }
        if (aktion is SpielAktion.FriedensvertragAnnehmen) {
            return friedensannahmeEreignisse(zustand, aktion)
        }
        if (aktion is SpielAktion.KriegKapitulieren) {
            return kapitulationsEreignisse(zustand, aktion)
        }
        if (aktion is SpielAktion.ZahlungsunfaehigkeitFeststellen) {
            val plan = de.teutonstudio.zentralbank.fachlogik.auswertung
                .ZahlungsfaehigkeitsAuswertung.plan(zustand, aktion.spieler)
            require(
                aktion.zugId == zustand.zugStatus?.zugId &&
                    plan.automatischeAbwicklungNoetig
            ) {
                "Der Spieler besitzt noch einen regulären Rettungsweg."
            }
            if (plan.schuldenstrichMoeglich) {
                return schuldenstrichEreignisse(
                    zustand,
                    SpielAktion.SchuldenstrichDurchfuehren(aktion.spieler),
                )
            }
            require(plan.ausscheidenNoetig) {
                "Die automatische Insolvenzabwicklung ist noch nicht entscheidbar."
            }
            return ausscheidenEreignisse(
                zustand,
                aktion.spieler,
                if (plan.hauptbahnhof != null) {
                    de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund
                        .HAUPTBAHNHOF_UNVERSORGT
                } else {
                    de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund.INSOLVENZ
                },
            )
        }
        return listOf(when (aktion) {
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
            is SpielAktion.KriegsEinheitenBewegen -> SpielEreignis.KriegsEinheitenBewegt(
                spieler = aktion.spieler,
                ids = aktion.ids.sorted(),
                weg = listOf(aktion.naechsteKante),
            )
            is SpielAktion.VerwaltungsruineReparieren ->
                SpielEreignis.VerwaltungsruineRepariert(aktion.spieler, aktion.ecke)
            is SpielAktion.VerwaltungsruineAbreissen ->
                SpielEreignis.VerwaltungsruineAbgerissen(aktion.spieler, aktion.ecke)
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
            is SpielAktion.AnleiheAufstocken -> {
                val alt = zustand.anleihen[aktion.alteAnleihe]
                    ?: error("Unbekannte Anleihe: ${aktion.alteAnleihe.wert}")
                val differenz = aktion.neuerNennwert - alt.nennwert
                SpielEreignis.AnleiheAufgestockt(
                    alteAnleihe = alt.id,
                    neueAnleihe = Anleihe(
                        id = AnleiheId("anleihe-${zustand.naechsteAnleiheNummer}"),
                        emittent = aktion.spieler,
                        nennwert = aktion.neuerNennwert,
                        zinsBasispunkte = aktion.zinsBasispunkte,
                        laufzeitRunden = aktion.laufzeitRunden,
                        emissionsRunde = zustand.rundenzähler,
                    ),
                    glaeubiger = AnleihenAuswertung.besitzer(zustand, alt.id)
                        ?: error("Die alte Anleihe besitzt keinen Gläubiger."),
                    liquiditaetsDifferenz = differenz,
                )
            }
            is SpielAktion.SchuldenstrichDurchfuehren -> error(
                "Wird als mehrstufige Regelfolge aufgelöst.",
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
            is SpielAktion.ZahlungsunfaehigkeitFeststellen -> error(
                "Wird als mehrstufige Regelfolge aufgelöst.",
            )
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
                krieg = de.teutonstudio.zentralbank.fachlogik.modell.KriegId(
                    "krieg-${zustand.naechsteKriegNummer}",
                ),
                aggressor = aktion.aggressor,
                verteidiger = aktion.verteidiger,
            )
            is SpielAktion.KriegsAllianzBeitreten -> SpielEreignis.KriegsAllianzBeigetreten(
                aktion.krieg,
                aktion.spieler,
                aktion.seite,
            )
            is SpielAktion.WaffenstillstandAnbieten -> SpielEreignis.WaffenstillstandAngeboten(
                aktion.krieg,
                aktion.spieler,
                aktion.gegner,
            )
            is SpielAktion.WaffenstillstandAnnehmen -> SpielEreignis.WaffenstillstandGeschlossen(
                aktion.krieg,
                SpielerPaar.aus(aktion.spieler, aktion.von),
                aktion.spieler,
            )
            is SpielAktion.KriegKapitulieren -> SpielEreignis.KriegKapituliert(
                aktion.krieg,
                aktion.spieler,
            )
            is SpielAktion.FriedensvertragVorschlagen -> {
                require(aktion.vertrag.angenommenVon == setOf(aktion.spieler)) {
                    "Ein neuer Friedensvorschlag darf nur vom handelnden Spieler vorab angenommen sein."
                }
                require(aktion.spieler in aktion.vertrag.beteiligteSpieler) {
                    "Nur ein Vertragsbeteiligter darf Frieden vorschlagen."
                }
                SpielEreignis.FriedensvertragVorgeschlagen(aktion.vertrag)
            }
            is SpielAktion.FriedensvertragAnnehmen -> error(
                "Wird als mehrstufige Regelfolge aufgelöst.",
            )
            is SpielAktion.UnabhaengigenFriedenSchliessen -> {
                val konflikt = zustand.konflikte.single { it.id == aktion.krieg }
                val vertrag = de.teutonstudio.zentralbank.fachlogik.modell.Friedensvertrag(
                    id = FriedensvertragId("frieden-${zustand.naechsteFriedensvertragNummer}"),
                    krieg = konflikt.id,
                    beteiligteSpieler = setOf(aktion.spieler, aktion.gegner),
                    unentschiedeneTeilnehmer = setOf(aktion.spieler, aktion.gegner),
                    ausscheidendeTeilnehmer = setOf(aktion.spieler),
                    angenommenVon = setOf(aktion.spieler, aktion.gegner),
                    abgeschlossenInRunde = zustand.rundenzähler,
                )
                SpielEreignis.FriedensvertragAbgeschlossen(vertrag)
            }
            is SpielAktion.RessourcenUebertragen -> SpielEreignis.RessourcenUebertragen(
                aktion.spieler,
                aktion.empfaenger,
                aktion.rohstoffe,
                aktion.geld,
            )
        })
    }

    private fun bewegungsEreignisse(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): List<SpielEreignis> {
        val bewegung = when (aktion) {
            is SpielAktion.KriegsEinheitBewegen -> SpielEreignis.KriegsEinheitBewegt(
                aktion.spieler,
                aktion.id,
                listOf(aktion.naechsteKante),
            )
            is SpielAktion.KriegsEinheitenBewegen -> SpielEreignis.KriegsEinheitenBewegt(
                aktion.spieler,
                aktion.ids.sorted(),
                listOf(aktion.naechsteKante),
            )
            else -> error("Keine Bewegungsaktion.")
        }
        val ereignisse = mutableListOf<SpielEreignis>(bewegung)
        var zwischenzustand = SpielRegelwerk.wendeAn(zustand, bewegung).getOrThrow()
        val bewegteIds = when (aktion) {
            is SpielAktion.KriegsEinheitBewegen -> setOf(aktion.id)
            is SpielAktion.KriegsEinheitenBewegen -> aktion.ids.toSet()
            else -> emptySet()
        }
        val bewegte = requireNotNull(zwischenzustand.karte).belegung.kriegseinheiten
            .filter { it.id in bewegteIds }
        if (bewegte.isEmpty()) return ereignisse
        val besitzer = bewegte.first().besitzer
        val typ = bewegte.first().typ
        val ziel = bewegte.first().position
        val gegner = zwischenzustand.karte!!.belegung.kriegseinheiten
            .filter { it.position == ziel && it.typ == typ && it.besitzer != besitzer }
            .map { it.besitzer }
            .distinct()
            .sortedBy { it.wert }
        gegner.forEach { verteidiger ->
            val einheiten = requireNotNull(zwischenzustand.karte).belegung.kriegseinheiten
            val anzahlA = einheiten.count { it.position == ziel && it.typ == typ && it.besitzer == besitzer }
            val anzahlV = einheiten.count {
                it.position == ziel && it.typ == typ && it.besitzer == verteidiger
            }
            if (anzahlA == 0 || anzahlV == 0) return@forEach
            val (nachA, nachV) = de.teutonstudio.zentralbank.fachlogik.regelwerk
                .KonfliktRegelwerk.ueberlebendeTruppen(anzahlA, anzahlV)
            val kampf = SpielEreignis.KampfAufgeloest(
                angreifer = besitzer,
                verteidiger = verteidiger,
                typ = typ,
                kante = ziel,
                angreiferVorher = anzahlA,
                verteidigerVorher = anzahlV,
                angreiferNachher = nachA,
                verteidigerNachher = nachV,
            )
            ereignisse += kampf
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, kampf).getOrThrow()
        }
        val angrenzendeStandorte = requireNotNull(zwischenzustand.karte).belegung.ecken
            .filter { it.position == ziel.anfang || it.position == ziel.ende }
            .map { it.position }
            .sorted()
        angrenzendeStandorte.forEach { standort ->
            val belagerung = SpielEreignis.BelagerungAktualisiert(standort)
            ereignisse += belagerung
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, belagerung).getOrThrow()
        }
        return ereignisse
    }

    private fun schuldenstrichEreignisse(
        zustand: SpielZustand,
        aktion: SpielAktion.SchuldenstrichDurchfuehren,
    ): List<SpielEreignis> {
        val betrag = zustand.anleihen.values
            .filter {
                it.emittent == aktion.spieler &&
                    AnleihenAuswertung.besitzer(zustand, it.id) != KontoId.Spieler(aktion.spieler)
            }
            .fold(Geld.NULL) { summe, anleihe -> summe + anleihe.nennwert }
        return buildList {
            if (betrag > Geld.NULL) {
                add(
                    SpielEreignis.ZentralbankgeldGeschoepft(
                        spieler = aktion.spieler,
                        betrag = betrag,
                        grund = "SCHULDENSTRICH_ANLEIHENRUECKKAUF",
                    ),
                )
            }
            add(SpielEreignis.Schuldenstrich(aktion.spieler, aktion.entfernteBahnwege))
        }
    }

    private fun ausscheidenEreignisse(
        zustand: SpielZustand,
        spieler: SpielerId,
        grund: de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund,
    ): List<SpielEreignis> {
        val vorherigeRunde = zustand.rundenzähler
        val ereignisse = mutableListOf<SpielEreignis>(SpielEreignis.SpielerAusgeschieden(spieler, grund))
        var zwischenzustand = SpielRegelwerk.wendeAn(zustand, ereignisse.last()).getOrThrow()
        SpielEndeAuswertung.ergebnisFallsBeendet(zwischenzustand)?.let { ergebnis ->
            ereignisse += SpielEreignis.PartieBeendet(ergebnis)
            return ereignisse
        }
        if (zwischenzustand.rundenzähler > vorherigeRunde) {
            ablaufereignis(zwischenzustand)?.let { ablauf ->
                ereignisse += ablauf
                zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ablauf).getOrThrow()
            }
            val werte = RundenAuswertung.naechsteRundenwerte(zwischenzustand)
            val runde = SpielEreignis.RundeBegonnen(
                werte.runde,
                werte.marktpreise,
                werte.leitzins,
                werte.preisinflation,
            )
            ereignisse += runde
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, runde).getOrThrow()
        }
        val zug = requireNotNull(zwischenzustand.zugStatus)
        ereignisse += SpielEreignis.ProzugBegonnen(zug.zugId)
        return ereignisse
    }

    private fun friedensannahmeEreignisse(
        zustand: SpielZustand,
        aktion: SpielAktion.FriedensvertragAnnehmen,
    ): List<SpielEreignis> {
        val vertrag = zustand.friedensvertraege.singleOrNull { it.id == aktion.vertrag }
            ?: error("Unbekannter Friedensvertrag: ${aktion.vertrag.wert}")
        val angenommen = vertrag.angenommenVon + aktion.spieler
        val aktualisiert = vertrag.copy(angenommenVon = angenommen)
        val annahme = SpielEreignis.FriedensvertragAngenommen(aktion.vertrag, aktion.spieler)
        if (!angenommen.containsAll(vertrag.beteiligteSpieler)) return listOf(annahme)

        val abschluss = SpielEreignis.FriedensvertragAbgeschlossen(
            aktualisiert.copy(abgeschlossenInRunde = zustand.rundenzähler),
        )
        val nachAnnahme = SpielRegelwerk.wendeAn(zustand, annahme).getOrThrow()
        val nachAbschluss = SpielRegelwerk.wendeAn(nachAnnahme, abschluss).getOrThrow()
        return listOf(annahme, abschluss) + friedensfolgenEreignisse(nachAbschluss)
    }

    private fun kapitulationsEreignisse(
        zustand: SpielZustand,
        aktion: SpielAktion.KriegKapitulieren,
    ): List<SpielEreignis> {
        val kapitulation = SpielEreignis.KriegKapituliert(aktion.krieg, aktion.spieler)
        val nachKapitulation = SpielRegelwerk.wendeAn(zustand, kapitulation).getOrThrow()
        return listOf(kapitulation) + friedensfolgenEreignisse(nachKapitulation)
    }

    /** Automatischer Schuldenstrich ist erst nach dem Ausscheiden aus allen Kriegen zulässig. */
    private fun friedensfolgenEreignisse(start: SpielZustand): List<SpielEreignis> {
        val ereignisse = mutableListOf<SpielEreignis>()
        var zwischenzustand = start
        val kandidaten = start.friedensvertraege
            .flatMap { vertrag -> vertrag.schuldenstrichDanach.sortedBy { it.wert }.map { vertrag to it } }
            .filter { (vertrag, spieler) ->
                vertrag.entstehendeAnleihen.any { id ->
                    zwischenzustand.anleihen[id]?.emittent == spieler
                }
            }
            .map { it.second }
            .distinct()

        for (spieler in kandidaten) {
            if (zwischenzustand.ergebnis != null ||
                zwischenzustand.konflikte.any { spieler in it.teilnehmer }
            ) continue
            val herabstufbar = zwischenzustand.karte?.belegung?.ecken.orEmpty().any {
                it.besitzer == spieler &&
                    it.typ != de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.HAUPTBAHNHOF
            }
            if (herabstufbar) {
                schuldenstrichEreignisse(
                    zwischenzustand,
                    SpielAktion.SchuldenstrichDurchfuehren(spieler),
                ).forEach { ereignis ->
                    ereignisse += ereignis
                    zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ereignis).getOrThrow()
                }
            } else {
                val ausgeschieden = SpielEreignis.SpielerAusgeschieden(
                    spieler,
                    de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund.KRIEGSFOLGE,
                )
                ereignisse += ausgeschieden
                zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ausgeschieden).getOrThrow()
                SpielEndeAuswertung.ergebnisFallsBeendet(zwischenzustand)?.let { ergebnis ->
                    val ende = SpielEreignis.PartieBeendet(ergebnis)
                    ereignisse += ende
                    zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ende).getOrThrow()
                }
            }
        }
        return ereignisse
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
            val belagerungsEreignisse = belagerungsRundenEreignisse(zwischenzustand)
            belagerungsEreignisse.forEach { ereignis ->
                ereignisse += ereignis
                zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, ereignis).getOrThrow()
                if (ereignis is SpielEreignis.BelagerungAktualisiert) {
                    val ruine = zwischenzustand.karte?.belegung?.eckenNachPosition?.get(ereignis.standort)
                    val vorher = zustand.karte?.belegung?.eckenNachPosition?.get(ereignis.standort)
                    if (vorher?.typ == de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.HAUPTBAHNHOF &&
                        vorher.besitzer != null &&
                        ruine?.zustand == de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand.ZERSTOERT
                    ) {
                        val aus = SpielEreignis.SpielerAusgeschieden(
                            vorher.besitzer,
                            de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund.HAUPTBAHNHOF_ZERSTOERT,
                        )
                        ereignisse += aus
                        zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, aus).getOrThrow()
                        val ergebnis = SpielEndeAuswertung.ergebnisFallsBeendet(zwischenzustand)
                        if (ergebnis != null) {
                            val ende = SpielEreignis.PartieBeendet(ergebnis)
                            ereignisse += ende
                            SpielRegelwerk.wendeAn(zwischenzustand, ende).getOrThrow()
                            return ereignisse
                        }
                    }
                }
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

    private fun belagerungsRundenEreignisse(zustand: SpielZustand): List<SpielEreignis> =
        zustand.karte?.belegung?.ecken.orEmpty()
            .filter { it.zustand != de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand.ZERSTOERT }
            .sortedBy { it.position }
            .map { SpielEreignis.BelagerungAktualisiert(it.position, rundeFortschreiben = true) }

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
