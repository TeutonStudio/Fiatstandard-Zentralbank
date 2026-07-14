package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Spieler
import de.teutonstudio.zentralbank.domain.SpielerId
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ZugAnzeigeTest {
    @Test
    fun zugAnzeigeEnthaeltAktivenSpielerUndPhase() {
        val anna = SpielerId("anna")
        val state = GameState(
            spieler = listOf(Spieler(anna, "Anna")),
            aktiverSpieler = anna,
            zugStatus = ZugStatus(anna, Phase.Ausgaben),
        )

        assertEquals(
            "Runde 0 · Leitzins 0,00 %\nAnna: Ausgaben abschließen",
            state.zuZugAnzeige().text,
        )
    }
}
