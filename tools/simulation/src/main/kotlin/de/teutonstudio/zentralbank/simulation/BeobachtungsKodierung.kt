package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

const val AKTUELLE_KODIERUNGS_VERSION = 1

@Serializable
data class ModellEingabe(
    val kodierungsVersion: Int = AKTUELLE_KODIERUNGS_VERSION,
    val globaleMerkmale: FloatArray,
    val spielerMerkmale: Array<FloatArray>,
    val feldMerkmale: Array<FloatArray>,
    val eckMerkmale: Array<FloatArray>,
    val kantenMerkmale: Array<FloatArray>,
    val aktionsMaske: BooleanArray,
)

/**
 * Feste Baseline-Kodierung: maximal vier Spieler, 256 Felder, 512 Ecken,
 * 768 Kanten und 512 deterministische Aktions-Hashplätze. Nicht belegte Zeilen
 * bleiben Null-Padding. Die Fachwerte bleiben ganzzahlig; nur diese Projektion
 * normalisiert Geld und Mengen in Float-Werte.
 */
object BeobachtungsKodierung {
    const val MAX_SPIELER = 4
    const val MAX_FELDER = 256
    const val MAX_ECKEN = 512
    const val MAX_KANTEN = 768
    const val AKTIONS_PLAETZE = 512

    fun kodiere(punkt: Entscheidungspunkt): ModellEingabe {
        val beobachtung = punkt.beobachtung
        val spieler = Array(MAX_SPIELER) { FloatArray(6) }
        spieler[0] = floatArrayOf(
            1f,
            normiereGeld(beobachtung.eigeneWirtschaft.geld.cent),
            normiereAnzahl(beobachtung.eigeneWirtschaft.rohstoffe.sumOf { it.menge }, 100),
            normiereAnzahl(beobachtung.eigeneWirtschaft.bauteile.sumOf { it.menge }, 100),
            normiereAnzahl(beobachtung.eigeneWirtschaft.anleihen.size, 50),
            if (beobachtung.eigeneWirtschaft.ausgeschieden) 1f else 0f,
        )
        beobachtung.gegner.take(MAX_SPIELER - 1).forEachIndexed { index, gegner ->
            spieler[index + 1] = floatArrayOf(
                0f,
                0f,
                0f,
                normiereAnzahl(gegner.oeffentlicheBauwerke, 100),
                normiereAnzahl(gegner.emittierteAnleihen, 50),
                if (gegner.ausgeschieden) 1f else 0f,
            )
        }
        val felder = Array(MAX_FELDER) { FloatArray(4) }
        beobachtung.karte?.gelaendefelder?.take(MAX_FELDER)?.forEachIndexed { index, feld ->
            felder[index] = floatArrayOf(
                feld.position.zeile.coerceIn(-100, 100) / 100f,
                feld.position.spalte.coerceIn(-100, 100) / 100f,
                if (feld.position.haelfte.name == "OBEN") 1f else -1f,
                feld.gelaende.ordinal.toFloat() /
                    de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp.entries.size,
            )
        }
        val ecken = Array(MAX_ECKEN) { FloatArray(5) }
        beobachtung.karte?.eckBauwerke?.take(MAX_ECKEN)?.forEachIndexed { index, ecke ->
            ecken[index] = floatArrayOf(
                ecke.position.x.coerceIn(-200, 200) / 200f,
                ecke.position.y.coerceIn(-200, 200) / 200f,
                ecke.typ.ordinal.toFloat() /
                    de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.entries.size,
                if (ecke.besitzer == beobachtung.betrachtenderSpieler) 1f else 0f,
                ecke.zustand.ordinal.toFloat() /
                    de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand.entries.size,
            )
        }
        val kanten = Array(MAX_KANTEN) { FloatArray(6) }
        beobachtung.karte?.handelslinien?.take(MAX_KANTEN)?.forEachIndexed { index, kante ->
            kanten[index] = floatArrayOf(
                kante.position.anfang.x.coerceIn(-200, 200) / 200f,
                kante.position.anfang.y.coerceIn(-200, 200) / 200f,
                kante.position.ende.x.coerceIn(-200, 200) / 200f,
                kante.position.ende.y.coerceIn(-200, 200) / 200f,
                if (kante.erbautVon == beobachtung.betrachtenderSpieler) 1f else 0f,
                kante.zustand.ordinal.toFloat() /
                    de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand.entries.size,
            )
        }
        val maske = BooleanArray(AKTIONS_PLAETZE)
        punkt.aktionsRaum.aktionen.forEach { aktion ->
            val hash = AktionsAuswertung.aktionsSchluessel(aktion).hashCode().toLong().absoluteValue
            maske[(hash % AKTIONS_PLAETZE).toInt()] = true
        }
        return ModellEingabe(
            globaleMerkmale = floatArrayOf(
                beobachtung.runde.coerceAtMost(100) / 100f,
                beobachtung.markt.leitzinsBasispunkte.coerceIn(-2_000, 2_000) / 2_000f,
                normiereAnzahl(beobachtung.angebote.size, 100),
                if (beobachtung.ergebnis != null) 1f else 0f,
            ),
            spielerMerkmale = spieler,
            feldMerkmale = felder,
            eckMerkmale = ecken,
            kantenMerkmale = kanten,
            aktionsMaske = maske,
        )
    }

    private fun normiereGeld(cent: Long): Float = (cent / 100_000f).coerceIn(-1f, 1f)
    private fun normiereAnzahl(anzahl: Int, maximum: Int): Float =
        (anzahl.toFloat() / maximum).coerceIn(0f, 1f)
}
