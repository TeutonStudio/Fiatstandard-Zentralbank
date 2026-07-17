package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import java.util.ArrayDeque

object KartenAuswertung {
    fun istHafenstandort(karte: Spielkarte, ecke: KartenEcke): Boolean {
        val nachbarn = angrenzendeFelder(ecke)
        val land = nachbarn.count { it in karte.landNachPosition }
        val wasser = nachbarn.size - land
        return land >= 2 && wasser >= 2
    }

    fun anschlussStaerke(
        karte: Spielkarte,
        feld: KartenFeld,
    ): Map<SpielerId, Int> {
        if (feld !in karte.landNachPosition) return emptyMap()
        val staerken = mutableMapOf<SpielerId, Int>()
        feld.kanten().forEach { kante ->
            karte.belegung.kantenNachPosition[kante]
                ?.takeIf { it.zustand == BauwerkZustand.INTAKT }
                ?.let { schiene ->
                    staerken[schiene.besitzer] = maxOf(staerken[schiene.besitzer] ?: 0, 1)
                }
        }
        feld.ecken().forEach { ecke ->
            karte.belegung.eckenNachPosition[ecke]
                ?.takeIf { it.zustand == BauwerkZustand.INTAKT }
                ?.let { gebaeude ->
                    val besitzer = gebaeude.besitzer ?: return@let
                    val staerke = when (gebaeude.typ) {
                        EckGebaeudeTyp.GROSSBAHNHOF -> 3
                        EckGebaeudeTyp.BAHNHOF -> 2
                        EckGebaeudeTyp.HAUPTBAHNHOF -> 0
                        EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN -> 0
                    }
                    staerken[besitzer] = maxOf(staerken[besitzer] ?: 0, staerke)
                }
        }
        return staerken.filterValues { it > 0 }
    }

    fun effektiverZustand(karte: Spielkarte, belegung: FeldBelegung): AnlagenZustand = when {
        belegung.zustand == AnlagenZustand.ZERSTOERT -> AnlagenZustand.ZERSTOERT
        anschlussStaerke(karte, belegung.position).isEmpty() -> AnlagenZustand.VERLASSEN
        else -> AnlagenZustand.AKTIV
    }

    fun ertrag(karte: Spielkarte, feld: KartenFeld): Map<SpielerId, Int> {
        val belegung = karte.belegung.felderNachPosition[feld] ?: return emptyMap()
        return if (effektiverZustand(karte, belegung) == AnlagenZustand.AKTIV) {
            anschlussStaerke(karte, feld)
        } else {
            emptyMap()
        }
    }

    fun rohstoffErtrag(
        karte: Spielkarte,
        spieler: SpielerId,
    ): Map<Rohstoff, Int> = karte.belegung.felder
        .mapNotNull { belegung ->
            val anlage = belegung.anlage as? FeldAnlage.Abbaueinheit ?: return@mapNotNull null
            val menge = ertrag(karte, belegung.position)[spieler] ?: return@mapNotNull null
            anlage.rohstoff to menge
        }
        .groupBy(Pair<Rohstoff, Int>::first, Pair<Rohstoff, Int>::second)
        .mapValues { (_, mengen) -> mengen.sum() }

    fun kontrolliertGeschaeftsbank(
        karte: Spielkarte,
        feld: KartenFeld,
        spieler: SpielerId,
    ): Boolean {
        val belegung = karte.belegung.felderNachPosition[feld] ?: return false
        return belegung.anlage == FeldAnlage.Geschaeftsbank &&
            effektiverZustand(karte, belegung) == AnlagenZustand.AKTIV &&
            spieler in anschlussStaerke(karte, feld)
    }

    fun mitHauptbahnhofVerbundeneSchienen(
        karte: Spielkarte,
        spieler: SpielerId,
    ): Set<KartenKante> {
        val startEcken = karte.belegung.ecken
            .filter {
                it.typ == EckGebaeudeTyp.HAUPTBAHNHOF &&
                    it.besitzer == spieler &&
                    it.zustand == BauwerkZustand.INTAKT
            }
            .mapTo(mutableSetOf()) { it.position }
        if (startEcken.isEmpty()) return emptySet()

        val schienen = karte.belegung.kanten.filter {
            it.besitzer == spieler && it.zustand == BauwerkZustand.INTAKT
        }
        val anEcke = buildMap<KartenEcke, MutableList<KantenBelegung>> {
            schienen.forEach { schiene ->
                getOrPut(schiene.position.anfang) { mutableListOf() }.add(schiene)
                getOrPut(schiene.position.ende) { mutableListOf() }.add(schiene)
            }
        }
        val besuchteEcken = startEcken.toMutableSet()
        val verbundeneSchienen = mutableSetOf<KartenKante>()
        val offen = ArrayDeque<KartenEcke>().apply { addAll(startEcken) }
        while (offen.isNotEmpty()) {
            val ecke = offen.removeFirst()
            anEcke[ecke].orEmpty().forEach { schiene ->
                verbundeneSchienen += schiene.position
                val andere = if (schiene.position.anfang == ecke) {
                    schiene.position.ende
                } else {
                    schiene.position.anfang
                }
                if (besuchteEcken.add(andere)) offen.add(andere)
            }
        }
        return verbundeneSchienen
    }
}
