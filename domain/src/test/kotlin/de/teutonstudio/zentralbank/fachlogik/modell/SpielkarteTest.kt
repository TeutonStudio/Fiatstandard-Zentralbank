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
            zeilen = 4,
            spalten = 4,
            gelaendefelder = land,
            belegung = KartenBelegung(
                ecken = listOf(
                    EckBelegung(
                        position = feld.ecken().first(),
                        typ = EckGebaeudeTyp.BAHNHOF,
                        besitzer = SpielerId("anna"),
                    ),
                ),
                kanten = listOf(KantenBelegung(kante, SpielerId("anna"))),
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
            zeilen = 2,
            spalten = 2,
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
            zeilen = 100_000,
            spalten = 250_000,
            startZeile = -50_000,
            startSpalte = -125_000,
        )

        assertEquals(0, karte.gelaendefelder.size)
        assertEquals(KartenBelegung(), karte.belegung)
    }

    @Test
    fun karteUnterstuetztNegativeKoordinatenOhneFesteAusdehnungsgrenze() {
        val position = KartenFeld(-7_500, -12_000, DreieckHaelfte.OBEN)
        val karte = Spielkarte(
            id = "unbegrenzt",
            name = "Unbegrenzte Karte",
            zeilen = 20_000,
            spalten = 30_000,
            startZeile = -10_000,
            startSpalte = -15_000,
            gelaendefelder = listOf(GelaendeFeld(position, GelaendeTyp.GEBIRGE)),
        )

        assertEquals(GelaendeTyp.GEBIRGE, karte.landNachPosition[position])
        assertEquals(10_000L, karte.endeZeileExklusiv)
        assertEquals(15_000L, karte.endeSpalteExklusiv)
    }

    @Test
    fun formatEinsMitLandfeldernBleibtLadbarUndWirdNormalisiert() {
        val geladen = json.decodeFromString<Spielkarte>(
            """{
                "formatVersion":1,
                "id":"alt",
                "name":"Alte Karte",
                "zeilen":3,
                "spalten":4,
                "landfelder":[
                    {"position":{"zeile":1,"spalte":1,"haelfte":"OBEN"},"gelaende":"WALD"}
                ],
                "spezialfelder":[{"id":"alt","name":"Stadt","typ":"STADT","positionen":[]}]
            }""".trimIndent(),
        )

        val aktuell = geladen.aufAktuellesFormat()

        assertEquals(AKTUELLE_KARTEN_FORMAT_VERSION, aktuell.formatVersion)
        assertEquals(1, aktuell.gelaendefelder.size)
        assertTrue(aktuell.belegung.ecken.isEmpty())
    }

    @Test
    fun feldanlageAufWasserWirdAbgelehnt() {
        assertThrows(IllegalArgumentException::class.java) {
            Spielkarte(
                id = "ungueltig",
                name = "Ungültig",
                zeilen = 3,
                spalten = 3,
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
                zeilen = 3,
                spalten = 3,
                gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.EBENE)),
                belegung = KartenBelegung(
                    kanten = listOf(KantenBelegung(feld.kanten().first(), SpielerId("anna"))),
                ),
            )
        }
    }
}
