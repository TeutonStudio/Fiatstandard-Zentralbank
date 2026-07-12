package de.teutonstudio.zentralbank.domain.engine

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.KontoId
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.events.GameEvent

object Reducer {
    fun reduce(state: GameState, event: GameEvent): Result<GameState> {
        return runCatching {
            when (event) {
                is GameEvent.RohstoffEinnahme -> state.bucheRohstoffe(event.spieler, event.mengen, faktor = 1)
                is GameEvent.RohstoffAusgabe -> state.bucheRohstoffe(event.spieler, event.mengen, faktor = -1)
                is GameEvent.Transaktion -> state.bucheTransaktion(event.von, event.an, event.betrag)
                is GameEvent.RohstoffHandel -> state.bucheRohstoffHandel(event)
                is GameEvent.AnleiheGekauft,
                is GameEvent.AnleiheVerkauft,
                is GameEvent.AnleiheFaellig,
                is GameEvent.Expansion,
                is GameEvent.KriegErklaert,
                is GameEvent.KriegBeendet,
                is GameEvent.SchrittAbgeschlossen,
                is GameEvent.PhaseAbgeschlossen,
                GameEvent.ZugBeendet -> error("Event ${event::class.simpleName} ist noch nicht implementiert.")
            }
        }
    }
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
