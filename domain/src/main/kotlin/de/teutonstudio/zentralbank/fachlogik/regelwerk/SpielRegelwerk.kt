package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Schuldenstrich
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.UeberschuldungsStatus
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.ereignis.TransaktionsGrund
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittZustand
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZugAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus

object SpielRegelwerk {
    fun reduce(state: SpielZustand, event: SpielEreignis): Result<SpielZustand> {
        return runCatching {
            state.pruefeZugGate(event)
            when (event) {
                is SpielEreignis.WarenkorbGeaendert -> state.warenkorbAendern(event.warenkorb)
                is SpielEreignis.RohstoffEinnahme -> state.bucheRohstoffe(event.spieler, event.mengen, faktor = 1)
                is SpielEreignis.RohstoffAusgabe -> state.bucheRohstoffe(event.spieler, event.mengen, faktor = -1)
                is SpielEreignis.Transaktion -> state.bucheTransaktion(event.von, event.an, event.betrag)
                is SpielEreignis.RohstoffHandel -> state.bucheRohstoffHandel(event)
                is SpielEreignis.AnleiheGekauft -> state.bucheAnleiheGekauft(event)
                is SpielEreignis.AnleiheVerkauft -> state.bucheAnleiheVerkauft(event)
                is SpielEreignis.AnleiheFaellig -> state.bucheAnleiheFaellig(event)
                is SpielEreignis.Expansion -> state.bucheExpansion(event)
                is SpielEreignis.KriegErklaert -> state.bucheKriegErklaert(event)
                is SpielEreignis.KriegBeendet -> state.bucheKriegBeendet(event)
                is SpielEreignis.Schuldenstrich -> state.bucheSchuldenstrich(event)
                is SpielEreignis.SchrittAbgeschlossen -> state.schrittAbschliessen(event)
                is SpielEreignis.PhaseAbgeschlossen -> state.phaseAbschliessen(event)
                SpielEreignis.ZugBeendet -> state.zugBeenden()
            }
        }
    }
}

private fun SpielZustand.pruefeZugGate(event: SpielEreignis) {
    if (event is SpielEreignis.WarenkorbGeaendert) return

    val zug = zugStatus ?: return
    val faelligerSchuldenstrich = faelligerSchuldenstrichSpieler()
    if (faelligerSchuldenstrich != null) {
        require(event is SpielEreignis.Schuldenstrich && event.spieler == faelligerSchuldenstrich) {
            "Zuerst muss der faellige Schuldenstrich fuer ${faelligerSchuldenstrich.wert} gebucht werden."
        }
        return
    }
    val schritt = event.schrittTyp() ?: return
    val info = ZugAuswertung.schritte(this).first { it.typ == schritt }
    require(info.zustand == SchrittZustand.VERFUEGBAR) {
        info.begruendung ?: "Schritt $schritt ist nicht verfuegbar."
    }
    event.primaererSpieler()?.let { spieler ->
        require(spieler == zug.spieler) {
            "Nur der aktive Spieler ${zug.spieler.wert} darf diesen Schritt ausfuehren."
        }
    }
}

private fun SpielEreignis.schrittTyp(): SchrittTyp? = when (this) {
    is SpielEreignis.WarenkorbGeaendert -> null
    is SpielEreignis.RohstoffEinnahme -> SchrittTyp.ROHSTOFF_EINNAHMEN
    is SpielEreignis.RohstoffAusgabe -> SchrittTyp.ROHSTOFF_AUSGABEN
    is SpielEreignis.Transaktion -> when (grund) {
        TransaktionsGrund.ROHSTOFFHANDEL -> SchrittTyp.ROHSTOFF_HANDEL
        TransaktionsGrund.ANLEIHENHANDEL -> SchrittTyp.ANLEIHEN_HANDEL
        else -> SchrittTyp.FINANZ_AUSGABEN
    }
    is SpielEreignis.AnleiheGekauft -> SchrittTyp.ANLEIHEN_HANDEL
    is SpielEreignis.AnleiheVerkauft -> SchrittTyp.ANLEIHEN_HANDEL
    is SpielEreignis.AnleiheFaellig -> SchrittTyp.ANLEIHEN_HANDEL
    is SpielEreignis.RohstoffHandel -> SchrittTyp.ROHSTOFF_HANDEL
    is SpielEreignis.Expansion -> SchrittTyp.EXPANSION
    is SpielEreignis.KriegErklaert -> SchrittTyp.KRIEG
    is SpielEreignis.KriegBeendet -> SchrittTyp.KRIEG
    is SpielEreignis.Schuldenstrich -> SchrittTyp.FINANZ_AUSGABEN
    is SpielEreignis.SchrittAbgeschlossen,
    is SpielEreignis.PhaseAbgeschlossen,
    SpielEreignis.ZugBeendet -> null
}

