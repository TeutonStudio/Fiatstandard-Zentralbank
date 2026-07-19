package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SeewegBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.Spezialfeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import de.teutonstudio.zentralbank.fachlogik.modell.kuerzesterWasserweg
import de.teutonstudio.zentralbank.fachlogik.modell.wasserKanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Karten3DZuordnungTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")

    @Test
    fun wuesteWirdAlsSandfarbenesGelaendeDargestellt() {
        val feld = KartenHexagon(radius = 1).felder().first()
        val modell = Spielkarte(
            id = "wueste",
            name = "Wüste",
            gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.WUESTE)),
        ).zu3DModell()

        assertEquals("Wüste", modell.auflagen.single().typ.name)
        assertEquals(Color(0xFFD8B56A), modell.auflagen.single().typ.farbe)
    }

    @Test
    fun teichWirdAlsFlachesObjektAufDerSpezialfeldmitteDargestellt() {
        val mitte = KartenEcke(6, 4)
        val spezialfeld = Spezialfeld(SpezialfeldTyp.TEICH, mitte)
        val modell = Spielkarte(
            id = "teich",
            name = "Teich",
            hexagon = KartenHexagon(radius = 5),
            gelaendefelder = spezialfeld.positionen.map { position ->
                GelaendeFeld(position, GelaendeTyp.EBENE)
            },
            spezialfelder = listOf(spezialfeld),
        ).zu3DModell()

        val teich = modell.eckObjekte.single()
        assertEquals(mitte, teich.position)
        assertEquals(SpielObjektForm.TEICH, teich.typ.form)
    }

    @Test
    fun schienenNutzenJeNachEndpunktenSpielerfarbeOderNeutraleFarbe() {
        val ecken = listOf(
            KartenEcke(6, 4),
            KartenEcke(8, 4),
            KartenEcke(10, 4),
            KartenEcke(12, 4),
        )
        val linien = ecken.zipWithNext(KartenKante::zwischen)
        val grundkarte = Spielkarte(
            id = "farben",
            name = "Linienfarben",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = linien
                .flatMap(::angrenzendeFelder)
                .distinct()
                .map { GelaendeFeld(it, GelaendeTyp.EBENE) },
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(ecken.first(), EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                ),
                kanten = linien.map(::KantenBelegung),
                felder = listOf(
                    FeldBelegung(
                        position = angrenzendeFelder(linien.first()).first(),
                        anlage = FeldAnlage.Geschaeftsbank,
                    ),
                ),
            ),
        )
        val nurAnna = grundkarte.zu3DModell(spielerReihenfolge = listOf(anna, bert))
        val annasFarbe = nurAnna.eckObjekte.single().typ.farbe
        assertEquals(true, nurAnna.eckObjekte.single().typ.istVerwaltungsstandort)
        assertTrue(
            nurAnna.eckObjekte.single().typ.infos.contains(
                SpielObjektInfoEintrag("Spieler", "anna"),
            ),
        )
        assertTrue(
            nurAnna.eckObjekte.single().typ.infos.contains(
                SpielObjektInfoEintrag("Zustand", "intakt"),
            ),
        )

        val neutraleFarbe = nurAnna.kantenObjekte
            .first { objekt -> objekt.typ.form == SpielObjektForm.SCHIENE }
            .typ.farbe
        assertNotEquals(annasFarbe, neutraleFarbe)
        assertEquals(neutraleFarbe, nurAnna.feldObjekte.single().typ.farbe)
        assertTrue(
            nurAnna.feldObjekte.single().typ.infos.contains(
                SpielObjektInfoEintrag("Zustand", "intakt"),
            ),
        )

        val vonAnnaKontrolliert = grundkarte.copy(
            belegung = grundkarte.belegung.copy(
                ecken = grundkarte.belegung.ecken +
                    EckBelegung(ecken.last(), EckGebaeudeTyp.BAHNHOF, anna),
            ),
        ).zu3DModell(spielerReihenfolge = listOf(anna, bert))
        val annasLinienfarben = vonAnnaKontrolliert.kantenObjekte
            .filter { objekt -> objekt.typ.form == SpielObjektForm.SCHIENE }
            .map { objekt -> objekt.typ.farbe }
            .toSet()
        assertEquals(setOf(annasFarbe), annasLinienfarben)
        val bahnhofInfos = vonAnnaKontrolliert.eckObjekte
            .single { objekt -> objekt.typ.form == SpielObjektForm.BAHNHOF }
            .typ.infos.associate { info -> info.bezeichnung to info.wert }
        assertEquals("2 × Ziegel, 2 × Holz, 1 × Stahl", bahnhofInfos["Kosten"])
        assertEquals("1 × Nahrung, 1 × Kohle", bahnhofInfos["Verbrauch"])

        val produktion = grundkarte.copy(
            belegung = grundkarte.belegung.copy(
                felder = listOf(
                    FeldBelegung(
                        position = grundkarte.belegung.felder.single().position,
                        anlage = FeldAnlage.Wirtschaftsregion(BauteilTyp.RAFFINERIE),
                        gebautInRunde = 3,
                    ),
                ),
            ),
        ).zu3DModell(spielerReihenfolge = listOf(anna, bert))
        val produktionsInfos = produktion.feldObjekte.single()
            .typ.infos.associate { info -> info.bezeichnung to info.wert }
        assertEquals("1 × Schweröl, 2 × Diesel", produktionsInfos["Erträge"])
        assertEquals("2 × Rohöl", produktionsInfos["Verbrauch"])
        assertEquals("3", produktionsInfos["Gebaut in Runde"])

        val gemeinsam = grundkarte.copy(
            belegung = grundkarte.belegung.copy(
                ecken = grundkarte.belegung.ecken +
                    EckBelegung(ecken.last(), EckGebaeudeTyp.HAUPTBAHNHOF, bert),
            ),
        ).zu3DModell(spielerReihenfolge = listOf(anna, bert))
        val bertsFarbe = gemeinsam.eckObjekte.single { objekt ->
            objekt.position == ecken.last()
        }.typ.farbe
        assertNotEquals(annasFarbe, bertsFarbe)
        val gemeinsameLinienfarben = gemeinsam.kantenObjekte
            .filter { objekt -> objekt.typ.form == SpielObjektForm.SCHIENE }
            .map { objekt -> objekt.typ.farbe }
            .toSet()

        assertEquals(1, gemeinsameLinienfarben.size)
        assertEquals(neutraleFarbe, gemeinsameLinienfarben.single())
        assertNotEquals(bertsFarbe, gemeinsameLinienfarben.single())
    }

    @Test
    fun panzerWirdAlsKantenobjektMitDieselverbrauchDargestellt() {
        val kante = KartenKante.zwischen(KartenEcke(2, 0), KartenEcke(3, 2))
        val modell = Spielkarte(
            id = "panzer",
            name = "Panzer",
            hexagon = KartenHexagon(radius = 3),
            gelaendefelder = angrenzendeFelder(kante).map { feld ->
                GelaendeFeld(feld, GelaendeTyp.EBENE)
            },
            belegung = KartenBelegung(
                kriegseinheiten = listOf(
                    KriegsEinheitBelegung(
                        id = "panzer-anna-1",
                        typ = KriegsEinheitTyp.PANZER,
                        besitzer = anna,
                        ort = KartenOrt.Kante(kante),
                    ),
                ),
            ),
        ).zu3DModell(spielerReihenfolge = listOf(anna, bert))

        val panzer = modell.kantenObjekte.single()
        assertEquals(kante, panzer.position)
        assertEquals(SpielObjektForm.PANZER, panzer.typ.form)
        assertTrue(
            panzer.typ.infos.contains(
                SpielObjektInfoEintrag("Bewegung", "1 Diesel je Truppe und Kante"),
            ),
        )
        assertTrue(modell.feldObjekte.isEmpty())
    }

    @Test
    fun mehrerePanzerAufEinerKanteWerdenAlsStapelDargestellt() {
        val kante = KartenKante.zwischen(KartenEcke(2, 0), KartenEcke(3, 2))
        val panzer = listOf("panzer-anna-2", "panzer-anna-1").map { id ->
            KriegsEinheitBelegung(
                id = id,
                typ = KriegsEinheitTyp.PANZER,
                besitzer = anna,
                ort = KartenOrt.Kante(kante),
            )
        }
        val modell = Spielkarte(
            id = "panzer-stapel",
            name = "Panzerstapel",
            hexagon = KartenHexagon(radius = 3),
            gelaendefelder = angrenzendeFelder(kante).map { feld ->
                GelaendeFeld(feld, GelaendeTyp.EBENE)
            },
            belegung = KartenBelegung(kriegseinheiten = panzer),
        ).zu3DModell(spielerReihenfolge = listOf(anna, bert))

        val stapel = modell.kantenObjekte.single()
        assertEquals("2 × Panzer", stapel.typ.name)
        assertEquals(listOf("panzer-anna-1", "panzer-anna-2"), stapel.objektIds)
        assertTrue(stapel.typ.infos.contains(SpielObjektInfoEintrag("Truppen", "2")))
    }

    @Test
    fun frachtschiffStehtAufSeinerWasserrouteUndDieRouteKannMarkiertWerden() {
        val grundkarte = Spielkarte(
            id = "frachtschiff",
            name = "Frachtschiff",
            hexagon = KartenHexagon(radius = 3),
            gelaendefelder = emptyList(),
        )
        val direkteRoute = grundkarte.wasserKanten().first()
        val karte = grundkarte.copy(
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(direkteRoute.anfang, EckGebaeudeTyp.HAFEN, anna),
                    EckBelegung(direkteRoute.ende, EckGebaeudeTyp.HAFEN, anna),
                ),
                seewege = listOf(
                    SeewegBelegung(
                        id = "schiff-1",
                        hafenA = direkteRoute.anfang,
                        hafenB = direkteRoute.ende,
                        besitzer = anna,
                        richtung = FrachtRichtung.A_NACH_B,
                    ),
                ),
            ),
        )
        val route = requireNotNull(
            karte.kuerzesterWasserweg(direkteRoute.anfang, direkteRoute.ende),
        )

        val modell = karte.zu3DModell(
            spielerReihenfolge = listOf(anna, bert),
            routenHervorhebung = route.toSet(),
        )

        val schiff = modell.kantenObjekte.single { objekt ->
            objekt.typ.form == SpielObjektForm.FRACHTSCHIFF
        }
        assertEquals(direkteRoute, schiff.position)
        assertEquals("schiff-1", schiff.objektId)
        assertEquals(route, schiff.bewegungsRoute)
        assertEquals(direkteRoute.anfang, schiff.routenStart)
        assertEquals(
            route.toSet(),
            modell.kantenObjekte
                .filter { objekt -> objekt.typ.form == SpielObjektForm.MARKIERUNG }
                .mapTo(mutableSetOf()) { objekt -> objekt.position },
        )
    }
}
