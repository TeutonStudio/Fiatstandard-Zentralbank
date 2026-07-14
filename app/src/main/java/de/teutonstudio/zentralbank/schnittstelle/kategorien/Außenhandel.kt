package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5


@Composable
fun zeigeAussenhandel(
    spiel: Spiel,
//    priceSaldoList: List<Zahlungsmittel>,
//    resourceSaldoList: List<Map<Rohstoffe, Zahlungsmittel>>,
//    marktpreise: List<Map<Rohstoffe, Zahlungsmittel>>
) {

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(modifier = Modifier.padding(5.dp)) {
            Text(
                text = "Außenhandelsbilanz",
                fontSize = 40.sp,
                modifier = Modifier.padding(5.dp)
            )
        }
        val priceSaldoList = emptyList<Zahlungsmittel>()
        val resourceSaldoList = emptyList<Map<Rohstoffe, Zahlungsmittel>>()
        if (resourceSaldoList.isEmpty()) {
            Card(modifier = ModiPad5) {
                Text(
                    text = "Noch keine Rohstoffdaten für den Außenhandel vorhanden",
                    modifier = ModiPad5
                )
            }
        } else {
            val resourceChartModel = remember(resourceSaldoList, Rohstoffe.entries) {
                CartesianChartModel(
                    ColumnCartesianLayerModel.build {
                        Rohstoffe.entries.forEach { resource ->
                            series(
                                x = List(resourceSaldoList.size) { idx -> idx + 1 },
                                y = resourceSaldoList.map { mapByList ->
                                    mapByList[resource]?.toIntOderNull() ?: 0
                                }
                            )
                        }
                    }
                )
            }

            val resourceScrollState = rememberVicoScrollState()
            val resourceZoomState = rememberVicoZoomState(
                initialZoom = remember { Zoom.Content }
            )

            val startAxis = VerticalAxis.rememberStart()
            val endAxis = VerticalAxis.rememberEnd()
            val bottomAxis = HorizontalAxis.rememberBottom()

            val legendIcon = ShapeComponent(margins = Insets(.5f.dp))
            val legendText = TextComponent(
                padding = Insets(5f.dp, 0f.dp, 10f.dp, 0f.dp)
            )

            Card(modifier = ModiPad5) {
                CartesianChartHost(
                    modifier = ModiPad5,
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(
                            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                Rohstoffe.entries.map { resource ->
                                    rememberLineComponent(
                                        fill = Fill(resource.farbe),
                                        thickness = 8.dp
                                    )
                                }
                            )
                        ),
                        startAxis = startAxis,
                        endAxis = endAxis,
                        bottomAxis = bottomAxis,
                        legend = rememberHorizontalLegend(
                            items = {
                                Rohstoffe.entries.forEach { resource ->
                                    add(
                                        LegendItem(
                                            icon = legendIcon.copy(
                                                fill = Fill(resource.farbe)
                                            ),
                                            labelComponent = legendText,
                                            label = resource.str
                                        )
                                    )
                                }
                            },
                            iconSize = 8.dp,
                            iconLabelSpacing = 8.dp
                        )
                    ),
                    model = resourceChartModel,
                    scrollState = resourceScrollState,
                    zoomState = resourceZoomState
                )
            }
        }

        if (priceSaldoList.isEmpty()) {
            Card(modifier = ModiPad5) {
                Text(
                    text = "Noch keine Geldbilanz für den Außenhandel vorhanden",
                    modifier = ModiPad5
                )
            }
        } else {
            val saldoChartModel = remember(priceSaldoList) {
                CartesianChartModel(
                    LineCartesianLayerModel.build {
                        series(
                            x = List(priceSaldoList.size) { idx -> idx + 1 },
                            y = priceSaldoList.map { saldo ->
                                saldo.toIntOderNull() ?: 0
                            }
                        )
                    }
                )
            }

            val saldoScrollState = rememberVicoScrollState()
            val saldoZoomState = rememberVicoZoomState(
                initialZoom = remember { Zoom.Content }
            )

            val endAxis = VerticalAxis.rememberEnd()
            val bottomAxis = HorizontalAxis.rememberBottom()

            Card(modifier = Modifier.padding(5.dp)) {
                CartesianChartHost(
                    modifier = Modifier.padding(5.dp),
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    remember {
                                        LineCartesianLayer.LineFill.single(
                                            Fill(Color.Gray)
                                        )
                                    }
                                )
                            )
                        ),
                        endAxis = endAxis,
                        bottomAxis = bottomAxis
                    ),
                    model = saldoChartModel,
                    scrollState = saldoScrollState,
                    zoomState = saldoZoomState
                )
            }
        }
    }
}


@Preview
@Composable
private fun PreviewAussenhandel() {
    val testPriceList = listOf((50),(50),(20),(0),(70),(-20),(30),(-70))
    val marketprices = listOf(
        mapOf(Pair("Vieh",10), Pair("Getreide",15),Pair("Holz",20),Pair("Stein",25),Pair("Ziegel",30),Pair("Tuch",35),Pair("Münze",40),Pair("Papier",45)),
        mapOf(Pair("Vieh",15), Pair("Getreide",25),Pair("Holz",50),Pair("Stein",60),Pair("Ziegel",50),Pair("Tuch",35),Pair("Münze",40),Pair("Papier",45)),
        mapOf(Pair("Vieh",20), Pair("Getreide",40),Pair("Holz",80),Pair("Stein",125),Pair("Ziegel",90),Pair("Tuch",35),Pair("Münze",40),Pair("Papier",45))
    )
    val testResourceList = listOf(
        mapOf(Pair("Vieh",2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",0),Pair("Papier",2)),
        mapOf(Pair("Vieh",4),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",0),Pair("Papier",2)),
        mapOf(Pair("Vieh",-2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",2),Pair("Papier",2)),
        mapOf(Pair("Vieh",2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",2),Pair("Papier",2)),
        mapOf(Pair("Vieh",2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",2),Pair("Papier",2)),
        mapOf(Pair("Vieh",2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",2),Pair("Papier",2)),
        mapOf(Pair("Vieh",2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",2),Pair("Papier",2)),
        mapOf(Pair("Vieh",2),Pair("Getreide",2),Pair("Holz",2),Pair("Stein",2),Pair("Ziegel",2),Pair("Tuch",2),Pair("Münze",2),Pair("Papier",2))
    )
    Column {
        // zeigeAussenhandel(testPriceList,testResourceList,marketprices)
    }
}