private fun SpielEreignis.primaererSpieler(): SpielerId? = when (this) {
    is SpielEreignis.WarenkorbGeaendert -> null
    is SpielEreignis.RohstoffEinnahme -> spieler
    is SpielEreignis.RohstoffAusgabe -> spieler
    is SpielEreignis.Transaktion -> when (von) {
        is KontoId.Spieler -> von.id
        KontoId.Bank -> (an as? KontoId.Spieler)?.id
    }
    is SpielEreignis.AnleiheGekauft -> kaeufer
    is SpielEreignis.AnleiheVerkauft -> verkaeufer
    is SpielEreignis.RohstoffHandel -> kaeufer
    is SpielEreignis.Expansion -> spieler
    is SpielEreignis.KriegErklaert -> aggressor
    is SpielEreignis.KriegBeendet -> spielerA
    is SpielEreignis.Schuldenstrich -> spieler
    is SpielEreignis.AnleiheFaellig,
    is SpielEreignis.SchrittAbgeschlossen,
    is SpielEreignis.PhaseAbgeschlossen,
    SpielEreignis.ZugBeendet -> null
}

private fun SpielZustand.warenkorbAendern(warenkorb: Map<Rohstoff, Int>): SpielZustand {
    require(warenkorb.values.all { menge -> menge >= 0 }) {
        "Warenkorbmengen duerfen nicht negativ sein."
    }
    return copy(warenkorb = warenkorb.filterValues { menge -> menge > 0 })
}

private fun SpielZustand.schrittAbschliessen(event: SpielEreignis.SchrittAbgeschlossen): SpielZustand {
    val zug = zugStatus ?: error("Es ist kein Zug aktiv.")
    val info = ZugAuswertung.schritte(this).first { it.typ == event.schritt }
    require(info.zustand == SchrittZustand.VERFUEGBAR || info.zustand == SchrittZustand.ERLEDIGT) {
        info.begruendung ?: "Schritt ist nicht verfuegbar."
    }
    return copy(zugStatus = zug.copy(erledigteSchritte = zug.erledigteSchritte + event.schritt))
}

private fun SpielZustand.phaseAbschliessen(event: SpielEreignis.PhaseAbgeschlossen): SpielZustand {
    val zug = zugStatus ?: error("Es ist kein Zug aktiv.")
    require(zug.phase == event.phase) { "Falsche Phase: aktueller Zug ist in ${zug.phase}." }
    require(ZugAuswertung.kannPhaseAbschliessen(this)) { "Nicht alle Pflichtschritte der Phase sind erledigt." }
    val naechstePhase = ZugAuswertung.naechstePhase(zug.phase)
        ?: error("Aktions-Phase wird mit ZugBeendet abgeschlossen.")
    return copy(zugStatus = ZugStatus(zug.spieler, naechstePhase))
}

private fun SpielZustand.zugBeenden(): SpielZustand {
    val zug = zugStatus ?: error("Es ist kein Zug aktiv.")
    require(ZugAuswertung.kannZugBeenden(this)) { "Zug kann erst in der Aktions-Phase beendet werden." }
    val nachPruefung = aktualisiereUeberschuldung(zug.spieler)
    return if (nachPruefung.istSchuldenstrichFaellig(zug.spieler)) {
        nachPruefung
    } else {
        nachPruefung.naechsterZug(zug.spieler)
    }
}

