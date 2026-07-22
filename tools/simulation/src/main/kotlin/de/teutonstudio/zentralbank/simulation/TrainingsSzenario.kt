package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.modell.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val TRAININGS_SEED_BASIS = 0L
const val VALIDIERUNGS_SEED_BASIS = 1_000_000_000L
const val TEST_SEED_BASIS = 2_000_000_000L

interface TrainingsSzenario {
    val id: String
    fun startzustand(seed: Long): SpielZustand
}

@Serializable
enum class SzenarioKategorie {
    WIRTSCHAFT,
    HANDEL_UND_SCHULDEN,
    SCHULDENKRISE,
    LANDKRIEG,
    SEEKRIEG,
    BLOCKADE,
    BELAGERUNG,
    FRIEDENSVERHANDLUNG,
    VOLLSTAENDIG,
}

data class SzenarioOptionen(
    val spielerAnzahl: Int = 3,
    val kategorie: SzenarioKategorie = SzenarioKategorie.WIRTSCHAFT,
    val startRohstoffe: Int = 6,
    val startGeld: Geld = Geld.mark(100),
    val marktpreis: Geld = Geld.mark(5),
    val startInRundeNull: Boolean = false,
    val kriegAktiv: Boolean = kategorie in setOf(
        SzenarioKategorie.LANDKRIEG,
        SzenarioKategorie.SEEKRIEG,
        SzenarioKategorie.BLOCKADE,
        SzenarioKategorie.BELAGERUNG,
        SzenarioKategorie.FRIEDENSVERHANDLUNG,
        SzenarioKategorie.VOLLSTAENDIG,
    ),
) {
    init {
        require(spielerAnzahl in 3..7) { "Szenarien unterstützen drei bis sieben Spieler." }
        require(startRohstoffe >= 0)
        require(startGeld >= Geld.NULL && marktpreis > Geld.NULL)
    }
}

class KleineWirtschaftsBaseline(
    override val id: String = "kleine-wirtschaft-v2",
    private val spielerNamen: List<String> = listOf("Agent-1", "Agent-2", "Agent-3"),
    private val optionen: SzenarioOptionen = SzenarioOptionen(spielerAnzahl = spielerNamen.size),
) : TrainingsSzenario {
    init {
        require(spielerNamen.size == optionen.spielerAnzahl)
        require(spielerNamen.distinct().size == spielerNamen.size)
    }

    override fun startzustand(seed: Long): SpielZustand {
        val ids = spielerNamen.indices.map { SpielerId("spieler-${it + 1}") }
        val karte = kompakteKarte(seed, ids, optionen.startInRundeNull)
        val basis = SpielZustand(
            spieler = spielerNamen.mapIndexed { index, name ->
                Spieler(
                    id = ids[index],
                    name = name,
                    rohstoffe = Rohstoff.entries.associateWith { optionen.startRohstoffe },
                    geldkonto = optionen.startGeld,
                    bauteile = if (optionen.startInRundeNull) {
                        mapOf(BauteilTyp.HAUPTBAHNHOF to 1)
                    } else emptyMap(),
                    spielstil = SpielerStil.entries[index % SpielerStil.entries.size],
                )
            },
            karte = karte,
            spielabschnitt = if (optionen.startInRundeNull) Spielabschnitt.RUNDE_NULL
                else Spielabschnitt.REGULAER,
            rundeNullRestbestand = if (optionen.startInRundeNull) ids.associateWith {
                mapOf(BauteilTyp.HAUPTBAHNHOF to 1)
            } else null,
            bankkonto = Geld.mark(1_000_000),
            auslandskonto = Geld.mark(1_000_000),
            warenkorb = Rohstoff.entries.associateWith { 1 },
            marktpreise = Rohstoff.entries.associateWith { optionen.marktpreis },
            leitzins = Basispunkte.prozent(2),
            konflikte = if (optionen.kriegAktiv) setOf(
                Konflikt(
                    spielerA = ids[0],
                    spielerB = ids[1],
                    id = KriegId("krieg-1"),
                ),
            ) else emptySet(),
            naechsteKriegNummer = if (optionen.kriegAktiv) 2L else 1L,
        )
        return if (optionen.startInRundeNull) basis else basis.mitSzenarioInhalt(optionen.kategorie)
    }

    private fun kompakteKarte(seed: Long, ids: List<SpielerId>, rundeNull: Boolean): Spielkarte {
        val hexagon = KartenHexagon(radius = maxOf(6, ids.size + 3))
        val allePositionen = hexagon.felder()
        val allePositionsMenge = allePositionen.toSet()
        val innereEcken = allePositionen.flatMap { it.ecken() }.distinct()
            .filter { ecke -> angrenzendeFelder(ecke).all(allePositionsMenge::contains) }
            .sorted()
        val startEcken = waehleStartEcken(innereEcken, ids.size)
        val inselPositionen = startEcken.flatMap(::angrenzendeFelder).toSet()
        val verbindungsPositionen = if (optionen.kategorie in setOf(
                SzenarioKategorie.LANDKRIEG,
                SzenarioKategorie.BELAGERUNG,
                SzenarioKategorie.VOLLSTAENDIG,
            )
        ) {
            verbindungsFelder(hexagon, startEcken)
        } else emptySet()
        val landPositionen = inselPositionen + verbindungsPositionen
        val positionen = allePositionen.filter(landPositionen::contains)
        val gelaende = positionen.mapIndexed { index, feld ->
            val typIndex = Math.floorMod(seed + index, GelaendeTyp.entries.size.toLong()).toInt()
            GelaendeFeld(feld, GelaendeTyp.entries[typIndex])
        }
        return Spielkarte(
            id = id,
            name = "Kompakte Trainingskarte",
            hexagon = hexagon,
            gelaendefelder = gelaende,
            belegung = KartenBelegung(
                ecken = if (rundeNull) emptyList() else startEcken.mapIndexed { index, ecke ->
                    EckBelegung(ecke, EckGebaeudeTyp.HAUPTBAHNHOF, ids[index], gebautInRunde = 0)
                },
            ),
        )
    }
}

