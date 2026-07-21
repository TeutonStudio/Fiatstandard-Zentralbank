package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SpielZustandSerialisierungTest {
    @Test
    fun spielZustandIstJsonSerialisierbar() {
        val spielerId = SpielerId("Anna")
        val anleiheId = AnleiheId("anna-1")
        val state = SpielZustand(
            spieler = listOf(
                Spieler(
                    id = spielerId,
                    name = "Anna",
                    rohstoffe = mapOf(Rohstoff.HOLZ to 2),
                    geldkonto = Geld.mark(10),
                    anleihen = listOf(anleiheId),
                    bauteile = mapOf(BauteilTyp.EISENBAHNLINIE to 1),
                ),
            ),
            karte = Spielkarte(
                id = "spiel-anna",
                name = "Annas Insel",
                hexagon = KartenHexagon(radius = 6),
                gelaendefelder = listOf(
                    Landfeld(
                        KartenDreieck(1, 2, DreieckHaelfte.OBEN),
                        GelaendeTyp.WALD,
                    ),
                ),
            ),
            spielabschnitt = Spielabschnitt.RUNDE_NULL,
            rundeNullRestbestand = mapOf(
                spielerId to mapOf(
                    BauteilTyp.HAUPTBAHNHOF to 1,
                    BauteilTyp.EISENBAHNLINIE to 2,
                ),
            ),
            bankkonto = Geld.mark(100),
            bankAnleihen = emptyList(),
            warenkorb = mapOf(Rohstoff.HOLZ to 3),
            anleihen = mapOf(
                anleiheId to Anleihe(
                    id = anleiheId,
                    emittent = spielerId,
                    nennwert = Geld.mark(80),
                    zinsBasispunkte = 750,
                    laufzeitRunden = 4,
                ),
            ),
            konflikte = emptySet(),
            marktpreise = mapOf(Rohstoff.HOLZ to Geld.mark(5)),
            leitzins = Basispunkte(250),
            rundenzähler = 3,
            aktiverSpieler = spielerId,
            zugStatus = ZugStatus(
                zugId = 12L,
                spieler = spielerId,
                phase = ZugPhase.Prozug,
                prozug = ProzugStatus(
                    begonnen = true,
                    abbauErtraege = mapOf(Rohstoff.HOLZ to 2),
                    verwaltungsVerpflichtungen = listOf(
                        VerwaltungsVerpflichtung(
                            id = VerwaltungsVerpflichtungId(12L, KartenEcke(2, 4)),
                            typ = EckGebaeudeTyp.BAHNHOF,
                            bedarf = mapOf(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
                        ),
                    ),
                    produktionsBuchungen = listOf(
                        ProduktionsBuchung(
                            ProduktionsStandortId(
                                KartenFeld(1, 2, DreieckHaelfte.OBEN),
                            ),
                            1,
                        ),
                    ),
                ),
            ),
        )

        val json = Json.encodeToString(state)
        val decoded = Json.decodeFromString<SpielZustand>(json)

        assertEquals(state, decoded)
    }
}