private fun SpielZustand.naechsterZug(aktuellerSpieler: SpielerId): SpielZustand {
    val aktuellerIndex = spieler.indexOfFirst { it.id == aktuellerSpieler }
    require(aktuellerIndex >= 0) { "Aktiver Spieler ist unbekannt." }
    val naechsterSpieler = spieler[(aktuellerIndex + 1) % spieler.size]
    val neueRunde = if (aktuellerIndex == spieler.lastIndex) rundenzähler + 1 else rundenzähler
    return copy(
        aktiverSpieler = naechsterSpieler.id,
        rundenzähler = neueRunde,
        zugStatus = ZugStatus(naechsterSpieler.id, Phase.Einnahmen),
    )
}

private fun SpielZustand.bucheSchuldenstrich(event: SpielEreignis.Schuldenstrich): SpielZustand {
    require(event.entfernteBahnwege >= 0) { "Entfernte Bahnwege duerfen nicht negativ sein." }
    val schuldner = spieler.firstOrNull { it.id == event.spieler }
        ?: error("Unbekannter Spieler: ${event.spieler.wert}")
    require(konflikte.none { it.spielerA == event.spieler || it.spielerB == event.spieler }) {
        "Schuldenstrich ist im Krieg nicht direkt verfuegbar."
    }
    val eigeneAnleihen = anleihen.values.filter { it.emittent == event.spieler }
    require(eigeneAnleihen.isNotEmpty()) { "Schuldenstrich benoetigt offene eigene Anleihen." }
    val vorhandeneBahnwege = schuldner.bauteile.getOrDefault(BauteilTyp.EISENBAHNLINIE, 0)
    require(event.entfernteBahnwege <= vorhandeneBahnwege) {
        "Mehr Bahnwege entfernt als vorhanden."
    }

    var ausgezahlterBetrag = Geld.NULL
    var neuerState = this
    val geloeschteAnleihen = eigeneAnleihen.map { it.id }
    eigeneAnleihen.forEach { anleihe ->
        val besitzer = anleiheBesitzer(anleihe.id)
        if (besitzer is KontoId.Spieler && besitzer.id != event.spieler) {
            ausgezahlterBetrag += anleihe.nennwert
            neuerState = neuerState.kontoAendern(besitzer, anleihe.nennwert)
        }
    }

    neuerState = neuerState.copy(
        bankAnleihen = neuerState.bankAnleihen - geloeschteAnleihen.toSet(),
        anleihen = neuerState.anleihen - geloeschteAnleihen.toSet(),
        spieler = neuerState.spieler.map { spieler ->
            val ohneAnleihen = spieler.copy(anleihen = spieler.anleihen - geloeschteAnleihen.toSet())
            if (spieler.id == event.spieler) {
                ohneAnleihen.copy(
                    bauteile = spieler.bauteile.nachSchuldenstrich(event.entfernteBahnwege),
                )
            } else {
                ohneAnleihen
            }
        },
    )

    neuerState = neuerState.copy(
        schuldenstriche = neuerState.schuldenstriche + Schuldenstrich(
            spieler = event.spieler,
            runde = rundenzähler,
            ausgezahlterBetrag = ausgezahlterBetrag,
            geloeschteAnleihen = geloeschteAnleihen,
            entfernteBahnwege = event.entfernteBahnwege,
        ),
    )

    val ohneUeberschuldung = neuerState.copy(
        ueberschuldungen = neuerState.ueberschuldungen.filterNot { it.spieler == event.spieler },
    )
    return if (zugStatus?.spieler == event.spieler) {
        ohneUeberschuldung.naechsterZug(event.spieler)
    } else {
        ohneUeberschuldung
    }
}

private fun SpielZustand.aktualisiereUeberschuldung(spielerId: SpielerId): SpielZustand {
    val schuldensumme = bankgehalteneSchuldensumme(spielerId)
    val marktwert = marktwert(spielerId)
    val istImFrieden = konflikte.none { it.spielerA == spielerId || it.spielerB == spielerId }
    val istUeberschuldet = istImFrieden && schuldensumme > marktwert && schuldensumme > Geld.NULL
    val bestehend = ueberschuldungen.firstOrNull { it.spieler == spielerId }
    val neuerStatus = if (istUeberschuldet) {
        val neueSerie = (bestehend?.friedlicheUeberschuldeteZuege ?: 0) + 1
        UeberschuldungsStatus(
            spieler = spielerId,
            friedlicheUeberschuldeteZuege = neueSerie,
            letztePruefungRunde = rundenzähler,
            schuldensumme = schuldensumme,
            marktwert = marktwert,
            warnungAktiv = neueSerie >= UEBERSCHULDUNG_WARNUNG_AB_ZUEGEN,
            schuldenstrichFaellig = neueSerie > UEBERSCHULDUNG_WARNUNG_AB_ZUEGEN,
        )
    } else {
        null
    }

    return copy(
        ueberschuldungen = ueberschuldungen.filterNot { it.spieler == spielerId } + listOfNotNull(neuerStatus),
    )
}

