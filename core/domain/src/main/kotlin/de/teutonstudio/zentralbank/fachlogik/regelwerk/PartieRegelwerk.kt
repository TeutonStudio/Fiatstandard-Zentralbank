package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus

internal object PartieRegelwerk {
    fun spielerAusscheiden(
        zustand: SpielZustand,
        ereignis: SpielEreignis.SpielerAusgeschieden,
    ): SpielZustand {
        require(zustand.ergebnis == null) { "Die Partie ist bereits beendet." }
        require(ereignis.spieler in zustand.spieler.map { it.id }) {
            "Unbekannter Spieler: ${ereignis.spieler.wert}."
        }
        require(ereignis.spieler !in zustand.ausgeschiedeneSpieler) {
            "Der Spieler ist bereits ausgeschieden."
        }
        val ausgeschieden = zustand.ausgeschiedeneSpieler + ereignis.spieler
        val warAktiv = zustand.aktiverSpieler == ereignis.spieler
        val naechster = if (warAktiv) {
            naechsterAktiverSpieler(zustand, ereignis.spieler, ausgeschieden)
        } else {
            zustand.aktiverSpieler
        }
        val aktuellerIndex = zustand.spieler.indexOfFirst { it.id == ereignis.spieler }
        val naechsterIndex = naechster?.let { id -> zustand.spieler.indexOfFirst { it.id == id } }
        val neueRunde = if (warAktiv && naechsterIndex != null && naechsterIndex <= aktuellerIndex) {
            zustand.rundenzähler + 1
        } else {
            zustand.rundenzähler
        }
        val neueZugId = (zustand.zugStatus?.zugId ?: 0L) + if (warAktiv) 1L else 0L
        val neueKarte = zustand.karte?.let { karte ->
            karte.copy(
                belegung = karte.belegung.copy(
                    ecken = karte.belegung.ecken.filterNot { it.besitzer == ereignis.spieler },
                    kanten = karte.belegung.kanten.map { handelslinie ->
                        if (handelslinie.erbautVon == ereignis.spieler) {
                            handelslinie.copy(erbautVon = null)
                        } else {
                            handelslinie
                        }
                    },
                    kriegseinheiten = karte.belegung.kriegseinheiten.filterNot {
                        it.besitzer == ereignis.spieler
                    },
                ),
            )
        }
        val neueKonflikte = zustand.konflikte.mapNotNull { krieg ->
            if (ereignis.spieler !in krieg.teilnehmer) return@mapNotNull krieg
            val aggressoren = krieg.aggressoren - ereignis.spieler
            val verteidiger = krieg.verteidiger - ereignis.spieler
            if (aggressoren.isEmpty() || verteidiger.isEmpty()) null else krieg.copy(
                aggressoren = aggressoren,
                verteidiger = verteidiger,
                kapitulationen = krieg.kapitulationen + ereignis.spieler,
            )
        }.toSet()
        return zustand.copy(
            karte = neueKarte,
            konflikte = neueKonflikte,
            belagerungen = zustand.belagerungen.filterNot {
                it.verteidiger == ereignis.spieler || ereignis.spieler in it.beteiligteBelagerer
            },
            ausgeschiedeneSpieler = ausgeschieden,
            ausscheidensReihenfolge = zustand.ausscheidensReihenfolge + ereignis.spieler,
            aktiverSpieler = naechster,
            rundenzähler = neueRunde,
            zugStatus = if (warAktiv) {
                naechster?.let { spieler -> ZugStatus(neueZugId, spieler, ZugPhase.Prozug) }
            } else {
                zustand.zugStatus
            },
        )
    }

    fun partieBeenden(
        zustand: SpielZustand,
        ereignis: SpielEreignis.PartieBeendet,
    ): SpielZustand {
        require(zustand.ergebnis == null) { "Die Partie ist bereits beendet." }
        require(ereignis.ergebnis.endRunde == zustand.rundenzähler) {
            "Das Partieergebnis gehört nicht zur laufenden Runde."
        }
        require(ereignis.ergebnis.ausgeschiedeneSpieler == zustand.ausgeschiedeneSpieler) {
            "Das Partieergebnis enthält einen abweichenden Ausscheidensstand."
        }
        return zustand.copy(
            ergebnis = ereignis.ergebnis,
            aktiverSpieler = null,
            zugStatus = null,
        )
    }

    private fun naechsterAktiverSpieler(
        zustand: SpielZustand,
        aktuell: SpielerId,
        ausgeschieden: Set<SpielerId>,
    ): SpielerId? {
        val index = zustand.spieler.indexOfFirst { it.id == aktuell }
        return (1..zustand.spieler.size)
            .asSequence()
            .map { versatz -> zustand.spieler[(index + versatz) % zustand.spieler.size].id }
            .firstOrNull { kandidat -> kandidat !in ausgeschieden }
    }
}
