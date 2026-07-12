package de.teutonstudio.zentralbank.domain.zug

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.Anleihe
import de.teutonstudio.zentralbank.domain.AnleiheId
import de.teutonstudio.zentralbank.domain.BauteilTyp
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
    fun einnahmenPhaseWartetAufRohstoffEinnahmen() {
        val schritte = ZugAutomat.schritte(state()).associateBy { it.typ }

        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.ROHSTOFF_EINNAHMEN).zustand)
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

    @Test
    fun ausgabenPhaseIstAutomatischErledigtOhneVerbrauchUndFremdeAnleihen() {
        val state = state(ZugStatus(annaId, Phase.Ausgaben))

        val schritte = ZugAutomat.schritte(state).associateBy { it.typ }

        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.FINANZ_AUSGABEN).zustand)
        assertTrue(ZugAutomat.kannPhaseAbschliessen(state))
    }

    @Test
    fun rohstoffAusgabenBleibenOffenBeiVerbrauchendenBauteilen() {
        val state = GameState(
            spieler = listOf(
                Spieler(
                    id = annaId,
                    name = "Anna",
                    geldkonto = Geld.mark(1),
                    bauteile = mapOf(BauteilTyp.BAHNHOF to 1),
                ),
            ),
            zugStatus = ZugStatus(annaId, Phase.Ausgaben),
        )

        val schritte = ZugAutomat.schritte(state).associateBy { it.typ }

        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.FINANZ_AUSGABEN).zustand)
        assertFalse(ZugAutomat.kannPhaseAbschliessen(state))
    }

    @Test
    fun finanzAusgabenBleibenOffenBeiFremdgehaltenerEigenerAnleihe() {
        val anleiheId = AnleiheId("anna-1")
        val state = GameState(
            spieler = listOf(
                Spieler(annaId, "Anna", geldkonto = Geld.mark(10)),
                Spieler(
                    id = SpielerId("Bernd"),
                    name = "Bernd",
                    geldkonto = Geld.mark(10),
                    anleihen = listOf(anleiheId),
                ),
            ),
            anleihen = mapOf(
                anleiheId to Anleihe(
                    id = anleiheId,
                    emittent = annaId,
                    nennwert = Geld.mark(8),
                    zinsBasispunkte = 500,
                    laufzeitRunden = 4,
                ),
            ),
            zugStatus = ZugStatus(annaId, Phase.Ausgaben),
        )

        val schritte = ZugAutomat.schritte(state).associateBy { it.typ }

        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.FINANZ_AUSGABEN).zustand)
        assertFalse(ZugAutomat.kannPhaseAbschliessen(state))
    }
}
