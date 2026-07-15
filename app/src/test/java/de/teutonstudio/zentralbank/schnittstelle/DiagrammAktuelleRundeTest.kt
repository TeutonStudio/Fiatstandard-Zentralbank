package de.teutonstudio.zentralbank.schnittstelle

import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagrammAktuelleRundeTest {
    @Test
    fun aktuelleRundeWirdAlsEigenesLetztesLiniensegmentAufgeteilt() {
        val model = LineCartesianLayerModel.build {
            seriesMitGepunkteterAktuellerRunde(
                x = listOf(0, 1, 2, 3),
                y = listOf(10, 20, 30, 40),
            )
        }

        assertEquals(
            listOf(0.0, 1.0, 2.0),
            model.series[0].map { eintrag -> eintrag.x },
        )
        assertEquals(
            listOf(2.0, 3.0),
            model.series[1].map { eintrag -> eintrag.x },
        )
        assertEquals(
            listOf(30.0, 40.0),
            model.series[1].map { eintrag -> eintrag.y },
        )
    }
}
