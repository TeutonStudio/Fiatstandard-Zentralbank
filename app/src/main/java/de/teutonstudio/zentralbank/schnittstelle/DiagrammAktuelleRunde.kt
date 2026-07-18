package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
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
import de.teutonstudio.zentralbank.datenbank.SpielZeitpunkt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class DiagrammSubrundenDaten(
    val x: List<Double>,
    val y: List<Number>,
    val prognoseAbX: Double,
)

internal val leererDiagrammEintrag = DiagrammLegendenEintrag(
    id = "diagramm-leerzustand",
    bezeichnung = "",
    farbe = Color.Transparent,
)

internal fun bilanzDiagrammYBereich(istLeer: Boolean): CartesianLayerRangeProvider =
    if (istLeer) {
        CartesianLayerRangeProvider.fixed(minY = -1.0, maxY = 1.0)
    } else {
        CartesianLayerRangeProvider.auto()
    }

internal fun rundenDiagrammMaxX(
    zeitpunkt: SpielZeitpunkt,
    rundenAnzahl: Int,
): Double {
    val letzteRunde = (rundenAnzahl - 1).coerceAtLeast(1)
    return if (zeitpunkt.runde == 0) {
        letzteRunde.toDouble()
    } else {
        letzteRunde * zeitpunkt.spielerAnzahl.toDouble()
    }
}

internal fun leeresLinienDiagrammModell(maxX: Number): CartesianChartModel =
    CartesianChartModel(
        LineCartesianLayerModel.build {
            series(
                x = listOf(0.0, maxX.toDouble().coerceAtLeast(1.0)),
                y = listOf(0, 0),
            )
        }
    )

internal fun leeresSaeulenDiagrammModell(maxX: Number): CartesianChartModel =
    CartesianChartModel(
        ColumnCartesianLayerModel.build {
            series(
                x = listOf(0.0, maxX.toDouble().coerceAtLeast(1.0)),
                y = listOf(0, 0),
            )
        }
    )

internal fun rundenXWerteOhneSubrunden(
    anzahl: Int,
): List<Int> {
    require(anzahl >= 0) { "Die Anzahl der Rundenwerte darf nicht negativ sein." }
    return List(anzahl) { runde -> runde }
}

internal fun erweitereDiagrammUmSubrunden(
    x: Collection<Number>,
    y: Collection<Number>,
    zeitpunkt: SpielZeitpunkt,
): DiagrammSubrundenDaten {
    require(x.size == y.size) { "X- und Y-Werte müssen gleich lang sein." }
    if (x.isEmpty()) return DiagrammSubrundenDaten(emptyList(), emptyList(), zeitpunkt.prognoseAbX)

    val rundenXWerte = x.map(Number::toDouble)
    val yWerte = y.toList()
    val aktuellerRundenIndex = rundenXWerte.indexOfFirst { wert ->
        wert == zeitpunkt.runde.toDouble()
    }
    if (aktuellerRundenIndex < 0 || zeitpunkt.runde == 0) {
        return DiagrammSubrundenDaten(
            x = if (zeitpunkt.runde == 0) {
                rundenXWerte
            } else {
                rundenXWerte.map { runde -> runde * zeitpunkt.spielerAnzahl }
            },
            y = yWerte,
            prognoseAbX = if (zeitpunkt.runde == 0) 0.0 else zeitpunkt.prognoseAbX,
        )
    }

    val xWerte = rundenXWerte.map { runde -> runde * zeitpunkt.spielerAnzahl }

    val aktuelleRundeY = yWerte[aktuellerRundenIndex]
    val vorherigeRundeY = yWerte.getOrNull(aktuellerRundenIndex - 1) ?: aktuelleRundeY
    val neueX = mutableListOf<Double>()
    val neueY = mutableListOf<Number>()

    xWerte.indices.forEach { index ->
        if (index != aktuellerRundenIndex) {
            neueX += xWerte[index]
            neueY += yWerte[index]
            return@forEach
        }

        repeat(zeitpunkt.spielerAnzahl) { spielerIndex ->
            neueX += zeitpunkt.xNachZug(spielerIndex)
            neueY += if (spielerIndex < zeitpunkt.aktiverSpielerIndex) {
                vorherigeRundeY
            } else {
                aktuelleRundeY
            }
        }
    }

    return DiagrammSubrundenDaten(
        x = neueX,
        y = neueY,
        prognoseAbX = zeitpunkt.prognoseAbX,
    )
}

