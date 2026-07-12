package de.teutonstudio.zentralbank.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStateSerializationTest {
    @Test
    fun gameStateIstJsonSerialisierbar() {
        val spielerId = SpielerId("Anna")
        val anleiheId = AnleiheId("anna-1")
        val state = GameState(
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
            bankkonto = Geld.mark(100),
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
            marktpreise = mapOf(Rohstoff.HOLZ to Geld.mark(5)),
            leitzins = Basispunkte(250),
            rundenzähler = 3,
            aktiverSpieler = spielerId,
        )

        val json = Json.encodeToString(state)
        val decoded = Json.decodeFromString<GameState>(json)

        assertEquals(state, decoded)
    }
}
