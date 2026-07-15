package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.farbe
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

class FarbkontrastTest {
    private val fachfarben: List<Color>
        get() = Rohstoffe.entries.map { rohstoff -> rohstoff.farbe } +
            Bauteil.entries.map { bauteil -> bauteil.farbe } +
            erhalteSpielerFarben(TestSpiel.spielerListe).values +
            auslandFarbe

    @Test
    fun fachfarbenErhaltenEineLesbareSchriftfarbe() {
        fachfarben.forEach { hintergrund ->
            val kontrast = kontrast(hintergrund, hintergrund.lesbareSchriftfarbe())
            assertTrue("Schriftkontrast $kontrast für $hintergrund", kontrast >= 4.5f)
        }
    }

    @Test
    fun fachfarbenSindAufHellenUndDunklenDiagrammenErkennbar() {
        val diagrammHintergruende = listOf(Color.White, Color(0xFF181B1E))

        fachfarben.forEach { fachfarbe ->
            diagrammHintergruende.forEach { hintergrund ->
                val kontrast = kontrast(fachfarbe, hintergrund)
                assertTrue("Farbkontrast $kontrast für $fachfarbe", kontrast >= 3f)
            }
        }
    }

    private fun kontrast(farbeA: Color, farbeB: Color): Float {
        val luminanzA = farbeA.luminance()
        val luminanzB = farbeB.luminance()
        return (max(luminanzA, luminanzB) + 0.05f) /
            (min(luminanzA, luminanzB) + 0.05f)
    }
}
