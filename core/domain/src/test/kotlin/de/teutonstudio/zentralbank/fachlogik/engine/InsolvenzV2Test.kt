package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.*
import de.teutonstudio.zentralbank.fachlogik.regelwerk.InsolvenzRegelwerk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsolvenzV2Test {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")

    @Test
    fun schuldenstrichZahltGlaeubigerMitProtokollierterGeldschoepfungUndIstWiederholbar() {
        val start = zustandMitStandortenUndSchuld()
        val erster = StandardSpielEngine().anwenden(
            start,
            SpielAktion.SchuldenstrichDurchfuehren(anna),
        ).getOrThrow()

        assertTrue(erster.ereignisse.first() is SpielEreignis.ZentralbankgeldGeschoepft)
        assertEquals(Geld.mark(50), erster.zustand.zentralbankGeldschoepfungen.single().betrag)
        assertEquals(Geld.mark(50), erster.zustand.spieler.single { it.id == bert }.geldkonto)
        assertEquals(Geld.NULL, erster.zustand.spieler.single { it.id == anna }.geldkonto)
        assertEquals(7, erster.zustand.spieler.single { it.id == anna }.rohstoffe[Rohstoff.KOHLE])
        assertTrue(erster.zustand.anleihen.isEmpty())
        assertTrue(erster.zustand.karte!!.belegung.kriegseinheiten.none { it.besitzer == anna })
        assertTrue(erster.zustand.karte.belegung.kanten.all { it.erbautVon == null })

        val zweiter = InsolvenzRegelwerk.schuldenstrichBuchen(
            erster.zustand,
            SpielEreignis.Schuldenstrich(anna, 0),
        )
        assertEquals(2, zweiter.schuldenstriche.size)
        assertFalse(zweiter.karte!!.belegung.ecken.any {
            it.besitzer == anna && it.typ != EckGebaeudeTyp.HAUPTBAHNHOF
        })
    }

    @Test
    fun selbstGehalteneAnleiheErzeugtBeimSchuldenstrichKeinZentralbankgeld() {
        val fremd = zustandMitStandortenUndSchuld()
        val id = AnleiheId("schuld")
        val selbst = fremd.copy(
            spieler = fremd.spieler.map {
                when (it.id) {
                    anna -> it.copy(anleihen = listOf(id))
                    else -> it.copy(anleihen = emptyList())
                }
            },
        )
        val schritt = StandardSpielEngine().anwenden(
            selbst,
            SpielAktion.SchuldenstrichDurchfuehren(anna),
        ).getOrThrow()

        assertTrue(schritt.ereignisse.none { it is SpielEreignis.ZentralbankgeldGeschoepft })
        assertTrue(schritt.zustand.zentralbankGeldschoepfungen.isEmpty())
    }

    @Test
    fun zahlungsunfaehigkeitsPruefungLoestSchuldenstrichAutomatischVorAusscheidenAus() {
        val basis = zustandMitStandortenUndSchuld()
        val ohneSchuld = basis.copy(
            anleihen = emptyMap(),
            spieler = basis.spieler.map {
                it.copy(geldkonto = Geld.NULL, anleihen = emptyList())
            },
            zugStatus = ZugStatus(1L, anna, ZugPhase.Prozug),
        )
        val begonnen = StandardSpielEngine().anwenden(
            ohneSchuld,
            SpielAktion.ProzugBeginnen(1L),
        ).getOrThrow().zustand
        val aktion = SpielAktion.ZahlungsunfaehigkeitFeststellen(anna, 1L)

        assertTrue(aktion in StandardSpielEngine().erlaubteAktionen(begonnen, anna))
        val schritt = StandardSpielEngine().anwenden(begonnen, aktion).getOrThrow()

        assertTrue(schritt.ereignisse.any { it is SpielEreignis.Schuldenstrich })
        assertTrue(schritt.ereignisse.none { it is SpielEreignis.SpielerAusgeschieden })
        assertFalse(anna in schritt.zustand.ausgeschiedeneSpieler)
        assertEquals(bert, schritt.zustand.aktiverSpieler)
    }

    private fun zustandMitStandortenUndSchuld(): SpielZustand {
        val hexagon = KartenHexagon(radius = 3)
        val felder = hexagon.felder()
        val ecken = felder.flatMap { it.ecken() }.distinct().sorted()
        val feldMenge = felder.toSet()
        val kante = felder.flatMap { it.kanten() }.distinct().first {
            angrenzendeFelder(it).size == 2 && angrenzendeFelder(it).all(feldMenge::contains)
        }
        val schuld = Anleihe(AnleiheId("schuld"), anna, Geld.mark(50), 200, 3)
        return SpielZustand(
            spieler = listOf(
                Spieler(anna, "Anna", geldkonto = Geld.mark(20), rohstoffe = mapOf(Rohstoff.KOHLE to 7)),
                Spieler(bert, "Bert", anleihen = listOf(schuld.id)),
            ),
            karte = Spielkarte(
                id = "insolvenz",
                name = "Insolvenz",
                hexagon = hexagon,
                gelaendefelder = felder.map { GelaendeFeld(it, GelaendeTyp.EBENE) },
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(ecken[0], EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                        EckBelegung(ecken[1], EckGebaeudeTyp.GROSSBAHNHOF, anna),
                        EckBelegung(ecken[2], EckGebaeudeTyp.HAFEN, anna),
                        EckBelegung(ecken[3], EckGebaeudeTyp.HAUPTBAHNHOF, bert),
                    ),
                    kanten = listOf(KantenBelegung(kante, erbautVon = anna)),
                    kriegseinheiten = listOf(
                        KriegsEinheitBelegung(
                            "panzer", KriegsEinheitTyp.PANZER, anna, ort = KartenOrt.Kante(kante),
                        ),
                    ),
                ),
            ),
            anleihen = mapOf(schuld.id to schuld),
            zugStatus = ZugStatus(1L, anna, ZugPhase.Prozug, ProzugStatus(begonnen = true)),
        )
    }
}
