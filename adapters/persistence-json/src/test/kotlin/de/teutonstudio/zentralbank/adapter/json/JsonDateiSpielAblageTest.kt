package de.teutonstudio.zentralbank.adapter.json

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonDateiSpielAblageTest {
    @Test
    fun erfuelltDenApplicationAblagevertrag() {
        pruefeAblageVertrag(JsonDateiSpielAblage(Files.createTempDirectory("spielablage-test")))
    }

    @Test
    fun roundtripErhaeltStartzustandEreignisverlaufUndVersionen() = runBlocking {
        val verzeichnis = Files.createTempDirectory("spiel-json-roundtrip")
        val ablage = JsonDateiSpielAblage(verzeichnis)
        val anna = SpielerId("Anna")
        val original = GespeichertesSpiel(
            id = 19,
            startzustand = SpielZustand(spieler = listOf(Spieler(anna, "Anna"))),
            ereignisse = listOf(
                SpielEreignis.ProzugBegonnen(1L),
                SpielEreignis.ProzugErfolgreichAbgeschlossen(1L),
                SpielEreignis.ZugBeendet,
            ),
            seed = 1234,
        )

        ablage.spielSpeichern(original)
        val neuGeoeffnet = JsonDateiSpielAblage(verzeichnis)

        assertEquals(original, neuGeoeffnet.spielLaden(19))
        assertEquals(
            original.aktuellerZustand(),
            neuGeoeffnet.spielLaden(19)?.aktuellerZustand(),
        )
    }

    @Test
    fun schemaEinsWirdGelesenUndBeimSpeichernAufSchemaZweiAngehoben() = runBlocking {
        val verzeichnis = Files.createTempDirectory("spiel-json-migration")
        val start = SpielZustand(
            spieler = listOf(Spieler(SpielerId("Anna"), "Anna")),
        )
        val alt = GespeichertesSpielFormat(
            schemaVersion = 1,
            engineVersion = "1.1.0",
            spielId = "7",
            startzustand = start,
            ereignisse = emptyList(),
        )
        Files.writeString(
            verzeichnis.resolve("spiel-7.json"),
            spielstandJson.encodeToString(alt),
        )
        val ablage = JsonDateiSpielAblage(verzeichnis)
        val geladen = requireNotNull(ablage.spielLaden(7))

        assertEquals(1, geladen.schemaVersion)
        assertEquals(start, geladen.startzustand)
        ablage.spielSpeichern(geladen)

        val neu = spielstandJson.decodeFromString<GespeichertesSpielFormat>(
            Files.readString(verzeichnis.resolve("spiel-7.json")),
        )
        assertEquals(AKTUELLE_JSON_SCHEMA_VERSION, neu.schemaVersion)
    }
}