fun LineCartesianLayerModel.BuilderScope.seriesMitGepunkteterAktuellerRunde(
    x: Collection<Number>,
    y: Collection<Number>,
    aktuelleRundeX: Number? = null,
    zeitpunkt: SpielZeitpunkt? = null,
) {
    require(x.size == y.size) { "X- und Y-Werte müssen gleich lang sein." }
    if (x.isEmpty()) return

    val subrundenDaten = zeitpunkt?.let { aktuellerZeitpunkt ->
        erweitereDiagrammUmSubrunden(x, y, aktuellerZeitpunkt)
    }
    val xWerte = subrundenDaten?.x ?: x.toList()
    val yWerte = subrundenDaten?.y ?: y.toList()
    val prognoseAbX = subrundenDaten?.prognoseAbX
        ?: (aktuelleRundeX ?: xWerte.last()).toDouble()
    val prognoseIndex = xWerte.indexOfFirst { wert -> wert.toDouble() >= prognoseAbX }
    require(prognoseIndex >= 0) { "Die aktuelle Runde muss innerhalb der X-Werte liegen." }
    val historischeAnzahl = prognoseIndex.coerceAtLeast(1)
    val gepunkteterStart = (prognoseIndex - 1).coerceAtLeast(0)

    series(
        x = xWerte.take(historischeAnzahl),
        y = yWerte.take(historischeAnzahl),
    )
    series(
        x = xWerte.drop(gepunkteterStart),
        y = yWerte.drop(gepunkteterStart),
    )
}

fun ColumnCartesianLayerModel.BuilderScope.seriesMitSubrundenPrognose(
    x: Collection<Number>,
    y: Collection<Number>,
    zeitpunkt: SpielZeitpunkt,
) {
    val daten = erweitereDiagrammUmSubrunden(x, y, zeitpunkt)
    series(x = daten.x, y = daten.y)
}

@Composable
fun rememberRundenachse(
    zeitpunkt: SpielZeitpunkt,
): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    val abstand = if (zeitpunkt.runde == 0) 1 else zeitpunkt.spielerAnzahl
    val itemPlacer = remember(abstand) {
        HorizontalAxis.ItemPlacer.aligned(spacing = { abstand })
    }
    val formatter = remember(abstand) {
        CartesianValueFormatter { _, value, _ ->
            (value.roundToInt() / abstand).toString()
        }
    }
    return HorizontalAxis.rememberBottom(
        valueFormatter = formatter,
        itemPlacer = itemPlacer,
    )
}

private fun vollrundenImBereich(
    sichtbarerBereich: ClosedFloatingPointRange<Double>,
    gesamterBereich: ClosedFloatingPointRange<Double>,
    rand: Int,
): List<Double> {
    val ersteRunde = maxOf(
        ceil(sichtbarerBereich.start).toInt() - rand,
        ceil(gesamterBereich.start).toInt(),
    )
    val letzteRunde = minOf(
        floor(sichtbarerBereich.endInclusive).toInt() + rand,
        floor(gesamterBereich.endInclusive).toInt(),
    )
    if (ersteRunde > letzteRunde) return emptyList()
    return (ersteRunde..letzteRunde).map(Int::toDouble)
}

@Composable
fun rememberVollrundenachse(): HorizontalAxis<Axis.Position.Horizontal.Bottom> {
    val itemPlacer = remember {
        val basis = HorizontalAxis.ItemPlacer.aligned(spacing = { 1 })
        object : HorizontalAxis.ItemPlacer by basis {
            override fun getLabelValues(
                context: CartesianDrawingContext,
                visibleXRange: ClosedFloatingPointRange<Double>,
                fullXRange: ClosedFloatingPointRange<Double>,
                maxLabelWidth: Float,
            ): List<Double> = vollrundenImBereich(visibleXRange, fullXRange, rand = 2)

            override fun getLineValues(
                context: CartesianDrawingContext,
                visibleXRange: ClosedFloatingPointRange<Double>,
                fullXRange: ClosedFloatingPointRange<Double>,
                maxLabelWidth: Float,
            ): List<Double> = vollrundenImBereich(visibleXRange, fullXRange, rand = 1)
        }
    }
    val formatter = remember {
        CartesianValueFormatter { _, value, _ -> value.roundToInt().toString() }
    }
    val hilfslinie = rememberAxisGuidelineComponent(
        shape = DashedShape(
            shape = CircleShape,
            dashLength = 2.dp,
            gapLength = 4.dp,
        )
    )
    return HorizontalAxis.rememberBottom(
        valueFormatter = formatter,
        guideline = hilfslinie,
        itemPlacer = itemPlacer,
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
    prognoseAbX: Number,
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
    val prognoseStart = prognoseAbX.toDouble()

    return remember(normaleSaeulen, aktuelleSaeulen, prognoseStart) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(
                entry: ColumnCartesianLayerModel.Entry,
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent {
                val saeulen = if (entry.x >= prognoseStart) aktuelleSaeulen else normaleSaeulen
                return saeulen[seriesIndex % saeulen.size]
            }

            override fun getWidestSeriesColumn(
                seriesIndex: Int,
                extraStore: ExtraStore,
            ): LineComponent = normaleSaeulen[seriesIndex % normaleSaeulen.size]
        }
    }
}