private fun SpielZustand.faelligerSchuldenstrichSpieler(): SpielerId? {
    return ueberschuldungen.firstOrNull { it.schuldenstrichFaellig }?.spieler
}

private fun SpielZustand.istSchuldenstrichFaellig(spielerId: SpielerId): Boolean {
    return ueberschuldungen.any { it.spieler == spielerId && it.schuldenstrichFaellig }
}

private fun SpielZustand.bankgehalteneSchuldensumme(spielerId: SpielerId): Geld {
    return anleihen.values
        .filter { anleihe -> anleihe.emittent == spielerId && anleihe.id in bankAnleihen }
        .fold(Geld.NULL) { summe, anleihe -> summe + anleihe.offeneSchuldMitZinsen() }
}

private fun Anleihe.offeneSchuldMitZinsen(): Geld {
    return nennwert + zinszahlung() * laufzeitRunden
}

private fun Anleihe.zinszahlung(): Geld {
    return Geld.cent(nennwert.cent * zinsBasispunkte / 10_000L)
}

private fun SpielZustand.marktwert(spielerId: SpielerId): Geld {
    val spieler = spieler.firstOrNull { it.id == spielerId }
        ?: error("Unbekannter Spieler: ${spielerId.wert}")
    return spieler.bauteile.entries.fold(Geld.NULL) { summe, (bauteil, menge) ->
        summe + bauteil.marktwert(marktpreise) * menge
    }
}

private fun BauteilTyp.marktwert(marktpreise: Map<Rohstoff, Geld>): Geld {
    return kosten.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
        val preis = marktpreise[rohstoff] ?: Geld.NULL
        summe + preis * menge
    }
}

private const val UEBERSCHULDUNG_WARNUNG_AB_ZUEGEN = 3

private fun Map<BauteilTyp, Int>.nachSchuldenstrich(entfernteBahnwege: Int): Map<BauteilTyp, Int> {
    val neu = toMutableMap()
    neu.remove(BauteilTyp.BAHNHOF)
    neu.remove(BauteilTyp.HAFEN)

    val grossbahnhoefe = neu.remove(BauteilTyp.GROSSBAHNHOF) ?: 0
    if (grossbahnhoefe > 0) {
        neu[BauteilTyp.BAHNHOF] = neu.getOrDefault(BauteilTyp.BAHNHOF, 0) + grossbahnhoefe
    }

    val grosshaefen = neu.remove(BauteilTyp.GROSSHAFEN) ?: 0
    if (grosshaefen > 0) {
        neu[BauteilTyp.HAFEN] = neu.getOrDefault(BauteilTyp.HAFEN, 0) + grosshaefen
    }

    val bahnwege = neu.getOrDefault(BauteilTyp.EISENBAHNLINIE, 0) - entfernteBahnwege
    if (bahnwege > 0) {
        neu[BauteilTyp.EISENBAHNLINIE] = bahnwege
    } else {
        neu.remove(BauteilTyp.EISENBAHNLINIE)
    }

    return neu.filterValues { it > 0 }
}

private fun SpielZustand.bucheExpansion(event: SpielEreignis.Expansion): SpielZustand {
    val nachKosten = bucheRohstoffe(
        spieler = event.spieler,
        mengen = event.bauteil.kosten,
        faktor = -1,
    )
    return nachKosten.updateSpieler(event.spieler) { spieler ->
        spieler.copy(
            bauteile = spieler.bauteile + (event.bauteil to (spieler.bauteile.getOrDefault(event.bauteil, 0) + 1)),
        )
    }
}

