package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `bauplan fasst Rohstoffkosten mehrerer Bauwerke zusammen`() {
        assertEquals(
            mapOf(
                Rohstoff.ZIEGEL to 2,
                Rohstoff.HOLZ to 3,
                Rohstoff.STAHL to 2,
            ),
            bauRohstoffKosten(
                listOf(BauteilTyp.BAHNHOF, BauteilTyp.EISENBAHNLINIE),
            ),
        )
    }

    @Test
    fun `bauplan berechnet Fehlmengen gegen gemeinsamen Lagerbestand`() {
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
        val kosten = bauRohstoffKosten(
            listOf(BauteilTyp.BAHNHOF, BauteilTyp.EISENBAHNLINIE),
        )

        assertEquals(
            mapOf(
                Rohstoff.HOLZ to 2,
                Rohstoff.STAHL to 2,
            ),
            fehlendeBauRohstoffe(zustand, kosten),
        )
    }

    @Test
    fun `marktpreis Summe bewertet alle Rohstoffkosten`() {
        val kosten = mapOf(
            Rohstoff.HOLZ to 3,
            Rohstoff.ZIEGEL to 2,
            Rohstoff.STAHL to 2,
        )

        assertEquals(
            Geld.mark(27),
            marktpreisSumme(
                kosten = kosten,
                marktpreise = mapOf(
                    Rohstoff.HOLZ to Geld.mark(2),
                    Rohstoff.ZIEGEL to Geld.cent(50),
                    Rohstoff.STAHL to Geld.mark(10),
                ),
            ),
        )
    }

    @Test
    fun `marktpreis Summe bleibt ohne vollstaendige Preise unbekannt`() {
        assertNull(
            marktpreisSumme(
                kosten = mapOf(Rohstoff.HOLZ to 1, Rohstoff.STAHL to 1),
                marktpreise = mapOf(Rohstoff.HOLZ to Geld.mark(2)),
            ),
        )
    }
}
