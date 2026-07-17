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
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.benachbarteEcken
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
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
    fun bahnhofBuchtKostenUndOrtAtomar() {
        val start = zustand()
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()

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
    }

    @Test
    fun fehlendeRohstoffeLassenAuchKarteUnveraendert() {
        val start = zustand().copy(
            spieler = listOf(
                Spieler(id = anna, name = "Anna"),
                Spieler(id = bert, name = "Bert"),
            ),
        )
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()

        val ergebnis = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.BAHNHOF),
        )

        assertTrue(ergebnis.isFailure)
        assertTrue(start.karte?.belegung?.ecken.orEmpty().isEmpty())
        assertTrue(start.spieler.first().bauteile.isEmpty())
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
    fun gebauteHandelslinieBleibtBesitzerlosAberUnterAnnasGewalt() {
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

        assertEquals(anna, KartenAuswertung.gewalthaber(requireNotNull(danach.karte), kante))
        assertNull(danach.spieler.first { it.id == anna }.bauteile[BauteilTyp.EISENBAHNLINIE])
        assertEquals(9, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(9, danach.spieler.first { it.id == anna }.rohstoffe[Rohstoff.STAHL])
    }

    @Test
    fun gemeinsamVerbundenesHandelsnetzDarfKeinerDerSpielerAbbauen() {
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

        val abbauen = SpielEreignis.KartenBelegungEntfernt(
            spieler = anna,
            ort = KartenOrt.Kante(linien.first()),
        )
        val ergebnis = SpielRegelwerk.wendeAn(gemeinsam, abbauen)

        assertTrue(ergebnis.isFailure)
        assertTrue(ergebnis.exceptionOrNull()?.message.orEmpty().contains("Gewalthaber"))
    }

    @Test
    fun hafenBrauchtZweiWasserUndZweiGelaendefelder() {
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
        val kuestenLand = angrenzendeFelder(ecke).take(2).map { feld ->
            GelaendeFeld(feld, GelaendeTyp.EBENE)
        }
        val start = zustand().copy(
            karte = Spielkarte(
                id = "kueste",
                name = "Küste",
                hexagon = KartenHexagon(radius = 12),
                gelaendefelder = kuestenLand,
            ),
        )

        val danach = SpielRegelwerk.wendeAn(
            start,
            SpielEreignis.EckGebaeudeGebaut(anna, ecke, EckGebaeudeTyp.HAFEN),
        ).getOrThrow()

        assertEquals(EckGebaeudeTyp.HAFEN, danach.karte?.belegung?.ecken?.single()?.typ)
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
    fun kartenereignisIstRueckgaengigUndWiederholbar() {
        val start = zustand()
        val ecke = KartenFeld(2, 2, DreieckHaelfte.UNTEN).ecken().first()
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
    fun frachtschiffVerbindetEigeneHaefenUndBeachtetKapazitaet() {
        val start = zustand()
        val hafenA = KartenFeld(1, 1, DreieckHaelfte.UNTEN).ecken().first()
        val hafenB = KartenFeld(4, 4, DreieckHaelfte.OBEN).ecken().last()
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
    fun kriegseinheitenExistierenNurWaehrendKonflikt() {
        val feld = KartenFeld(2, 2, DreieckHaelfte.UNTEN)
        val imKrieg = zustand().copy(konflikte = setOf(Konflikt(anna, bert)))
        val mitPanzer = SpielRegelwerk.wendeAn(
            imKrieg,
            SpielEreignis.KriegsEinheitEingesetzt(
                id = "panzer-1",
                spieler = anna,
                gegner = bert,
                typ = KriegsEinheitTyp.PANZER,
                ort = KartenOrt.Feld(feld),
            ),
        ).getOrThrow()
        val nachFrieden = SpielRegelwerk.wendeAn(
            mitPanzer,
            SpielEreignis.KriegBeendet(anna, bert),
        ).getOrThrow()

        assertEquals(1, mitPanzer.karte?.belegung?.kriegseinheiten?.size)
        assertTrue(nachFrieden.karte?.belegung?.kriegseinheiten.orEmpty().isEmpty())
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
            zugStatus = ZugStatus(bert, Phase.Aktionen),
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
            zugStatus = ZugStatus(anna, if (rundeNull) Phase.Einnahmen else Phase.Aktionen),
        )
    }
}
