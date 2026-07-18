package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ProzugStatus
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ZugStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ZugAnzeigeTest {
    @Test
    fun zugAnzeigeEnthaeltAktivenSpielerUndPhase() {
        val anna = SpielerId("anna")
        val state = SpielZustand(
            spieler = listOf(Spieler(anna, "Anna")),
            aktiverSpieler = anna,
            zugStatus = ZugStatus(
                1L,
                anna,
                ZugPhase.Epizug,
                ProzugStatus(begonnen = true, erfolgreichAbgeschlossen = true),
            ),
        )

        assertEquals(
            "Runde 0 · Leitzins 0,00 %\nEpizug von Anna · Spielzug beenden",
            state.zuZugAnzeige().text,
        )
    }
}
