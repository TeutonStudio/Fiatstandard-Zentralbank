package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SeewegBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.benachbarteEcken
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.enthaeltFeld
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import de.teutonstudio.zentralbank.fachlogik.modell.kuerzesterWasserweg
import de.teutonstudio.zentralbank.fachlogik.modell.wasserKanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenAuswertungTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")
    private val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
    private val kante = feld.kanten().first()
    private val verbindungsKante = KartenKante.zwischen(feld.ecken().last(), kante.anfang)
    private val land = feld.kanten()
        .flatMap(::angrenzendeFelder)
        .distinct()
        .map { GelaendeFeld(it, GelaendeTyp.EBENE) }

    @Test
    fun besteAnschlussartBestimmtErtrag() {
        val karte = karte(
            ecken = listOf(
                EckBelegung(feld.ecken().last(), EckGebaeudeTyp.GROSSBAHNHOF, anna),
            ),
            kanten = listOf(KantenBelegung(verbindungsKante)),
        )

        assertEquals(mapOf(anna to 3), KartenAuswertung.ertrag(karte, feld))
        assertEquals(mapOf(Rohstoff.NAHRUNG to 3), KartenAuswertung.abbauErtrag(karte, anna))
    }

    @Test
    fun gleisAnDerFeldkanteErzeugtEinfachenErtrag() {
        val karte = karte(kanten = listOf(KantenBelegung(kante)))

        assertEquals(mapOf(anna to 1), KartenAuswertung.ertrag(karte, feld))
        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), KartenAuswertung.abbauErtrag(karte, anna))
    }

    @Test
    fun gleisDasNurZuEinerFeldeckeFuehrtErzeugtEinfachenErtrag() {
        val startEcke = feld.ecken().first()
        val feldKanten = feld.kanten().toSet()
        val zubringer = benachbarteEcken(startEcke)
            .map { nachbar -> KartenKante.zwischen(startEcke, nachbar) }
            .first { kandidat -> kandidat !in feldKanten }
        val hauptbahnhof = if (zubringer.anfang == startEcke) {
            zubringer.ende
        } else {
            zubringer.anfang
        }
        val karte = Spielkarte(
            id = "eckanschluss",
            name = "Eckanschluss",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = (angrenzendeFelder(zubringer) + feld)
                .distinct()
                .map { GelaendeFeld(it, GelaendeTyp.EBENE) },
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(hauptbahnhof, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                ),
                kanten = listOf(KantenBelegung(zubringer)),
                felder = listOf(
                    FeldBelegung(feld, FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG)),
                ),
            ),
        )

        assertEquals(mapOf(anna to 1), KartenAuswertung.ertrag(karte, feld))
        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), KartenAuswertung.abbauErtrag(karte, anna))
    }

    @Test
    fun verwaltungsgebaeudeAnDerFeldeckeVervielfachenDenErtrag() {
        val staerken = mapOf(
            EckGebaeudeTyp.BAHNHOF to 2,
            EckGebaeudeTyp.HAFEN to 2,
            EckGebaeudeTyp.GROSSBAHNHOF to 3,
            EckGebaeudeTyp.GROSSHAFEN to 3,
        )

        staerken.forEach { (typ, staerke) ->
            val karte = karte(
                ecken = listOf(EckBelegung(feld.ecken().last(), typ, anna)),
                kanten = listOf(KantenBelegung(verbindungsKante)),
            )

            assertEquals(typ.name, mapOf(anna to staerke), KartenAuswertung.ertrag(karte, feld))
            assertEquals(
                typ.name,
                mapOf(Rohstoff.NAHRUNG to staerke),
                KartenAuswertung.abbauErtrag(karte, anna),
            )
        }
    }

    @Test
    fun anlageOhneAnschlussIstVerlassenUndErtraglos() {
        val karte = karte()
        val belegung = karte.belegung.felder.single()

        assertEquals(AnlagenZustand.VERLASSEN, KartenAuswertung.effektiverZustand(karte, belegung))
        assertTrue(KartenAuswertung.ertrag(karte, feld).isEmpty())
    }

    @Test
    fun panzerBlockiertNurOhneAlternativeRouteDenTransportZumHauptbahnhof() {
        val (a, b, hauptbahnhof) = feld.ecken()
        val zubringer = KartenKante.zwischen(a, b)
        val blockierteLinie = KartenKante.zwischen(b, hauptbahnhof)
        val alternativeLinie = KartenKante.zwischen(a, hauptbahnhof)
        val panzer = KriegsEinheitBelegung(
            id = "panzer-bert-1",
            typ = KriegsEinheitTyp.PANZER,
            besitzer = bert,
            ort = KartenOrt.Kante(blockierteLinie),
        )
        val konflikt = setOf(Konflikt(anna, bert))
        val direkterWeg = transportKarte(
            hauptbahnhof = hauptbahnhof,
            gleise = listOf(zubringer, blockierteLinie),
            kriegseinheiten = listOf(panzer),
        )
        val mitAlternative = transportKarte(
            hauptbahnhof = hauptbahnhof,
            gleise = listOf(zubringer, blockierteLinie, alternativeLinie),
            kriegseinheiten = listOf(panzer),
        )

        assertEquals(mapOf(anna to 1), KartenAuswertung.ertrag(direkterWeg, feld))
        assertTrue(KartenAuswertung.ertrag(direkterWeg, feld, konflikt).isEmpty())
        val umleitung = requireNotNull(
            KartenAuswertung.transportWeg(mitAlternative, feld, anna, konflikt),
        )
        assertEquals(1, umleitung.anschlussStaerke)
        assertTrue(
            umleitung.abschnitte.none { abschnitt ->
                abschnitt == TransportAbschnitt.Handelslinie(blockierteLinie)
            },
        )
        assertEquals(mapOf(anna to 1), KartenAuswertung.ertrag(mitAlternative, feld, konflikt))
    }

    @Test
    fun frachtschiffVerbindetInselstandortUndWirdImKriegAmHafenBlockiert() {
        val wasserkarte = Spielkarte(
            id = "wasser-grundlage",
            name = "Wasser-Grundlage",
            hexagon = KartenHexagon(radius = 5),
        )
        fun landGleisGegenueber(
            hafen: KartenEcke,
            andererHafen: KartenEcke,
            seeNachbarn: Set<KartenFeld>,
        ): KartenKante? = benachbarteEcken(hafen)
            .asSequence()
            .filter { ecke -> ecke != andererHafen }
            .map { ecke -> KartenKante.zwischen(hafen, ecke) }
            .firstOrNull { kandidat ->
                val nachbarn = angrenzendeFelder(kandidat)
                nachbarn.all(wasserkarte::enthaeltFeld) &&
                    nachbarn.none { feld -> feld in seeNachbarn }
            }
        val (seeKante, hafenA, hafenB) = wasserkarte.wasserKanten()
            .asSequence()
            .flatMap { kante ->
                sequenceOf(
                    Triple(kante, kante.anfang, kante.ende),
                    Triple(kante, kante.ende, kante.anfang),
                )
            }
            .first { (kante, start, ziel) ->
                val seeNachbarn = angrenzendeFelder(kante).toSet()
                angrenzendeFelder(start).any { feld ->
                    wasserkarte.enthaeltFeld(feld) && feld !in seeNachbarn
                } && landGleisGegenueber(ziel, start, seeNachbarn) != null
            }
        val seeNachbarn = angrenzendeFelder(seeKante).toSet()
        val quellFeld = angrenzendeFelder(hafenA).first { kandidat ->
            wasserkarte.enthaeltFeld(kandidat) && kandidat !in seeNachbarn
        }
        val festlandGleis = requireNotNull(
            landGleisGegenueber(hafenB, hafenA, seeNachbarn),
        )
        val hauptbahnhof = if (festlandGleis.anfang == hafenB) {
            festlandGleis.ende
        } else {
            festlandGleis.anfang
        }
        val land = (listOf(quellFeld) + angrenzendeFelder(festlandGleis))
            .distinct()
            .map { GelaendeFeld(it, GelaendeTyp.EBENE) }
        val schiff = SeewegBelegung(
            id = "frachter-anna-1",
            hafenA = hafenA,
            hafenB = hafenB,
            besitzer = anna,
            richtung = FrachtRichtung.A_NACH_B,
        )
        val kriegsschiff = KriegsEinheitBelegung(
            id = "kriegsschiff-bert-1",
            typ = KriegsEinheitTyp.KRIEGSSCHIFF,
            besitzer = bert,
            ort = KartenOrt.Kante(seeKante),
        )
        val karte = wasserkarte.copy(
            gelaendefelder = land,
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(hafenA, EckGebaeudeTyp.HAFEN, anna),
                    EckBelegung(hafenB, EckGebaeudeTyp.HAFEN, anna),
                    EckBelegung(hauptbahnhof, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                ),
                kanten = listOf(KantenBelegung(festlandGleis)),
                felder = listOf(
                    FeldBelegung(
                        quellFeld,
                        FeldAnlage.Wirtschaftsregion(BauteilTyp.VIEHHOF),
                    ),
                ),
                seewege = listOf(schiff),
                kriegseinheiten = listOf(kriegsschiff),
            ),
        )

        val friedensWeg = requireNotNull(KartenAuswertung.transportWeg(karte, quellFeld, anna))
        assertTrue(friedensWeg.abschnitte.any { it is TransportAbschnitt.Frachtschiff })
        assertEquals(mapOf(anna to 2), KartenAuswertung.ertrag(karte, quellFeld))
        assertTrue(KartenAuswertung.kannAussenhandelBetreiben(karte, anna))
        val gegenDieFrachtrichtung = karte.copy(
            belegung = karte.belegung.copy(
                seewege = listOf(schiff.copy(richtung = FrachtRichtung.B_NACH_A)),
                kriegseinheiten = emptyList(),
            ),
        )
        assertNull(KartenAuswertung.transportWeg(gegenDieFrachtrichtung, quellFeld, anna))
        assertTrue(
            KartenAuswertung.ertrag(
                karte,
                quellFeld,
                setOf(Konflikt(anna, bert)),
            ).isEmpty(),
        )
        assertFalse(
            KartenAuswertung.kannAussenhandelBetreiben(
                karte,
                anna,
                setOf(Konflikt(anna, bert)),
            ),
        )
    }

    @Test
    fun kriegsschiffAufInnererWasserrouteBlockiertKeinenHafen() {
        val grundkarte = Spielkarte(
            id = "innerer-seeweg",
            name = "Innerer Seeweg",
            hexagon = KartenHexagon(radius = 5),
        )
        val wasserecken = grundkarte.wasserKanten()
            .flatMap { kante -> listOf(kante.anfang, kante.ende) }
            .distinct()
        val (hafenA, hafenB, wasserweg) = wasserecken.firstNotNullOf { a ->
            wasserecken.firstNotNullOfOrNull { b ->
                if (a == b) return@firstNotNullOfOrNull null
                grundkarte.kuerzesterWasserweg(a, b)
                    ?.takeIf { weg ->
                        weg.any { kante ->
                            a !in setOf(kante.anfang, kante.ende) &&
                                b !in setOf(kante.anfang, kante.ende)
                        }
                    }
                    ?.let { weg -> Triple(a, b, weg) }
            }
        }
        val innereKante = wasserweg.first { kante ->
            hafenA !in setOf(kante.anfang, kante.ende) &&
                hafenB !in setOf(kante.anfang, kante.ende)
        }
        val karte = grundkarte.copy(
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(hafenA, EckGebaeudeTyp.HAFEN, anna),
                    EckBelegung(hafenB, EckGebaeudeTyp.HAFEN, anna),
                ),
                seewege = listOf(
                    SeewegBelegung(
                        id = "frachter-anna-innen",
                        hafenA = hafenA,
                        hafenB = hafenB,
                        besitzer = anna,
                        richtung = FrachtRichtung.A_NACH_B,
                    ),
                ),
                kriegseinheiten = listOf(
                    KriegsEinheitBelegung(
                        id = "kriegsschiff-bert-innen",
                        typ = KriegsEinheitTyp.KRIEGSSCHIFF,
                        besitzer = bert,
                        ort = KartenOrt.Kante(innereKante),
                    ),
                ),
            ),
        )

        assertTrue(
            KartenAuswertung.kannAussenhandelBetreiben(
                karte,
                anna,
                setOf(Konflikt(anna, bert)),
            ),
        )
    }

    @Test
    fun verarbeitungWirdNichtAlsAutomatischerAbbauGewertet() {
        val karte = karte(
            anlage = FeldAnlage.Wirtschaftsregion(BauteilTyp.ZIEGELBRENNER),
            kanten = listOf(KantenBelegung(kante)),
        )

        assertTrue(KartenAuswertung.abbauErtrag(karte, anna).isEmpty())
        val standort = KartenAuswertung.verarbeitungsStandorte(karte, anna).single()
        assertEquals(1, standort.maximaleLaeufe)
        assertEquals(mapOf(Rohstoff.LEHM to 1), standort.einsatzJeLauf)
        assertEquals(mapOf(Rohstoff.ZIEGEL to 1), standort.ertragJeLauf)
    }

    @Test
    fun alleVerwaltungsstandorteHabenDenFestgelegtenProzugVerbrauch() {
        val typen = listOf(
            EckGebaeudeTyp.BAHNHOF,
            EckGebaeudeTyp.GROSSBAHNHOF,
            EckGebaeudeTyp.HAFEN,
            EckGebaeudeTyp.GROSSHAFEN,
            EckGebaeudeTyp.HAUPTBAHNHOF,
        )
        val ecken = (listOf(feld) + feld.kanten().flatMap(::angrenzendeFelder))
            .flatMap(KartenFeld::ecken)
            .distinct()
            .take(typen.size)
        val karte = Spielkarte(
            id = "verwaltungsverbrauch",
            name = "Verwaltungsverbrauch",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = land,
            belegung = KartenBelegung(
                ecken = typen.zip(ecken) { typ, ecke -> EckBelegung(ecke, typ, anna) },
            ),
        )

        val bedarf = KartenAuswertung.verwaltungsStandorte(karte, anna)
            .associate { standort -> standort.typ to standort.bedarf }

        assertEquals(mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1), bedarf[EckGebaeudeTyp.BAHNHOF])
        assertEquals(mapOf(Rohstoff.NAHRUNG to 2, Rohstoff.KOHLE to 2), bedarf[EckGebaeudeTyp.GROSSBAHNHOF])
        assertEquals(mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.SCHWEROEL to 1), bedarf[EckGebaeudeTyp.HAFEN])
        assertEquals(mapOf(Rohstoff.NAHRUNG to 2, Rohstoff.SCHWEROEL to 2), bedarf[EckGebaeudeTyp.GROSSHAFEN])
        assertEquals(mapOf(Rohstoff.NAHRUNG to 3, Rohstoff.KOHLE to 3), bedarf[EckGebaeudeTyp.HAUPTBAHNHOF])
    }

    @Test
    fun geschaeftsbankWirdNurMitAnschlussKontrolliert() {
        val ohne = karte(anlage = FeldAnlage.Geschaeftsbank)
        val mit = karte(
            anlage = FeldAnlage.Geschaeftsbank,
            kanten = listOf(KantenBelegung(kante)),
        )

        assertFalse(KartenAuswertung.kontrolliertGeschaeftsbank(ohne, feld, anna))
        assertTrue(KartenAuswertung.kontrolliertGeschaeftsbank(mit, feld, anna))
    }

    @Test
    fun geschaeftsbankErzeugtAuchMitGrossgebaeudeKeinenErtrag() {
        val geschaeftsbanken = listOf(
            FeldAnlage.Geschaeftsbank,
            FeldAnlage.Wirtschaftsregion(BauteilTyp.GESCHAEFTSBANK),
        )

        geschaeftsbanken.forEach { geschaeftsbank ->
            val karte = karte(
                anlage = geschaeftsbank,
                ecken = listOf(
                    EckBelegung(feld.ecken().last(), EckGebaeudeTyp.GROSSHAFEN, anna),
                ),
            )

            assertTrue(KartenAuswertung.ertrag(karte, feld).isEmpty())
            assertTrue(KartenAuswertung.abbauErtrag(karte, anna).isEmpty())
        }
    }

    @Test
    fun routenEndpunkteBestimmenKontrolleUndAbrissberechtigung() {
        val ecken = listOf(
            KartenEcke(6, 4),
            KartenEcke(8, 4),
            KartenEcke(10, 4),
            KartenEcke(12, 4),
        )
        val linien = ecken.zipWithNext(KartenKante::zwischen)
        val netz = Spielkarte(
            id = "gemeinsames-netz",
            name = "Gemeinsames Netz",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = linien
                .flatMap(::angrenzendeFelder)
                .distinct()
                .map { GelaendeFeld(it, GelaendeTyp.EBENE) },
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(ecken.first(), EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    EckBelegung(ecken.last(), EckGebaeudeTyp.HAUPTBAHNHOF, bert),
                ),
                kanten = linien.map(::KantenBelegung),
            ),
        )

        linien.forEach { linie ->
            assertEquals(setOf(anna, bert), KartenAuswertung.verbundeneSpieler(netz, linie))
            assertNull(KartenAuswertung.gewalthaber(netz, linie))
        }

        val nurAnna = netz.copy(
            belegung = netz.belegung.copy(ecken = netz.belegung.ecken.take(1)),
        )
        linien.forEach { linie ->
            assertEquals(setOf(anna), KartenAuswertung.verbundeneSpieler(nurAnna, linie))
            assertEquals(setOf(anna), KartenAuswertung.abrissberechtigteSpieler(nurAnna, linie))
            assertNull(KartenAuswertung.gewalthaber(nurAnna, linie))
        }

        val zwischenAnnasBauwerken = netz.copy(
            belegung = netz.belegung.copy(
                ecken = listOf(
                    netz.belegung.ecken.first(),
                    netz.belegung.ecken.last().copy(
                        typ = EckGebaeudeTyp.BAHNHOF,
                        besitzer = anna,
                    ),
                ),
            ),
        )
        linien.forEach { linie ->
            assertEquals(anna, KartenAuswertung.gewalthaber(zwischenAnnasBauwerken, linie))
            assertEquals(
                setOf(anna),
                KartenAuswertung.abrissberechtigteSpieler(zwischenAnnasBauwerken, linie),
            )
        }
    }

    private fun karte(
        anlage: FeldAnlage = FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG),
        ecken: List<EckBelegung> = emptyList(),
        kanten: List<KantenBelegung> = emptyList(),
    ) = Spielkarte(
        id = "auswertung",
        name = "Auswertung",
        hexagon = KartenHexagon(radius = 8),
        gelaendefelder = land,
        belegung = KartenBelegung(
            ecken = if (kanten.isEmpty()) ecken else {
                ecken + EckBelegung(
                    kante.anfang,
                    EckGebaeudeTyp.HAUPTBAHNHOF,
                    anna,
                )
            },
            kanten = kanten,
            felder = listOf(FeldBelegung(feld, anlage)),
        ),
    )

    private fun transportKarte(
        hauptbahnhof: KartenEcke,
        gleise: List<KartenKante>,
        kriegseinheiten: List<KriegsEinheitBelegung> = emptyList(),
    ) = Spielkarte(
        id = "transport-auswertung",
        name = "Transport-Auswertung",
        hexagon = KartenHexagon(radius = 8),
        gelaendefelder = feld.kanten()
            .flatMap(::angrenzendeFelder)
            .distinct()
            .map { GelaendeFeld(it, GelaendeTyp.EBENE) },
        belegung = KartenBelegung(
            ecken = listOf(
                EckBelegung(hauptbahnhof, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
            ),
            kanten = gleise.map(::KantenBelegung),
            felder = listOf(
                FeldBelegung(
                    feld,
                    FeldAnlage.Wirtschaftsregion(BauteilTyp.VIEHHOF),
                ),
            ),
            kriegseinheiten = kriegseinheiten,
        ),
    )
}
