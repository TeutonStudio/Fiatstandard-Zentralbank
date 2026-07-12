package de.teutonstudio.zentralbank.domain.zug

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZugAutomatTest {
    private val annaId = SpielerId("Anna")

    private fun state(zugStatus: ZugStatus = ZugStatus(annaId, Phase.Einnahmen)): GameState = GameState(
        spieler = listOf(Spieler(annaId, "Anna", geldkonto = Geld.mark(1))),
        zugStatus = zugStatus,
    )

    @Test
    fun einnahmenPhaseHatNurRohstoffEinnahmenVerfuegbar() {
        val schritte = ZugAutomat.schritte(state()).associateBy { it.typ }

        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.ROHSTOFF_EINNAHMEN).zustand)
        assertEquals(SchrittZustand.GESPERRT, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertFalse(ZugAutomat.kannPhaseAbschliessen(state()))
    }

    @Test
    fun phaseKannNachPflichtschrittAbgeschlossenWerden() {
        val state = state(
            ZugStatus(
                spieler = annaId,
                phase = Phase.Einnahmen,
                erledigteSchritte = setOf(SchrittTyp.ROHSTOFF_EINNAHMEN),
            ),
        )

        assertTrue(ZugAutomat.kannPhaseAbschliessen(state))
    }
}
