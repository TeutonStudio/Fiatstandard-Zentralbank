package de.teutonstudio.zentralbank.domain.engine

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.KontoId
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.events.GameEvent
import de.teutonstudio.zentralbank.domain.events.TransaktionsGrund
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducerTest {
    private val annaId = SpielerId("Anna")
    private val berndId = SpielerId("Bernd")

    private fun startState(): GameState = GameState(
        spieler = listOf(
            Spieler(
                id = annaId,
                name = "Anna",
                rohstoffe = mapOf(Rohstoff.HOLZ to 2),
                geldkonto = Geld.mark(10),
            ),
            Spieler(
                id = berndId,
                name = "Bernd",
                rohstoffe = emptyMap(),
                geldkonto = Geld.mark(5),
            ),
        ),
        bankkonto = Geld.mark(100),
    )

    @Test
    fun rohstoffEinnahmeErhoehtBestand() {
        val state = Reducer.reduce(
            startState(),
            GameEvent.RohstoffEinnahme(annaId, mapOf(Rohstoff.HOLZ to 3)),
        ).getOrThrow()

        assertEquals(5, state.spieler.first { it.id == annaId }.rohstoffe[Rohstoff.HOLZ])
    }

    @Test
    fun rohstoffAusgabeLehntNegativenBestandAb() {
        val result = Reducer.reduce(
            startState(),
            GameEvent.RohstoffAusgabe(berndId, mapOf(Rohstoff.HOLZ to 1)),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun transaktionIstSummenneutral() {
        val start = startState()
        val state = Reducer.reduce(
            start,
            GameEvent.Transaktion(
                von = KontoId.Spieler(annaId),
                an = KontoId.Spieler(berndId),
                betrag = Geld.mark(3),
                grund = TransaktionsGrund.SONSTIGES,
            ),
        ).getOrThrow()

        assertEquals(start.geldsumme(), state.geldsumme())
        assertEquals(Geld.mark(7), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(8), state.spieler.first { it.id == berndId }.geldkonto)
    }

    @Test
    fun transaktionLehntUnterdeckungAb() {
        val result = Reducer.reduce(
            startState(),
            GameEvent.Transaktion(
                von = KontoId.Spieler(berndId),
                an = KontoId.Bank,
                betrag = Geld.mark(6),
                grund = TransaktionsGrund.SONSTIGES,
            ),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rohstoffHandelVerschiebtRohstoffUndGeldSummenneutral() {
        val start = startState()
        val state = Reducer.reduce(
            start,
            GameEvent.RohstoffHandel(
                kaeufer = berndId,
                verkaeufer = annaId,
                rohstoff = Rohstoff.HOLZ,
                menge = 1,
                preis = Geld.mark(2),
            ),
        ).getOrThrow()

        assertEquals(start.geldsumme(), state.geldsumme())
        assertEquals(1, state.spieler.first { it.id == annaId }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(1, state.spieler.first { it.id == berndId }.rohstoffe[Rohstoff.HOLZ])
        assertEquals(Geld.mark(12), state.spieler.first { it.id == annaId }.geldkonto)
        assertEquals(Geld.mark(3), state.spieler.first { it.id == berndId }.geldkonto)
    }
}
