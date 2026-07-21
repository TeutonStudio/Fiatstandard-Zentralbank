package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpielbrettGebirgsMeshTest {
    private val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 2))
    private val gebirge = DreieckTyp(
        name = "Gebirge",
        farbe = Color.Gray,
        relief = DreieckRelief.GEBIRGE,
    )

    @Test
    fun `isoliertes Gebirgsfeld erhaelt eine Pyramide ueber seinem Mittelpunkt`() {
        val feld = geometrie.dreiecke.first()
        val mesh = erstelleGebirgsPyramidenMesh(
            geometrie,
            listOf(DreieckAuflage(feld.position, gebirge)),
        )

        assertNotNull(mesh)
        assertEquals(9, mesh?.ecken?.size)
        assertEquals(9, mesh?.indizes?.size)
        assertEquals(
            OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE + GEBIRGS_PYRAMIDEN_HOEHE,
            mesh?.ecken?.maxOf { ecke -> ecke.position.y } ?: 0f,
            0.0001f,
        )
        val spitzen = mesh?.ecken.orEmpty().filter { ecke ->
            kotlin.math.abs(
                ecke.position.y -
                    (OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE + GEBIRGS_PYRAMIDEN_HOEHE)
            ) < 0.0001f
        }
        assertEquals(3, spitzen.size)
        spitzen.forEach { spitze ->
            assertEquals(feld.mittelpunkt.x, spitze.position.x, 0.0001f)
            assertEquals(feld.mittelpunkt.z, spitze.position.z, 0.0001f)
        }
    }

    @Test
    fun `benachbarte Gebirgsfelder verbinden ihre Spitzen mit gefuellten Seiten`() {
        val erstes = geometrie.dreiecke.first()
        val zweites = geometrie.dreiecke.first { kandidat ->
            kandidat !== erstes && gemeinsamerEckenAnzahl(erstes, kandidat) == 2
        }
        val mesh = erstelleGebirgsPyramidenMesh(
            geometrie,
            listOf(
                DreieckAuflage(erstes.position, gebirge),
                DreieckAuflage(zweites.position, gebirge),
            ),
        )

        // Vier äußere Pyramidenflächen plus zwei Flächen entlang der gemeinsamen Kante.
        assertEquals(18, mesh?.indizes?.size)
        assertEquals(18, mesh?.ecken?.size)
    }

    @Test
    fun `gemeinsame Kante zweier Gebirgsfelder wird als Binnenkante erkannt`() {
        val erstes = geometrie.dreiecke.first()
        val zweites = geometrie.dreiecke.first { kandidat ->
            kandidat !== erstes && gemeinsamerEckenAnzahl(erstes, kandidat) == 2
        }

        val binnenkanten = ermittleGebirgsBinnenkanten(
            listOf(erstes.position, zweites.position),
        )

        assertEquals(
            erstes.position.zuKartenFeld().kanten()
                .intersect(zweites.position.zuKartenFeld().kanten().toSet()),
            binnenkanten,
        )
    }

    @Test
    fun `flaches Gelaende erzeugt kein Gebirgsmesh`() {
        val flach = DreieckTyp("Ebene", Color.Green)

        assertNull(
            erstelleGebirgsPyramidenMesh(
                geometrie,
                listOf(DreieckAuflage(geometrie.dreiecke.first().position, flach)),
            ),
        )
    }

    private fun gemeinsamerEckenAnzahl(a: GrundDreieck, b: GrundDreieck): Int =
        a.ecken.count { ersteEcke ->
            b.ecken.any { zweiteEcke ->
                kotlin.math.abs(ersteEcke.x - zweiteEcke.x) < 0.0001f &&
                    kotlin.math.abs(ersteEcke.z - zweiteEcke.z) < 0.0001f
            }
        }
}