private fun SpielZustand.bucheKriegErklaert(event: SpielEreignis.KriegErklaert): SpielZustand {
    require(event.aggressor != event.verteidiger) { "Ein Spieler kann sich nicht selbst Krieg erklaeren." }
    require(spieler.any { it.id == event.aggressor }) { "Unbekannter Spieler: ${event.aggressor.wert}" }
    require(spieler.any { it.id == event.verteidiger }) { "Unbekannter Spieler: ${event.verteidiger.wert}" }
    require(konflikte.none { it.betrifft(event.aggressor, event.verteidiger) }) {
        "Zwischen diesen Spielern besteht bereits Krieg."
    }
    return copy(konflikte = konflikte + Konflikt(event.aggressor, event.verteidiger))
}

private fun SpielZustand.bucheKriegBeendet(event: SpielEreignis.KriegBeendet): SpielZustand {
    val konflikt = konflikte.firstOrNull { it.betrifft(event.spielerA, event.spielerB) }
        ?: error("Zwischen diesen Spielern besteht kein Krieg.")
    return copy(konflikte = konflikte - konflikt)
}

private fun SpielZustand.bucheAnleiheGekauft(event: SpielEreignis.AnleiheGekauft): SpielZustand {
    require(event.preis > Geld.NULL) { "Anleihepreis muss positiv sein." }
    require(event.anleihe in anleihen.keys) { "Unbekannte Anleihe: ${event.anleihe.wert}" }

    return bucheTransaktion(
        von = KontoId.Spieler(event.kaeufer),
        an = event.verkaeufer,
        betrag = event.preis,
    ).verschiebeAnleihe(
        anleihe = event.anleihe,
        von = event.verkaeufer,
        an = KontoId.Spieler(event.kaeufer),
    )
}

private fun SpielZustand.bucheAnleiheVerkauft(event: SpielEreignis.AnleiheVerkauft): SpielZustand {
    require(event.preis > Geld.NULL) { "Anleihepreis muss positiv sein." }
    require(event.anleihe in anleihen.keys) { "Unbekannte Anleihe: ${event.anleihe.wert}" }

    return bucheTransaktion(
        von = event.kaeufer,
        an = KontoId.Spieler(event.verkaeufer),
        betrag = event.preis,
    ).verschiebeAnleihe(
        anleihe = event.anleihe,
        von = KontoId.Spieler(event.verkaeufer),
        an = event.kaeufer,
    )
}

private fun SpielZustand.bucheAnleiheFaellig(event: SpielEreignis.AnleiheFaellig): SpielZustand {
    val anleihe = anleihen[event.anleihe]
        ?: error("Unbekannte Anleihe: ${event.anleihe.wert}")
    val besitzer = anleiheBesitzer(event.anleihe)
        ?: error("Anleihe ${event.anleihe.wert} hat keinen Besitzer.")

    return bucheTransaktion(
        von = KontoId.Spieler(anleihe.emittent),
        an = besitzer,
        betrag = anleihe.nennwert,
    ).entferneAnleihe(event.anleihe)
        .let { state -> state.copy(anleihen = state.anleihen - event.anleihe) }
}

private fun SpielZustand.bucheRohstoffHandel(event: SpielEreignis.RohstoffHandel): SpielZustand {
    require(event.menge > 0) { "Rohstoffhandelsmenge muss positiv sein." }
    require(event.preis > Geld.NULL) { "Rohstoffhandelspreis muss positiv sein." }

    return bucheRohstoffe(
        spieler = event.verkaeufer,
        mengen = mapOf(event.rohstoff to event.menge),
        faktor = -1,
    )
        .bucheRohstoffe(
            spieler = event.kaeufer,
            mengen = mapOf(event.rohstoff to event.menge),
            faktor = 1,
        )
        .bucheTransaktion(
            von = KontoId.Spieler(event.kaeufer),
            an = KontoId.Spieler(event.verkaeufer),
            betrag = event.preis,
        )
}

private fun SpielZustand.bucheRohstoffe(
    spieler: SpielerId,
    mengen: Map<Rohstoff, Int>,
    faktor: Int,
): SpielZustand {
    require(mengen.isNotEmpty()) { "Rohstoffbuchung darf nicht leer sein." }
    require(mengen.values.all { it > 0 }) { "Rohstoffmengen muessen positiv sein." }

    return updateSpieler(spieler) { alt ->
        val neu = alt.rohstoffe.toMutableMap()
        mengen.forEach { (rohstoff, menge) ->
            val neuerWert = neu.getOrDefault(rohstoff, 0) + menge * faktor
            require(neuerWert >= 0) {
                "${alt.name} hat nicht genug ${rohstoff.name}."
            }
            if (neuerWert == 0) {
                neu.remove(rohstoff)
            } else {
                neu[rohstoff] = neuerWert
            }
        }
        alt.copy(rohstoffe = neu.toMap())
    }
}

