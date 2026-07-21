package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeobachtungsAuswertungTest {
    private val anna = SpielerId("Anna")
    private val bert = SpielerId("Bert")
    private val carla = SpielerId("Carla")

    @Test
    fun beobachtungIstDeterministischUndEnthaeltKeinPasswort() {
        val zustand = SpielZustand(
            spieler = listOf(
                Spieler(
                    anna,
                    "Anna",
                    passwortHash = "NIEMALS_EXPORTIEREN",
                    rohstoffe = hashMapOf(Rohstoff.STAHL to 2, Rohstoff.HOLZ to 1),
                    geldkonto = Geld.mark(50),
                    bauteile = hashMapOf(BauteilTyp.BAHNHOF to 1, BauteilTyp.EISENBAHNLINIE to 2),
                ),
                Spieler(
                    bert,
                    "Bert",
                    passwortHash = "AUCH_GEHEIM",
                    rohstoffe = mapOf(Rohstoff.KOHLE to 99),
                    geldkonto = Geld.mark(999),
                ),
            ),
        )

        val links = BeobachtungsAuswertung.fuerSpieler(zustand, anna)
        val rechts = BeobachtungsAuswertung.fuerSpieler(zustand, anna)
        val json = Json { classDiscriminator = "art" }.encodeToString<SpielBeobachtung>(links)

        assertEquals(links, rechts)
        assertEquals(listOf(Rohstoff.HOLZ, Rohstoff.STAHL), links.eigeneWirtschaft.rohstoffe.map { it.rohstoff })
        assertFalse(json.contains("NIEMALS_EXPORTIEREN"))
        assertFalse(json.contains("AUCH_GEHEIM"))
        assertFalse(json.contains("passwort", ignoreCase = true))
        assertTrue(links.gegner.single().name == "Bert")
    }

    @Test
    fun adressiertesAngebotIstNurFuerBeteiligteSichtbar() {
        val start = SpielZustand(
            spieler = listOf(
                Spieler(anna, "Anna", rohstoffe = mapOf(Rohstoff.HOLZ to 1)),
                Spieler(bert, "Bert", geldkonto = Geld.mark(10)),
                Spieler(carla, "Carla", geldkonto = Geld.mark(10)),
            ),
        )
        val engine = StandardSpielEngine()
        var zustand = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(
            zustand,
            SpielAktion.HandelsangebotErstellen(
                anna,
                bert,
                angeboteneRohstoffe = mapOf(Rohstoff.HOLZ to 1),
                geforderterGeldbetrag = Geld.mark(1),
            ),
        ).getOrThrow().zustand

        assertEquals(1, BeobachtungsAuswertung.fuerSpieler(zustand, anna).angebote.size)
        assertEquals(1, BeobachtungsAuswertung.fuerSpieler(zustand, bert).angebote.size)
        assertTrue(BeobachtungsAuswertung.fuerSpieler(zustand, carla).angebote.isEmpty())
    }
}
