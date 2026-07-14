package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GameUiStateTest {
    @Test
    fun gameUiStateKombiniertZugUndSpieler() {
        val anna = SpielerId("anna")
        val state = GameState(
            spieler = listOf(Spieler(anna, "Anna")),
            aktiverSpieler = anna,
            zugStatus = ZugStatus(anna, Phase.Einnahmen),
        )

        val uiState = state.zuGameUiState()

        assertEquals(
            "Runde 0 · Leitzins 0,00 %\nAnna: Einnahmen abschließen",
            uiState.zug.text,
        )
        assertEquals(listOf("Anna"), uiState.spieler.map { it.name })
    }
}
