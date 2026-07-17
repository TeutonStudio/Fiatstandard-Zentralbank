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
                    verbundeneSpieler(karte, schiene.position).forEach { spieler ->
                        staerken[spieler] = maxOf(staerken[spieler] ?: 0, 1)
                    }
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
        .flatMap { belegung ->
            val menge = ertrag(karte, belegung.position)[spieler] ?: return@flatMap emptyList()
            when (val anlage = belegung.anlage) {
                is FeldAnlage.Abbaueinheit -> mapOf(anlage.rohstoff to 1)
                is FeldAnlage.Wirtschaftsregion -> anlage.bauteil.ertrag
                FeldAnlage.Geschaeftsbank -> emptyMap()
            }.map { (rohstoff, ertrag) -> rohstoff to ertrag * menge }
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
    ): Set<KartenKante> = karte.belegung.kanten
        .asSequence()
        .filter { it.zustand == BauwerkZustand.INTAKT }
        .filter { spieler in verbundeneSpieler(karte, it.position) }
        .mapTo(mutableSetOf(), KantenBelegung::position)

    /**
     * Spieler, deren intakter Hauptbahnhof über die zusammenhängende Handelslinie erreichbar ist.
     * Die Linie bleibt fachlich neutral; diese Menge beschreibt Nutzung und aktuelle Gewalt.
     */
    fun verbundeneSpieler(
        karte: Spielkarte,
        kante: KartenKante,
    ): Set<SpielerId> {
        val komponente = schienenKomponente(karte, kante)
        if (komponente.isEmpty()) return emptySet()
        val ecken = komponente.flatMapTo(mutableSetOf()) { listOf(it.anfang, it.ende) }
        return karte.belegung.ecken
            .asSequence()
            .filter {
                it.typ == EckGebaeudeTyp.HAUPTBAHNHOF &&
                    it.zustand == BauwerkZustand.INTAKT &&
                    it.position in ecken
            }
            .mapNotNullTo(mutableSetOf()) { it.besitzer }
    }

    /** Nur eine ausschließlich mit einem Spieler verbundene Linie steht unter dessen Gewalt. */
    fun gewalthaber(
        karte: Spielkarte,
        kante: KartenKante,
    ): SpielerId? = verbundeneSpieler(karte, kante).singleOrNull()

    /** Bestimmt die Gewalt auch für eine zerstörte Linie anhand ihres Netzes im intakten Zustand. */
    fun gewalthaberBeiIntakterLinie(
        karte: Spielkarte,
        kante: KartenKante,
    ): SpielerId? {
        val eintrag = karte.belegung.kantenNachPosition[kante] ?: return null
        val pruefKarte = if (eintrag.zustand == BauwerkZustand.INTAKT) {
            karte
        } else {
            karte.copy(
                belegung = karte.belegung.copy(
                    kanten = karte.belegung.kanten.map {
                        if (it.position == kante) it.copy(zustand = BauwerkZustand.INTAKT) else it
                    },
                ),
            )
        }
        return gewalthaber(pruefKarte, kante)
    }

    private fun schienenKomponente(
        karte: Spielkarte,
        start: KartenKante,
    ): Set<KartenKante> {
        val schienen = karte.belegung.kanten.filter { it.zustand == BauwerkZustand.INTAKT }
        if (schienen.none { it.position == start }) return emptySet()
        val anEcke = buildMap<KartenEcke, MutableList<KantenBelegung>> {
            schienen.forEach { schiene ->
                getOrPut(schiene.position.anfang) { mutableListOf() }.add(schiene)
                getOrPut(schiene.position.ende) { mutableListOf() }.add(schiene)
            }
        }
        val besuchteEcken = mutableSetOf(start.anfang, start.ende)
        val verbundeneSchienen = mutableSetOf(start)
        val offen = ArrayDeque<KartenEcke>().apply {
            add(start.anfang)
            add(start.ende)
        }
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
