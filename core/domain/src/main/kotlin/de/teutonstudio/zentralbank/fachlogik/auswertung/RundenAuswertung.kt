package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Rundenwerte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import kotlin.math.abs

object RundenAuswertung {
    fun naechsteRundenwerte(zustand: SpielZustand): Rundenwerte {
        val neuePreise = Rohstoff.entries.associateWith { rohstoff ->
            val beobachtungen = zustand.marktpreisBeobachtungen[rohstoff].orEmpty()
            if (beobachtungen.isEmpty()) {
                zustand.marktpreise[rohstoff] ?: Geld.NULL
            } else {
                Geld.cent(beobachtungen.sumOf(Geld::cent) / beobachtungen.size)
            }
        }
        val inflation = warenkorbInflation(zustand, neuePreise)
        val leitzinsAenderung = inflation?.let { preisinflation ->
            val abweichung = preisinflation.wert - zustand.geldpolitik.inflationsziel.wert
            val faktor = when {
                abs(abweichung) < zustand.geldpolitik.normaleAbweichung.wert -> 0
                abs(abweichung) < zustand.geldpolitik.starkeAbweichung.wert ->
                    if (abweichung < 0) -1 else 1
                else -> if (abweichung < 0) -2 else 2
            }
            zustand.geldpolitik.leitzinsSchritt.wert * faktor
        } ?: 0
        return Rundenwerte(
            runde = zustand.rundenzähler,
            marktpreise = neuePreise,
            leitzins = Basispunkte(zustand.leitzins.wert + leitzinsAenderung),
            preisinflation = inflation,
        )
    }

    private fun warenkorbInflation(
        zustand: SpielZustand,
        neuePreise: Map<Rohstoff, Geld>,
    ): Basispunkte? {
        if (zustand.rundenzähler <= 1) return null
        val vorher = warenkorbPreis(zustand.warenkorb, zustand.marktpreise)
        if (vorher.cent == 0L) return null
        val nachher = warenkorbPreis(zustand.warenkorb, neuePreise)
        val basispunkte = Math.multiplyExact(nachher.cent - vorher.cent, 10_000L) / vorher.cent
        return Basispunkte(basispunkte.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
    }

    private fun warenkorbPreis(
        warenkorb: Map<Rohstoff, Int>,
        preise: Map<Rohstoff, Geld>,
    ): Geld = warenkorb.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
        summe + (preise[rohstoff] ?: Geld.NULL) * menge
    }
}
