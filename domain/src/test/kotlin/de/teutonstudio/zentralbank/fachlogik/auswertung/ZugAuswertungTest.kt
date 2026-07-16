package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZugAuswertungTest {
    private val annaId = SpielerId("Anna")

    private fun state(zugStatus: ZugStatus = ZugStatus(annaId, Phase.Einnahmen)): SpielZustand = SpielZustand(
        spieler = listOf(Spieler(annaId, "Anna", geldkonto = Geld.mark(1))),
        zugStatus = zugStatus,
    )

    @Test
    fun einnahmenPhaseWartetAufRohstoffEinnahmen() {
        val schritte = ZugAuswertung.schritte(state()).associateBy { it.typ }

        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.ROHSTOFF_EINNAHMEN).zustand)
        assertFalse(ZugAuswertung.kannPhaseAbschliessen(state()))
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

        assertTrue(ZugAuswertung.kannPhaseAbschliessen(state))
    }

    @Test
    fun ausgabenPhaseIstAutomatischErledigtOhneVerbrauchUndFremdeAnleihen() {
        val state = state(ZugStatus(annaId, Phase.Ausgaben))

        val schritte = ZugAuswertung.schritte(state).associateBy { it.typ }

        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.FINANZ_AUSGABEN).zustand)
        assertTrue(ZugAuswertung.kannPhaseAbschliessen(state))
    }

    @Test
    fun rohstoffAusgabenBleibenOffenBeiVerbrauchendenBauteilen() {
        val state = SpielZustand(
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

        val schritte = ZugAuswertung.schritte(state).associateBy { it.typ }

        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.FINANZ_AUSGABEN).zustand)
        assertFalse(ZugAuswertung.kannPhaseAbschliessen(state))
    }

    @Test
    fun finanzAusgabenBleibenOffenBeiFremdgehaltenerEigenerAnleihe() {
        val anleiheId = AnleiheId("anna-1")
        val state = SpielZustand(
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

        val schritte = ZugAuswertung.schritte(state).associateBy { it.typ }

        assertEquals(SchrittZustand.ERLEDIGT, schritte.getValue(SchrittTyp.ROHSTOFF_AUSGABEN).zustand)
        assertEquals(SchrittZustand.VERFUEGBAR, schritte.getValue(SchrittTyp.FINANZ_AUSGABEN).zustand)
        assertFalse(ZugAuswertung.kannPhaseAbschliessen(state))
    }
}
