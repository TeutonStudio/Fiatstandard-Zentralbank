package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnleihenAuswertungTest {
    private val anna = SpielerId("Anna")
    private val anleiheId = AnleiheId("anna-1")
    private val anleihe = Anleihe(
        id = anleiheId,
        emittent = anna,
        nennwert = Geld.mark(100),
        zinsBasispunkte = 500,
        laufzeitRunden = 4,
    )

    @Test
    fun berechnetZinszahlungUndGesamtschuldCentgenau() {
        assertEquals(Geld.mark(5), AnleihenAuswertung.zinszahlung(anleihe))
        assertEquals(Geld.mark(120), AnleihenAuswertung.gesamtschuld(anleihe))
    }

    @Test
    fun ordnetBankgehalteneAnleiheDerBankZu() {
        val zustand = SpielZustand(
            spieler = listOf(Spieler(anna, "Anna")),
            bankAnleihen = listOf(anleiheId),
            anleihen = mapOf(anleiheId to anleihe),
        )

        assertEquals(KontoId.Bank, AnleihenAuswertung.besitzer(zustand, anleiheId))
        assertEquals(
            Geld.mark(120),
            AnleihenAuswertung.bankgehalteneSchuldensumme(zustand, anna),
        )
    }
}
