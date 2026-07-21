package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsArt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielErgebnis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.serialization.Serializable

interface BelohnungsModell {
    fun berechne(
        vorher: SpielZustand,
        nachher: SpielZustand,
        aktion: SpielAktion,
        ergebnis: SpielErgebnis?,
    ): Map<SpielerId, Float>
}

@Serializable
data class PotentialGewichte(
    val version: Int = 1,
    val beta: Float = 0.02f,
    val gamma: Float = 0.99f,
    val liquiditaet: Float = 0.35f,
    val nettovermoegen: Float = 0.20f,
    val produktiveKapazitaet: Float = 0.20f,
    val aktiveInfrastruktur: Float = 0.15f,
    val schuldendienstfaehigkeit: Float = 0.10f,
)

class PotentialBelohnungsModell(
    private val gewichte: PotentialGewichte = PotentialGewichte(),
) : BelohnungsModell {
    override fun berechne(
        vorher: SpielZustand,
        nachher: SpielZustand,
        aktion: SpielAktion,
        ergebnis: SpielErgebnis?,
    ): Map<SpielerId, Float> = vorher.spieler.associate { spieler ->
        val terminal = when {
            ergebnis == null -> 0f
            ergebnis.gewinner == null -> 0f
            ergebnis.gewinner == spieler.id -> 1f
            else -> -1f
        }
        val potentialVorher = potential(vorher, spieler.id)
        val potentialNachher = potential(nachher, spieler.id)
        spieler.id to (
            terminal + gewichte.beta *
                (gewichte.gamma * potentialNachher - potentialVorher)
            )
    }

    private fun potential(zustand: SpielZustand, spielerId: SpielerId): Float {
        val spieler = zustand.spieler.first { it.id == spielerId }
        val schulden = zustand.anleihen.values
            .filter { it.emittent == spielerId }
            .sumOf { it.nennwert.cent }
        val liquiditaet = spieler.geldkonto.cent / 100_000f
        val nettovermoegen = (spieler.geldkonto.cent - schulden) / 100_000f
        val produktiv = spieler.bauteile.entries.sumOf { (typ, anzahl) ->
            if (typ.produktionsArt != ProduktionsArt.KEINE) anzahl else 0
        }.toFloat() / 20f
        val aktiv = zustand.karte?.let { karte ->
            val ecken = karte.belegung.ecken.count {
                it.besitzer == spielerId && it.zustand == BauwerkZustand.INTAKT
            }
            val felder = karte.belegung.felder.count { it.zustand == AnlagenZustand.AKTIV }
            (ecken + felder).toFloat() / 50f
        } ?: 0f
        val faelligeSchuld = AnleihenAuswertung.faelligeVerbindlichkeiten(
            zustand,
            spielerId,
            zustand.zugStatus?.zugId ?: 0L,
        ).sumOf { it.betrag.cent }
        val schuldendienst = if (faelligeSchuld <= 0L) 1f else {
            (spieler.geldkonto.cent.toFloat() / faelligeSchuld.toFloat()).coerceIn(0f, 1f)
        }
        return gewichte.liquiditaet * liquiditaet.coerceIn(-1f, 1f) +
            gewichte.nettovermoegen * nettovermoegen.coerceIn(-1f, 1f) +
            gewichte.produktiveKapazitaet * produktiv.coerceIn(0f, 1f) +
            gewichte.aktiveInfrastruktur * aktiv.coerceIn(0f, 1f) +
            gewichte.schuldendienstfaehigkeit * schuldendienst
    }
}
