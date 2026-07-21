package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotStatus
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AngebotsAblaufTest {
    private val anna = SpielerId("Anna")
    private val bert = SpielerId("Bert")
    private val engine = StandardSpielEngine()

    @Test
    fun erstAnnahmeUebertraegtLeistungenUndPrueftAktuellenBestand() {
        val start = SpielZustand(
            spieler = listOf(
                Spieler(
                    anna,
                    "Anna",
                    rohstoffe = mapOf(Rohstoff.HOLZ to 2),
                    geldkonto = Geld.mark(10),
                ),
                Spieler(bert, "Bert", geldkonto = Geld.mark(20)),
            ),
        )
        var zustand = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(
            zustand,
            SpielAktion.HandelsangebotErstellen(
                spieler = anna,
                empfaenger = bert,
                angeboteneRohstoffe = mapOf(Rohstoff.HOLZ to 2),
                geforderterGeldbetrag = Geld.mark(5),
            ),
        ).getOrThrow().zustand

        assertEquals(2, zustand.spieler.first().rohstoffe[Rohstoff.HOLZ])
        assertEquals(Geld.mark(10), zustand.spieler.first().geldkonto)

        zustand = engine.anwenden(zustand, SpielAktion.ProzugAbschliessen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ZugBeenden).getOrThrow().zustand
        val angebot = zustand.handelsAngebote.single()
        val angenommen = engine.anwenden(
            zustand,
            SpielAktion.HandelsangebotAnnehmen(bert, angebot.id),
        ).getOrThrow().zustand

        assertEquals(0, angenommen.spieler.first().rohstoffe.getOrDefault(Rohstoff.HOLZ, 0))
        assertEquals(2, angenommen.spieler.last().rohstoffe[Rohstoff.HOLZ])
        assertEquals(Geld.mark(15), angenommen.spieler.first().geldkonto)
        assertEquals(Geld.mark(15), angenommen.spieler.last().geldkonto)
        assertEquals(HandelsAngebotStatus.ANGENOMMEN, angenommen.handelsAngebote.single().status)
    }

    @Test
    fun nichtReserviertesUnbedecktesAngebotWirdBeiAnnahmeAbgelehnt() {
        val start = SpielZustand(
            spieler = listOf(
                Spieler(anna, "Anna", rohstoffe = mapOf(Rohstoff.HOLZ to 1)),
                Spieler(bert, "Bert", geldkonto = Geld.mark(20)),
            ),
        )
        var zustand = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(
            zustand,
            SpielAktion.HandelsangebotErstellen(
                anna,
                bert,
                angeboteneRohstoffe = mapOf(Rohstoff.HOLZ to 2),
                geforderterGeldbetrag = Geld.mark(5),
            ),
        ).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ProzugAbschliessen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ZugBeenden).getOrThrow().zustand
        val vorher = zustand

        assertTrue(
            engine.anwenden(
                zustand,
                SpielAktion.HandelsangebotAnnehmen(bert, zustand.handelsAngebote.single().id),
            ).isFailure,
        )
        assertEquals(vorher, zustand)
    }

    @Test
    fun offeneAngeboteLaufenBeimNaechstenRundenbeginnAb() {
        val start = SpielZustand(
            spieler = listOf(
                Spieler(anna, "Anna", rohstoffe = mapOf(Rohstoff.HOLZ to 1)),
                Spieler(bert, "Bert", geldkonto = Geld.mark(20)),
            ),
        )
        var zustand = engine.anwenden(start, SpielAktion.ProzugBeginnen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(
            zustand,
            SpielAktion.HandelsangebotErstellen(
                anna,
                bert,
                angeboteneRohstoffe = mapOf(Rohstoff.HOLZ to 1),
                geforderterGeldbetrag = Geld.mark(5),
            ),
        ).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ProzugAbschliessen(1L)).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ZugBeenden).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ProzugAbschliessen(2L)).getOrThrow().zustand
        zustand = engine.anwenden(zustand, SpielAktion.ZugBeenden).getOrThrow().zustand

        assertEquals(1, zustand.rundenzähler)
        assertEquals(HandelsAngebotStatus.ABGELAUFEN, zustand.handelsAngebote.single().status)
    }
}
