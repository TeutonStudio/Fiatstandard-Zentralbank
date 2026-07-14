package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.zug.Phase

data class ZugAnzeige(
    val text: String,
)

fun GameState.zuZugAnzeige(): ZugAnzeige {
    val kopf = "Runde $rundenzähler · Leitzins ${leitzins.zuProzentString()}"
    val zug = zugStatus ?: return ZugAnzeige(kopf)
    val spielerName = spieler.firstOrNull { it.id == zug.spieler }?.name ?: zug.spieler.wert
    val aktion = when (zug.phase) {
        Phase.Einnahmen -> "$spielerName: Einnahmen abschließen"
        Phase.Ausgaben -> "$spielerName: Ausgaben abschließen"
        Phase.Aktionen -> "Zug von $spielerName beenden"
    }
    return ZugAnzeige("$kopf\n$aktion")
}
