package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.KartenAenderungsGrund
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.ProzugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.Spezialfeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.benachbarteEcken
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import de.teutonstudio.zentralbank.fachlogik.modell.gesperrteKanten
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import de.teutonstudio.zentralbank.fachlogik.modell.wasserKanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenRegelwerkTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")

    @Test
    fun rundeNullPlatziertReihumHauptbahnhoefeUndStartetDanachRegulaer() {
        val start = zustand(rundeNull = true)
        val annaEcke = KartenFeld(1, 1, DreieckHaelfte.UNTEN).ecken().first()
        val bertEcke = KartenFeld(4, 4, DreieckHaelfte.OBEN).ecken().last()

        val nachAnna = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.HauptbahnhofPlatziert(anna, annaEcke),
        ).getOrThrow()
        val nachBert = SpielRegelwerk.wendeAn(
            nachAnna,
            SpielEreignis.HauptbahnhofPlatziert(bert, bertEcke),
        ).getOrThrow()

        assertEquals(bert, nachAnna.aktiverSpieler)
        assertEquals(Spielabschnitt.REGULAER, nachBert.spielabschnitt)
        assertEquals(anna, nachBert.aktiverSpieler)
        assertEquals(2, nachBert.karte?.belegung?.ecken?.size)
    }

    @Test
    fun rundeNullVerbrauchtIndividuelleStartplatzierungenOhneBestandZuVerdoppeln() {
        val annasFeld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
        val annaEcke = annasFeld.ecken().first()
        val annaKante = annasFeld.kanten().first { kante ->
            annaEcke == kante.anfang || annaEcke == kante.ende
        }
        val bertEcke = KartenFeld(4, 4, DreieckHaelfte.OBEN).ecken().last()
        val annasStartbestand = mapOf(
            BauteilTyp.HAUPTBAHNHOF to 1,
            BauteilTyp.EISENBAHNLINIE to 1,
            BauteilTyp.VIEHHOF to 1,
        )
        val bertsStartbestand = mapOf(BauteilTyp.HAUPTBAHNHOF to 1)
        val start = zustand(rundeNull = true).copy(
            spieler = listOf(
                Spieler(id = anna, name = "Anna", bauteile = annasStartbestand),
                Spieler(id = bert, name = "Bert", bauteile = bertsStartbestand),
            ),
            rundeNullRestbestand = linkedMapOf(
                anna to annasStartbestand,
                bert to bertsStartbestand,
            ),
        )

        val nachHauptbahnhof = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.HauptbahnhofPlatziert(anna, annaEcke),
        ).getOrThrow()
        val nachSchiene = SpielRegelwerk.wendeAn(
            nachHauptbahnhof,
            SpielEreignis.SchieneGebaut(anna, annaKante),
        ).getOrThrow()
        val nachViehhof = SpielRegelwerk.wendeAn(
            nachSchiene,
            SpielEreignis.NeutraleAnlageErrichtet(
                errichter = anna,
                feld = annasFeld,
                anlage = FeldAnlage.Wirtschaftsregion(BauteilTyp.VIEHHOF),
            ),
        ).getOrThrow()
        val danach = SpielRegelwerk.wendeAn(
            nachViehhof,
            SpielEreignis.HauptbahnhofPlatziert(bert, bertEcke),
        ).getOrThrow()

        assertEquals(anna, nachHauptbahnhof.aktiverSpieler)
        assertEquals(anna, nachSchiene.aktiverSpieler)
        assertEquals(anna, nachSchiene.karte?.belegung?.kanten?.single()?.erbautVon)
        assertEquals(bert, nachViehhof.aktiverSpieler)
        assertEquals(Spielabschnitt.REGULAER, danach.spielabschnitt)
        assertTrue(danach.rundeNullRestbestand.orEmpty().isEmpty())
        assertEquals(annasStartbestand, danach.spieler.first { it.id == anna }.bauteile)
        assertEquals(1, danach.karte?.belegung?.kanten?.size)
        assertEquals(1, danach.karte?.belegung?.felder?.size)
    }

    @Test
    fun rundeNullLehntZuNahenHauptbahnhofAb() {
        val start = zustand(rundeNull = true)
        val annaEcke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val bertEcke = de.teutonstudio.zentralbank.fachlogik.modell.benachbarteEcken(annaEcke).first()
        val nachAnna = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.HauptbahnhofPlatziert(anna, annaEcke),
        ).getOrThrow()

        val ergebnis = SpielRegelwerk.wendeAn(
            nachAnna,
            SpielEreignis.HauptbahnhofPlatziert(bert, bertEcke),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("drei Kanten"))
    }

    @Test
    fun hauptbahnhofBrauchtSechsAngrenzendeGelaendefelder() {
        val eckeMitWasser = KartenFeld(0, 0, DreieckHaelfte.UNTEN).ecken().first()

        val ergebnis = SpielRegelwerk.wendeAn(
            zustand(rundeNull = true),
            SpielEreignis.HauptbahnhofPlatziert(anna, eckeMitWasser),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("sechs Geländefeldern"))
    }

    @Test
    fun teichmitteIstAuchBeiSechsGelaendefeldernNichtBebaubar() {
        val mitte = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val spezialfeld = Spezialfeld(SpezialfeldTyp.TEICH, mitte)
        val start = zustand(rundeNull = true).mitSpezialfeld(spezialfeld)

        val ergebnis = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.HauptbahnhofPlatziert(anna, mitte),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("Teichmitte"))
    }

    @Test
    fun kantenZurTeichmitteSindNichtMitHandelslinienBebaubar() {
        val mitte = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val spezialfeld = Spezialfeld(SpezialfeldTyp.TEICH, mitte)
        val start = zustand().mitSpezialfeld(spezialfeld)
        val innenkante = spezialfeld.gesperrteKanten().first()

        val ergebnis = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.SchieneGebaut(anna, innenkante),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("Teichmitte"))
    }

    @Test
    fun bahnhofBuchtKostenUndOrtAtomar() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val start = zustand().copy(rundenzähler = 4).mitHandelslinieZu(ecke)

        val danach = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.BAHNHOF),
        ).getOrThrow()

        val spieler = danach.spieler.first { it.id == anna }
        assertEquals(1, spieler.bauteile[BauteilTyp.BAHNHOF])
        assertEquals(8, spieler.rohstoffe[Rohstoff.HOLZ])
        assertEquals(8, spieler.rohstoffe[Rohstoff.ZIEGEL])
        assertEquals(9, spieler.rohstoffe[Rohstoff.STAHL])
        assertEquals(EckGebaeudeTyp.BAHNHOF, danach.karte?.belegung?.ecken?.single()?.typ)
        assertEquals(4, danach.karte?.belegung?.ecken?.single()?.gebautInRunde)
    }

    @Test
    fun fehlendeRohstoffeLassenAuchKarteUnveraendert() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val start = zustand().copy(
            spieler = listOf(
                Spieler(id = anna, name = "Anna"),
                Spieler(id = bert, name = "Bert"),
            ),
        ).mitHandelslinieZu(ecke)

        val ergebnis = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.BAHNHOF),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(start.karte?.belegung?.ecken.orEmpty().isEmpty())
        assertTrue(start.spieler.first().bauteile.isEmpty())
    }

    @Test
    fun eckgebaeudeBrauchtEineIntakteHandelslinieAnDerZielecke() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val ohneLinie = SpielRegelwerk.wendeAn(
            zustand(),
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.BAHNHOF),
        )
        val mitZerstoerterLinie = SpielRegelwerk.wendeAn(
            zustand().mitHandelslinieZu(ecke, BauwerkZustand.ZERSTOERT),
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.BAHNHOF),
        )

        assertTrue(ohneLinie.isFailure)
        assertTrue(ohneLinie.exceptionOrNull()?.message.orEmpty().contains("Handelslinie"))
        assertTrue(mitZerstoerterLinie.isFailure)
        assertTrue(
            mitZerstoerterLinie.exceptionOrNull()?.message.orEmpty().contains("intakte Handelslinie"),
        )
    }

    @Test
    fun eckgebaeudeHaeltAufKuerzestemKantenwegMindestensDreiKantenAbstand() {
        val bestehend = KartenEcke(6, 4)
        val zuNah = KartenEcke(10, 4)
        val linien = listOf(
            KartenKante.zwischen(KartenEcke(6, 4), KartenEcke(8, 4)),
            KartenKante.zwischen(KartenEcke(8, 4), KartenEcke(10, 4)),
        )
        val karte = requireNotNull(zustand().karte)
        val start = zustand().copy(
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(bestehend, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    ),
                    kanten = linien.map(::KantenBelegung),
                ),
            ),
        )

        val ergebnis = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeGebaut(anna, zuNah, EckGebaeudeTyp.BAHNHOF),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("mindestens drei Kanten"))
    }

    @Test
    fun geplanteHandelslinieErmoeglichtEckgebaeudeBeiGenauDreiKantenAbstand() {
        val bestehend = KartenEcke(6, 4)
        val ziel = KartenEcke(12, 4)
        val bestehendeLinien = listOf(
            KartenKante.zwischen(KartenEcke(6, 4), KartenEcke(8, 4)),
            KartenKante.zwischen(KartenEcke(8, 4), KartenEcke(10, 4)),
        )
        val geplanteLinie = KartenKante.zwischen(KartenEcke(10, 4), KartenEcke(12, 4))
        val karte = requireNotNull(zustand().karte)
        val start = zustand().copy(
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(bestehend, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    ),
                    kanten = bestehendeLinien.map(::KantenBelegung),
                ),
            ),
        )

        val nachHandelslinie = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.SchieneGebaut(anna, geplanteLinie),
        ).getOrThrow()
        assertEquals(
            anna,
            nachHandelslinie.karte?.belegung?.kantenNachPosition?.get(geplanteLinie)?.erbautVon,
        )
        val danach = SpielRegelwerk.wendeAn(
            nachHandelslinie,
            SpielEreignis.EckGebaeudeGebaut(anna, ziel, EckGebaeudeTyp.BAHNHOF),
        ).getOrThrow()

        assertEquals(
            EckGebaeudeTyp.BAHNHOF,
            danach.karte?.belegung?.eckenNachPosition?.get(ziel)?.typ,
        )
    }

    @Test
    fun schieneIstNurZwischenZweiGelaendefeldernErlaubt() {
        val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
        val kante = feld.kanten().first()
        val nurEinLandfeld = zustand().copy(
            karte = Spielkarte(
                id = "kueste",
                name = "Küste",
                hexagon = KartenHexagon(radius = 12),
                gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.EBENE)),
            ),
        )

        val ergebnis = SpielRegelwerk.wendeAn(
            nurEinLandfeld,
            SpielEreignis.SchieneGebaut(anna, kante),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("zwei Geländefeldern"))
    }

    @Test
    fun eckBauwerkeBegrenzenSchienenrichtungen() {
        val ecke = KartenEcke(8, 4)
        listOf(
            EckGebaeudeTyp.BAHNHOF to 3,
            EckGebaeudeTyp.GROSSBAHNHOF to 4,
            EckGebaeudeTyp.HAFEN to 2,
            EckGebaeudeTyp.GROSSHAFEN to 2,
        ).forEach { (typ, maximal) ->
            val start = zustand().mitEckBauwerk(ecke, typ, anna)
            val richtungen = start.schienenrichtungen(ecke)
            val mitMaximalerBelegung = richtungen.take(maximal).fold(start) { aktuell, kante ->
                SpielRegelwerk.wendeAn(
                    aktuell,
                    SpielEreignis.SchieneGebaut(anna, kante),
                ).getOrThrow()
            }

            val zuViel = SpielRegelwerk.wendeAn(
                mitMaximalerBelegung,
                SpielEreignis.SchieneGebaut(anna, richtungen[maximal]),
            )

            assertEquals(maximal, mitMaximalerBelegung.karte?.belegung?.kanten?.size)
            assertTrue(zuViel.isFailure)
            assertTrue(zuViel.exceptionOrNull()?.message.orEmpty().contains("höchstens $maximal"))
        }
    }

    @Test
    fun hauptbahnhofErlaubtAlleSechsSchienenrichtungen() {
        val ecke = KartenEcke(8, 4)
        val start = zustand().mitEckBauwerk(ecke, EckGebaeudeTyp.HAUPTBAHNHOF, anna)

        val danach = start.schienenrichtungen(ecke).fold(start) { aktuell, kante ->
            SpielRegelwerk.wendeAn(
                aktuell,
                SpielEreignis.SchieneGebaut(anna, kante),
            ).getOrThrow()
        }

        assertEquals(6, danach.karte?.belegung?.kanten?.size)
    }

    @Test
    fun schienenkreuzungOhneBahnhofWirdAbgelehnt() {
        val bahnhof = KartenEcke(6, 4)
        val kreuzung = KartenEcke(8, 4)
        val start = zustand().mitEckBauwerk(bahnhof, EckGebaeudeTyp.BAHNHOF, anna)
        val zumKnoten = KartenKante.zwischen(bahnhof, kreuzung)
        val vorbereitet = start.copy(
            karte = start.karte?.copy(
                belegung = start.karte.belegung.copy(
                    kanten = listOf(KantenBelegung(zumKnoten)),
                ),
            ),
        )
        val abzweige = vorbereitet.schienenrichtungen(kreuzung)
            .filterNot { kante -> kante == zumKnoten }
        val mitFortsetzung = SpielRegelwerk.wendeAn(
            vorbereitet,
            SpielEreignis.SchieneGebaut(anna, abzweige.first()),
        ).getOrThrow()

        val kreuzungErgebnis = SpielRegelwerk.wendeAn(
            mitFortsetzung,
            SpielEreignis.SchieneGebaut(anna, abzweige[1]),
        )

        assertTrue(kreuzungErgebnis.isFailure)
        assertTrue(
            kreuzungErgebnis.exceptionOrNull()?.message.orEmpty().contains("Schienenkreuzung"),
        )
    }

    @Test
    fun offeneHandelslinieBleibtNeutralAberNurAnnaDarfSieAbbauen() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val kante = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten().first()
        val mitHauptbahnhof = start.copy(
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(kante.anfang, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    ),
                ),
            ),
        )

        val danach = SpielRegelwerk.wendeAn(
            mitHauptbahnhof,
            SpielEreignis.SchieneGebaut(anna, kante),
        ).getOrThrow()

        assertNull(KartenAuswertung.gewalthaber(requireNotNull(danach.karte), kante))
        assertEquals(
            setOf(anna),
            KartenAuswertung.abrissberechtigteSpieler(requireNotNull(danach.karte), kante),
        )
        assertNull(danach.spieler.first { it.id == anna }.bauteile[BauteilTyp.EISENBAHNLINIE])
        assertEquals(9, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(9, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.STAHL])
    }

    @Test
    fun neutraleHandelslinieZwischenVerschiedenenSpielernDuerfenBeideAbbauen() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val ecken = listOf(
            de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke(6, 4),
            de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke(8, 4),
            de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke(10, 4),
            de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke(12, 4),
        )
        val linien = ecken.zipWithNext(KartenKante::zwischen)
        val gemeinsam = start.copy(
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(ecken.first(), EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                        EckBelegung(ecken.last(), EckGebaeudeTyp.HAUPTBAHNHOF, bert),
                    ),
                    kanten = linien.map(::KantenBelegung),
                ),
            ),
        )

        val durchAnna = SpielRegelwerk.wendeAn(
            gemeinsam,
            SpielEreignis.KartenBelegungEntfernt(
                spieler = anna,
                ort = KartenOrt.Kante(linien.first()),
            ),
        ).getOrThrow()
        val durchBert = SpielRegelwerk.wendeAn(
            gemeinsam.copy(aktiverSpieler = bert, zugStatus = epizug(bert)),
            SpielEreignis.KartenBelegungEntfernt(
                spieler = bert,
                ort = KartenOrt.Kante(linien.last()),
            ),
        ).getOrThrow()

        assertEquals(linien.size - 1, durchAnna.karte?.belegung?.kanten?.size)
        assertEquals(linien.size - 1, durchBert.karte?.belegung?.kanten?.size)
    }

    @Test
    fun kontrollierteHandelslinieZwischenEigenenBauwerkenDarfNurBesitzerAbbauen() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val ecken = listOf(KartenEcke(6, 4), KartenEcke(8, 4), KartenEcke(10, 4))
        val linien = ecken.zipWithNext(KartenKante::zwischen)
        val annasRoute = start.copy(
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(ecken.first(), EckGebaeudeTyp.BAHNHOF, anna),
                        EckBelegung(ecken.last(), EckGebaeudeTyp.BAHNHOF, anna),
                    ),
                    kanten = linien.map(::KantenBelegung),
                ),
            ),
        )
        val abbauen = SpielEreignis.KartenBelegungEntfernt(
            spieler = anna,
            ort = KartenOrt.Kante(linien.first()),
        )
        val durchAnna = SpielRegelwerk.wendeAn(annasRoute, abbauen)
        val durchBert = SpielRegelwerk.wendeAn(
            annasRoute.copy(aktiverSpieler = bert, zugStatus = epizug(bert)),
            abbauen.copy(spieler = bert),
        )

        assertTrue(durchAnna.isSuccess)
        assertTrue(durchBert.isFailure)
        assertTrue(durchBert.exceptionOrNull()?.message.orEmpty().contains("Besitzer"))
    }

    @Test
    fun hafenUndGrosshafenErlaubenZweiZuVierBisVierZuZwei() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val hafenKante = benachbarteEcken(ecke)
            .map { nachbar -> KartenKante.zwischen(ecke, nachbar) }
            .first()
        val alleNachbarn = angrenzendeFelder(ecke)
        val linienNachbarn = angrenzendeFelder(hafenKante)
        listOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN).forEach { typ ->
            (2..4).forEach { landAnzahl ->
                val kuestenLand = (linienNachbarn + alleNachbarn)
                    .distinct()
                    .take(landAnzahl)
                    .map { feld -> GelaendeFeld(feld, GelaendeTyp.EBENE) }
                val start = zustand().copy(
                    karte = Spielkarte(
                        id = "kueste-$typ-$landAnzahl",
                        name = "Küste",
                        hexagon = KartenHexagon(radius = 12),
                        gelaendefelder = kuestenLand,
                        belegung = KartenBelegung(
                            kanten = listOf(KantenBelegung(hafenKante)),
                        ),
                    ),
                )

                val mitHafen = SpielRegelwerk.wendeAn(
                    start,
                    SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.HAFEN),
                ).getOrThrow()
                val danach = if (typ == EckGebaeudeTyp.GROSSHAFEN) {
                    SpielRegelwerk.wendeAn(
                        mitHafen,
                        SpielEreignis.EckGebaeudeAufgewertet(
                            spieler = anna,
                            ecke = ecke,
                            zu = EckGebaeudeTyp.GROSSHAFEN,
                        ),
                    ).getOrThrow()
                } else {
                    mitHafen
                }

                assertEquals(typ, danach.karte?.belegung?.ecken?.single()?.typ)
            }
        }
    }

    @Test
    fun hafenBrauchtMindestensZweiWasserfelder() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val hafenKante = benachbarteEcken(ecke)
            .map { nachbar -> KartenKante.zwischen(ecke, nachbar) }
            .first()
        val fuenfLandfelder = (angrenzendeFelder(hafenKante) + angrenzendeFelder(ecke))
            .distinct()
            .take(5)
            .map { feld -> GelaendeFeld(feld, GelaendeTyp.EBENE) }
        val start = zustand().copy(
            karte = Spielkarte(
                id = "fast-nur-land",
                name = "Fast nur Land",
                hexagon = KartenHexagon(radius = 12),
                gelaendefelder = fuenfLandfelder,
                belegung = KartenBelegung(kanten = listOf(KantenBelegung(hafenKante))),
            ),
        )

        val ergebnis = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.HAFEN),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("zwei Wasser"))
    }

    @Test
    fun neutraleAnlageHatKeinenBesitzerUndKannNichtDoppeltStehen() {
        val start = zustand()
        val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
        val ereignis = SpielEreignis.NeutraleAnlageErrichtet(
            anna,
            feld,
            FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG),
        )
        val danach = SpielRegelwerk.wendeAn(start, ereignis).getOrThrow()

        assertEquals(1, danach.karte?.belegung?.felder?.size)
        assertTrue(SpielRegelwerk.wendeAn(danach, ereignis).isFailure)
    }

    @Test
    fun wirtschaftsstandortBuchtBeimBauDreiHolzUndZweiZiegel() {
        val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
        val anlagen = listOf(
            FeldAnlage.Geschaeftsbank,
            FeldAnlage.Wirtschaftsregion(BauteilTyp.RAFFINERIE),
        )

        anlagen.forEach { anlage ->
            val danach = SpielRegelwerk.wendeAn(
                zustand(),
                SpielEreignis.NeutraleAnlageErrichtet(anna, feld, anlage),
            ).getOrThrow()

            val rohstoffe = danach.spieler.first { it.id == anna }.rohstoffe
            assertEquals(7, rohstoffe[Rohstoff.HOLZ])
            assertEquals(8, rohstoffe[Rohstoff.ZIEGEL])
            assertEquals(10, rohstoffe[Rohstoff.STAHL])
        }
    }

    @Test
    fun anglerProduziertNahrungUndKannNurAufTeichfeldernGebautWerden() {
        val mitte = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val teich = Spezialfeld(SpezialfeldTyp.TEICH, mitte)
        val teichfeld = teich.positionen.first()
        val normalesFeld = KartenFeld(5, 5, DreieckHaelfte.UNTEN)
        val start = zustand().mitSpezialfeld(teich)
        val angler = FeldAnlage.Wirtschaftsregion(BauteilTyp.ANGLER)

        val danach = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.NeutraleAnlageErrichtet(anna, teichfeld, angler),
        ).getOrThrow()

        assertEquals(BauteilTyp.ANGLER, (danach.karte?.belegung?.felder?.single()?.anlage as FeldAnlage.Wirtschaftsregion).bauteil)
        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), BauteilTyp.ANGLER.ertrag)
        assertTrue(
            SpielRegelwerk.wendeAn(
                start,
                SpielEreignis.NeutraleAnlageErrichtet(anna, normalesFeld, angler),
            ).isFailure,
        )
        assertTrue(
            SpielRegelwerk.wendeAn(
                start,
                SpielEreignis.NeutraleAnlageErrichtet(
                    anna,
                    teichfeld,
                    FeldAnlage.Wirtschaftsregion(BauteilTyp.VIEHHOF),
                ),
            ).isFailure,
        )
    }

    @Test
    fun kartenereignisIstRueckgaengigUndWiederholbar() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val start = zustand().mitHandelslinieZu(ecke)
        val ablauf = SpielAblauf(start)
        ablauf.ereignisAnwenden(
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.BAHNHOF),
        ).getOrThrow()

        val zurueck = ablauf.rueckgaengig()
        val wieder = ablauf.wiederholen().getOrThrow()

        assertTrue(zurueck.karte?.belegung?.ecken.orEmpty().isEmpty())
        assertEquals(1, wieder.karte?.belegung?.ecken?.size)
    }

    @Test
    fun grossgebaeudeKoennenNurDurchAufwertungEntstehen() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val start = zustand().mitHandelslinieZu(ecke)

        listOf(EckGebaeudeTyp.GROSSBAHNHOF, EckGebaeudeTyp.GROSSHAFEN).forEach { typ ->
            val direktbau = SpielRegelwerk.wendeAn(
                start,
                SpielEreignis.EckGebaeudeGebaut(anna, ecke, typ),
            )

            assertTrue(direktbau.isFailure)
            assertTrue(direktbau.exceptionOrNull()?.message.orEmpty().contains("Aufwertung"))
        }
    }

    @Test
    fun bahnhofWirdZuGrossbahnhofAufgewertet() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val basis = zustand()
        val start = basis.copy(
            spieler = basis.spieler.map { spieler ->
                if (spieler.id == anna) {
                    spieler.copy(bauteile = mapOf(BauteilTyp.BAHNHOF to 1))
                } else {
                    spieler
                }
            },
            karte = basis.karte?.copy(
                belegung = KartenBelegung(
                    ecken = listOf(EckBelegung(ecke, EckGebaeudeTyp.BAHNHOF, anna)),
                ),
            ),
        )

        val danach = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeAufgewertet(
                spieler = anna,
                ecke = ecke,
                zu = EckGebaeudeTyp.GROSSBAHNHOF,
            ),
        ).getOrThrow()

        assertEquals(
            EckGebaeudeTyp.GROSSBAHNHOF,
            danach.karte?.belegung?.eckenNachPosition?.get(ecke)?.typ,
        )
        val bauteile = danach.spieler.first { it.id == anna }.bauteile
        assertEquals(null, bauteile[BauteilTyp.BAHNHOF])
        assertEquals(1, bauteile[BauteilTyp.GROSSBAHNHOF])
        val rohstoffe = danach.spieler.first { it.id == anna }.rohstoffe
        assertEquals(6, rohstoffe[Rohstoff.HOLZ])
        assertEquals(7, rohstoffe[Rohstoff.ZIEGEL])
        assertEquals(8, rohstoffe[Rohstoff.STAHL])
    }

    @Test
    fun frachtschiffVerbindetEigeneHaefenUndBeachtetKapazitaet() {
        val start = zustand()
        val wasserKante = requireNotNull(start.karte).wasserKanten().first()
        val hafenA = wasserKante.anfang
        val hafenB = wasserKante.ende
        val mitHaefen = start.copy(
            karte = start.karte?.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(hafenA, EckGebaeudeTyp.HAFEN, anna),
                        EckBelegung(hafenB, EckGebaeudeTyp.HAFEN, anna),
                    ),
                ),
            ),
        )
        val danach = SpielRegelwerk.wendeAn(
            mitHaefen,
            SpielEreignis.SeewegEingerichtet(
                id = "schiff-1",
                spieler = anna,
                hafenA = hafenA,
                hafenB = hafenB,
                richtung = FrachtRichtung.A_NACH_B,
            ),
        ).getOrThrow()

        val zweites = SpielRegelwerk.wendeAn(
            danach,
            SpielEreignis.SeewegEingerichtet(
                id = "schiff-2",
                spieler = anna,
                hafenA = hafenA,
                hafenB = hafenB,
                richtung = FrachtRichtung.B_NACH_A,
            ),
        )

        assertEquals(1, danach.karte?.belegung?.seewege?.size)
        assertTrue(zweites.isFailure)
        assertTrue(zweites.exceptionOrNull()?.message.orEmpty().contains("kapazität", ignoreCase = true))
    }

    @Test
    fun grosshafenHatKapazitaetFuerZweiFrachtschiffe() {
        val start = zustand()
        val wasserKante = requireNotNull(start.karte).wasserKanten().first()
        val hafenA = wasserKante.anfang
        val hafenB = wasserKante.ende
        val mitGrosshaefen = start.copy(
            karte = start.karte?.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(hafenA, EckGebaeudeTyp.GROSSHAFEN, anna),
                        EckBelegung(hafenB, EckGebaeudeTyp.GROSSHAFEN, anna),
                    ),
                ),
            ),
        )
        val erstes = SpielRegelwerk.wendeAn(
            mitGrosshaefen,
            SpielEreignis.SeewegEingerichtet(
                id = "grosshafen-schiff-1",
                spieler = anna,
                hafenA = hafenA,
                hafenB = hafenB,
                richtung = FrachtRichtung.A_NACH_B,
            ),
        ).getOrThrow()
        val zweites = SpielRegelwerk.wendeAn(
            erstes,
            SpielEreignis.SeewegEingerichtet(
                id = "grosshafen-schiff-2",
                spieler = anna,
                hafenA = hafenA,
                hafenB = hafenB,
                richtung = FrachtRichtung.B_NACH_A,
            ),
        ).getOrThrow()
        val drittes = SpielRegelwerk.wendeAn(
            zweites,
            SpielEreignis.SeewegEingerichtet(
                id = "grosshafen-schiff-3",
                spieler = anna,
                hafenA = hafenA,
                hafenB = hafenB,
                richtung = FrachtRichtung.A_NACH_B,
            ),
        )

        assertEquals(2, zweites.karte?.belegung?.seewege?.size)
        assertTrue(drittes.isFailure)
        assertTrue(drittes.exceptionOrNull()?.message.orEmpty().contains("kapazität", ignoreCase = true))
    }

    @Test
    fun frachtschiffRouteKannZwischenEigenenHaefenGeaendertWerden() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val ersteKante = karte.wasserKanten().first()
        val zweiteKante = karte.wasserKanten().first { kandidat ->
            kandidat != ersteKante &&
                setOf(kandidat.anfang, kandidat.ende)
                    .intersect(setOf(ersteKante.anfang, ersteKante.ende)).size == 1
        }
        val mitte = setOf(ersteKante.anfang, ersteKante.ende)
            .intersect(setOf(zweiteKante.anfang, zweiteKante.ende)).single()
        val hafenA = if (ersteKante.anfang == mitte) ersteKante.ende else ersteKante.anfang
        val hafenB = mitte
        val hafenC = if (zweiteKante.anfang == mitte) zweiteKante.ende else zweiteKante.anfang
        val mitHaefen = start.copy(
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(hafenA, hafenB, hafenC).map { ecke ->
                        EckBelegung(ecke, EckGebaeudeTyp.HAFEN, anna)
                    },
                ),
            ),
        )
        val eingerichtet = SpielRegelwerk.wendeAn(
            mitHaefen,
            SpielEreignis.SeewegEingerichtet(
                id = "schiff-1",
                spieler = anna,
                hafenA = hafenA,
                hafenB = hafenB,
                richtung = FrachtRichtung.A_NACH_B,
            ),
        ).getOrThrow()

        val danach = SpielRegelwerk.wendeAn(
            eingerichtet,
            SpielEreignis.SeewegRouteGeaendert(
                spieler = anna,
                id = "schiff-1",
                hafenA = hafenB,
                hafenB = hafenC,
            ),
        ).getOrThrow()

        val route = danach.karte?.belegung?.seewege?.single()
        assertEquals(hafenB, route?.hafenA)
        assertEquals(hafenC, route?.hafenB)
        assertEquals(
            eingerichtet.spieler.first { it.id == anna }.rohstoffe,
            danach.spieler.first { it.id == anna }.rohstoffe,
        )
    }

    @Test
    fun ueberlegeneKriegseinheitUeberlebtDasKriegsende() {
        val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
        val imKrieg = zustand().copy(konflikte = setOf(Konflikt(anna, bert)))
        val mitPanzer = SpielRegelwerk.wendeAn(
            imKrieg,
            SpielEreignis.KriegsEinheitEingesetzt(
                id = "panzer-1",
                spieler = anna,
                gegner = bert,
                typ = KriegsEinheitTyp.PANZER,
                // Kompatibilitätsereignisse mit altem Feldort werden auf eine Kante überführt.
                ort = KartenOrt.Feld(feld),
            ),
        ).getOrThrow()
        val nachFrieden = SpielRegelwerk.wendeAn(
            mitPanzer,
            SpielEreignis.KriegBeendet(anna, bert),
        ).getOrThrow()

        assertEquals(1, mitPanzer.karte?.belegung?.kriegseinheiten?.size)
        assertEquals(1, nachFrieden.karte?.belegung?.kriegseinheiten?.size)
    }

    @Test
    fun kriegsendeWertetPanzerUndKriegsschiffeGetrenntAus() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val panzerKanten = karte.gelaendefelder
            .flatMap { feld -> feld.position.kanten() }
            .distinct()
            .take(8)
        val schiffsKanten = karte.wasserKanten().take(6)
        val panzer = panzerKanten.mapIndexed { index, kante ->
            KriegsEinheitBelegung(
                id = "panzer-$index",
                typ = KriegsEinheitTyp.PANZER,
                besitzer = if (index < 5) anna else bert,
                ort = KartenOrt.Kante(kante),
            )
        }
        val schiffe = schiffsKanten.mapIndexed { index, kante ->
            KriegsEinheitBelegung(
                id = "kriegsschiff-$index",
                typ = KriegsEinheitTyp.KRIEGSSCHIFF,
                besitzer = if (index < 3) anna else bert,
                ort = KartenOrt.Kante(kante),
            )
        }
        val imKrieg = start.copy(
            konflikte = setOf(Konflikt(anna, bert)),
            karte = karte.copy(
                belegung = KartenBelegung(kriegseinheiten = panzer + schiffe),
            ),
        )

        val danach = SpielRegelwerk.wendeAn(
            imKrieg,
            SpielEreignis.KriegBeendet(anna, bert),
        ).getOrThrow()

        val ueberlebende = danach.karte?.belegung?.kriegseinheiten.orEmpty()
        assertEquals(3, ueberlebende.count { it.typ == KriegsEinheitTyp.PANZER })
        assertTrue(ueberlebende.all { it.besitzer == anna })
        assertEquals(0, ueberlebende.count { it.typ == KriegsEinheitTyp.KRIEGSSCHIFF })
    }

    @Test
    fun spielerBautPanzerOhneKonfliktAufEigenerHandelslinie() {
        val kante = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten().first()

        val danach = SpielRegelwerk.wendeAn(
            zustand().mitKontrollierterHandelslinie(kante, anna),
            SpielEreignis.KriegsEinheitGebaut(
                id = "panzer-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.PANZER,
                kante = kante,
            ),
        ).getOrThrow()

        val panzer = danach.karte?.belegung?.kriegseinheiten?.single()
        assertEquals(kante, panzer?.position)
        assertEquals(null, panzer?.gegner)
    }

    @Test
    fun panzerBewegtSichKantenweiseUndVerbrauchtJeSchrittDiesel() {
        val kanten = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten()
        val mitPanzer = SpielRegelwerk.wendeAn(
            zustand().mitKontrollierterHandelslinie(kanten[0], anna),
            SpielEreignis.KriegsEinheitGebaut(
                id = "panzer-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.PANZER,
                kante = kanten[0],
            ),
        ).getOrThrow()

        val danach = SpielRegelwerk.wendeAn(
            mitPanzer,
            SpielEreignis.KriegsEinheitBewegt(
                spieler = anna,
                id = "panzer-anna-1",
                weg = listOf(kanten[1], kanten[2]),
            ),
        ).getOrThrow()

        assertEquals(kanten[2], danach.karte?.belegung?.kriegseinheiten?.single()?.position)
        assertEquals(8, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.DIESEL])
    }

    @Test
    fun mehrereTruppenStehenAufEinerKanteUndBewegenSichGemeinsam() {
        val kanten = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten()
        val start = zustand().mitKontrollierterHandelslinie(kanten[0], anna)
        val mitErstemPanzer = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.KriegsEinheitGebaut(
                id = "panzer-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.PANZER,
                kante = kanten[0],
            ),
        ).getOrThrow()
        val mitStapel = SpielRegelwerk.wendeAn(
            mitErstemPanzer,
            SpielEreignis.KriegsEinheitGebaut(
                id = "panzer-anna-2",
                spieler = anna,
                typ = KriegsEinheitTyp.PANZER,
                kante = kanten[0],
            ),
        ).getOrThrow()

        val danach = SpielRegelwerk.wendeAn(
            mitStapel,
            SpielEreignis.KriegsEinheitenBewegt(
                spieler = anna,
                ids = listOf("panzer-anna-1", "panzer-anna-2"),
                weg = listOf(kanten[1]),
            ),
        ).getOrThrow()

        assertEquals(2, mitStapel.karte?.belegung?.kriegseinheiten?.count {
            it.position == kanten[0]
        })
        assertTrue(danach.karte?.belegung?.kriegseinheiten.orEmpty().all {
            it.position == kanten[1]
        })
        assertEquals(8, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.DIESEL])
    }

    @Test
    fun bewegungOhneGenugTreibstoffAendertWederBestandNochPosition() {
        val kanten = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten()
        val start = zustand().mitKontrollierterHandelslinie(kanten[0], anna).copy(
            spieler = zustand().spieler.map { spieler ->
                if (spieler.id == anna) {
                    spieler.copy(rohstoffe = spieler.rohstoffe + (Rohstoff.DIESEL to 1))
                } else {
                    spieler
                }
            },
        )
        val mitPanzer = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.KriegsEinheitGebaut(
                id = "panzer-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.PANZER,
                kante = kanten[0],
            ),
        ).getOrThrow()

        val ergebnis = SpielRegelwerk.wendeAn(
            mitPanzer,
            SpielEreignis.KriegsEinheitBewegt(
                spieler = anna,
                id = "panzer-anna-1",
                weg = listOf(kanten[1], kanten[2]),
            ),
        )

        assertTrue(ergebnis.isFailure)
        assertEquals(kanten[0], mitPanzer.karte?.belegung?.kriegseinheiten?.single()?.position)
        assertEquals(1, mitPanzer.spieler.first { it.id == anna }.rohstoffe[Rohstoff.DIESEL])
    }

    @Test
    fun kriegsschiffBefaehrtWasserkantenMitSchweroel() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val wasserfeld = karte.hexagon.felder().first { feld ->
            feld !in karte.landNachPosition
        }
        val kanten = wasserfeld.kanten()
        val mitSchiff = SpielRegelwerk.wendeAn(
            start.mitEigenemHafenAn(kanten[0], anna),
            SpielEreignis.KriegsEinheitGebaut(
                id = "kriegsschiff-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.KRIEGSSCHIFF,
                kante = kanten[0],
            ),
        ).getOrThrow()

        val danach = SpielRegelwerk.wendeAn(
            mitSchiff,
            SpielEreignis.KriegsEinheitBewegt(
                spieler = anna,
                id = "kriegsschiff-anna-1",
                weg = listOf(kanten[1]),
            ),
        ).getOrThrow()

        assertEquals(kanten[1], danach.karte?.belegung?.kriegseinheiten?.single()?.position)
        assertEquals(9, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.SCHWEROEL])
    }

    @Test
    fun panzerKannNichtOhneEigeneHandelslinieGebautWerden() {
        val kante = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten().first()

        val ergebnis = SpielRegelwerk.wendeAn(
            zustand(),
            SpielEreignis.KriegsEinheitGebaut(
                id = "panzer-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.PANZER,
                kante = kante,
            ),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("eigenen"))
    }

    @Test
    fun kriegsschiffKannNichtOhneEigenenHafenGebautWerden() {
        val karte = requireNotNull(zustand().karte)
        val wasserKante = karte.wasserKanten().first()

        val ergebnis = SpielRegelwerk.wendeAn(
            zustand(),
            SpielEreignis.KriegsEinheitGebaut(
                id = "kriegsschiff-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.KRIEGSSCHIFF,
                kante = wasserKante,
            ),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("Hafen"))
    }

    @Test
    fun fremdeHandelslinieIstNurImKriegBefahrbar() {
        val kanten = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten()
        val startKante = kanten[0]
        val fremdeKante = kanten[1]
        val basis = zustand().mitKontrollierterHandelslinie(fremdeKante, bert)
        val mitPanzer = basis.copy(
            karte = basis.karte?.copy(
                belegung = basis.karte.belegung.copy(
                    kriegseinheiten = listOf(
                        KriegsEinheitBelegung(
                            id = "panzer-anna-1",
                            typ = KriegsEinheitTyp.PANZER,
                            besitzer = anna,
                            ort = KartenOrt.Kante(startKante),
                        ),
                    ),
                ),
            ),
        )
        val bewegung = SpielEreignis.KriegsEinheitBewegt(
            spieler = anna,
            id = "panzer-anna-1",
            weg = listOf(fremdeKante),
        )

        val imFrieden = SpielRegelwerk.wendeAn(mitPanzer, bewegung)
        val imKrieg = SpielRegelwerk.wendeAn(
            mitPanzer.copy(konflikte = setOf(Konflikt(anna, bert))),
            bewegung,
        ).getOrThrow()

        assertTrue(imFrieden.isFailure)
        assertTrue(imFrieden.exceptionOrNull()?.message.orEmpty().contains("während eines Krieges"))
        assertEquals(fremdeKante, imKrieg.karte?.belegung?.kriegseinheiten?.single()?.position)
    }

    @Test
    fun truppentypMussZumAngrenzendenGelaendePassen() {
        val reineLandkante = KartenFeld(2, 2, DreieckHaelfte.UNTEN).kanten().first()

        val ergebnis = SpielRegelwerk.wendeAn(
            zustand(),
            SpielEreignis.KriegsEinheitGebaut(
                id = "kriegsschiff-anna-1",
                spieler = anna,
                typ = KriegsEinheitTyp.KRIEGSSCHIFF,
                kante = reineLandkante,
            ),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("Wasser"))
    }

    @Test
    fun zerstoertesEckgebaeudeEntferntNichtMehrVerbundeneSchienen() {
        val start = zustand()
        val karte = requireNotNull(start.karte)
        val hauptbahnhof = KartenFeld(1, 1, DreieckHaelfte.UNTEN).ecken().first()
        val verbundeneSchiene = benachbarteEcken(hauptbahnhof)
            .map { nachbar ->
                KartenKante.zwischen(hauptbahnhof, nachbar)
            }
            .first { kante -> angrenzendeFelder(kante).all { it in karte.landNachPosition } }
        val bahnhof = KartenFeld(4, 4, DreieckHaelfte.OBEN).ecken().last()
        val unverbundeneSchiene = KartenFeld(4, 4, DreieckHaelfte.UNTEN).kanten().first()
        val vorbereitet = start.copy(
            spieler = start.spieler.map { spieler ->
                if (spieler.id == anna) {
                    spieler.copy(
                        bauteile = mapOf(
                            BauteilTyp.BAHNHOF to 1,
                        ),
                    )
                } else spieler
            },
            karte = karte.copy(
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(hauptbahnhof, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                        EckBelegung(bahnhof, EckGebaeudeTyp.BAHNHOF, anna),
                    ),
                    kanten = listOf(
                        KantenBelegung(verbundeneSchiene),
                        KantenBelegung(unverbundeneSchiene),
                    ),
                ),
            ),
        )

        val danach = SpielRegelwerk.wendeAn(
            vorbereitet,
            SpielEreignis.KartenBauwerkZustandGeaendert(
                spieler = anna,
                ort = KartenOrt.Ecke(bahnhof),
                zustand = BauwerkZustand.ZERSTOERT,
                grund = KartenAenderungsGrund.REGELFOLGE,
            ),
        ).getOrThrow()

        assertEquals(listOf(verbundeneSchiene), danach.karte?.belegung?.kanten?.map { it.position })
        assertEquals(null, danach.spieler.first { it.id == anna }.bauteile[BauteilTyp.EISENBAHNLINIE])
        assertEquals(
            BauwerkZustand.ZERSTOERT,
            danach.karte?.belegung?.eckenNachPosition?.get(bahnhof)?.zustand,
        )
    }

    @Test
    fun belagerungBrauchtEinenBestehendenKonflikt() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val start = zustand().copy(
            aktiverSpieler = bert,
            zugStatus = epizug(bert),
            karte = zustand().karte?.copy(
                belegung = KartenBelegung(
                    ecken = listOf(EckBelegung(ecke, EckGebaeudeTyp.BAHNHOF, anna)),
                ),
            ),
        )
        val belagerung = SpielEreignis.KartenBauwerkZustandGeaendert(
            spieler = bert,
            ort = KartenOrt.Ecke(ecke),
            zustand = BauwerkZustand.BELAGERT,
            grund = KartenAenderungsGrund.BELAGERUNG,
        )

        assertTrue(SpielRegelwerk.wendeAn(start, belagerung).isFailure)
        val danach = SpielRegelwerk.wendeAn(
            start.copy(konflikte = setOf(Konflikt(anna, bert))),
            belagerung,
        ).getOrThrow()
        assertEquals(
            BauwerkZustand.BELAGERT,
            danach.karte?.belegung?.eckenNachPosition?.get(ecke)?.zustand,
        )
    }

    private fun zustand(rundeNull: Boolean = false): SpielZustand {
        val land = buildList {
            repeat(6) { zeile ->
                repeat(6) { spalte ->
                    DreieckHaelfte.entries.forEach { haelfte ->
                        add(GelaendeFeld(KartenFeld(zeile, spalte, haelfte), GelaendeTyp.EBENE))
                    }
                }
            }
        }
        val rohstoffe = Rohstoff.entries.associateWith { 10 }
        return SpielZustand(
            spieler = listOf(
                Spieler(id = anna, name = "Anna", rohstoffe = rohstoffe),
                Spieler(id = bert, name = "Bert", rohstoffe = rohstoffe),
            ),
            karte = Spielkarte(
                id = "test",
                name = "Testkarte",
                hexagon = KartenHexagon(radius = 12),
                gelaendefelder = land,
            ),
            spielabschnitt = if (rundeNull) Spielabschnitt.RUNDE_NULL else Spielabschnitt.REGULAER,
            aktiverSpieler = anna,
            zugStatus = if (rundeNull) {
                ZugStatus(1L, anna, ZugPhase.Prozug)
            } else {
                epizug(anna)
            },
        )
    }

    private fun epizug(spieler: SpielerId) = ZugStatus(
        zugId = 1L,
        spieler = spieler,
        phase = ZugPhase.Epizug,
        prozug = ProzugStatus(begonnen = true, erfolgreichAbgeschlossen = true),
    )

    private fun SpielZustand.mitSpezialfeld(spezialfeld: Spezialfeld): SpielZustand {
        val karte = requireNotNull(karte)
        return copy(karte = karte.copy(spezialfelder = listOf(spezialfeld)))
    }

    private fun SpielZustand.mitHandelslinieZu(
        ecke: KartenEcke,
        linienZustand: BauwerkZustand = BauwerkZustand.INTAKT,
    ): SpielZustand {
        val karte = requireNotNull(karte)
        val kante = benachbarteEcken(ecke)
            .asSequence()
            .map { nachbar -> KartenKante.zwischen(ecke, nachbar) }
            .first { kandidat ->
                val nachbarn = angrenzendeFelder(kandidat)
                nachbarn.size == 2 && nachbarn.all { feld -> feld in karte.landNachPosition }
            }
        return copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    kanten = karte.belegung.kanten + KantenBelegung(kante, linienZustand),
                ),
            ),
        )
    }

    private fun SpielZustand.mitEckBauwerk(
        ecke: KartenEcke,
        typ: EckGebaeudeTyp,
        spieler: SpielerId,
    ): SpielZustand {
        val karte = requireNotNull(karte)
        return copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    ecken = karte.belegung.ecken + EckBelegung(ecke, typ, spieler),
                ),
            ),
        )
    }

    private fun SpielZustand.mitKontrollierterHandelslinie(
        kante: KartenKante,
        spieler: SpielerId,
    ): SpielZustand {
        val karte = requireNotNull(karte)
        return copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    ecken = karte.belegung.ecken + listOf(
                        EckBelegung(kante.anfang, EckGebaeudeTyp.BAHNHOF, spieler),
                        EckBelegung(kante.ende, EckGebaeudeTyp.BAHNHOF, spieler),
                    ),
                    kanten = karte.belegung.kanten + KantenBelegung(kante),
                ),
            ),
        )
    }

    private fun SpielZustand.mitEigenemHafenAn(
        kante: KartenKante,
        spieler: SpielerId,
    ): SpielZustand {
        val karte = requireNotNull(karte)
        return copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    ecken = karte.belegung.ecken +
                        EckBelegung(kante.anfang, EckGebaeudeTyp.HAFEN, spieler),
                ),
            ),
        )
    }

    private fun SpielZustand.schienenrichtungen(ecke: KartenEcke): List<KartenKante> {
        val karte = requireNotNull(karte)
        return benachbarteEcken(ecke)
            .map { nachbar -> KartenKante.zwischen(ecke, nachbar) }
            .filter { kante ->
                val nachbarn = angrenzendeFelder(kante)
                nachbarn.size == 2 && nachbarn.all { feld -> feld in karte.landNachPosition }
            }
    }
}
