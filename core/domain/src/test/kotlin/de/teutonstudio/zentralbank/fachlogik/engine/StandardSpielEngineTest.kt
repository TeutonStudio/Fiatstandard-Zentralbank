package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardSpielEngineTest {
    private val anna = SpielerId("Anna")
    private val start = SpielZustand(
        spieler = listOf(Spieler(anna, "Anna", geldkonto = Geld.mark(10))),
    )
    private val engine = StandardSpielEngine()

    @Test
    fun ungueltigeAktionVeraendertDenEingangszustandNicht() {
        val vorher = start.copy()

        val ergebnis = engine.anwenden(start, SpielAktion.ProzugAbschliessen(999L))

        assertTrue(ergebnis.isFailure)
        assertEquals(vorher, start)
    }

    @Test
    fun erlaubteAktionenFuehrenMitDerselbenEngineDurchEinenZug() {
        val begonnen = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow()
        val abschliessen = engine.erlaubteAktionen(begonnen.zustand, anna).single()
        val epizug = engine.anwenden(begonnen.zustand, abschliessen).getOrThrow()

        assertEquals(listOf(SpielAktion.ZugBeenden), engine.erlaubteAktionen(epizug.zustand, anna))
        assertEquals(1, begonnen.ereignisse.size)
    }

    @Test
    fun gleicherSeedLiefertGleicheAuswahlfolge() {
        val links = SeedZufallsquelle(42)
        val rechts = SeedZufallsquelle(42)

        assertEquals(
            List(100) { links.naechsteGanzzahl(17) },
            List(100) { rechts.naechsteGanzzahl(17) },
        )
    }

    @Test
    fun zweiVollstaendigeRundenLaufenMitRundenwertenNurInDerDomain() {
        val bert = SpielerId("Bert")
        var zustand = SpielZustand(
            spieler = listOf(
                Spieler(
                    anna,
                    "Anna",
                    geldkonto = Geld.mark(100),
                    rohstoffe = mapOf(Rohstoff.HOLZ to 1),
                ),
                Spieler(
                    bert,
                    "Bert",
                    geldkonto = Geld.mark(100),
                    rohstoffe = mapOf(Rohstoff.HOLZ to 3),
                ),
            ),
            warenkorb = mapOf(Rohstoff.HOLZ to 1),
            marktpreise = mapOf(Rohstoff.HOLZ to Geld.mark(5)),
            marktpreisBeobachtungen = mapOf(
                Rohstoff.HOLZ to listOf(Geld.mark(10), Geld.mark(20)),
            ),
            leitzins = Basispunkte.prozent(2),
        )
        val verlauf = mutableListOf<SpielEreignis>()

        fun ausfuehren(aktion: SpielAktion) {
            val schritt = engine.anwenden(zustand, aktion).getOrThrow()
            zustand = schritt.zustand
            verlauf += schritt.ereignisse
        }

        ausfuehren(SpielAktion.ProzugBeginnen(1L))
        ausfuehren(SpielAktion.ProzugAbschliessen(1L))
        ausfuehren(SpielAktion.ZugBeenden)
        ausfuehren(SpielAktion.ProzugAbschliessen(2L))
        ausfuehren(SpielAktion.ZugBeenden)

        assertEquals(1, zustand.rundenzähler)
        assertEquals(Geld.mark(15), zustand.marktpreise[Rohstoff.HOLZ])
        assertTrue(zustand.zugStatus?.prozug?.begonnen == true)

        ausfuehren(
            SpielAktion.RohstoffHandeln(
                kaeufer = anna,
                verkaeufer = bert,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(20),
            ),
        )
        ausfuehren(SpielAktion.ProzugAbschliessen(3L))
        ausfuehren(SpielAktion.ZugBeenden)
        ausfuehren(SpielAktion.ProzugAbschliessen(4L))
        ausfuehren(SpielAktion.ZugBeenden)

        assertEquals(2, zustand.rundenzähler)
        assertEquals(Geld.mark(20), zustand.marktpreise[Rohstoff.HOLZ])
        assertEquals(Basispunkte.prozent(4), zustand.leitzins)
        assertEquals(2, zustand.rundenwerte.size)
        assertEquals(
            zustand,
            SpielAblauf(
                startzustand = SpielZustand(
                    spieler = listOf(
                        Spieler(
                            anna,
                            "Anna",
                            geldkonto = Geld.mark(100),
                            rohstoffe = mapOf(Rohstoff.HOLZ to 1),
                        ),
                        Spieler(
                            bert,
                            "Bert",
                            geldkonto = Geld.mark(100),
                            rohstoffe = mapOf(Rohstoff.HOLZ to 3),
                        ),
                    ),
                    warenkorb = mapOf(Rohstoff.HOLZ to 1),
                    marktpreise = mapOf(Rohstoff.HOLZ to Geld.mark(5)),
                    marktpreisBeobachtungen = mapOf(
                        Rohstoff.HOLZ to listOf(Geld.mark(10), Geld.mark(20)),
                    ),
                    leitzins = Basispunkte.prozent(2),
                ),
                ereignisse = verlauf,
            ).zustand,
        )
    }
}
