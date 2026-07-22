package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.ecken

/** Eine zentrale, deterministische Graphsicht für Kontrolle, Versorgung und Konflikt. */
object ErreichbarkeitsAuswertung {
    fun erreichbareEcken(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
    ): Set<KartenEcke> {
        val hauptbahnhof = karte.belegung.ecken.firstOrNull {
            it.besitzer == spieler && it.typ == EckGebaeudeTyp.HAUPTBAHNHOF &&
                it.zustand != BauwerkZustand.ZERSTOERT
        }?.position ?: return emptySet()
        val blockiert = blockierteLandkanten(karte, spieler, konflikte)
        val graph = mutableMapOf<KartenEcke, MutableSet<KartenEcke>>()
        fun verbinden(a: KartenEcke, b: KartenEcke) {
            graph.getOrPut(a) { mutableSetOf() }.add(b)
            graph.getOrPut(b) { mutableSetOf() }.add(a)
        }
        karte.belegung.kanten.asSequence()
            .filter { it.zustand == BauwerkZustand.INTAKT && it.position !in blockiert }
            .sortedWith(compareBy({ it.position.anfang }, { it.position.ende }))
            .forEach { verbinden(it.position.anfang, it.position.ende) }
        val blockierteHaefen = blockierteHaefen(karte, spieler, konflikte)
        val eigeneIntakteHaefen = karte.belegung.ecken.asSequence()
            .filter {
                it.besitzer == spieler && it.zustand == BauwerkZustand.INTAKT &&
                    it.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN)
            }
            .mapTo(mutableSetOf()) { it.position }
        karte.belegung.seewege.asSequence()
            .filter { it.besitzer == spieler }
            .filter { it.hafenA in eigeneIntakteHaefen && it.hafenB in eigeneIntakteHaefen }
            .filter { it.hafenA !in blockierteHaefen && it.hafenB !in blockierteHaefen }
            .sortedBy { it.id }
            .forEach { verbinden(it.hafenA, it.hafenB) }
        val besucht = linkedSetOf(hauptbahnhof)
        val offen = ArrayDeque<KartenEcke>()
        offen.add(hauptbahnhof)
        while (offen.isNotEmpty()) {
            val aktuell = offen.removeFirst()
            graph[aktuell].orEmpty().sorted().forEach { ziel ->
                if (besucht.add(ziel)) offen.add(ziel)
            }
        }
        return besucht
    }

    fun erreichbareWirtschaftsstandorte(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
    ): Set<KartenFeld> {
        val ecken = erreichbareEcken(karte, spieler, konflikte)
        return karte.belegung.felder.asSequence()
            .filter { feld -> feld.position.ecken().any { it in ecken } }
            .mapTo(linkedSetOf()) { it.position }
    }

    fun kontrollierendeSpieler(
        karte: Spielkarte,
        feld: KartenFeld,
        spieler: Iterable<SpielerId>,
        konflikte: Set<Konflikt>,
    ): Set<SpielerId> = spieler.filterTo(linkedSetOf()) {
        feld in erreichbareWirtschaftsstandorte(karte, it, konflikte)
    }

    fun istErreichbar(
        karte: Spielkarte,
        ecke: KartenEcke,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
    ): Boolean = ecke in erreichbareEcken(karte, spieler, konflikte)

    fun blockierendePanzerNachSpieler(
        karte: Spielkarte,
        standort: KartenEcke,
        verteidiger: SpielerId,
        konflikte: Set<Konflikt>,
    ): Map<SpielerId, Int> {
        val linien = karte.belegung.kanten.filter {
            it.position.anfang == standort || it.position.ende == standort
        }
        if (linien.isEmpty()) return emptyMap()
        val jeLinie = linien.map { linie ->
            karte.belegung.kriegseinheiten.filter { einheit ->
                einheit.typ == KriegsEinheitTyp.PANZER &&
                    einheit.position == linie.position &&
                    einheit.besitzer != verteidiger &&
                    konflikte.any { it.betrifft(verteidiger, einheit.besitzer) &&
                        !it.hatWaffenstillstand(verteidiger, einheit.besitzer) }
            }
        }
        if (jeLinie.any { it.isEmpty() }) return emptyMap()
        return jeLinie.flatten().groupingBy { it.besitzer }.eachCount().toSortedMap(
            compareBy { it.wert },
        )
    }

    fun istVollstaendigFeindlichBlockiert(
        karte: Spielkarte,
        standort: KartenEcke,
        verteidiger: SpielerId,
        konflikte: Set<Konflikt>,
    ): Boolean = blockierendePanzerNachSpieler(karte, standort, verteidiger, konflikte).isNotEmpty()

    fun blockierteLandkanten(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
    ): Set<KartenKante> = karte.belegung.kriegseinheiten.asSequence()
        .filter { it.typ == KriegsEinheitTyp.PANZER && it.besitzer != spieler }
        .filter { einheit -> konflikte.any {
            it.betrifft(spieler, einheit.besitzer) && !it.hatWaffenstillstand(spieler, einheit.besitzer)
        } }
        .mapTo(mutableSetOf()) { it.position }

    fun blockierteHaefen(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
    ): Set<KartenEcke> = karte.belegung.kriegseinheiten.asSequence()
        .filter { it.typ == KriegsEinheitTyp.KRIEGSSCHIFF && it.besitzer != spieler }
        .filter { einheit -> konflikte.any {
            it.betrifft(spieler, einheit.besitzer) && !it.hatWaffenstillstand(spieler, einheit.besitzer)
        } }
        .flatMap { sequenceOf(it.position.anfang, it.position.ende) }
        .filter { ecke -> karte.belegung.ecken.any {
            it.position == ecke && it.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN)
        } }
        .toSet()
}
