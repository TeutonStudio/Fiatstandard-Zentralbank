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
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus


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
            val rohstoffe = Rohstoffe.entries.toList()
            val legende = rohstoffe.map { rohstoff ->
                DiagrammLegendenEintrag(
                    id = "aussenhandel-rohstoff:${rohstoff.name}",
                    bezeichnung = rohstoff.str,
                    farbe = rohstoff.farbe,
                )
            }
            val legendenStatus = rememberDiagrammLegendenStatus(legende)
            val sichtbareRohstoffe = rohstoffe.filter { rohstoff ->
                legendenStatus.istSichtbar("aussenhandel-rohstoff:${rohstoff.name}")
            }
            val resourceChartModel = remember(resourceSaldoList, sichtbareRohstoffe) {
                if (sichtbareRohstoffe.isEmpty()) {
                    null
                } else {
                    CartesianChartModel(
                        ColumnCartesianLayerModel.build {
                            sichtbareRohstoffe.forEach { resource ->
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
            }

            val resourceScrollState = rememberVicoScrollState()
            val resourceZoomState = rememberVicoZoomState(
                initialZoom = remember { Zoom.Content }
            )

            val startAxis = VerticalAxis.rememberStart()
            val endAxis = VerticalAxis.rememberEnd()
            val bottomAxis = HorizontalAxis.rememberBottom()

            Card(modifier = ModiPad5) {
                Column {
                    if (resourceChartModel == null) {
                        Text(
                            text = "Keine Datenreihe ausgewählt",
                            modifier = ModiPad5,
                        )
                    } else {
                        CartesianChartHost(
                            modifier = ModiPad5,
                            chart = rememberCartesianChart(
                                rememberColumnCartesianLayer(
                                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                        sichtbareRohstoffe.map { resource ->
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
                            ),
                            model = resourceChartModel,
                            scrollState = resourceScrollState,
                            zoomState = resourceZoomState
                        )
                    }

                    UmschaltbareDiagrammLegende(
                        eintraege = legende,
                        status = legendenStatus,
                    )
                }
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
            val saldoFarbe = Color.Gray
            val legende = listOf(
                DiagrammLegendenEintrag(
                    id = "aussenhandel-geldbilanz",
                    bezeichnung = "Geldbilanz",
                    farbe = saldoFarbe,
                )
            )
            val legendenStatus = rememberDiagrammLegendenStatus(legende)
            val saldoSichtbar = legendenStatus.istSichtbar("aussenhandel-geldbilanz")
            val saldoChartModel = remember(priceSaldoList, saldoSichtbar) {
                if (!saldoSichtbar) {
                    null
                } else {
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
            }

            val saldoScrollState = rememberVicoScrollState()
            val saldoZoomState = rememberVicoZoomState(
                initialZoom = remember { Zoom.Content }
            )

            val endAxis = VerticalAxis.rememberEnd()
            val bottomAxis = HorizontalAxis.rememberBottom()

            Card(modifier = Modifier.padding(5.dp)) {
                Column {
                    if (saldoChartModel == null) {
                        Text(
                            text = "Keine Datenreihe ausgewählt",
                            modifier = ModiPad5,
                        )
                    } else {
                        CartesianChartHost(
                            modifier = Modifier.padding(5.dp),
                            chart = rememberCartesianChart(
                                rememberLineCartesianLayer(
                                    lineProvider = LineCartesianLayer.LineProvider.series(
                                        LineCartesianLayer.rememberLine(
                                            remember {
                                                LineCartesianLayer.LineFill.single(
                                                    Fill(saldoFarbe)
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

                    UmschaltbareDiagrammLegende(
                        eintraege = legende,
                        status = legendenStatus,
                    )
                }
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
