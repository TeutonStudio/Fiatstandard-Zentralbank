package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsArt
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten

data class VerarbeitungsStandort(
    val feld: KartenFeld,
    val typ: BauteilTyp,
    val maximaleLaeufe: Int,
    val einsatzJeLauf: Map<Rohstoff, Int>,
    val ertragJeLauf: Map<Rohstoff, Int>,
)

data class VerwaltungsStandort(
    val ecke: KartenEcke,
    val typ: EckGebaeudeTyp,
    val bedarf: Map<Rohstoff, Int>,
)

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

    fun abbauErtrag(
        karte: Spielkarte,
        spieler: SpielerId,
    ): Map<Rohstoff, Int> = karte.belegung.felder
        .flatMap { belegung ->
            val menge = ertrag(karte, belegung.position)[spieler] ?: return@flatMap emptyList()
            when (val anlage = belegung.anlage) {
                is FeldAnlage.Abbaueinheit -> mapOf(anlage.rohstoff to 1)
                is FeldAnlage.Wirtschaftsregion -> if (
                    anlage.bauteil.produktionsArt == ProduktionsArt.ABBAU
                ) {
                    anlage.bauteil.ertrag
                } else {
                    emptyMap()
                }
                FeldAnlage.Geschaeftsbank -> emptyMap()
            }.map { (rohstoff, ertrag) -> rohstoff to ertrag * menge }
        }
        .groupBy(Pair<Rohstoff, Int>::first, Pair<Rohstoff, Int>::second)
        .mapValues { (_, mengen) -> mengen.sum() }

    fun verarbeitungsStandorte(
        karte: Spielkarte,
        spieler: SpielerId,
    ): List<VerarbeitungsStandort> = karte.belegung.felder.mapNotNull { belegung ->
        val wirtschaft = belegung.anlage as? FeldAnlage.Wirtschaftsregion
            ?: return@mapNotNull null
        val typ = wirtschaft.bauteil
        if (typ.produktionsArt != ProduktionsArt.VERARBEITUNG) return@mapNotNull null
        val kapazitaet = ertrag(karte, belegung.position)[spieler] ?: return@mapNotNull null
        VerarbeitungsStandort(
            feld = belegung.position,
            typ = typ,
            maximaleLaeufe = kapazitaet,
            einsatzJeLauf = typ.verbrauch,
            ertragJeLauf = typ.ertrag,
        )
    }.sortedWith(
        compareBy<VerarbeitungsStandort> { it.feld.zeile }
            .thenBy { it.feld.spalte }
            .thenBy { it.feld.haelfte },
    )

    fun verwaltungsStandorte(
        karte: Spielkarte,
        spieler: SpielerId,
    ): List<VerwaltungsStandort> = karte.belegung.ecken
        .asSequence()
        .filter { belegung ->
            belegung.besitzer == spieler &&
                belegung.zustand == BauwerkZustand.INTAKT &&
                belegung.typ != EckGebaeudeTyp.HAUPTBAHNHOF
        }
        .map { belegung ->
            val bauteil = when (belegung.typ) {
                EckGebaeudeTyp.HAUPTBAHNHOF -> BauteilTyp.HAUPTBAHNHOF
                EckGebaeudeTyp.BAHNHOF -> BauteilTyp.BAHNHOF
                EckGebaeudeTyp.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
                EckGebaeudeTyp.HAFEN -> BauteilTyp.HAFEN
                EckGebaeudeTyp.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
            }
            VerwaltungsStandort(belegung.position, belegung.typ, bauteil.verbrauch)
        }
        .sortedBy { it.ecke }
        .toList()

    fun kannAussenhandelBetreiben(
        karte: Spielkarte,
        spieler: SpielerId,
    ): Boolean {
        val eigeneIntakteHaefen = karte.belegung.ecken
            .filter { belegung ->
                belegung.besitzer == spieler &&
                    belegung.zustand == BauwerkZustand.INTAKT &&
                    belegung.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN)
            }
            .mapTo(mutableSetOf()) { it.position }
        return karte.belegung.seewege.any { seeweg ->
            seeweg.besitzer == spieler &&
                (seeweg.hafenA in eigeneIntakteHaefen || seeweg.hafenB in eigeneIntakteHaefen)
        }
    }

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

    /** Spieler an den beiden Bauwerk-Endpunkten der Route, auf der [kante] liegt. */
    fun verbundeneSpieler(
        karte: Spielkarte,
        kante: KartenKante,
    ): Set<SpielerId> = routenEndpunkte(karte, kante)
        .let { endpunkte -> setOfNotNull(endpunkte.anfang, endpunkte.ende) }

    /** Nur eine zwischen zwei Bauwerken desselben Spielers verlaufende Route wird kontrolliert. */
    fun gewalthaber(
        karte: Spielkarte,
        kante: KartenKante,
    ): SpielerId? = routenEndpunkte(karte, kante).let { endpunkte ->
        endpunkte.anfang?.takeIf { spieler -> spieler == endpunkte.ende }
    }

    /**
     * Beide Besitzer einer verbindenden Route dürfen sie abbauen. Bei einer offenen Route ist
     * ausschließlich der Besitzer des einzigen Bauwerk-Endpunkts abrissberechtigt.
     */
    fun abrissberechtigteSpieler(
        karte: Spielkarte,
        kante: KartenKante,
    ): Set<SpielerId> = verbundeneSpieler(karte, kante)

    private fun routenEndpunkte(
        karte: Spielkarte,
        start: KartenKante,
    ): RoutenEndpunkte {
        if (start !in karte.belegung.kantenNachPosition) return RoutenEndpunkte(null, null)
        val anEcke = buildMap<KartenEcke, MutableList<KartenKante>> {
            karte.belegung.kanten.forEach { schiene ->
                getOrPut(schiene.position.anfang) { mutableListOf() }.add(schiene.position)
                getOrPut(schiene.position.ende) { mutableListOf() }.add(schiene.position)
            }
        }
        return RoutenEndpunkte(
            anfang = routenEndpunktBesitzer(karte, start, start.anfang, anEcke),
            ende = routenEndpunktBesitzer(karte, start, start.ende, anEcke),
        )
    }

    private fun routenEndpunktBesitzer(
        karte: Spielkarte,
        start: KartenKante,
        ersteEcke: KartenEcke,
        anEcke: Map<KartenEcke, List<KartenKante>>,
    ): SpielerId? {
        var aktuelleEcke = ersteEcke
        var vorherigeKante = start
        val besuchteKanten = mutableSetOf(start)
        while (true) {
            karte.belegung.eckenNachPosition[aktuelleEcke]
                ?.takeIf { bauwerk -> bauwerk.zustand != BauwerkZustand.ZERSTOERT }
                ?.besitzer
                ?.let { return it }
            val fortsetzungen = anEcke[aktuelleEcke]
                .orEmpty()
                .filterNot { kante -> kante == vorherigeKante }
            if (fortsetzungen.size != 1) return null
            val naechsteKante = fortsetzungen.single()
            if (!besuchteKanten.add(naechsteKante)) return null
            aktuelleEcke = if (naechsteKante.anfang == aktuelleEcke) {
                naechsteKante.ende
            } else {
                naechsteKante.anfang
            }
            vorherigeKante = naechsteKante
        }
    }

    private data class RoutenEndpunkte(
        val anfang: SpielerId?,
        val ende: SpielerId?,
    )
}
