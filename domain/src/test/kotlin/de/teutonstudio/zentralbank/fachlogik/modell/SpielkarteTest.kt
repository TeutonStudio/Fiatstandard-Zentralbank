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
            zeilen = 64,
            spalten = 64,
        )

        assertEquals(0, karte.landfelder.size)
        assertEquals(0, karte.spezialfelder.size)
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
