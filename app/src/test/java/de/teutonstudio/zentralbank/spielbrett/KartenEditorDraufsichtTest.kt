package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import org.junit.Assert.assertEquals
import org.junit.Test

class KartenEditorDraufsichtTest {
    private val ansichtsGroesse = IntSize(width = 1_000, height = 600)
    private val basisMassstab = 20f

    @Test
    fun `Bildschirmmitte entspricht dem Fokus`() {
        val transformation = DraufsichtTransformation(
            zoom = 2f,
            fokusX = 7.5f,
            fokusZ = -3.25f,
        )

        val brettPunkt = transformation.bildschirmZuBrett(
            bildschirmPunkt = Offset(500f, 300f),
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )

        assertEquals(7.5f, brettPunkt.x, 0.0001f)
        assertEquals(-3.25f, brettPunkt.z, 0.0001f)
    }

    @Test
    fun `Ziehen verschiebt den Fokus entgegen der Fingerbewegung`() {
        val verschoben = DraufsichtTransformation().nachGeste(
            vorherigerSchwerpunkt = Offset(500f, 300f),
            verschiebung = Offset(40f, -20f),
            zoomFaktor = 1f,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )

        assertEquals(-2f, verschoben.fokusX, 0.0001f)
        assertEquals(1f, verschoben.fokusZ, 0.0001f)
    }

    @Test
    fun `Pinch haelt den Kartenpunkt unter dem Schwerpunkt fest`() {
        val vorher = DraufsichtTransformation(
            zoom = 1.2f,
            fokusX = 4f,
            fokusZ = -2f,
        )
        val vorherigerSchwerpunkt = Offset(620f, 340f)
        val verschiebung = Offset(15f, -10f)
        val brettPunktVorher = vorher.bildschirmZuBrett(
            bildschirmPunkt = vorherigerSchwerpunkt,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )

        val nachher = vorher.nachGeste(
            vorherigerSchwerpunkt = vorherigerSchwerpunkt,
            verschiebung = verschiebung,
            zoomFaktor = 1.7f,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )
        val brettPunktNachher = nachher.bildschirmZuBrett(
            bildschirmPunkt = vorherigerSchwerpunkt + verschiebung,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )

        assertEquals(brettPunktVorher.x, brettPunktNachher.x, 0.0001f)
        assertEquals(brettPunktVorher.z, brettPunktNachher.z, 0.0001f)
    }

    @Test
    fun `Zoom wird auf den Darstellungsbereich begrenzt`() {
        val maximal = DraufsichtTransformation().nachGeste(
            vorherigerSchwerpunkt = Offset(500f, 300f),
            verschiebung = Offset.Zero,
            zoomFaktor = 100f,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )
        val minimal = maximal.nachGeste(
            vorherigerSchwerpunkt = Offset(500f, 300f),
            verschiebung = Offset.Zero,
            zoomFaktor = 0.0001f,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )

        assertEquals(5f, maximal.zoom, 0.0001f)
        assertEquals(0.35f, minimal.zoom, 0.0001f)
    }

    @Test
    fun `Bildschirmabbildung trifft nach Verschieben und Zoomen dasselbe Dreieck`() {
        val geometrie = berechneSpielbrettGeometrie(KartenHexagon(radius = 2))
        val ziel = geometrie.dreiecke.first()
        val transformation = DraufsichtTransformation(
            zoom = 2.4f,
            fokusX = -1.3f,
            fokusZ = 2.1f,
        )
        val bildschirmPunkt = transformation.brettZuBildschirm(
            brettPunkt = ziel.mittelpunkt,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )

        val zurueckgerechnet = transformation.bildschirmZuBrett(
            bildschirmPunkt = bildschirmPunkt,
            ansichtsGroesse = ansichtsGroesse,
            basisMassstab = basisMassstab,
        )
        val treffer = geometrie.unbegrenzterTreffer(zurueckgerechnet)

        assertEquals(ziel.position, treffer?.position)
    }

    @Test
    fun `Rasteranker verschiebt einen wiederverwendbaren Pfad auf Gitterkoordinaten`() {
        val ursprung = BrettPunkt(0f, 0f)
        val position = DreieckPosition(7, -3, DreieckAusrichtung.UNTEN)
        val fokus = grundDreieck(position, ursprung).mittelpunkt

        val verschiebung = rasterAnkerVerschiebung(fokus, ursprung)

        assertEquals(GRUNDDREIECK_SEITENLAENGE / 2f, verschiebung.x, 0.0001f)
        assertEquals(7f * GRUNDDREIECK_HOEHE, verschiebung.z, 0.0001f)
    }
}
