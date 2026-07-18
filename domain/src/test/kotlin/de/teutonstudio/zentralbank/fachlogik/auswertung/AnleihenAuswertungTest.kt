package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitArt
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

    @Test
    fun plantUnvermoegenAbFolgerundeUndRueckkaufInFaelligkeitsrunde() {
        val zustand = SpielZustand(
            spieler = listOf(Spieler(anna, "Anna")),
            bankAnleihen = listOf(anleiheId),
            anleihen = mapOf(anleiheId to anleihe),
            rundenzähler = 1,
        )

        val zins = AnleihenAuswertung.faelligeVerbindlichkeiten(zustand, anna, 8L).single()
        val rueckkauf = AnleihenAuswertung.faelligeVerbindlichkeiten(
            zustand.copy(rundenzähler = anleihe.faelligkeitsRunde),
            anna,
            9L,
        ).single()

        assertEquals(VerbindlichkeitArt.UNVERMOEGEN, zins.id.art)
        assertEquals(Geld.mark(5), zins.betrag)
        assertEquals(KontoId.Bank, zins.empfaenger)
        assertEquals(VerbindlichkeitArt.RUECKKAUF, rueckkauf.id.art)
        assertEquals(Geld.mark(100), rueckkauf.betrag)
    }
}
