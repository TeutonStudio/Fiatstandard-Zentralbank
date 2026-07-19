package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsArt
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import de.teutonstudio.zentralbank.fachlogik.modell.kuerzesterWasserweg
import java.util.ArrayDeque

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

sealed interface TransportAbschnitt {
    data class Handelslinie(val kante: KartenKante) : TransportAbschnitt

    data class Frachtschiff(
        val seewegId: String,
        val von: KartenEcke,
        val nach: KartenEcke,
        val wasserweg: List<KartenKante>,
    ) : TransportAbschnitt
}

data class TransportWeg(
    val spieler: SpielerId,
    val feld: KartenFeld,
    val start: KartenEcke,
    val hauptbahnhof: KartenEcke,
    val anschlussStaerke: Int,
    val abschnitte: List<TransportAbschnitt>,
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
        konflikte: Set<Konflikt> = emptySet(),
    ): Map<SpielerId, Int> = karte.belegung.ecken
        .asSequence()
        .filter { belegung ->
            belegung.typ == EckGebaeudeTyp.HAUPTBAHNHOF &&
                belegung.zustand == BauwerkZustand.INTAKT
        }
        .mapNotNull { belegung -> belegung.besitzer }
        .distinct()
        .mapNotNull { spieler ->
            transportWeg(karte, feld, spieler, konflikte)
                ?.let { weg -> spieler to weg.anschlussStaerke }
        }
        .toMap()

    fun effektiverZustand(
        karte: Spielkarte,
        belegung: FeldBelegung,
        konflikte: Set<Konflikt> = emptySet(),
    ): AnlagenZustand = when {
        belegung.zustand == AnlagenZustand.ZERSTOERT -> AnlagenZustand.ZERSTOERT
        anschlussStaerke(karte, belegung.position, konflikte).isEmpty() ->
            AnlagenZustand.VERLASSEN
        else -> AnlagenZustand.AKTIV
    }

    fun ertrag(
        karte: Spielkarte,
        feld: KartenFeld,
        konflikte: Set<Konflikt> = emptySet(),
    ): Map<SpielerId, Int> {
        val belegung = karte.belegung.felderNachPosition[feld] ?: return emptyMap()
        val istGeschaeftsbank = belegung.anlage == FeldAnlage.Geschaeftsbank ||
            (belegung.anlage as? FeldAnlage.Wirtschaftsregion)?.bauteil ==
            BauteilTyp.GESCHAEFTSBANK
        if (istGeschaeftsbank) return emptyMap()
        return if (effektiverZustand(karte, belegung, konflikte) == AnlagenZustand.AKTIV) {
            anschlussStaerke(karte, feld, konflikte)
        } else {
            emptyMap()
        }
    }

    fun abbauErtrag(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt> = emptySet(),
    ): Map<Rohstoff, Int> = karte.belegung.felder
        .flatMap { belegung ->
            val menge = ertrag(karte, belegung.position, konflikte)[spieler]
                ?: return@flatMap emptyList()
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
        konflikte: Set<Konflikt> = emptySet(),
    ): List<VerarbeitungsStandort> = karte.belegung.felder.mapNotNull { belegung ->
        val wirtschaft = belegung.anlage as? FeldAnlage.Wirtschaftsregion
            ?: return@mapNotNull null
        val typ = wirtschaft.bauteil
        if (typ.produktionsArt != ProduktionsArt.VERARBEITUNG) return@mapNotNull null
        if (ertrag(karte, belegung.position, konflikte)[spieler] == null) {
            return@mapNotNull null
        }
        VerarbeitungsStandort(
            feld = belegung.position,
            typ = typ,
            maximaleLaeufe = 1,
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
                belegung.zustand == BauwerkZustand.INTAKT
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
        konflikte: Set<Konflikt> = emptySet(),
    ): Boolean {
        val blockierteHaefen = blockierteHaefen(karte, spieler, konflikte)
        val eigeneIntakteHaefen = karte.belegung.ecken
            .filter { belegung ->
                belegung.besitzer == spieler &&
                    belegung.zustand == BauwerkZustand.INTAKT &&
                    belegung.position !in blockierteHaefen &&
                    belegung.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN)
            }
            .mapTo(mutableSetOf()) { it.position }
        return karte.belegung.seewege.any { seeweg ->
            seeweg.besitzer == spieler &&
                seeweg.hafenA in eigeneIntakteHaefen &&
                seeweg.hafenB in eigeneIntakteHaefen
        }
    }

    fun kontrolliertGeschaeftsbank(
        karte: Spielkarte,
        feld: KartenFeld,
        spieler: SpielerId,
        konflikte: Set<Konflikt> = emptySet(),
    ): Boolean {
        val belegung = karte.belegung.felderNachPosition[feld] ?: return false
        return belegung.anlage == FeldAnlage.Geschaeftsbank &&
            effektiverZustand(karte, belegung, konflikte) == AnlagenZustand.AKTIV &&
            spieler in anschlussStaerke(karte, feld, konflikte)
    }

    fun transportWeg(
        karte: Spielkarte,
        feld: KartenFeld,
        spieler: SpielerId,
        konflikte: Set<Konflikt> = emptySet(),
    ): TransportWeg? {
        if (feld !in karte.landNachPosition) return null
        val hauptbahnhof = karte.belegung.ecken.firstOrNull { belegung ->
            belegung.typ == EckGebaeudeTyp.HAUPTBAHNHOF &&
                belegung.besitzer == spieler &&
                belegung.zustand == BauwerkZustand.INTAKT
        }?.position ?: return null
        val blockierteGleise = blockierteKanten(
            karte = karte,
            spieler = spieler,
            konflikte = konflikte,
            typ = KriegsEinheitTyp.PANZER,
        )
        val blockierteHaefen = blockierteHaefen(karte, spieler, konflikte)
        val befahrbareGleise = karte.belegung.kanten
            .asSequence()
            .filter { gleis ->
                gleis.zustand == BauwerkZustand.INTAKT &&
                    gleis.position !in blockierteGleise
            }
            .mapTo(mutableSetOf(), KantenBelegung::position)
        val graph = transportGraph(
            karte = karte,
            spieler = spieler,
            befahrbareGleise = befahrbareGleise,
            blockierteHaefen = blockierteHaefen,
        )
        val anschluesse = buildList {
            feld.ecken().forEach { ecke ->
                karte.belegung.eckenNachPosition[ecke]
                    ?.takeIf { gebaeude ->
                        gebaeude.besitzer == spieler &&
                            gebaeude.zustand == BauwerkZustand.INTAKT &&
                            gebaeude.position !in blockierteHaefen
                    }
                    ?.anschlussStaerke()
                    ?.takeIf { staerke -> staerke > 0 }
                    ?.let { staerke -> add(TransportAnschluss(ecke, staerke, emptyList())) }
            }
            feld.ecken().forEach { ecke ->
                if (befahrbareGleise.any { kante -> kante.anfang == ecke || kante.ende == ecke }) {
                    add(TransportAnschluss(ecke, 1, emptyList()))
                }
            }
        }
        anschluesse.map(TransportAnschluss::staerke)
            .distinct()
            .sortedDescending()
            .forEach { staerke ->
                findeTransportWeg(
                    graph = graph,
                    anschluesse = anschluesse.filter { anschluss ->
                        anschluss.staerke == staerke
                    },
                    hauptbahnhof = hauptbahnhof,
                )?.let { (start, abschnitte) ->
                    return TransportWeg(
                        spieler = spieler,
                        feld = feld,
                        start = start,
                        hauptbahnhof = hauptbahnhof,
                        anschlussStaerke = staerke,
                        abschnitte = abschnitte,
                    )
                }
            }
        return null
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

    private fun blockierteKanten(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
        typ: KriegsEinheitTyp,
    ): Set<KartenKante> = karte.belegung.kriegseinheiten
        .asSequence()
        .filter { einheit ->
            einheit.typ == typ &&
                einheit.besitzer != spieler &&
                konflikte.any { konflikt -> konflikt.betrifft(spieler, einheit.besitzer) }
        }
        .mapTo(mutableSetOf()) { einheit -> einheit.position }

    private fun transportGraph(
        karte: Spielkarte,
        spieler: SpielerId,
        befahrbareGleise: Set<KartenKante>,
        blockierteHaefen: Set<KartenEcke>,
    ): Map<KartenEcke, List<TransportSchritt>> {
        val graph = mutableMapOf<KartenEcke, MutableList<TransportSchritt>>()
        fun verbinden(
            a: KartenEcke,
            b: KartenEcke,
            vonANachB: TransportAbschnitt,
            vonBNachA: TransportAbschnitt,
        ) {
            graph.getOrPut(a) { mutableListOf() }.add(TransportSchritt(b, vonANachB))
            graph.getOrPut(b) { mutableListOf() }.add(TransportSchritt(a, vonBNachA))
        }

        befahrbareGleise.sortedWith(kartenKantenVergleich).forEach { kante ->
            val abschnitt = TransportAbschnitt.Handelslinie(kante)
            verbinden(kante.anfang, kante.ende, abschnitt, abschnitt)
        }
        karte.belegung.seewege
            .asSequence()
            .filter { seeweg -> seeweg.besitzer == spieler }
            .filter { seeweg ->
                seeweg.hafenA !in blockierteHaefen && seeweg.hafenB !in blockierteHaefen
            }
            .sortedBy { seeweg -> seeweg.id }
            .forEach { seeweg ->
                val wasserweg = karte.kuerzesterWasserweg(seeweg.hafenA, seeweg.hafenB)
                    ?: return@forEach
                val (von, nach, gerichteterWasserweg) = when (seeweg.richtung) {
                    FrachtRichtung.A_NACH_B -> Triple(
                        seeweg.hafenA,
                        seeweg.hafenB,
                        wasserweg,
                    )
                    FrachtRichtung.B_NACH_A -> Triple(
                        seeweg.hafenB,
                        seeweg.hafenA,
                        wasserweg.asReversed(),
                    )
                }
                graph.getOrPut(von) { mutableListOf() }.add(
                    TransportSchritt(
                        ziel = nach,
                        abschnitt = TransportAbschnitt.Frachtschiff(
                            seewegId = seeweg.id,
                            von = von,
                            nach = nach,
                            wasserweg = gerichteterWasserweg,
                        ),
                    ),
                )
            }
        return graph.mapValues { (_, schritte) ->
            schritte.sortedWith(
                compareBy<TransportSchritt> { schritt -> schritt.ziel }
                    .thenBy { schritt -> schritt.abschnitt.sortierSchluessel() },
            )
        }
    }

    private fun findeTransportWeg(
        graph: Map<KartenEcke, List<TransportSchritt>>,
        anschluesse: List<TransportAnschluss>,
        hauptbahnhof: KartenEcke,
    ): Pair<KartenEcke, List<TransportAbschnitt>>? {
        val starts = anschluesse
            .sortedWith(
                compareBy<TransportAnschluss> { anschluss -> anschluss.ecke }
                    .thenBy { anschluss -> anschluss.vorlauf.joinToString { it.sortierSchluessel() } },
            )
            .distinctBy(TransportAnschluss::ecke)
        val startNachEcke = starts.associateBy(TransportAnschluss::ecke)
        startNachEcke[hauptbahnhof]?.let { start ->
            return start.ecke to start.vorlauf
        }
        val besucht = startNachEcke.keys.toMutableSet()
        val offen = ArrayDeque<KartenEcke>()
        starts.forEach { start -> offen.add(start.ecke) }
        val vorgaenger = mutableMapOf<KartenEcke, Pair<KartenEcke, TransportAbschnitt>>()
        while (offen.isNotEmpty()) {
            val aktuell = offen.removeFirst()
            graph[aktuell].orEmpty().forEach { schritt ->
                if (!besucht.add(schritt.ziel)) return@forEach
                vorgaenger[schritt.ziel] = aktuell to schritt.abschnitt
                if (schritt.ziel == hauptbahnhof) {
                    val rueckwaerts = mutableListOf<TransportAbschnitt>()
                    var ecke = hauptbahnhof
                    while (ecke !in startNachEcke) {
                        val (vorher, abschnitt) = vorgaenger.getValue(ecke)
                        rueckwaerts += abschnitt
                        ecke = vorher
                    }
                    val start = startNachEcke.getValue(ecke)
                    return start.ecke to (start.vorlauf + rueckwaerts.asReversed())
                }
                offen.add(schritt.ziel)
            }
        }
        return null
    }

    private fun EckBelegung.anschlussStaerke(): Int = when (typ) {
        EckGebaeudeTyp.GROSSBAHNHOF, EckGebaeudeTyp.GROSSHAFEN -> 3
        EckGebaeudeTyp.BAHNHOF, EckGebaeudeTyp.HAFEN -> 2
        EckGebaeudeTyp.HAUPTBAHNHOF -> 0
    }

    private fun blockierteHaefen(
        karte: Spielkarte,
        spieler: SpielerId,
        konflikte: Set<Konflikt>,
    ): Set<KartenEcke> = karte.belegung.kriegseinheiten
        .asSequence()
        .filter { einheit ->
            einheit.typ == KriegsEinheitTyp.KRIEGSSCHIFF &&
                einheit.besitzer != spieler &&
                konflikte.any { konflikt -> konflikt.betrifft(spieler, einheit.besitzer) }
        }
        .flatMap { einheit -> sequenceOf(einheit.position.anfang, einheit.position.ende) }
        .filter { ecke ->
            karte.belegung.eckenNachPosition[ecke]?.typ in
                setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN)
        }
        .toSet()

    private fun TransportAbschnitt.sortierSchluessel(): String = when (this) {
        is TransportAbschnitt.Handelslinie ->
            "gleis:${kante.anfang.x}:${kante.anfang.y}:${kante.ende.x}:${kante.ende.y}"
        is TransportAbschnitt.Frachtschiff -> "seeweg:$seewegId:${von.x}:${von.y}:${nach.x}:${nach.y}"
    }

    private data class TransportAnschluss(
        val ecke: KartenEcke,
        val staerke: Int,
        val vorlauf: List<TransportAbschnitt>,
    )

    private data class TransportSchritt(
        val ziel: KartenEcke,
        val abschnitt: TransportAbschnitt,
    )

    private val kartenKantenVergleich = compareBy<KartenKante> { kante -> kante.anfang }
        .thenBy { kante -> kante.ende }

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
