package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KriegId
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AktionsMenueAuswertungTest {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")
    private val carla = SpielerId("carla")

    @Test
    fun `runde null zeigt keine regulären konflikt oder truppenaktionen`() {
        val zustand = zustand(phase = ZugPhase.Prozug).copy(spielabschnitt = Spielabschnitt.RUNDE_NULL)
        val uebersicht = requireNotNull(AktionsMenueAuswertung.uebersicht(zustand))
        assertFalse(uebersicht.bereiche.single { it.bereich == AktionsMenueBereich.KONFLIKT }.verfuegbar)
        assertTrue(uebersicht.bereiche.single { it.bereich == AktionsMenueBereich.ZUG }.verfuegbar)
    }

    @Test
    fun `prozug bietet nur phasengerechte zugaktionen`() {
        val eintraege = AktionsMenueAuswertung.eintraege(zustand(ZugPhase.Prozug), AktionsMenueBereich.ZUG)
        assertEquals(listOf(AktionsMenueAktion.PROZUG_BEGINNEN), eintraege.map { it.aktion })
        assertTrue(AktionsMenueAuswertung.eintraege(zustand(ZugPhase.Prozug), AktionsMenueBereich.KONFLIKT).isEmpty())
    }

    @Test
    fun `epizug trennt konflikt von diplomatie und schließt selbst sowie ausgeschiedene aus`() {
        val krieg = Konflikt(anna, bert, KriegId("krieg-1"))
        val zustand = zustand(ZugPhase.Epizug).copy(konflikte = setOf(krieg), ausgeschiedeneSpieler = setOf(carla))
        val konflikt = AktionsMenueAuswertung.eintraege(zustand, AktionsMenueBereich.KONFLIKT)
        val diplomatie = AktionsMenueAuswertung.eintraege(zustand, AktionsMenueBereich.DIPLOMATIE)
        assertTrue(konflikt.any { it.aktion == AktionsMenueAktion.KAPITULIEREN && it.konflikt == krieg.id })
        assertFalse(konflikt.any { it.zielSpieler == anna || it.zielSpieler == carla })
        assertTrue(diplomatie.any { it.aktion == AktionsMenueAktion.WAFFENSTILLSTAND_ANBIETEN })
        assertFalse(konflikt.any { it.aktion == AktionsMenueAktion.WAFFENSTILLSTAND_ANBIETEN })
    }

    @Test
    fun `grosse karte aendert die bereichsübersicht nicht`() {
        val klein = zustand(ZugPhase.Epizug, karte = karte(radius = 1))
        val gross = zustand(ZugPhase.Epizug, karte = karte(radius = 50))
        assertEquals(
            AktionsMenueAuswertung.uebersicht(klein)?.bereiche?.map { it.bereich to it.verfuegbar },
            AktionsMenueAuswertung.uebersicht(gross)?.bereiche?.map { it.bereich to it.verfuegbar },
        )
    }

    @Test
    fun `zwanzig einheiten ergeben beim öffnen genau einen stapel und keine potenzmenge`() {
        val karte = karte(radius = 2)
        val kante = karte.hexagon.felder().first().kanten().first()
        val mitStapel = karte.copy(belegung = KartenBelegung(kriegseinheiten = (1..20).map { nummer ->
            KriegsEinheitBelegung("e$nummer", KriegsEinheitTyp.KRIEGSSCHIFF, anna, ort = KartenOrt.Kante(kante))
        }))
        val zustand = zustand(ZugPhase.Epizug, karte = mitStapel)
        val stapel = AktionsMenueAuswertung.eintraege(zustand, AktionsMenueBereich.TRUPPEN)
        assertEquals(1, stapel.size)
        assertEquals(20, stapel.single().einheitenIds.size)
        assertTrue(AktionsMenueAuswertung.bewegungsZiele(zustand, stapel.single().einheitenIds.toSet()).size < 100)
    }

    @Test
    fun `ki stil wird als fachaktion im spielzustand persistiert`() {
        val nachher = StandardSpielEngine().anwenden(
            zustand(ZugPhase.Prozug),
            SpielAktion.SpielerStilSetzen(anna, SpielerStil.AGGRESSIV),
        ).getOrThrow().zustand
        assertEquals(SpielerStil.AGGRESSIV, nachher.spieler.single { it.id == anna }.spielstil)
    }

    private fun zustand(phase: ZugPhase, karte: Spielkarte? = null) = SpielZustand(
        spieler = listOf(Spieler(anna, "Anna"), Spieler(bert, "Bert"), Spieler(carla, "Carla")),
        karte = karte,
        spielabschnitt = Spielabschnitt.REGULAER,
        aktiverSpieler = anna,
        zugStatus = ZugStatus(1, anna, phase),
    )

    private fun karte(radius: Int) = Spielkarte(
        id = "test-$radius",
        name = "Test",
        hexagon = KartenHexagon(radius = radius),
    )
}
