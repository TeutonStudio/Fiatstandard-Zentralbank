package de.teutonstudio.zentralbank.fachlogik.modell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BauteilKostenTest {
    @Test
    fun wirtschaftsstandorteKostenDreiHolzUndZweiZiegel() {
        val erwarteteKosten = mapOf(Rohstoff.HOLZ to 3, Rohstoff.ZIEGEL to 2)
        val wirtschaftsstandorte = BauteilTyp.entries.filter {
            it.art == BauteilArt.WIRTSCHAFTSREGION
        }

        assertEquals(11, wirtschaftsstandorte.size)
        assertTrue(wirtschaftsstandorte.all { it.kosten == erwarteteKosten })
    }

    @Test
    fun verwaltungsstandorteUndHandelslinienVerwendenDieFestgelegtenKosten() {
        val erwarteteKosten = mapOf(
            BauteilTyp.HAUPTBAHNHOF to emptyMap(),
            BauteilTyp.BAHNHOF to rohstoffe(
                Rohstoff.HOLZ to 2,
                Rohstoff.STAHL to 1,
                Rohstoff.ZIEGEL to 2,
            ),
            BauteilTyp.GROSSBAHNHOF to rohstoffe(
                Rohstoff.HOLZ to 4,
                Rohstoff.STAHL to 2,
                Rohstoff.ZIEGEL to 3,
            ),
            BauteilTyp.HAFEN to rohstoffe(
                Rohstoff.HOLZ to 1,
                Rohstoff.STAHL to 2,
                Rohstoff.ZIEGEL to 2,
            ),
            BauteilTyp.GROSSHAFEN to rohstoffe(
                Rohstoff.HOLZ to 2,
                Rohstoff.STAHL to 4,
                Rohstoff.ZIEGEL to 3,
            ),
            BauteilTyp.EISENBAHNLINIE to rohstoffe(
                Rohstoff.HOLZ to 1,
                Rohstoff.STAHL to 1,
            ),
            BauteilTyp.FRACHTSCHIFF to rohstoffe(Rohstoff.STAHL to 2),
        )

        erwarteteKosten.forEach { (bauteil, kosten) ->
            assertEquals(bauteil.text, kosten, bauteil.kosten)
        }
    }

    @Test
    fun grossgebaeudeSindKeineDirektenStartplatzierungen() {
        assertFalse(BauteilTyp.GROSSBAHNHOF.istInRundeNullPlatzierbar)
        assertFalse(BauteilTyp.GROSSHAFEN.istInRundeNullPlatzierbar)
        assertTrue(BauteilTyp.BAHNHOF.istInRundeNullPlatzierbar)
        assertTrue(BauteilTyp.HAFEN.istInRundeNullPlatzierbar)
    }
}