class VorlagenSzenario(
    private val ressourcenName: String,
    private val optionen: SzenarioOptionen,
) : TrainingsSzenario {
    override val id: String = "vorlage-${ressourcenName.substringBeforeLast('.')}"

    override fun startzustand(seed: Long): SpielZustand {
        val strom = requireNotNull(javaClass.classLoader.getResourceAsStream(
            "karten/vorlagen/$ressourcenName",
        )) { "Kartenvorlage $ressourcenName wurde nicht gefunden." }
        val vorlage = strom.bufferedReader().use { reader ->
            Json { ignoreUnknownKeys = false }.decodeFromString<KartenVorlage>(reader.readText())
        }
        val ids = (1..optionen.spielerAnzahl).map { SpielerId("spieler-$it") }
        val alleKandidaten = vorlage.gelaendefelder.flatMap { it.position.ecken() }.distinct().sorted()
        // Vollständig von Land umschlossene Ecken bleiben bevorzugt. Kleine Inselkarten
        // dürfen für große Partien zusätzlich ihre regulären Küstenecken verwenden.
        val kandidaten = alleKandidaten.filter { ecke ->
            angrenzendeFelder(ecke).all { it in vorlage.landNachPosition }
        } + alleKandidaten.filterNot { ecke ->
            angrenzendeFelder(ecke).all { it in vorlage.landNachPosition }
        }
        val start = waehleStartEcken(kandidaten, ids.size)
        val karte = vorlage.alsSpielkarte("$id-$seed").copy(
            belegung = KartenBelegung(
                ecken = if (optionen.startInRundeNull) emptyList() else start.mapIndexed { index, ecke ->
                    EckBelegung(ecke, EckGebaeudeTyp.HAUPTBAHNHOF, ids[index], gebautInRunde = 0)
                },
            ),
        )
        val basis = SpielZustand(
            spieler = ids.mapIndexed { index, spieler ->
                Spieler(
                    spieler,
                    "Agent-${index + 1}",
                    rohstoffe = Rohstoff.entries.associateWith { optionen.startRohstoffe },
                    geldkonto = optionen.startGeld,
                    bauteile = if (optionen.startInRundeNull) {
                        mapOf(BauteilTyp.HAUPTBAHNHOF to 1)
                    } else emptyMap(),
                    spielstil = SpielerStil.entries[index % SpielerStil.entries.size],
                )
            },
            karte = karte,
            spielabschnitt = if (optionen.startInRundeNull) Spielabschnitt.RUNDE_NULL else Spielabschnitt.REGULAER,
            rundeNullRestbestand = if (optionen.startInRundeNull) ids.associateWith {
                mapOf(BauteilTyp.HAUPTBAHNHOF to 1)
            } else null,
            bankkonto = Geld.mark(1_000_000),
            auslandskonto = Geld.mark(1_000_000),
            warenkorb = Rohstoff.entries.associateWith { 1 },
            marktpreise = Rohstoff.entries.associateWith { optionen.marktpreis },
            leitzins = Basispunkte.prozent(2),
        )
        return if (optionen.startInRundeNull) basis else basis.mitSzenarioInhalt(optionen.kategorie)
    }
}

