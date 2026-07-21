package de.teutonstudio.zentralbank.adapter.json

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
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
}
