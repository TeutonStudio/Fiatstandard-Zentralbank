package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
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
        assertEquals(Rohstoff.entries, links.eigeneWirtschaft.rohstoffe.map { it.rohstoff })
        assertFalse(json.contains("NIEMALS_EXPORTIEREN"))
        assertFalse(json.contains("AUCH_GEHEIM"))
        assertFalse(json.contains("passwort", ignoreCase = true))
        assertTrue(links.gegner.single().name == "Bert")
    }

    @Test
    fun adressiertesAngebotIstAlsEingetretenerZustandOeffentlichSichtbar() {
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
        assertEquals(1, BeobachtungsAuswertung.fuerSpieler(zustand, carla).angebote.size)
    }

    @Test
    fun beobachtungEnthaeltExpliziteVollstaendigeKartentopologie() {
        val hexagon = KartenHexagon(radius = 2)
        val land = KartenFeld(0, 0, DreieckHaelfte.UNTEN)
        val zustand = SpielZustand(
            spieler = listOf(Spieler(anna, "Anna"), Spieler(bert, "Bert")),
            karte = Spielkarte(
                id = "beobachtungs-topologie",
                name = "Beobachtungs-Topologie",
                hexagon = hexagon,
                gelaendefelder = listOf(GelaendeFeld(land, GelaendeTyp.EBENE)),
            ),
        )

        val karte = requireNotNull(BeobachtungsAuswertung.fuerSpieler(zustand, anna).karte)

        assertEquals(hexagon.anzahlFelder.toInt(), karte.felder.size)
        assertEquals(karte.knoten.size, karte.knoten.distinct().size)
        assertEquals(karte.kanten.size, karte.kanten.map { it.position }.distinct().size)
        assertTrue(karte.felder.any { it.position == land && it.gelaende == GelaendeTyp.EBENE })
        assertTrue(karte.felder.any { it.wasser })
        assertTrue(karte.kanten.any { it.landkante })
        assertTrue(karte.kanten.any { it.seekante })
    }
}
