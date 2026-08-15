package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilArt
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsArt
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.istTeichfeld
import kotlin.math.abs

/** Kartenabhängige, reproduzierbare Ausgangspunkte für den frei bearbeitbaren Warenkorb. */
enum class WarenkorbPreset(
    val produktionsArt: ProduktionsArt,
) {
    UNVERARBEITETE_ROHSTOFFE(ProduktionsArt.ABBAU),
    VERARBEITETE_ROHSTOFFE(ProduktionsArt.VERARBEITUNG),
}

object WarenkorbAuswertung {
    /**
     * Gewichtet Rohstoffe nach der Zahl unterschiedlicher Kartenfelder, auf denen mindestens
     * ein Produzent dieses Rohstoffs gebaut werden kann. Die absoluten Feldzahlen werden auf
     * ihr kleinstes ganzzahliges Verhältnis gekürzt.
     *
     * Die Förder-/Produktionsmenge eines Bauwerks ist absichtlich kein Faktor: Ein Feld zählt
     * genau einmal, auch wenn dort mehrere Produzenten desselben Rohstoffs möglich wären.
     */
    fun vordefinierterWarenkorb(
        karte: Spielkarte,
        preset: WarenkorbPreset,
    ): Map<Rohstoff, Int> {
        val produzenten = BauteilTyp.entries.filter { bauteil ->
            bauteil.art == BauteilArt.WIRTSCHAFTSREGION &&
                bauteil.produktionsArt == preset.produktionsArt &&
                bauteil.ertrag.isNotEmpty()
        }
        val felderNachRohstoff = linkedMapOf<Rohstoff, MutableSet<KartenFeld>>()

        produzenten.forEach { produzent ->
            val moeglicheFelder = karte.gelaendefelder
                .asSequence()
                .map { feld -> feld.position }
                .filter { feld -> istProduktionsstandort(karte, feld, produzent) }
                .toSet()
            produzent.ertrag.keys.forEach { rohstoff ->
                felderNachRohstoff.getOrPut(rohstoff) { linkedSetOf() }.addAll(moeglicheFelder)
            }
        }

        val feldzahlen = Rohstoff.entries.mapNotNull { rohstoff ->
            val anzahl = felderNachRohstoff[rohstoff]?.size ?: 0
            if (anzahl > 0) rohstoff to anzahl else null
        }
        if (feldzahlen.isEmpty()) return emptyMap()

        val teiler = feldzahlen
            .map(Pair<Rohstoff, Int>::second)
            .reduce(::ggT)
            .coerceAtLeast(1)
        return linkedMapOf<Rohstoff, Int>().apply {
            feldzahlen.forEach { (rohstoff, anzahl) -> this[rohstoff] = anzahl / teiler }
        }
    }

    private fun istProduktionsstandort(
        karte: Spielkarte,
        feld: KartenFeld,
        produzent: BauteilTyp,
    ): Boolean {
        if (feld !in karte.landNachPosition) return false
        val istTeich = karte.istTeichfeld(feld)
        return when (produzent) {
            BauteilTyp.ANGLER -> istTeich
            BauteilTyp.FOERSTER -> !istTeich && karte.landNachPosition[feld] == GelaendeTyp.WALD
            BauteilTyp.VIEHHOF -> !istTeich && karte.landNachPosition[feld] == GelaendeTyp.EBENE
            else -> !istTeich
        }
    }

    private tailrec fun ggT(a: Int, b: Int): Int {
        val links = abs(a)
        val rechts = abs(b)
        return if (rechts == 0) links else ggT(rechts, links % rechts)
    }
}