object SzenarioKatalog {
    val echteKarten = listOf(
        "europa-1-kompakt.json",
        "europa-2-klein.json",
        "europa-3-mittel.json",
        "europa-4-gross.json",
        "europa-5-kontinental.json",
        "inselreich.json",
    )

    fun szenario(id: String): TrainingsSzenario {
        if (id == "kleine-wirtschaft-v1" || id == "kleine-wirtschaft-v2") {
            return KleineWirtschaftsBaseline(id = "kleine-wirtschaft-v2")
        }
        if (id.startsWith("generiert-")) {
            val teile = id.split('-')
            val anzahl = teile.lastOrNull()?.toIntOrNull() ?: 3
            val kategorie = when {
                "friedensverhandlung" in id || "frieden" in id ->
                    SzenarioKategorie.FRIEDENSVERHANDLUNG
                "belagerung" in id -> SzenarioKategorie.BELAGERUNG
                "blockade" in id -> SzenarioKategorie.BLOCKADE
                "vollstaendig" in id -> SzenarioKategorie.VOLLSTAENDIG
                "seekrieg" in id -> SzenarioKategorie.SEEKRIEG
                "landkrieg" in id -> SzenarioKategorie.LANDKRIEG
                "schulden" in id -> SzenarioKategorie.SCHULDENKRISE
                "handel" in id -> SzenarioKategorie.HANDEL_UND_SCHULDEN
                else -> SzenarioKategorie.WIRTSCHAFT
            }
            return KleineWirtschaftsBaseline(
                id = id,
                spielerNamen = (1..anzahl).map { "Agent-$it" },
                optionen = SzenarioOptionen(anzahl, kategorie),
            )
        }
        val datei = echteKarten.firstOrNull { datei ->
            val basis = "vorlage-${datei.substringBeforeLast('.')}"
            id == basis || id.matches(Regex("${Regex.escape(basis)}-[3-7]"))
        }
            ?: error("Unbekanntes Szenario: $id")
        val anzahl = id.substringAfterLast('-').toIntOrNull()?.takeIf { it in 3..7 } ?: 3
        return VorlagenSzenario(datei, SzenarioOptionen(spielerAnzahl = anzahl))
    }
}

