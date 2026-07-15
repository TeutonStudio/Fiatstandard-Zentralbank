package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

fun LineCartesianLayerModel.BuilderScope.seriesMitGepunkteterAktuellerRunde(
    x: Collection<Number>,
    y: Collection<Number>,
    aktuelleRundeX: Number? = null,
) {
    require(x.size == y.size) { "X- und Y-Werte müssen gleich lang sein." }
    if (x.isEmpty()) return

    val xWerte = x.toList()
    val yWerte = y.toList()
    val aktuelleRunde = (aktuelleRundeX ?: xWerte.last()).toDouble()
    val aktuellerIndex = xWerte.indexOfLast { wert -> wert.toDouble() <= aktuelleRunde }
    require(aktuellerIndex >= 0) { "Die aktuelle Runde muss innerhalb der X-Werte liegen." }
    val historischeAnzahl = aktuellerIndex.coerceAtLeast(1)
    val gepunkteterStart = (aktuellerIndex - 1).coerceAtLeast(0)

    series(
        x = xWerte.take(historischeAnzahl),
        y = yWerte.take(historischeAnzahl),
    )
    series(
        x = xWerte.drop(gepunkteterStart),
        y = yWerte.drop(gepunkteterStart),
    )
}

@Composable
fun rememberLinienMitGepunkteterAktuellerRunde(
    eintraege: List<DiagrammLegendenEintrag>,
): List<LineCartesianLayer.Line> = eintraege.flatMap { eintrag ->
    val farbe = eintrag.farbe
    val fuellung = remember(farbe) {
        LineCartesianLayer.LineFill.single(Fill(farbe))
    }
    val interpolator = remember {
        LineCartesianLayer.Interpolator.cubic(curvature = 0.5f)
    }
    listOf(
        LineCartesianLayer.rememberLine(
            fill = fuellung,
            interpolator = interpolator,
        ),
        LineCartesianLayer.rememberLine(
            fill = fuellung,
            stroke = LineCartesianLayer.LineStroke.Dashed(
                cap = StrokeCap.Round,
                dashLength = 2.dp,
                gapLength = 4.dp,
            ),
            interpolator = interpolator,
        ),
    )
}

@Composable
fun rememberSaeulenMitGepunkteterAktuellerRunde(
    eintraege: List<DiagrammLegendenEintrag>,
    aktuelleRundeX: Number,
    dicke: Dp = 8.dp,
): ColumnCartesianLayer.ColumnProvider {
    val normaleSaeulen = eintraege.map { eintrag ->
        rememberLineComponent(
            fill = Fill(eintrag.farbe),
            thickness = dicke,
        )
    }
    val aktuelleSaeulen = eintraege.map { eintrag ->
        rememberLineComponent(
            fill = Fill(eintrag.farbe),
            thickness = dicke,
            shape = DashedShape(
                shape = CircleShape,
                dashLength = 2.dp,
                gapLength = 4.dp,
            ),
        )
    }
    val aktuellesX = aktuelleRundeX.toDouble()

    return remember(normaleSaeulen, aktuelleSaeulen, aktuellesX) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(
                entry: ColumnCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent {
                val saeulen = if (entry.x == aktuellesX) aktuelleSaeulen else normaleSaeulen
                return saeulen[seriesIndex % saeulen.size]
            }

            override fun getWidestSeriesColumn(
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent = normaleSaeulen[seriesIndex % normaleSaeulen.size]
        }
    }
}
