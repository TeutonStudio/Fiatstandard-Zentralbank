package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.*
import de.teutonstudio.zentralbank.fachlogik.modell.*
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
    val version: Int = 2,
    val beta: Float = 0.02f,
    val gamma: Float = 0.99f,
    val liquiditaet: Float = 0.10f,
    val marktwert: Float = 0.20f,
    val produktion: Float = 0.14f,
    val versorgung: Float = 0.10f,
    val infrastruktur: Float = 0.12f,
    val schuldendienst: Float = 0.10f,
    val handelswege: Float = 0.08f,
    val militaer: Float = 0.06f,
    val blockaden: Float = 0.05f,
    val belagerung: Float = 0.05f,
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
            ergebnis.gewinner == spieler.id -> 1f
            else -> -1f
        }
        val potentialVorher = relativesPotential(vorher, spieler.id)
        val potentialNachher = relativesPotential(nachher, spieler.id)
        var shaping = gewichte.beta * (gewichte.gamma * potentialNachher - potentialVorher)
        if (aktion is SpielAktion.AnleiheEmittieren || aktion is SpielAktion.AnleiheAufstocken) {
            val strategischVorher = produktiverZustand(vorher, spieler.id)
            val strategischNachher = produktiverZustand(nachher, spieler.id)
            val schuldendienstVorher = tragfaehigerSchuldendienst(vorher, spieler.id)
            val schuldendienstNachher = tragfaehigerSchuldendienst(nachher, spieler.id)
            if (
                strategischNachher <= strategischVorher &&
                schuldendienstNachher <= schuldendienstVorher
            ) shaping = minOf(0f, shaping)
        }
        if (nachher.zentralbankGeldschoepfungen.size > vorher.zentralbankGeldschoepfungen.size) {
            shaping = minOf(0f, shaping)
        }
        if (aktion is SpielAktion.SchuldenstrichDurchfuehren) shaping = minOf(0f, shaping)
        if (aktion is SpielAktion.KriegKapitulieren && !kapitulationBegruendet(vorher, spieler.id)) {
            shaping -= 0.01f
        }
        spieler.id to terminal + shaping
    }

    internal fun relativesPotential(zustand: SpielZustand, spieler: SpielerId): Float {
        val eigen = absolutesPotential(zustand, spieler)
        val gegner = zustand.spieler.map { it.id }
            .filter { it != spieler && it !in zustand.ausgeschiedeneSpieler }
            .map { absolutesPotential(zustand, it) }
        return eigen - (gegner.average().takeUnless(Double::isNaN)?.toFloat() ?: 0f)
    }

    private fun absolutesPotential(zustand: SpielZustand, spielerId: SpielerId): Float {
        val spieler = zustand.spieler.single { it.id == spielerId }
        val karte = zustand.karte
        val schulden = zustand.anleihen.values.filter { it.emittent == spielerId }
            .sumOf { it.nennwert.cent }
        val liquiditaet = ((spieler.geldkonto.cent - schulden) / 100_000f).coerceIn(-5f, 5f)
        val marktwert = (MarktAuswertung.spielerMarktwert(zustand, spielerId).cent / 100_000f)
            .coerceIn(0f, 10f)
        val produktion = karte?.let {
            KartenAuswertung.abbauErtrag(it, spielerId, zustand.konflikte).values.sum() / 30f
        } ?: 0f
        val versorgung = if (zustand.aktiverSpieler == spielerId &&
            zustand.zugStatus?.phase == ZugPhase.Prozug
        ) {
            val plan = ZahlungsfaehigkeitsAuswertung.plan(zustand, spielerId)
            if (plan.direktVersorgbar || plan.nachMarkthandelVersorgbar) 1f else 0f
        } else 1f
        val erreichbareFelder = karte?.let {
            ErreichbarkeitsAuswertung.erreichbareWirtschaftsstandorte(
                it,
                spielerId,
                zustand.konflikte,
            ).size
        } ?: 0
        val verwaltung = karte?.belegung?.ecken?.count {
            it.besitzer == spielerId && it.zustand == BauwerkZustand.INTAKT
        } ?: 0
        val infrastruktur = ((verwaltung + erreichbareFelder) / 30f).coerceIn(0f, 2f)
        val schuldendienst = tragfaehigerSchuldendienst(zustand, spielerId)
        val handelswege = karte?.let {
            val land = it.belegung.kanten.count { kante ->
                KartenAuswertung.gewalthaber(it, kante.position) == spielerId
            }
            val see = it.belegung.seewege.count { seeweg -> seeweg.besitzer == spielerId }
            (land + see) / 30f
        } ?: 0f
        val militaer = karte?.belegung?.kriegseinheiten?.count { it.besitzer == spielerId }
            ?.div(20f) ?: 0f
        val blockaden = karte?.belegung?.kriegseinheiten?.count { einheit ->
            einheit.besitzer == spielerId && zustand.konflikte.any { krieg ->
                krieg.teilnehmer.any { gegner -> gegner != spielerId && krieg.betrifft(spielerId, gegner) }
            }
        }?.div(20f) ?: 0f
        val belagerung = zustand.belagerungen.sumOf { belagerung ->
            when {
                belagerung.fuehrenderBelagerer == spielerId -> belagerung.fortschrittRunden
                belagerung.verteidiger == spielerId -> -belagerung.fortschrittRunden
                else -> 0
            }
        } / 10f
        return gewichte.liquiditaet * liquiditaet +
            gewichte.marktwert * marktwert +
            gewichte.produktion * produktion.coerceIn(0f, 2f) +
            gewichte.versorgung * versorgung +
            gewichte.infrastruktur * infrastruktur +
            gewichte.schuldendienst * schuldendienst +
            gewichte.handelswege * handelswege.coerceIn(0f, 2f) +
            gewichte.militaer * militaer.coerceIn(0f, 2f) +
            gewichte.blockaden * blockaden.coerceIn(0f, 2f) +
            gewichte.belagerung * belagerung.coerceIn(-2f, 2f)
    }

    private fun produktiverZustand(zustand: SpielZustand, spieler: SpielerId): Float {
        val karte = zustand.karte ?: return 0f
        val produktion = KartenAuswertung.abbauErtrag(karte, spieler, zustand.konflikte)
            .values.sum()
        val infrastruktur = karte.belegung.ecken.count { it.besitzer == spieler } +
            ErreichbarkeitsAuswertung.erreichbareWirtschaftsstandorte(
                karte,
                spieler,
                zustand.konflikte,
            ).size
        return produktion + infrastruktur / 10f
    }

    private fun tragfaehigerSchuldendienst(zustand: SpielZustand, spieler: SpielerId): Float {
        val faellig = AnleihenAuswertung.faelligeVerbindlichkeiten(
            zustand,
            spieler,
            zustand.zugStatus?.zugId ?: 0L,
        ).sumOf { it.betrag.cent }
        if (faellig <= 0L) return 1f
        val geld = zustand.spieler.single { it.id == spieler }.geldkonto.cent
        return (geld.toFloat() / faellig).coerceIn(0f, 1f)
    }

    private fun kapitulationBegruendet(zustand: SpielZustand, spieler: SpielerId): Boolean {
        val eigene = zustand.karte?.belegung?.kriegseinheiten?.count { it.besitzer == spieler } ?: 0
        val gegner = zustand.karte?.belegung?.kriegseinheiten?.count { einheit ->
            einheit.besitzer != spieler && zustand.konflikte.any { it.betrifft(spieler, einheit.besitzer) }
        } ?: 0
        val zahlung = if (zustand.aktiverSpieler == spieler &&
            zustand.zugStatus?.phase == ZugPhase.Prozug
        ) ZahlungsfaehigkeitsAuswertung.plan(zustand, spieler).kapitulationNoetig else false
        return gegner > eigene || zahlung
    }
}