private fun SpielZustand.mitSzenarioInhalt(kategorie: SzenarioKategorie): SpielZustand {
    if (kategorie == SzenarioKategorie.WIRTSCHAFT) return this
    val ids = spieler.map { it.id }
    var ergebnis = this

    if (kategorie in setOf(
            SzenarioKategorie.HANDEL_UND_SCHULDEN,
            SzenarioKategorie.SCHULDENKRISE,
            SzenarioKategorie.VOLLSTAENDIG,
        )
    ) {
        val id = AnleiheId("szenario-schuld-1")
        val schuld = Anleihe(
            id = id,
            emittent = ids[0],
            nennwert = Geld.mark(if (kategorie == SzenarioKategorie.SCHULDENKRISE) 120 else 40),
            zinsBasispunkte = maxOf(leitzins.wert, 300),
            laufzeitRunden = 3,
            emissionsRunde = rundenzähler,
        )
        ergebnis = ergebnis.copy(
            spieler = ergebnis.spieler.map {
                if (it.id == ids[0] && kategorie == SzenarioKategorie.SCHULDENKRISE) {
                    it.copy(geldkonto = Geld.NULL)
                } else it
            },
            anleihen = ergebnis.anleihen + (id to schuld),
            bankAnleihen = ergebnis.bankAnleihen + id,
            naechsteAnleiheNummer = maxOf(ergebnis.naechsteAnleiheNummer, 2L),
        )
    }

    if (kategorie in setOf(SzenarioKategorie.LANDKRIEG, SzenarioKategorie.VOLLSTAENDIG)) {
        val karte = requireNotNull(ergebnis.karte)
        val landKanten = karte.gelaendefelder.flatMap { it.position.kanten() }.distinct()
            .filter { kante -> angrenzendeFelder(kante).all { it in karte.landNachPosition } }
            .sortedWith(compareBy({ it.anfang }, { it.ende }))
        ergebnis = ergebnis.copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    kriegseinheiten = karte.belegung.kriegseinheiten + listOf(
                        KriegsEinheitBelegung(
                            "szenario-panzer-1", KriegsEinheitTyp.PANZER, ids[0],
                            ort = KartenOrt.Kante(landKanten[0]),
                        ),
                        KriegsEinheitBelegung(
                            "szenario-panzer-2", KriegsEinheitTyp.PANZER, ids[1],
                            ort = KartenOrt.Kante(landKanten[1]),
                        ),
                    ),
                ),
            ),
            naechsteEinheitenNummer = maxOf(ergebnis.naechsteEinheitenNummer, 3L),
        )
    }

    if (kategorie in setOf(
            SzenarioKategorie.SEEKRIEG,
            SzenarioKategorie.BLOCKADE,
            SzenarioKategorie.VOLLSTAENDIG,
        )
    ) {
        val karte = requireNotNull(ergebnis.karte)
        val wasser = karte.wasserKanten().sortedWith(compareBy({ it.anfang }, { it.ende }))
        require(wasser.size >= 2) { "Die Szenariokarte bietet nicht genug Seekanten." }
        val route = wasser.first()
        val vorhandeneEcken = karte.belegung.ecken.mapTo(mutableSetOf()) { it.position }
        require(route.anfang !in vorhandeneEcken && route.ende !in vorhandeneEcken)
        ergebnis = ergebnis.copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    ecken = karte.belegung.ecken + listOf(
                        EckBelegung(route.anfang, EckGebaeudeTyp.HAFEN, ids[0]),
                        EckBelegung(route.ende, EckGebaeudeTyp.HAFEN, ids[0]),
                    ),
                    seewege = karte.belegung.seewege + SeewegBelegung(
                        "szenario-frachter-1", route.anfang, route.ende, ids[0],
                        FrachtRichtung.A_NACH_B,
                    ),
                    kriegseinheiten = karte.belegung.kriegseinheiten + listOf(
                        KriegsEinheitBelegung(
                            "szenario-kriegsschiff-1", KriegsEinheitTyp.KRIEGSSCHIFF, ids[1],
                            ort = KartenOrt.Kante(route),
                        ),
                        KriegsEinheitBelegung(
                            "szenario-kriegsschiff-2", KriegsEinheitTyp.KRIEGSSCHIFF, ids[0],
                            ort = KartenOrt.Kante(wasser[1]),
                        ),
                    ),
                ),
            ),
            naechsteSeewegNummer = maxOf(ergebnis.naechsteSeewegNummer, 2L),
            naechsteEinheitenNummer = maxOf(ergebnis.naechsteEinheitenNummer, 5L),
        )
    }

    if (kategorie == SzenarioKategorie.BELAGERUNG) {
        val karte = requireNotNull(ergebnis.karte)
        val ziel = karte.belegung.ecken.single {
            it.besitzer == ids[1] && it.typ == EckGebaeudeTyp.HAUPTBAHNHOF
        }.position
        val linien = karte.gelaendefelder.flatMap { it.position.kanten() }.distinct()
            .filter { (it.anfang == ziel || it.ende == ziel) &&
                angrenzendeFelder(it).all { feld -> feld in karte.landNachPosition } }
            .sortedWith(compareBy({ it.anfang }, { it.ende }))
        val panzer = linien.mapIndexed { index, linie ->
            KriegsEinheitBelegung(
                "szenario-belagerer-$index", KriegsEinheitTyp.PANZER, ids[0],
                ort = KartenOrt.Kante(linie),
            )
        }
        ergebnis = ergebnis.copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    ecken = karte.belegung.ecken.map {
                        if (it.position == ziel) it.copy(zustand = BauwerkZustand.BELAGERT) else it
                    },
                    kanten = karte.belegung.kanten + linien.map {
                        KantenBelegung(it, erbautVon = ids[1])
                    },
                    kriegseinheiten = karte.belegung.kriegseinheiten + panzer,
                ),
            ),
            belagerungen = listOf(
                Belagerung(
                    standort = ziel,
                    verteidiger = ids[1],
                    beteiligteBelagerer = mapOf(ids[0] to panzer.size),
                    begonnenInRunde = rundenzähler,
                    fortschrittRunden = 1,
                    fuehrenderBelagerer = ids[0],
                ),
            ),
            naechsteEinheitenNummer = maxOf(ergebnis.naechsteEinheitenNummer, panzer.size + 1L),
        )
    }

    if (kategorie == SzenarioKategorie.FRIEDENSVERHANDLUNG) {
        val krieg = ergebnis.konflikte.single()
        val vertrag = Friedensvertrag(
            id = FriedensvertragId("szenario-frieden-1"),
            krieg = krieg.id,
            beteiligteSpieler = krieg.teilnehmer,
            unentschiedeneTeilnehmer = krieg.teilnehmer,
            angenommenVon = setOf(ids[0]),
        )
        ergebnis = ergebnis.copy(
            konflikte = setOf(krieg.copy(status = KriegsStatus.FRIEDEN_ANGEBOTEN)),
            friedensvertraege = listOf(vertrag),
            naechsteFriedensvertragNummer = 2L,
        )
    }
    return ergebnis
}

