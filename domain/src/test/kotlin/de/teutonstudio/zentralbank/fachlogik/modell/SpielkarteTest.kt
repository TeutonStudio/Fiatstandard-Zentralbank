package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpielkarteTest {
    private val hexagon = listOf(
        KartenDreieck(3, 3, DreieckHaelfte.UNTEN),
        KartenDreieck(3, 3, DreieckHaelfte.OBEN),
        KartenDreieck(3, 4, DreieckHaelfte.UNTEN),
        KartenDreieck(2, 4, DreieckHaelfte.UNTEN),
        KartenDreieck(2, 3, DreieckHaelfte.OBEN),
        KartenDreieck(2, 4, DreieckHaelfte.OBEN),
    )

    @Test
    fun duennBesetzteKarteIstOhneInformationsverlustSerialisierbar() {
        val karte = Spielkarte(
            id = "inselreich",
            name = "Inselreich",
            zeilen = 8,
            spalten = 8,
            landfelder = hexagon.map { position -> Landfeld(position, GelaendeTyp.EBENE) },
            spezialfelder = listOf(
                Spezialfeld("stadt-1", "Hafenstadt", SpezialfeldTyp.STADT, hexagon),
            ),
        )

        val geladen = Json.decodeFromString<Spielkarte>(Json.encodeToString(karte))

        assertEquals(karte, geladen)
    }

    @Test
    fun wasserMussNichtAlsEinzelfeldGespeichertWerden() {
        val karte = Spielkarte(
            id = "wasser",
            name = "Offenes Meer",
            zeilen = 100_000,
            spalten = 250_000,
            startZeile = -50_000,
            startSpalte = -125_000,
        )

        assertEquals(0, karte.landfelder.size)
        assertEquals(0, karte.spezialfelder.size)
    }

    @Test
    fun karteUnterstuetztNegativeKoordinatenOhneFesteAusdehnungsgrenze() {
        val position = KartenDreieck(-7_500, -12_000, DreieckHaelfte.OBEN)

        val karte = Spielkarte(
            id = "unbegrenzt",
            name = "Unbegrenzte Karte",
            zeilen = 20_000,
            spalten = 30_000,
            startZeile = -10_000,
            startSpalte = -15_000,
            landfelder = listOf(Landfeld(position, GelaendeTyp.GEBIRGE)),
        )

        assertEquals(GelaendeTyp.GEBIRGE, karte.landNachPosition[position])
        assertEquals(10_000L, karte.endeZeileExklusiv)
        assertEquals(15_000L, karte.endeSpalteExklusiv)
    }

    @Test
    fun bestehendeKarteOhneUrsprungBeginntWeiterhinBeiNull() {
        val geladen = Json.decodeFromString<Spielkarte>(
            """{"id":"alt","name":"Alte Karte","zeilen":3,"spalten":4}""",
        )

        assertEquals(0, geladen.startZeile)
        assertEquals(0, geladen.startSpalte)
    }

    @Test
    fun spezialfeldAufWasserWirdAbgelehnt() {
        assertThrows(IllegalArgumentException::class.java) {
            Spielkarte(
                id = "ungueltig",
                name = "Ungültig",
                zeilen = 8,
                spalten = 8,
                spezialfelder = listOf(
                    Spezialfeld("stadt-1", "Stadt", SpezialfeldTyp.STADT, hexagon),
                ),
            )
        }
    }
}
