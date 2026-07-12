package de.teutonstudio.zentralbank.domain.engine

import de.teutonstudio.zentralbank.domain.AnleiheId
import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Konflikt
import de.teutonstudio.zentralbank.domain.KontoId
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.events.GameEvent
import de.teutonstudio.zentralbank.domain.events.TransaktionsGrund
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.SchrittTyp
import de.teutonstudio.zentralbank.domain.zug.SchrittZustand
import de.teutonstudio.zentralbank.domain.zug.ZugAutomat
import de.teutonstudio.zentralbank.domain.zug.ZugStatus

object Reducer {
    fun reduce(state: GameState, event: GameEvent): Result<GameState> {
        return runCatching {
            state.pruefeZugGate(event)
            when (event) {
                is GameEvent.RohstoffEinnahme -> state.bucheRohstoffe(event.spieler, event.mengen, faktor = 1)
                is GameEvent.RohstoffAusgabe -> state.bucheRohstoffe(event.spieler, event.mengen, faktor = -1)
                is GameEvent.Transaktion -> state.bucheTransaktion(event.von, event.an, event.betrag)
                is GameEvent.RohstoffHandel -> state.bucheRohstoffHandel(event)
                is GameEvent.AnleiheGekauft -> state.bucheAnleiheGekauft(event)
                is GameEvent.AnleiheVerkauft -> state.bucheAnleiheVerkauft(event)
                is GameEvent.AnleiheFaellig -> state.bucheAnleiheFaellig(event)
                is GameEvent.Expansion -> state.bucheExpansion(event)
                is GameEvent.KriegErklaert -> state.bucheKriegErklaert(event)
                is GameEvent.KriegBeendet -> state.bucheKriegBeendet(event)
                is GameEvent.SchrittAbgeschlossen -> state.schrittAbschliessen(event)
                is GameEvent.PhaseAbgeschlossen -> state.phaseAbschliessen(event)
                GameEvent.ZugBeendet -> state.zugBeenden()
            }
        }
    }
}

private fun GameState.pruefeZugGate(event: GameEvent) {
    val zug = zugStatus ?: return
    val schritt = event.schrittTyp() ?: return
    val info = ZugAutomat.schritte(this).first { it.typ == schritt }
    require(info.zustand == SchrittZustand.VERFUEGBAR) {
        info.begruendung ?: "Schritt $schritt ist nicht verfuegbar."
    }
    event.primaererSpieler()?.let { spieler ->
        require(spieler == zug.spieler) {
            "Nur der aktive Spieler ${zug.spieler.wert} darf diesen Schritt ausfuehren."
        }
    }
}

private fun GameEvent.schrittTyp(): SchrittTyp? = when (this) {
    is GameEvent.RohstoffEinnahme -> SchrittTyp.ROHSTOFF_EINNAHMEN
    is GameEvent.RohstoffAusgabe -> SchrittTyp.ROHSTOFF_AUSGABEN
    is GameEvent.Transaktion -> when (grund) {
        TransaktionsGrund.ROHSTOFFHANDEL -> SchrittTyp.ROHSTOFF_HANDEL
        TransaktionsGrund.ANLEIHENHANDEL -> SchrittTyp.ANLEIHEN_HANDEL
        else -> SchrittTyp.FINANZ_AUSGABEN
    }
    is GameEvent.AnleiheGekauft -> SchrittTyp.ANLEIHEN_HANDEL
    is GameEvent.AnleiheVerkauft -> SchrittTyp.ANLEIHEN_HANDEL
    is GameEvent.AnleiheFaellig -> SchrittTyp.ANLEIHEN_HANDEL
    is GameEvent.RohstoffHandel -> SchrittTyp.ROHSTOFF_HANDEL
    is GameEvent.Expansion -> SchrittTyp.EXPANSION
    is GameEvent.KriegErklaert -> SchrittTyp.KRIEG
    is GameEvent.KriegBeendet -> SchrittTyp.KRIEG
    is GameEvent.SchrittAbgeschlossen,
    is GameEvent.PhaseAbgeschlossen,
    GameEvent.ZugBeendet -> null
}

private fun GameEvent.primaererSpieler(): SpielerId? = when (this) {
    is GameEvent.RohstoffEinnahme -> spieler
    is GameEvent.RohstoffAusgabe -> spieler
    is GameEvent.Transaktion -> when (von) {
        is KontoId.Spieler -> von.id
        KontoId.Bank -> (an as? KontoId.Spieler)?.id
    }
    is GameEvent.AnleiheGekauft -> kaeufer
    is GameEvent.AnleiheVerkauft -> verkaeufer
    is GameEvent.RohstoffHandel -> kaeufer
    is GameEvent.Expansion -> spieler
    is GameEvent.KriegErklaert -> aggressor
    is GameEvent.KriegBeendet -> spielerA
    is GameEvent.AnleiheFaellig,
    is GameEvent.SchrittAbgeschlossen,
    is GameEvent.PhaseAbgeschlossen,
    GameEvent.ZugBeendet -> null
}

private fun GameState.schrittAbschliessen(event: GameEvent.SchrittAbgeschlossen): GameState {
    val zug = zugStatus ?: error("Es ist kein Zug aktiv.")
    val info = ZugAutomat.schritte(this).first { it.typ == event.schritt }
    require(info.zustand == SchrittZustand.VERFUEGBAR || info.zustand == SchrittZustand.ERLEDIGT) {
        info.begruendung ?: "Schritt ist nicht verfuegbar."
    }
    return copy(zugStatus = zug.copy(erledigteSchritte = zug.erledigteSchritte + event.schritt))
}

