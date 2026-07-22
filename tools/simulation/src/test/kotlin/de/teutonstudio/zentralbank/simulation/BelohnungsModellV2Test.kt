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
    fun profitableProduktionErzeugtPositivesEigenesPotential() {
        val vorher = basis.copy(marktpreise = mapOf(Rohstoff.KOHLE to Geld.mark(10)))
        val nachher = vorher.copy(
            spieler = vorher.spieler.map {
                if (it.id == anna) it.copy(rohstoffe = mapOf(Rohstoff.KOHLE to 2)) else it
            },
        )
        val rewards = modell.berechne(
            vorher,
            nachher,
            SpielAktion.VerarbeitungAusfuehren(
                1L,
                KartenFeld(0, 0, DreieckHaelfte.OBEN),
            ),
            null,
        )

        assertTrue(rewards.getValue(anna) > 0f)
        assertTrue(rewards.getValue(bert) < 0f)
    }

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
    fun sinnvolleAufstockungDieFaelligenRueckkaufAblöstVerbessertSchuldendienst() {
        val alt = Anleihe(
            id = AnleiheId("alt"),
            emittent = anna,
            nennwert = Geld.mark(50),
            zinsBasispunkte = 200,
            laufzeitRunden = 1,
            emissionsRunde = 0,
            faelligkeitsRunde = 2,
        )
        val vorher = basis.copy(
            rundenzähler = 2,
            anleihen = mapOf(alt.id to alt),
            bankAnleihen = listOf(alt.id),
        )
        val neu = Anleihe(
            id = AnleiheId("neu"),
            emittent = anna,
            nennwert = Geld.mark(80),
            zinsBasispunkte = 200,
            laufzeitRunden = 3,
            emissionsRunde = 2,
        )
        val nachher = vorher.copy(
            spieler = vorher.spieler.map {
                if (it.id == anna) it.copy(geldkonto = Geld.mark(30)) else it
            },
            anleihen = mapOf(neu.id to neu),
            bankAnleihen = listOf(neu.id),
        )
        val reward = modell.berechne(
            vorher,
            nachher,
            SpielAktion.AnleiheAufstocken(anna, alt.id, neu.nennwert, 200, 3),
            null,
        ).getValue(anna)

        assertTrue(reward > 0f)
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
