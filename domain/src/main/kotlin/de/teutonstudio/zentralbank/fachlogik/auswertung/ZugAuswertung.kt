package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittInfo
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

object ZugAuswertung {
    fun schritte(zustand: SpielZustand): List<SchrittInfo> {
        val zug = zustand.zugStatus ?: return emptyList()
        val automatischErledigt = automatischErledigteSchritte(zustand)
        return SchrittTyp.entries.map { schritt ->
            if (schritt in zug.erledigteSchritte || schritt in automatischErledigt) {
                SchrittInfo(schritt, SchrittZustand.ERLEDIGT)
            } else if (istInPhaseVerfuegbar(zug.phase, schritt)) {
                SchrittInfo(schritt, SchrittZustand.VERFUEGBAR)
            } else {
                SchrittInfo(
                    typ = schritt,
                    zustand = SchrittZustand.GESPERRT,
                    begruendung = begruendung(zug.phase, schritt),
                )
            }
        }
    }

    fun kannPhaseAbschliessen(zustand: SpielZustand): Boolean {
        val zug = zustand.zugStatus ?: return false
        val erledigt = zug.erledigteSchritte + automatischErledigteSchritte(zustand)
        return pflichtschritte(zug.phase).all { it in erledigt }
    }

    fun kannZugBeenden(zustand: SpielZustand): Boolean {
        return zustand.zugStatus?.phase == Phase.Aktionen
    }

    fun naechstePhase(phase: Phase): Phase? = when (phase) {
        Phase.Einnahmen -> Phase.Ausgaben
        Phase.Ausgaben -> Phase.Aktionen
        Phase.Aktionen -> null
    }

    private fun pflichtschritte(phase: Phase): Set<SchrittTyp> = when (phase) {
        Phase.Einnahmen -> setOf(SchrittTyp.ROHSTOFF_EINNAHMEN)
        Phase.Ausgaben -> setOf(SchrittTyp.ROHSTOFF_AUSGABEN, SchrittTyp.FINANZ_AUSGABEN)
        Phase.Aktionen -> emptySet()
    }

    private fun istInPhaseVerfuegbar(phase: Phase, schritt: SchrittTyp): Boolean = when (phase) {
        Phase.Einnahmen -> schritt == SchrittTyp.ROHSTOFF_EINNAHMEN
        Phase.Ausgaben -> schritt == SchrittTyp.ROHSTOFF_AUSGABEN || schritt == SchrittTyp.FINANZ_AUSGABEN
        Phase.Aktionen -> schritt in setOf(
            SchrittTyp.ANLEIHEN_HANDEL,
            SchrittTyp.ROHSTOFF_HANDEL,
            SchrittTyp.EXPANSION,
            SchrittTyp.KRIEG,
        )
    }

    private fun begruendung(phase: Phase, schritt: SchrittTyp): String = when {
        phase == Phase.Einnahmen -> "Erst verfuegbar nach Abschluss der Einnahmen-Phase."
        phase == Phase.Ausgaben && schritt == SchrittTyp.ROHSTOFF_EINNAHMEN -> "Einnahmen-Phase ist abgeschlossen."
        phase == Phase.Ausgaben -> "Erst verfuegbar in der Aktions-Phase."
        else -> "Nur in einer frueheren Phase verfuegbar."
    }

    private fun automatischErledigteSchritte(zustand: SpielZustand): Set<SchrittTyp> {
        val zug = zustand.zugStatus ?: return emptySet()
        val spieler = zustand.spieler.firstOrNull { it.id == zug.spieler } ?: return emptySet()
        val erledigt = mutableSetOf<SchrittTyp>()

        if (spieler.bauteile.entries.none { (bauteil, menge) ->
                menge > 0 && bauteil.verbrauch.isNotEmpty()
            }
        ) {
            erledigt += SchrittTyp.ROHSTOFF_AUSGABEN
        }

        val fremdgehalteneEigeneAnleihen = zustand.anleihen.values.any { anleihe ->
            anleihe.emittent == zug.spieler && zustand.anleiheBesitzer(anleihe.id) != null &&
                zustand.anleiheBesitzer(anleihe.id) != KontoId.Spieler(zug.spieler)
        }
        if (!fremdgehalteneEigeneAnleihen) {
            erledigt += SchrittTyp.FINANZ_AUSGABEN
        }

        return erledigt
    }

    private fun SpielZustand.anleiheBesitzer(
        anleihe: AnleiheId,
    ): KontoId? {
        if (anleihe in bankAnleihen) return KontoId.Bank
        return spieler.firstOrNull { anleihe in it.anleihen }
            ?.let { KontoId.Spieler(it.id) }
    }
}
