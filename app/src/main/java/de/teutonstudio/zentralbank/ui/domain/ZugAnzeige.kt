package de.teutonstudio.zentralbank.ui.domain

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.zug.Phase

data class ZugAnzeige(
    val text: String,
)

fun GameState.zuZugAnzeige(): ZugAnzeige {
    val zug = zugStatus ?: return ZugAnzeige("nächste Runde")
    val spielerName = spieler.firstOrNull { it.id == zug.spieler }?.name ?: zug.spieler.wert
    val phase = when (zug.phase) {
        Phase.Einnahmen -> "Einnahmen"
        Phase.Ausgaben -> "Ausgaben"
        Phase.Aktionen -> "Aktionen"
    }
    return ZugAnzeige("$spielerName: $phase")
}
