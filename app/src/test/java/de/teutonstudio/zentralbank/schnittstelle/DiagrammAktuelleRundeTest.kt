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

    @Test
    fun projektionBleibtAbDerAktuellenRundeGepunktet() {
        val model = LineCartesianLayerModel.build {
            seriesMitGepunkteterAktuellerRunde(
                x = listOf(0, 1, 2, 3, 4, 5, 6),
                y = listOf(10, 20, 30, 40, 30, 20, 0),
                aktuelleRundeX = 3,
            )
        }

        assertEquals(listOf(0.0, 1.0, 2.0), model.series[0].map { it.x })
        assertEquals(listOf(2.0, 3.0, 4.0, 5.0, 6.0), model.series[1].map { it.x })
        assertEquals(listOf(30.0, 40.0, 30.0, 20.0, 0.0), model.series[1].map { it.y })
    }
}
