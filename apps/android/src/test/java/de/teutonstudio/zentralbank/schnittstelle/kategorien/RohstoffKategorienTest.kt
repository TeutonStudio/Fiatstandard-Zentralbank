package de.teutonstudio.zentralbank.schnittstelle.kategorien

import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import org.junit.Assert.assertEquals
import org.junit.Test

class RohstoffKategorienTest {
    @Test
    fun verarbeiteteRohstoffeSindKorrektZugeordnet() {
        assertEquals(
            listOf(Rohstoff.ZIEGEL, Rohstoff.DIESEL, Rohstoff.SCHWEROEL, Rohstoff.STAHL),
            verarbeiteteRohstoffe,
        )
    }

    @Test
    fun unverarbeiteteRohstoffeSindKorrektZugeordnet() {
        assertEquals(
            listOf(
                Rohstoff.NAHRUNG,
                Rohstoff.LEHM,
                Rohstoff.HOLZ,
                Rohstoff.ROHOEL,
                Rohstoff.KOHLE,
                Rohstoff.EISEN,
            ),
            unverarbeiteteRohstoffe,
        )
    }

    @Test
    fun jedeRohstoffartStehtInGenauEinerKategorie() {
        val alleKategorien = verarbeiteteRohstoffe + unverarbeiteteRohstoffe

        assertEquals(Rohstoff.entries.toSet(), alleKategorien.toSet())
        assertEquals(alleKategorien.size, alleKategorien.distinct().size)
    }
}
