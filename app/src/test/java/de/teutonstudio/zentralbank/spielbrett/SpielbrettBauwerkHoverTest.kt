package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielbrettBauwerkHoverTest {
    @Test
    fun `Infofenster bleibt ausserhalb der Statusleisten`() {
        val groesse = IntSize(width = 300, height = 220)
        val ansicht = IntSize(width = 1_280, height = 720)
        val sicher = BauwerkInfoSicherheitsBereich(
            links = 140f,
            oben = 146f,
            rechts = 16f,
            unten = 56f,
        )

        listOf(
            Offset(0f, 0f),
            Offset(1_280f, 0f),
            Offset(0f, 720f),
            Offset(1_280f, 720f),
        ).forEach { anker ->
            val position = begrenzeBauwerkInfoPosition(anker, groesse, ansicht, sicher)
            assertTrue(position.x >= sicher.links)
            assertTrue(position.y >= sicher.oben)
            assertTrue(position.x + groesse.width <= ansicht.width - sicher.rechts)
            assertTrue(position.y + groesse.height <= ansicht.height - sicher.unten)
        }
    }

    @Test
    fun `hoher Hauptbahnhof wird entlang seiner gesamten Hoehe getroffen`() {
        val hauptbahnhof = ziel(
            name = "Hauptbahnhof",
            form = SpielObjektForm.HAUPTBAHNHOF,
            anfang = Position(x = 0f, y = 0.35f, z = 0f),
            ende = Position(x = 0f, y = 1.27f, z = 0f),
            prioritaet = 0,
        )

        val treffer = listOf(hauptbahnhof).findeTreffer(
            ursprungX = 0f,
            ursprungY = 3f,
            ursprungZ = 3f,
            richtungX = 0f,
            richtungY = -2f,
            richtungZ = -3f,
        )

        assertEquals(hauptbahnhof, treffer)
    }

    @Test
    fun `Eckgebaeude hat am Schienenende Vorrang vor Handelslinie`() {
        val handelslinie = ziel(
            name = "Handelslinie",
            form = SpielObjektForm.SCHIENE,
            anfang = Position(x = -1f, y = 0.47f, z = 0f),
            ende = Position(x = 1f, y = 0.47f, z = 0f),
            prioritaet = 2,
        )
        val hauptbahnhof = ziel(
            name = "Hauptbahnhof",
            form = SpielObjektForm.HAUPTBAHNHOF,
            anfang = Position(x = 0f, y = 0.35f, z = 0f),
            ende = Position(x = 0f, y = 1.27f, z = 0f),
            prioritaet = 0,
        )

        val treffer = listOf(handelslinie, hauptbahnhof).findeTreffer(
            ursprungX = 0f,
            ursprungY = 3f,
            ursprungZ = 0f,
            richtungX = 0f,
            richtungY = -1f,
            richtungZ = 0f,
        )

        assertEquals(hauptbahnhof, treffer)
    }

    @Test
    fun `Hauptbahnhof heftet alle zugeordneten Bauwerke seines Spielers an`() {
        val hauptbahnhof = ziel(
            name = "Hauptbahnhof",
            form = SpielObjektForm.HAUPTBAHNHOF,
            spieler = setOf("anna"),
        )
        val bahnhof = ziel(name = "Bahnhof", spieler = setOf("anna"))
        val handelslinie = ziel(
            name = "Handelslinie",
            form = SpielObjektForm.SCHIENE,
            spieler = setOf("anna"),
        )
        val fremderHafen = ziel(name = "Hafen", spieler = setOf("bert"))

        val angeheftet = listOf(
            hauptbahnhof,
            bahnhof,
            handelslinie,
            fremderHafen,
        ).angehefteteZieleFuer(hauptbahnhof)

        assertEquals(setOf(hauptbahnhof, bahnhof, handelslinie), angeheftet.toSet())
    }

    @Test
    fun `Strahl ins Leere waehlt kein Bauwerk`() {
        val bauwerk = ziel(name = "Bahnhof")

        val treffer = listOf(bauwerk).findeTreffer(
            ursprungX = 4f,
            ursprungY = 3f,
            ursprungZ = 4f,
            richtungX = 0f,
            richtungY = -1f,
            richtungZ = 0f,
        )

        assertNull(treffer)
    }

    private fun ziel(
        name: String,
        form: SpielObjektForm = SpielObjektForm.BAHNHOF,
        spieler: Set<String> = emptySet(),
        anfang: Position = Position(x = 0f, y = 0.35f, z = 0f),
        ende: Position = Position(x = 0f, y = 0.9f, z = 0f),
        prioritaet: Int = 0,
    ) = BauwerkHoverZiel(
        schluessel = "$name:${spieler.joinToString()}",
        typ = SpielObjektTyp(
            name = name,
            farbe = Color.Blue,
            form = form,
            spieler = spieler,
        ),
        trefferAnfang = anfang,
        trefferEnde = ende,
        trefferRadius = 0.48f,
        prioritaet = prioritaet,
        infoPosition = ende,
    )
}
