package de.teutonstudio.zentralbank.domain.zug

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.SpielerId
import kotlinx.serialization.Serializable

@Serializable
enum class SchrittTyp(val pflicht: Boolean) {
    ROHSTOFF_EINNAHMEN(true),
    ROHSTOFF_AUSGABEN(true),
    FINANZ_AUSGABEN(true),
    ANLEIHEN_HANDEL(false),
    ROHSTOFF_HANDEL(false),
    EXPANSION(false),
    KRIEG(false),
}

@Serializable
sealed interface Phase {
    @Serializable
    data object Einnahmen : Phase

    @Serializable
    data object Ausgaben : Phase

    @Serializable
    data object Aktionen : Phase
}

@Serializable
data class ZugStatus(
    val spieler: SpielerId,
    val phase: Phase,
    val erledigteSchritte: Set<SchrittTyp> = emptySet(),
)

enum class SchrittZustand {
    ERLEDIGT,
    VERFUEGBAR,
    GESPERRT,
}

data class SchrittInfo(
    val typ: SchrittTyp,
    val zustand: SchrittZustand,
    val begruendung: String? = null,
)

object ZugAutomat {
    fun schritte(state: GameState): List<SchrittInfo> {
        val zug = state.zugStatus ?: return emptyList()
        val automatischErledigt = automatischErledigteSchritte(state)
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

    fun kannPhaseAbschliessen(state: GameState): Boolean {
        val zug = state.zugStatus ?: return false
        val erledigt = zug.erledigteSchritte + automatischErledigteSchritte(state)
        return pflichtschritte(zug.phase).all { it in erledigt }
    }

    fun kannZugBeenden(state: GameState): Boolean {
        return state.zugStatus?.phase == Phase.Aktionen
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

    private fun automatischErledigteSchritte(state: GameState): Set<SchrittTyp> {
        val zug = state.zugStatus ?: return emptySet()
        val spieler = state.spieler.firstOrNull { it.id == zug.spieler } ?: return emptySet()
        val erledigt = mutableSetOf<SchrittTyp>()

        if (spieler.bauteile.entries.none { (bauteil, menge) ->
                menge > 0 && bauteil.verbrauch.isNotEmpty()
            }
        ) {
            erledigt += SchrittTyp.ROHSTOFF_AUSGABEN
        }

        val fremdgehalteneEigeneAnleihen = state.anleihen.values.any { anleihe ->
            anleihe.emittent == zug.spieler && state.anleiheBesitzer(anleihe.id) != null &&
                state.anleiheBesitzer(anleihe.id) != de.teutonstudio.zentralbank.domain.KontoId.Spieler(zug.spieler)
        }
        if (!fremdgehalteneEigeneAnleihen) {
            erledigt += SchrittTyp.FINANZ_AUSGABEN
        }

        return erledigt
    }

    private fun GameState.anleiheBesitzer(
        anleihe: de.teutonstudio.zentralbank.domain.AnleiheId,
    ): de.teutonstudio.zentralbank.domain.KontoId? {
        if (anleihe in bankAnleihen) return de.teutonstudio.zentralbank.domain.KontoId.Bank
        return spieler.firstOrNull { anleihe in it.anleihen }
            ?.let { de.teutonstudio.zentralbank.domain.KontoId.Spieler(it.id) }
    }
}