/** Verbindet die Startinseln deterministisch mit schmalen Landkorridoren. */
private fun verbindungsFelder(
    hexagon: KartenHexagon,
    startEcken: List<KartenEcke>,
): Set<KartenFeld> {
    val felder = hexagon.felder().toSet()
    val ergebnis = linkedSetOf<KartenFeld>()
    val start = startEcken.first()
    startEcken.drop(1).forEach { ziel ->
        val offen = java.util.ArrayDeque<KartenEcke>()
        val besucht = mutableSetOf(start)
        val vorgaenger = mutableMapOf<KartenEcke, KartenEcke>()
        offen += start
        while (offen.isNotEmpty() && ziel !in besucht) {
            val aktuell = offen.removeFirst()
            benachbarteEcken(aktuell).sorted().forEach { nachbar ->
                val liegtAufKarte = angrenzendeFelder(nachbar).any { it in felder }
                if (liegtAufKarte && besucht.add(nachbar)) {
                    vorgaenger[nachbar] = aktuell
                    offen += nachbar
                }
            }
        }
        require(ziel in besucht) { "Startstandorte konnten nicht durch Land verbunden werden." }
        val pfad = buildList {
            var ecke = ziel
            add(ecke)
            while (ecke != start) {
                ecke = requireNotNull(vorgaenger[ecke])
                add(ecke)
            }
        }.asReversed()
        pfad.zipWithNext().forEach { (a, b) ->
            angrenzendeFelder(KartenKante.zwischen(a, b))
                .filterTo(ergebnis) { it in felder }
        }
    }
    return ergebnis
}

private fun waehleStartEcken(kandidaten: List<KartenEcke>, anzahl: Int): List<KartenEcke> {
    for (mindestAbstand in 6 downTo 0) {
        val ausgewaehlt = mutableListOf<KartenEcke>()
        kandidaten.forEach { kandidat ->
            if (ausgewaehlt.all { bisher ->
                    kantenAbstand(kandidat, bisher, maximal = mindestAbstand) == null
                }
            ) {
                ausgewaehlt += kandidat
            }
        }
        if (ausgewaehlt.size >= anzahl) return ausgewaehlt.take(anzahl)
    }
    error("Die Karte bietet nicht genug getrennte Startstandorte.")
}
