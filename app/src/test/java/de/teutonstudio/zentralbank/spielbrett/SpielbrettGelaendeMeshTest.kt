package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielbrettGelaendeMeshTest {
    private val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 2))
    private val typ = DreieckTyp("Ebene", Color.Green)

    @Test
    fun `Landdreieck erhaelt Deckflaeche Seiten und drei Bevel-Flaechen`() {
        val mesh = erstelleAbgeschraegteGelaendeMeshes(
            geometrie = geometrie,
            auflagen = listOf(
                DreieckAuflage(
                    position = geometrie.dreiecke.first().position,
                    typ = typ,
                ),
            ),
        ).getValue(typ)

        assertEquals(27, mesh.ecken.size)
        assertEquals(39, mesh.indizes.size)
        assertEquals(3, mesh.ecken.count { it.normale.y == 1f })
        assertEquals(12, mesh.ecken.count { it.normale.y == 0f })
        assertEquals(12, mesh.ecken.count { it.normale.y in 0.01f..0.99f })
    }

    @Test
    fun `Bevel zieht die Deckflaeche nach innen und hebt sie an`() {
        val dreieck = geometrie.dreiecke.first()
        val mesh = erstelleAbgeschraegteGelaendeMeshes(
            geometrie = geometrie,
            auflagen = listOf(DreieckAuflage(dreieck.position, typ)),
        ).getValue(typ)
        val deckEcken = mesh.ecken.take(3).map(GelaendeMeshEcke::position)

        deckEcken.forEachIndexed { index, ecke ->
            val aussen = dreieck.ecken[index]
            val aussenAbstand = abstand(aussen, dreieck.mittelpunkt)
            val innenAbstand = abstand(BrettPunkt(ecke.x, ecke.z), dreieck.mittelpunkt)
            assertTrue(innenAbstand < aussenAbstand)
            assertEquals(
                OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE,
                ecke.y,
                0.0001f,
            )
        }
        val ersterBevelBeginn = mesh.ecken[7].position
        assertEquals(
            OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE - GELAENDE_BEVEL_HOEHE,
            ersterBevelBeginn.y,
            0.0001f,
        )
    }

    @Test
    fun `alle Beleuchtungsnormalen sind normiert`() {
        val mesh = erstelleAbgeschraegteGelaendeMeshes(
            geometrie = geometrie,
            auflagen = listOf(DreieckAuflage(geometrie.dreiecke.first().position, typ)),
        ).getValue(typ)

        mesh.ecken.forEach { ecke ->
            val normale = ecke.normale
            val laenge = sqrt(
                normale.x * normale.x +
                    normale.y * normale.y +
                    normale.z * normale.z,
            )
            assertEquals(1f, laenge, 0.0001f)
        }
    }

    @Test
    fun `Flaechenwicklung zeigt in Richtung der Beleuchtungsnormalen`() {
        val mesh = erstelleAbgeschraegteGelaendeMeshes(
            geometrie = geometrie,
            auflagen = listOf(DreieckAuflage(geometrie.dreiecke.first().position, typ)),
        ).getValue(typ)

        mesh.indizes.chunked(3).forEach { dreieck ->
            val a = mesh.ecken[dreieck[0]]
            val b = mesh.ecken[dreieck[1]]
            val c = mesh.ecken[dreieck[2]]
            val ab = b.position.minus(a.position)
            val ac = c.position.minus(a.position)
            val flaechenNormale = ab.kreuz(ac)
            val skalarprodukt = flaechenNormale.skalarprodukt(a.normale)

            assertTrue("Dreieck zeigt entgegen seiner Normale: $dreieck", skalarprodukt > 0f)
        }
    }

    @Test
    fun `mehrere Landdreiecke eines Typs werden in einem Mesh gebuendelt`() {
        val positionen = geometrie.dreiecke.take(2).map(GrundDreieck::position)

        val meshes = erstelleAbgeschraegteGelaendeMeshes(
            geometrie = geometrie,
            auflagen = positionen.map { position -> DreieckAuflage(position, typ) },
        )

        assertEquals(1, meshes.size)
        assertEquals(54, meshes.getValue(typ).ecken.size)
        assertEquals(78, meshes.getValue(typ).indizes.size)
    }

    @Test
    fun `Spezialauflagen werden nicht abgeschraegt`() {
        val meshes = erstelleAbgeschraegteGelaendeMeshes(
            geometrie = geometrie,
            auflagen = listOf(
                DreieckAuflage(
                    position = geometrie.dreiecke.first().position,
                    typ = DreieckTyp("Markierung", Color.Yellow),
                    ebene = AuflagenEbene.SPEZIAL,
                ),
            ),
        )

        assertTrue(meshes.isEmpty())
    }

    private fun abstand(a: BrettPunkt, b: BrettPunkt): Float = sqrt(
        (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z),
    )

    private fun GelaendeMeshVektor.minus(anderer: GelaendeMeshVektor) = GelaendeMeshVektor(
        x = x - anderer.x,
        y = y - anderer.y,
        z = z - anderer.z,
    )

    private fun GelaendeMeshVektor.kreuz(anderer: GelaendeMeshVektor) = GelaendeMeshVektor(
        x = y * anderer.z - z * anderer.y,
        y = z * anderer.x - x * anderer.z,
        z = x * anderer.y - y * anderer.x,
    )

    private fun GelaendeMeshVektor.skalarprodukt(anderer: GelaendeMeshVektor): Float =
        x * anderer.x + y * anderer.y + z * anderer.z
}
