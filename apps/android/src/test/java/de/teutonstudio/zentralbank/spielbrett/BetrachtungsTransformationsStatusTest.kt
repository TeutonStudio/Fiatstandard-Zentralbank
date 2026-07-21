package de.teutonstudio.zentralbank.spielbrett

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BetrachtungsTransformationsStatusTest {
    @Test
    fun `Zoom wird multipliziert und auf gueltigen Bereich begrenzt`() {
        val status = BetrachtungsTransformationsStatus()

        status.zoome(2f)
        assertEquals(2f, status.zoom, 0.0001f)

        status.zoome(100f)
        assertEquals(BetrachtungsTransformation.MAX_ZOOM, status.zoom, 0.0001f)

        status.zoome(0.0001f)
        assertEquals(BetrachtungsTransformation.MIN_ZOOM, status.zoom, 0.0001f)
    }

    @Test
    fun `Orbit normalisiert Azimut und begrenzt Neigung`() {
        val status = BetrachtungsTransformationsStatus(
            BetrachtungsTransformation(azimutGrad = 350f, neigungGrad = 80f),
        )

        status.dreheUmFokus(azimutDeltaGrad = 20f, neigungsDeltaGrad = 20f)

        assertEquals(10f, status.azimutGrad, 0.0001f)
        assertEquals(BetrachtungsTransformation.MAX_NEIGUNG_GRAD, status.neigungGrad, 0.0001f)
    }

    @Test
    fun `Ebenenverschiebung folgt der aktuellen Blickrichtung`() {
        val status = BetrachtungsTransformationsStatus()

        status.verschiebeInEbene(rechts = 2f, vorwaerts = 3f)
        assertEquals(2f, status.fokusX, 0.0001f)
        assertEquals(-3f, status.fokusZ, 0.0001f)

        status.setzeFokus(0f, 0f)
        status.dreheUmFokus(azimutDeltaGrad = 90f, neigungsDeltaGrad = 0f)
        status.verschiebeInEbene(rechts = 2f, vorwaerts = 3f)
        assertEquals(-3f, status.fokusX, 0.0001f)
        assertEquals(-2f, status.fokusZ, 0.0001f)
    }

    @Test
    fun `Bildschirmgeste verschiebt den Kamerafokus in der Ebene`() {
        val status = BetrachtungsTransformationsStatus()

        status.verschiebeDurchBildschirmgeste(
            deltaX = 20f,
            deltaY = 10f,
            welteinheitenProPixel = 0.1f,
        )

        assertEquals(-2f, status.fokusX, 0.0001f)
        assertEquals(-1f, status.fokusZ, 0.0001f)
        assertEquals(0f, status.azimutGrad, 0.0001f)
        assertEquals(38f, status.neigungGrad, 0.0001f)
    }

    @Test
    fun `Status kann gesetzt und auf Startwert zurueckgesetzt werden`() {
        val start = BetrachtungsTransformation(
            zoom = 1.5f,
            azimutGrad = 725f,
            neigungGrad = 45f,
            fokusX = 2f,
            fokusZ = -1f,
        )
        val status = BetrachtungsTransformationsStatus(start)

        assertEquals(5f, status.azimutGrad, 0.0001f)
        status.zoome(2f)
        status.setzeFokus(8f, 9f)
        status.zuruecksetzen()

        assertEquals(start.copy(azimutGrad = 5f), status.transformation)
    }

    @Test
    fun `ungueltige Zoomfaktoren werden abgelehnt`() {
        val status = BetrachtungsTransformationsStatus()

        assertThrows(IllegalArgumentException::class.java) {
            status.zoome(0f)
        }
    }
}
