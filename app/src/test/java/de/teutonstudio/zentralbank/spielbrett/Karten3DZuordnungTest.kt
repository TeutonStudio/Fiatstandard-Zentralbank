package de.teutonstudio.zentralbank.spielbrett

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
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Karten3DZuordnungTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")

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

        val neutraleFarbe = nurAnna.kantenObjekte
            .first { objekt -> objekt.typ.form == SpielObjektForm.SCHIENE }
            .typ.farbe
        assertNotEquals(annasFarbe, neutraleFarbe)
        assertEquals(neutraleFarbe, nurAnna.feldObjekte.single().typ.farbe)

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
