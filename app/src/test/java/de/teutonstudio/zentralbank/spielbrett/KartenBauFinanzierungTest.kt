package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenBauFinanzierungTest {
    private val anna = SpielerId("Anna")

    @Test
    fun `fehlende Baurohstoffe werden je Rohstoffart zusammengefasst`() {
        val zustand = SpielZustand(
            spieler = listOf(
                Spieler(
                    id = anna,
                    name = "Anna",
                    rohstoffe = mapOf(
                        Rohstoff.HOLZ to 1,
                        Rohstoff.ZIEGEL to 3,
                    ),
                ),
            ),
        )

        assertEquals(
            mapOf(
                Rohstoff.HOLZ to 1,
                Rohstoff.STAHL to 1,
            ),
            fehlendeBauRohstoffe(zustand, BauteilTyp.BAHNHOF),
        )
    }

    @Test
    fun `vollstaendiger Lagerbestand benoetigt keinen Auslandseinkauf`() {
        val zustand = SpielZustand(
            spieler = listOf(
                Spieler(
                    id = anna,
                    name = "Anna",
                    rohstoffe = BauteilTyp.GROSSHAFEN.kosten,
                ),
            ),
        )

        assertTrue(fehlendeBauRohstoffe(zustand, BauteilTyp.GROSSHAFEN).isEmpty())
    }
}
