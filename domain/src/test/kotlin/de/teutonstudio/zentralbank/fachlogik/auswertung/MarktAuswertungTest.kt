package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Test

class MarktAuswertungTest {
    private val anna = SpielerId("Anna")
    private val zustand = SpielZustand(
        spieler = listOf(
            Spieler(
                id = anna,
                name = "Anna",
                bauteile = mapOf(BauteilTyp.BAHNHOF to 2),
            ),
        ),
        marktpreise = mapOf(
            Rohstoff.HOLZ to Geld.mark(1),
            Rohstoff.ZIEGEL to Geld.mark(2),
            Rohstoff.STAHL to Geld.mark(3),
        ),
    )

    @Test
    fun ermitteltAktuellenPreisUndVerwendetNullFuerFehlendenPreis() {
        assertEquals(Geld.mark(1), MarktAuswertung.aktuellerPreis(zustand, Rohstoff.HOLZ))
        assertEquals(Geld.NULL, MarktAuswertung.aktuellerPreis(zustand, Rohstoff.KOHLE))
    }

    @Test
    fun bewertetBauteileUndSpielerAusRohstoffpreisen() {
        assertEquals(
            Geld.mark(9),
            MarktAuswertung.bauteilMarktwert(zustand, BauteilTyp.BAHNHOF),
        )
        assertEquals(Geld.mark(18), MarktAuswertung.spielerMarktwert(zustand, anna))
    }
}
