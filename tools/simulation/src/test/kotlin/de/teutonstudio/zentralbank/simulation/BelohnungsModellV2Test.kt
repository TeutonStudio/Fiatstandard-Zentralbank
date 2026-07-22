package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BelohnungsModellV2Test {
    private val anna = SpielerId("anna")
    private val bert = SpielerId("bert")
    private val modell = PotentialBelohnungsModell()
    private val basis = SpielZustand(
        spieler = listOf(Spieler(anna, "Anna"), Spieler(bert, "Bert")),
    )

    @Test
    fun gegnerischeExpansionBelohntNichtDenEigenenSpieler() {
        val nachher = basis.copy(
            spieler = basis.spieler.map {
                if (it.id == bert) it.copy(geldkonto = Geld.mark(100)) else it
            },
        )
        val rewards = modell.berechne(basis, nachher, SpielAktion.ProzugBeginnen(1L), null)
        assertTrue(rewards.getValue(anna) <= 0f)
        assertTrue(rewards.getValue(bert) >= rewards.getValue(anna))
    }

    @Test
    fun unproduktiveSchuldenaufnahmeIstKeinPositiverExploit() {
        val anleihe = Anleihe(AnleiheId("a1"), anna, Geld.mark(100), 200, 3)
        val nachher = basis.copy(
            spieler = basis.spieler.map {
                if (it.id == anna) it.copy(geldkonto = Geld.mark(100)) else it
            },
            anleihen = mapOf(anleihe.id to anleihe),
            bankAnleihen = listOf(anleihe.id),
        )
        val aktion = SpielAktion.AnleiheEmittieren(anna, Geld.mark(100), 200, 3)
        assertTrue(modell.berechne(basis, nachher, aktion, null).getValue(anna) <= 0f)
    }

    @Test
    fun schuldenstrichUndZentralbankgeldschoepfungSindKeineRewardSchleife() {
        val nachher = basis.copy(
            spieler = basis.spieler.map {
                if (it.id == anna) it.copy(geldkonto = Geld.mark(100)) else it
            },
            zentralbankGeldschoepfungen = listOf(
                ZentralbankGeldschoepfung(anna, Geld.mark(100), 0, "TEST"),
            ),
        )
        val rewards = modell.berechne(
            basis,
            nachher,
            SpielAktion.SchuldenstrichDurchfuehren(anna),
            null,
        )
        assertTrue(rewards.getValue(anna) <= 0f)
    }

    @Test
    fun unbegruendeteKapitulationWirdNegativBewertetUndTerminalNurSiegerPositiv() {
        val krieg = basis.copy(konflikte = setOf(Konflikt(anna, bert)))
        val kapitulation = modell.berechne(
            krieg,
            krieg,
            SpielAktion.KriegKapitulieren(anna, krieg.konflikte.single().id),
            null,
        )
        assertTrue(kapitulation.getValue(anna) < 0f)

        val ergebnis = SpielErgebnis(
            gewinner = bert,
            platzierungen = listOf(bert, anna),
            ausgeschiedeneSpieler = setOf(anna),
            grund = SpielEndeGrund.LETZTER_SPIELFAEHIGER_SPIELER,
            endRunde = 1,
        )
        val terminal = modell.berechne(basis, basis.copy(ergebnis = ergebnis), SpielAktion.ZugBeenden, ergebnis)
        assertTrue(terminal.getValue(bert) > 0f)
        assertTrue(terminal.getValue(anna) < 0f)
        assertEquals(2, terminal.size)
    }
}
