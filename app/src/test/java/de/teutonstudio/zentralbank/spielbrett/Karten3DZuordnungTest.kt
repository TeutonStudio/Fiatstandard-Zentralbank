package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.Spezialfeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Karten3DZuordnungTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")

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
}
