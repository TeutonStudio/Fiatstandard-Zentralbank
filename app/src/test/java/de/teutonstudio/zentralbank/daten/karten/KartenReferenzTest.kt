package de.teutonstudio.zentralbank.daten.karten

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class KartenReferenzTest {
    @Test
    fun `Ausrichtungsgeste verschiebt und skaliert das Bild in Brettkoordinaten`() {
        val vorher = KartenReferenzMetadaten(
            zentrumX = 3f,
            zentrumZ = -4f,
            breiteInBrettEinheiten = 20f,
        )

        val nachher = vorher.nachAusrichtungsGeste(
            verschiebungX = 2.5f,
            verschiebungZ = -1.5f,
            zoomFaktor = 1.5f,
        )

        assertEquals(5.5f, nachher.zentrumX, 0.0001f)
        assertEquals(-5.5f, nachher.zentrumZ, 0.0001f)
        assertEquals(30f, nachher.breiteInBrettEinheiten, 0.0001f)
    }

    @Test
    fun `Skalierung wird auf den sicheren Darstellungsbereich begrenzt`() {
        val minimal = KartenReferenzMetadaten(
            breiteInBrettEinheiten = MIN_REFERENZ_BREITE,
        ).nachAusrichtungsGeste(0f, 0f, 0.0001f)
        val maximal = KartenReferenzMetadaten(
            breiteInBrettEinheiten = MAX_REFERENZ_BREITE,
        ).nachAusrichtungsGeste(0f, 0f, 10_000f)

        assertEquals(MIN_REFERENZ_BREITE, minimal.breiteInBrettEinheiten, 0f)
        assertEquals(MAX_REFERENZ_BREITE, maximal.breiteInBrettEinheiten, 0f)
    }

    @Test
    fun `Ungueltige Geste veraendert die Metadaten nicht`() {
        val vorher = KartenReferenzMetadaten(breiteInBrettEinheiten = 12f)

        val nachher = vorher.nachAusrichtungsGeste(Float.NaN, 0f, 1f)

        assertSame(vorher, nachher)
    }

    @Test
    fun `Ungueltige Deckkraft wird abgelehnt`() {
        assertThrows(IllegalArgumentException::class.java) {
            KartenReferenzMetadaten(
                breiteInBrettEinheiten = 12f,
                deckkraft = 1.1f,
            )
        }
    }
}
