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
        require(ereignis.spieler == zustand.aktiverSpieler) {
            "Nur der aktive Spieler kann diese Aufgabe erklären."
        }
        val ausgeschieden = zustand.ausgeschiedeneSpieler + ereignis.spieler
        val naechster = naechsterAktiverSpieler(zustand, ereignis.spieler, ausgeschieden)
        val aktuellerIndex = zustand.spieler.indexOfFirst { it.id == ereignis.spieler }
        val naechsterIndex = naechster?.let { id -> zustand.spieler.indexOfFirst { it.id == id } }
        val neueRunde = if (naechsterIndex != null && naechsterIndex <= aktuellerIndex) {
            zustand.rundenzähler + 1
        } else {
            zustand.rundenzähler
        }
        val neueZugId = (zustand.zugStatus?.zugId ?: 0L) + 1L
        return zustand.copy(
            ausgeschiedeneSpieler = ausgeschieden,
            ausscheidensReihenfolge = zustand.ausscheidensReihenfolge + ereignis.spieler,
            aktiverSpieler = naechster,
            rundenzähler = neueRunde,
            zugStatus = naechster?.let { spieler ->
                ZugStatus(neueZugId, spieler, ZugPhase.Prozug)
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