private fun SpielZustand.verschiebeAnleihe(
    anleihe: AnleiheId,
    von: KontoId,
    an: KontoId,
): SpielZustand {
    require(von != an) { "Anleihe-Sender und Empfaenger muessen verschieden sein." }
    val ohne = entferneAnleiheVonKonto(anleihe, von)
    return ohne.fuegeAnleiheZuKonto(anleihe, an)
}

private fun SpielZustand.anleiheBesitzer(
    anleihe: AnleiheId,
): KontoId? {
    if (anleihe in bankAnleihen) return KontoId.Bank
    return spieler.firstOrNull { anleihe in it.anleihen }?.let { KontoId.Spieler(it.id) }
}

private fun SpielZustand.entferneAnleihe(
    anleihe: AnleiheId,
): SpielZustand {
    return copy(
        bankAnleihen = bankAnleihen - anleihe,
        spieler = spieler.map { spieler -> spieler.copy(anleihen = spieler.anleihen - anleihe) },
    )
}

private fun SpielZustand.entferneAnleiheVonKonto(
    anleihe: AnleiheId,
    konto: KontoId,
): SpielZustand {
    return when (konto) {
        KontoId.Bank -> {
            require(anleihe in bankAnleihen) { "Bank besitzt Anleihe ${anleihe.wert} nicht." }
            copy(bankAnleihen = bankAnleihen - anleihe)
        }
        is KontoId.Spieler -> updateSpieler(konto.id) { spieler ->
            require(anleihe in spieler.anleihen) { "${spieler.name} besitzt Anleihe ${anleihe.wert} nicht." }
            spieler.copy(anleihen = spieler.anleihen - anleihe)
        }
    }
}

private fun SpielZustand.fuegeAnleiheZuKonto(
    anleihe: AnleiheId,
    konto: KontoId,
): SpielZustand {
    require(anleiheBesitzer(anleihe) == null) { "Anleihe ${anleihe.wert} hat bereits einen Besitzer." }
    return when (konto) {
        KontoId.Bank -> copy(bankAnleihen = bankAnleihen + anleihe)
        is KontoId.Spieler -> updateSpieler(konto.id) { spieler ->
            spieler.copy(anleihen = spieler.anleihen + anleihe)
        }
    }
}

private fun SpielZustand.bucheTransaktion(
    von: KontoId,
    an: KontoId,
    betrag: Geld,
): SpielZustand {
    require(betrag > Geld.NULL) { "Transaktionsbetrag muss positiv sein." }
    require(von != an) { "Sender und Empfaenger muessen verschieden sein." }

    val nachAbzug = kontoAendern(von, -betrag)
    return nachAbzug.kontoAendern(an, betrag)
}

private fun SpielZustand.kontoAendern(konto: KontoId, delta: Geld): SpielZustand {
    return when (konto) {
        KontoId.Bank -> {
            val neu = bankkonto + delta
            require(neu >= Geld.NULL) { "Bankkonto darf nicht negativ werden." }
            copy(bankkonto = neu)
        }
        is KontoId.Spieler -> updateSpieler(konto.id) { spieler ->
            val neu = spieler.geldkonto + delta
            require(neu >= Geld.NULL) { "${spieler.name} hat nicht genug Geld." }
            spieler.copy(geldkonto = neu)
        }
    }
}

private fun SpielZustand.updateSpieler(
    spielerId: SpielerId,
    update: (Spieler) -> Spieler,
): SpielZustand {
    var gefunden = false
    val neueSpieler = spieler.map { spieler ->
        if (spieler.id == spielerId) {
            gefunden = true
            update(spieler)
        } else {
            spieler
        }
    }
    require(gefunden) { "Unbekannter Spieler: ${spielerId.wert}" }
    return copy(spieler = neueSpieler)
}

fun SpielZustand.geldsumme(): Geld {
    return spieler.fold(bankkonto) { summe, spieler -> summe + spieler.geldkonto }
}
