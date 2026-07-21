package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SpielUebersichtZustandTest {
    @Test
    fun spielUebersichtKombiniertZugUndSpieler() {
        val anna = SpielerId("anna")
        val state = SpielZustand(
            spieler = listOf(Spieler(anna, "Anna")),
            aktiverSpieler = anna,
            zugStatus = ZugStatus(1L, anna, ZugPhase.Prozug),
        )

        val uiState = state.zuSpielUebersichtZustand()

        assertEquals(
            "Runde 0 · Leitzins 0,00 %\nAnna: Prozug · 0 Pflichtposten offen",
            uiState.zug.text,
        )
        assertEquals(listOf("Anna"), uiState.spieler.map { it.name })
    }
}