private fun GameState.phaseAbschliessen(event: GameEvent.PhaseAbgeschlossen): GameState {
    val zug = zugStatus ?: error("Es ist kein Zug aktiv.")
    require(zug.phase == event.phase) { "Falsche Phase: aktueller Zug ist in ${zug.phase}." }
    require(ZugAutomat.kannPhaseAbschliessen(this)) { "Nicht alle Pflichtschritte der Phase sind erledigt." }
    val naechstePhase = ZugAutomat.naechstePhase(zug.phase)
        ?: error("Aktions-Phase wird mit ZugBeendet abgeschlossen.")
    return copy(zugStatus = ZugStatus(zug.spieler, naechstePhase))
}

private fun GameState.zugBeenden(): GameState {
    val zug = zugStatus ?: error("Es ist kein Zug aktiv.")
    require(ZugAutomat.kannZugBeenden(this)) { "Zug kann erst in der Aktions-Phase beendet werden." }
    val aktuellerIndex = spieler.indexOfFirst { it.id == zug.spieler }
    require(aktuellerIndex >= 0) { "Aktiver Spieler ist unbekannt." }
    val naechsterSpieler = spieler[(aktuellerIndex + 1) % spieler.size]
    val neueRunde = if (aktuellerIndex == spieler.lastIndex) rundenzähler + 1 else rundenzähler
    return copy(
        aktiverSpieler = naechsterSpieler.id,
        rundenzähler = neueRunde,
        zugStatus = ZugStatus(naechsterSpieler.id, Phase.Einnahmen),
    )
}

private fun GameState.bucheExpansion(event: GameEvent.Expansion): GameState {
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

private fun GameState.bucheKriegErklaert(event: GameEvent.KriegErklaert): GameState {
    require(event.aggressor != event.verteidiger) { "Ein Spieler kann sich nicht selbst Krieg erklaeren." }
    require(spieler.any { it.id == event.aggressor }) { "Unbekannter Spieler: ${event.aggressor.wert}" }
    require(spieler.any { it.id == event.verteidiger }) { "Unbekannter Spieler: ${event.verteidiger.wert}" }
    require(konflikte.none { it.betrifft(event.aggressor, event.verteidiger) }) {
        "Zwischen diesen Spielern besteht bereits Krieg."
    }
    return copy(konflikte = konflikte + Konflikt(event.aggressor, event.verteidiger))
}

private fun GameState.bucheKriegBeendet(event: GameEvent.KriegBeendet): GameState {
    val konflikt = konflikte.firstOrNull { it.betrifft(event.spielerA, event.spielerB) }
        ?: error("Zwischen diesen Spielern besteht kein Krieg.")
    return copy(konflikte = konflikte - konflikt)
}

private fun GameState.bucheAnleiheGekauft(event: GameEvent.AnleiheGekauft): GameState {
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

private fun GameState.bucheAnleiheVerkauft(event: GameEvent.AnleiheVerkauft): GameState {
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

private fun GameState.bucheAnleiheFaellig(event: GameEvent.AnleiheFaellig): GameState {
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

private fun GameState.bucheRohstoffHandel(event: GameEvent.RohstoffHandel): GameState {
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

private fun GameState.bucheRohstoffe(
    spieler: SpielerId,
    mengen: Map<Rohstoff, Int>,
    faktor: Int,
): GameState {
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

private fun GameState.verschiebeAnleihe(
    anleihe: AnleiheId,
    von: KontoId,
    an: KontoId,
): GameState {
    require(von != an) { "Anleihe-Sender und Empfaenger muessen verschieden sein." }
    val ohne = entferneAnleiheVonKonto(anleihe, von)
    return ohne.fuegeAnleiheZuKonto(anleihe, an)
}

private fun GameState.anleiheBesitzer(
    anleihe: AnleiheId,
): KontoId? {
    if (anleihe in bankAnleihen) return KontoId.Bank
    return spieler.firstOrNull { anleihe in it.anleihen }?.let { KontoId.Spieler(it.id) }
}

private fun GameState.entferneAnleihe(
    anleihe: AnleiheId,
): GameState {
    return copy(
        bankAnleihen = bankAnleihen - anleihe,
        spieler = spieler.map { spieler -> spieler.copy(anleihen = spieler.anleihen - anleihe) },
    )
}

private fun GameState.entferneAnleiheVonKonto(
    anleihe: AnleiheId,
    konto: KontoId,
): GameState {
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

private fun GameState.fuegeAnleiheZuKonto(
    anleihe: AnleiheId,
    konto: KontoId,
): GameState {
    require(anleiheBesitzer(anleihe) == null) { "Anleihe ${anleihe.wert} hat bereits einen Besitzer." }
    return when (konto) {
        KontoId.Bank -> copy(bankAnleihen = bankAnleihen + anleihe)
        is KontoId.Spieler -> updateSpieler(konto.id) { spieler ->
            spieler.copy(anleihen = spieler.anleihen + anleihe)
        }
    }
}

private fun GameState.bucheTransaktion(
    von: KontoId,
    an: KontoId,
    betrag: Geld,
): GameState {
    require(betrag > Geld.NULL) { "Transaktionsbetrag muss positiv sein." }
    require(von != an) { "Sender und Empfaenger muessen verschieden sein." }

    val nachAbzug = kontoAendern(von, -betrag)
    return nachAbzug.kontoAendern(an, betrag)
}

private fun GameState.kontoAendern(konto: KontoId, delta: Geld): GameState {
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

private fun GameState.updateSpieler(
    spielerId: SpielerId,
    update: (Spieler) -> Spieler,
): GameState {
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

fun GameState.geldsumme(): Geld {
    return spieler.fold(bankkonto) { summe, spieler -> summe + spieler.geldkonto }
}
