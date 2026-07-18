package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.auswertung.ProzugAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase

data class ZugAnzeige(
    val text: String,
)

fun SpielZustand.zuZugAnzeige(): ZugAnzeige {
    val kopf = "Runde $rundenzähler · Leitzins ${leitzins.zuProzentString()}"
    val zug = zugStatus ?: return ZugAnzeige(kopf)
    val spielerName = spieler.firstOrNull { it.id == zug.spieler }?.name ?: zug.spieler.wert
    val aktion = when (zug.phase) {
        ZugPhase.Prozug -> {
            val plan = ProzugAuswertung.plan(this)
            val offen = plan?.let {
                it.verwaltungsVerpflichtungen.count { post -> post.id !in zug.prozug.versorgteStandorte } +
                    it.verbindlichkeiten.count { post -> post.id !in zug.prozug.beglicheneVerbindlichkeiten }
            } ?: 0
            "$spielerName: Prozug · $offen Pflichtposten offen"
        }
        ZugPhase.Epizug -> "Epizug von $spielerName · Spielzug beenden"
    }
    return ZugAnzeige("$kopf\n$aktion")
}
