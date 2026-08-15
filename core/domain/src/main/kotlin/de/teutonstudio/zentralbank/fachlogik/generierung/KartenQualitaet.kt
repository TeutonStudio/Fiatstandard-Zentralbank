package de.teutonstudio.zentralbank.fachlogik.generierung

import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.VorkommensArt
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import java.util.ArrayDeque
import kotlin.math.roundToInt

data class KartenQualitaet(
    val punkte: Int,
    val spielbar: Boolean,
    val hinweise: List<String> = emptyList(),
)

/**
 * Bewertet eine fertige Karte unabhängig von ihrer Herkunft. Dadurch kann dieselbe Prüfung
 * für generierte und im Kartenbauer erstellte Karten verwendet werden.
 */
object KartenQualitaetsPruefung {
    fun pruefe(vorlage: KartenVorlage): KartenQualitaet {
        val hinweise = mutableListOf<String>()
        val land = vorlage.landNachPosition.keys
        if (land.isEmpty()) {
            return KartenQualitaet(
                punkte = 0,
                spielbar = false,
                hinweise = listOf("Die Karte besitzt kein Land."),
            )
        }

        var punkte = 0
        GelaendeTyp.entries.forEach { typ ->
            if (vorlage.gelaendefelder.any { feld -> feld.gelaende == typ }) {
                punkte += 4
            } else {
                hinweise += "Gelände ${typ.name.lowercase()} fehlt."
            }
        }

        VorkommensArt.entries.forEach { art ->
            if (vorlage.vorkommen.any { vorkommen -> vorkommen.art == art }) {
                punkte += 10
            } else {
                hinweise += "${art.anzeigeName}-Vorkommen fehlt."
            }
        }

        val gesamtFelder = vorlage.hexagon.anzahlFelder.coerceAtLeast(1L)
        val landAnteil = land.size.toDouble() / gesamtFelder.toDouble()
        punkte += when {
            landAnteil in 0.45..0.90 -> 15
            landAnteil in 0.30..0.95 -> 10
            else -> 4
        }
        if (landAnteil !in 0.30..0.95) {
            hinweise += "Der Landanteil ist mit ${(landAnteil * 100).roundToInt()} % extrem."
        }

        val groessteKomponente = groessteLandKomponente(land)
        val verbindungsAnteil = groessteKomponente.toDouble() / land.size.toDouble()
        punkte += (verbindungsAnteil * 25.0).roundToInt().coerceIn(0, 25)
        if (verbindungsAnteil < 0.55) {
            hinweise += "Weniger als 55 % der Landfläche bilden die größte zusammenhängende Landmasse."
        }

        val hatEbene = vorlage.gelaendefelder.any { it.gelaende == GelaendeTyp.EBENE }
        val hatWald = vorlage.gelaendefelder.any { it.gelaende == GelaendeTyp.WALD }
        val hatGebirge = vorlage.gelaendefelder.any { it.gelaende == GelaendeTyp.GEBIRGE }
        val alleVorkommen = VorkommensArt.entries.all { art ->
            vorlage.vorkommen.any { eintrag -> eintrag.art == art }
        }
        val spielbar = hatEbene && hatWald && hatGebirge && alleVorkommen

        return KartenQualitaet(
            punkte = punkte.coerceIn(0, 100),
            spielbar = spielbar,
            hinweise = hinweise,
        )
    }

    private fun groessteLandKomponente(land: Set<KartenFeld>): Int {
        val offen = land.toMutableSet()
        var groesste = 0
        while (offen.isNotEmpty()) {
            val start = offen.first()
            val schlange = ArrayDeque<KartenFeld>()
            schlange.add(start)
            offen.remove(start)
            var groesse = 0
            while (schlange.isNotEmpty()) {
                val aktuell = schlange.removeFirst()
                groesse++
                feldNachbarn(aktuell)
                    .filter { nachbar -> nachbar in offen }
                    .forEach { nachbar ->
                        offen.remove(nachbar)
                        schlange.add(nachbar)
                    }
            }
            groesste = maxOf(groesste, groesse)
        }
        return groesste
    }
}

internal fun feldNachbarn(feld: KartenFeld): List<KartenFeld> = feld.kanten()
    .asSequence()
    .flatMap { kante -> angrenzendeFelder(kante).asSequence() }
    .filterNot { nachbar -> nachbar == feld }
    .distinct()
    .sortedWith(
        compareBy<KartenFeld>(KartenFeld::zeile)
            .thenBy(KartenFeld::spalte)
            .thenBy { eintrag -> eintrag.haelfte.ordinal },
    )
    .toList()
