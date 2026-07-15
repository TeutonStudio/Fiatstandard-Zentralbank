package de.teutonstudio.zentralbank.schnittstelle

import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import de.teutonstudio.zentralbank.datenbank.SpielZeitpunkt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagrammAktuelleRundeTest {
    @Test
    fun rundenwerteOhneSubrundenEnthaltenNurVolleRunden() {
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            rundenXWerteOhneSubrunden(anzahl = 5),
        )
    }

    @Test
    fun siebenSpielerErzeugenSechsSubrundenpunkteZwischenZweiRunden() {
        val zeitpunkt = SpielZeitpunkt(
            runde = 3,
            aktiverSpielerIndex = 2,
            spielerAnzahl = 7,
        )

        val daten = erweitereDiagrammUmSubrunden(
            x = listOf(0, 1, 2, 3, 4),
            y = listOf(10, 20, 30, 40, 50),
            zeitpunkt = zeitpunkt,
        )

        assertEquals(
            listOf(0.0, 7.0, 14.0) +
                (15..21).map(Int::toDouble) +
                28.0,
            daten.x,
        )
        assertEquals(
            listOf<Number>(10, 20, 30, 30, 30, 40, 40, 40, 40, 40, 50),
            daten.y,
        )
        assertEquals(17.0, daten.prognoseAbX, 0.0)
    }

    @Test
    fun subrundenSindBisZumAktuellenSpielerHistorischUndDanachGepunktet() {
        val zeitpunkt = SpielZeitpunkt(
            runde = 3,
            aktiverSpielerIndex = 2,
            spielerAnzahl = 7,
        )
        val model = LineCartesianLayerModel.build {
            seriesMitGepunkteterAktuellerRunde(
                x = listOf(0, 1, 2, 3, 4),
                y = listOf(10, 20, 30, 40, 50),
                zeitpunkt = zeitpunkt,
            )
        }

        assertEquals(
            listOf(0.0, 7.0, 14.0, 15.0, 16.0),
            model.series[0].map { eintrag -> eintrag.x },
        )
        assertEquals(
            (16..21).map(Int::toDouble) + 28.0,
            model.series[1].map { eintrag -> eintrag.x },
        )
    }

    @Test
    fun subrundenVerwendenAuchBeiNichtTeilbarenSpielerzahlenExakteXWerte() {
        listOf(3, 6, 7).forEach { spielerAnzahl ->
            val zeitpunkt = SpielZeitpunkt(
                runde = 3,
                aktiverSpielerIndex = 1,
                spielerAnzahl = spielerAnzahl,
            )

            val daten = erweitereDiagrammUmSubrunden(
                x = listOf(0, 1, 2, 3),
                y = listOf(10, 20, 30, 40),
                zeitpunkt = zeitpunkt,
            )

            assertTrue(
                "Alle X-Werte müssen für $spielerAnzahl Spieler ganzzahlig sein.",
                daten.x.all { wert -> wert % 1.0 == 0.0 },
            )
            assertEquals(
                (2 * spielerAnzahl + 2).toDouble(),
                daten.prognoseAbX,
                0.0,
            )

            val model = LineCartesianLayerModel.build {
                series(x = daten.x, y = daten.y)
            }
            assertEquals(1.0, model.getXDeltaGcd(), 0.0)
        }
    }

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
