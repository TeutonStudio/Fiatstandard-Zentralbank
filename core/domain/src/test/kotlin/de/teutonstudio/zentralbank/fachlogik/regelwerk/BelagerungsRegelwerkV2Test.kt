package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BelagerungsRegelwerkV2Test {
    private val angreifer = SpielerId("angreifer")
    private val verteidiger = SpielerId("verteidiger")

    @Test
    fun freieLinieVerhindertBelagerungUndUnterbrechungLoeschtGesamtenFortschritt() {
        val vollBlockiert = belagerungsZustand()
        val begonnen = BelagerungsRegelwerk.aktualisieren(
            vollBlockiert,
            SpielEreignis.BelagerungAktualisiert(KartenEcke(0, 0), rundeFortschreiben = true),
        )
        assertEquals(1, begonnen.belagerungen.single().fortschrittRunden)
        assertTrue(begonnen.belagerungen.single().gespeicherterErtrag.isNotEmpty())

        val freieLinie = begonnen.copy(
            karte = begonnen.karte!!.copy(
                belegung = begonnen.karte.belegung.copy(
                    kriegseinheiten = begonnen.karte.belegung.kriegseinheiten.drop(1),
                ),
            ),
        )
        val unterbrochen = BelagerungsRegelwerk.aktualisieren(
            freieLinie,
            SpielEreignis.BelagerungAktualisiert(KartenEcke(0, 0)),
        )

        assertTrue(unterbrochen.belagerungen.isEmpty())
        assertEquals(
            BauwerkZustand.INTAKT,
            unterbrochen.karte!!.belegung.eckenNachPosition.getValue(KartenEcke(0, 0)).zustand,
        )
        assertTrue(unterbrochen.spieler.single { it.id == angreifer }.rohstoffe.isEmpty())
    }

    @Test
    fun hauptbahnhofWirdNachSiebenVollenRundenRuineUndFuerBelagererAusgezahlt() {
        var zustand = belagerungsZustand()
        repeat(7) {
            zustand = BelagerungsRegelwerk.aktualisieren(
                zustand,
                SpielEreignis.BelagerungAktualisiert(
                    KartenEcke(0, 0),
                    rundeFortschreiben = true,
                ),
            )
        }

        val ruine = zustand.karte!!.belegung.eckenNachPosition.getValue(KartenEcke(0, 0))
        assertEquals(BauwerkZustand.ZERSTOERT, ruine.zustand)
        assertEquals(null, ruine.besitzer)
        assertTrue(zustand.belagerungen.isEmpty())
        assertTrue(zustand.spieler.single { it.id == angreifer }.rohstoffe.values.sum() > 0)
    }

    @Test
    fun ruineKannUeberFreieRouteRepariertOderOhneErtragAbgerissenWerden() {
        val basis = belagerungsZustand().let { start ->
            val ziel = KartenEcke(0, 0)
            val hq = start.karte!!.belegung.ecken.first { it.position != ziel }
            start.copy(
                spieler = start.spieler.map {
                    if (it.id == verteidiger) it.copy(
                        rohstoffe = mapOf(Rohstoff.ZIEGEL to 3, Rohstoff.KOHLE to 2),
                    ) else it
                },
                konflikte = emptySet(),
                karte = start.karte.copy(
                    belegung = start.karte.belegung.copy(
                        ecken = listOf(
                            hq.copy(typ = EckGebaeudeTyp.HAUPTBAHNHOF, besitzer = verteidiger),
                            EckBelegung(
                                ziel,
                                EckGebaeudeTyp.BAHNHOF,
                                besitzer = null,
                                zustand = BauwerkZustand.ZERSTOERT,
                            ),
                        ),
                        kriegseinheiten = emptyList(),
                    ),
                ),
            )
        }
        val repariert = KartenRegelwerk.verwaltungsruineReparieren(
            basis,
            SpielEreignis.VerwaltungsruineRepariert(verteidiger, KartenEcke(0, 0)),
        )
        assertEquals(
            verteidiger,
            repariert.karte!!.belegung.eckenNachPosition.getValue(KartenEcke(0, 0)).besitzer,
        )
        assertEquals(
            0,
            repariert.spieler.single { it.id == verteidiger }.rohstoffe.getOrDefault(Rohstoff.ZIEGEL, 0),
        )

        val abgerissen = KartenRegelwerk.verwaltungsruineAbreissen(
            basis,
            SpielEreignis.VerwaltungsruineAbgerissen(verteidiger, KartenEcke(0, 0)),
        )
        assertTrue(KartenEcke(0, 0) !in abgerissen.karte!!.belegung.eckenNachPosition)
        assertEquals(
            basis.spieler.single { it.id == verteidiger }.rohstoffe,
            abgerissen.spieler.single { it.id == verteidiger }.rohstoffe,
        )
    }

    private fun belagerungsZustand(): SpielZustand {
        val hexagon = KartenHexagon(radius = 2)
        val felder = hexagon.felder()
        val ziel = KartenEcke(0, 0)
        val linien = felder.flatMap { it.kanten() }.distinct().filter {
            it.anfang == ziel || it.ende == ziel
        }.sortedWith(compareBy({ it.anfang }, { it.ende }))
        val wirtschaft = felder.first { ziel in it.ecken() }
        val andereEcke = linien.first().let { if (it.anfang == ziel) it.ende else it.anfang }
        val karte = Spielkarte(
            id = "belagerung",
            name = "Belagerung",
            hexagon = hexagon,
            gelaendefelder = felder.map { GelaendeFeld(it, GelaendeTyp.EBENE) },
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(ziel, EckGebaeudeTyp.HAUPTBAHNHOF, verteidiger),
                    EckBelegung(andereEcke, EckGebaeudeTyp.HAUPTBAHNHOF, angreifer),
                ),
                kanten = linien.map { KantenBelegung(it, erbautVon = verteidiger) },
                felder = listOf(FeldBelegung(wirtschaft, FeldAnlage.Abbaueinheit(Rohstoff.KOHLE))),
                kriegseinheiten = linien.mapIndexed { index, linie ->
                    KriegsEinheitBelegung(
                        id = "panzer-$index",
                        typ = KriegsEinheitTyp.PANZER,
                        besitzer = angreifer,
                        ort = KartenOrt.Kante(linie),
                    )
                },
            ),
        )
        return SpielZustand(
            spieler = listOf(Spieler(angreifer, "A"), Spieler(verteidiger, "V")),
            karte = karte,
            konflikte = setOf(Konflikt(angreifer, verteidiger, KriegId("krieg"))),
        )
    }
}
