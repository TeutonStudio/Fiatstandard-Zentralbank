package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielkarteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun karteMitEckKantenUndFeldbelegungIstSerialisierbar() {
        val feld = KartenFeld(1, 1, DreieckHaelfte.UNTEN)
        val kante = feld.kanten().first()
        val land = angrenzendeFelder(kante).map { position ->
            GelaendeFeld(position, GelaendeTyp.EBENE)
        }
        val karte = Spielkarte(
            id = "inselreich",
            name = "Inselreich",
            hexagon = KartenHexagon(radius = 5),
            gelaendefelder = land,
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(
                        position = feld.ecken().first(),
                        typ = EckGebaeudeTyp.BAHNHOF,
                        besitzer = SpielerId("anna"),
                    ),
                ),
                kanten = listOf(KantenBelegung(kante)),
                felder = listOf(
                    FeldBelegung(feld, FeldAnlage.Abbaueinheit(Rohstoff.NAHRUNG)),
                ),
            ),
        )

        val geladen = json.decodeFromString<Spielkarte>(json.encodeToString(karte))

        assertEquals(karte, geladen)
    }

    @Test
    fun vorlageEnthaeltNurGelaendeUndErzeugtLeerePartiebelegung() {
        val vorlage = KartenVorlage(
            id = "vorlage",
            name = "Vorlage",
            hexagon = KartenHexagon(radius = 2),
            gelaendefelder = listOf(
                GelaendeFeld(KartenFeld(0, 0, DreieckHaelfte.OBEN), GelaendeTyp.WALD),
            ),
        )

        val karte = vorlage.alsSpielkarte("spiel-1")

        assertEquals("spiel-1", karte.id)
        assertEquals(vorlage.gelaendefelder, karte.gelaendefelder)
        assertEquals(KartenBelegung(), karte.belegung)
    }

    @Test
    fun wasserMussNichtAlsEinzelfeldGespeichertWerden() {
        val karte = Spielkarte(
            id = "wasser",
            name = "Offenes Meer",
            hexagon = KartenHexagon(radius = 100_000),
        )

        assertEquals(0, karte.gelaendefelder.size)
        assertEquals(KartenBelegung(), karte.belegung)
        assertEquals(60_000_000_000L, karte.hexagon.anzahlFelder)
    }

    @Test
    fun karteUnterstuetztNegativeKoordinatenOhneFesteAusdehnungsgrenze() {
        val position = KartenFeld(-7_500, -12_000, DreieckHaelfte.OBEN)
        val hexagon = KartenHexagon().mitMindestradiusFuer(listOf(position))
        val karte = Spielkarte(
            id = "unbegrenzt",
            name = "Unbegrenzte Karte",
            hexagon = hexagon,
            gelaendefelder = listOf(GelaendeFeld(position, GelaendeTyp.GEBIRGE)),
        )

        assertEquals(GelaendeTyp.GEBIRGE, karte.landNachPosition[position])
        assertTrue(karte.enthaeltFeld(position))
        assertTrue(hexagon.radius > 12_000)
    }

    @Test
    fun radiusBestimmtEinHexagonAusSechsMalRadiusQuadratDreiecken() {
        assertEquals(6, KartenHexagon(radius = 1).felder().size)
        assertEquals(24, KartenHexagon(radius = 2).felder().size)
        assertEquals(54, KartenHexagon(radius = 3).felder().size)

        val kodiert = json.encodeToString(
            KartenVorlage(id = "hex", name = "Hexagon", hexagon = KartenHexagon(radius = 3)),
        )
        assertTrue(kodiert.contains("\"hexagon\""))
        assertTrue(!kodiert.contains("\"zeilen\""))
        assertTrue(!kodiert.contains("\"spalten\""))
    }

    @Test
    fun feldanlageAufWasserWirdAbgelehnt() {
        assertThrows(IllegalArgumentException::class.java) {
            Spielkarte(
                id = "ungueltig",
                name = "Ungültig",
                hexagon = KartenHexagon(radius = 5),
                belegung = KartenBelegung(
                    felder = listOf(
                        FeldBelegung(
                            KartenFeld(1, 1, DreieckHaelfte.UNTEN),
                            FeldAnlage.Geschaeftsbank,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun schieneNebenWasserWirdAuchBeimLadenAbgelehnt() {
        val feld = KartenFeld(1, 1, DreieckHaelfte.UNTEN)
        assertThrows(IllegalArgumentException::class.java) {
            Spielkarte(
                id = "ungueltig",
                name = "Ungültig",
                hexagon = KartenHexagon(radius = 5),
                gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.EBENE)),
                belegung = KartenBelegung(
                    kanten = listOf(KantenBelegung(feld.kanten().first())),
                ),
            )
        }
    }

    @Test
    fun spielerDarfAuchInGeladenemSpielNurEinenHauptbahnhofHaben() {
        val anna = SpielerId("anna")
        val erster = KartenFeld(0, 0, DreieckHaelfte.UNTEN).ecken().first()
        val zweiter = KartenFeld(2, 2, DreieckHaelfte.OBEN).ecken().last()

        val fehler = assertThrows(IllegalArgumentException::class.java) {
            Spielkarte(
                id = "doppelt",
                name = "Doppelter Hauptbahnhof",
                hexagon = KartenHexagon(radius = 8),
                belegung = KartenBelegung(
                    ecken = listOf(
                        EckBelegung(erster, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                        EckBelegung(zweiter, EckGebaeudeTyp.HAUPTBAHNHOF, anna),
                    ),
                ),
            )
        }

        assertTrue(fehler.message.orEmpty().contains("höchstens einen Hauptbahnhof"))
    }
}
