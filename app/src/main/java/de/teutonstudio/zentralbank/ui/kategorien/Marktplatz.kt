package de.teutonstudio.zentralbank.ui.kategorien

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.LineProvider.Companion.series
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
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.HandelsDaten
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.toBauteilPreis
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.ausgabe.zeigeRohstoff
import de.teutonstudio.zentralbank.ui.markBy

@Composable
fun zeigeHafenPreis(
    hafen: String,
    marktpreise: List<Map<Rohstoffe, Zahlungsmittel>>,
    beiKlick: (Pair<String,Pair<Rohstoffe, Zahlungsmittel>>) -> Unit
) {
    val havenList = mapOf(
        Pair( "3 : 1 Hafen",1f + 1f/3f ),
        Pair( "2 : 1 Hafen",1f + 1f/2f ),
        Pair( "6 : 1 Hafen",1f + 1f/6f ),
        Pair( "5 : 1 Hafen",1f + 1f/5f ),
        Pair( "8 : 1 Hafen",1f + 1f/8f ),
        Pair( "9 : 1 Hafen",1f + 1f/9f ),
        Pair("11 : 1 Hafen",1f + 1f/11f ),
        Pair( "1 : 1 Hafen",1f )
    )
    val resources = marktpreise.first().keys.toList()
    Card(modifier = ModiPad5) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row( modifier = ModiPad5,
                verticalAlignment = Alignment.CenterVertically ) {
                Image(
                    painter = painterResource(id = R.drawable.handel),
                    contentDescription = null
                )
                Text(text = hafen, fontSize = 25.sp)
            }
            VerticalGrid(
                columns = SimpleGridCells.Fixed(3),
//                modifier = Modifier.width(250.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center
            ) {
                Text(modifier = Modifier.padding(5.dp), text = "Rohstoff")
                Text(modifier = Modifier.padding(5.dp), text = "Handelskosten")
                Text(modifier = Modifier.padding(5.dp), text = "Handelserlös")
                Rohstoffe.entries.forEach {
                    zeigeRohstoff(it,12.sp)
                    val marktpreis = marktpreise.last()[it]!!
                    val importPreis = (marktpreis * havenList[hafen]!!)
                    val exportPreis = (marktpreis / havenList[hafen]!!)
                    Text(modifier = ModiPad5.clickable {
                        val handelsdaten = Pair(it, importPreis)
                        beiKlick(Pair("konsumiert", handelsdaten))
                    }, text = markBy(importPreis) )
                    Text(modifier = ModiPad5.clickable {
                        val handelsdaten = Pair(it, exportPreis)
                        beiKlick(Pair("veräußert", handelsdaten))
                    }, text = markBy(exportPreis) )
                }
                Rohstoffe.entries.forEach {
                    zeigeRohstoff(it,12.sp)
                    val marktpreis = marktpreise.last()[it]!!
                    val importPreis = (marktpreis * havenList[hafen]!!)
                    val exportPreis = (marktpreis / havenList[hafen]!!)
                    Text(modifier = ModiPad5.clickable {
                        val handelsdaten = Pair(it, importPreis)
                        beiKlick(Pair("konsumiert", handelsdaten))
                    }, text = markBy(importPreis) )
                    Text(modifier = ModiPad5.clickable {
                        val handelsdaten = Pair(it, exportPreis)
                        beiKlick(Pair("veräußert", handelsdaten))
                    }, text = markBy(exportPreis) )
                }
            }
            Row( modifier = ModiPad5 ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {


                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    for (idx in 0..4) {
                    }

                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    for (idx in 0..4) {
                    }
                }
            }
        }
    }
}

private fun <A> List<Map<A, Zahlungsmittel>>.toY(args: Set<A> =this.first().keys): Map<A,List<Int>> = args.associateWith { arg -> this.map { it[arg]!!.toIntOderNull() } }
@Composable
private fun <A> List<Map<A, Zahlungsmittel>>.toChart(args: Set<A> =this.first().keys): CartesianChartModel = remember(this,args) { CartesianChartModel( LineCartesianLayerModel.build { args.forEach { arg ->
    var hatSchonWert = false
    val punkte = this@toChart.mapIndexedNotNull { idx, preisMap ->
        val y = preisMap[arg]!!.toIntOderNull()
        if (!hatSchonWert && y == 0) { null } else {
            hatSchonWert = true
            idx to y
        }
    }
    if (punkte.isNotEmpty()) { series(
        x = punkte.map { it.first },
        y = punkte.map { it.second }
    ) }
} } ) }

@Composable
fun zeigeMarktplatz(spiel: Spiel,onTrade: (HandelsDaten) -> Unit) {
    val hafenListe = remember {
        listOf(
            "3 : 1 Hafen",
            "2 : 1 Hafen",
            "6 : 1 Hafen",
            "5 : 1 Hafen",
            "8 : 1 Hafen",
            "9 : 1 Hafen",
            "11 : 1 Hafen",
            "1 : 1 Hafen"
        )
    }

    val isBilanzExpanded = remember { mutableStateOf(true) }

    val chart = remember { mutableIntStateOf(0) }
    val isBuying = remember { mutableStateOf(false) }
    val isSelling = remember { mutableStateOf(false) }

    val inputResource = remember { mutableStateOf<Rohstoffe?>(null) }
    val inputPrice = remember { mutableFloatStateOf(0f) }
    val inputAmount = remember { mutableIntStateOf(0) }
    val inputAmountText = remember { mutableStateOf("") }
    val eingabeSpieler = remember { mutableStateOf<String?>(null) }

    Column(horizontalAlignment = Alignment.Start) {
        if (spiel.marktpreise.isNotEmpty()) {
            Card(
                modifier = ModiPad5.clickable {
                    isBuying.value = false
                    isSelling.value = false
                }
            ) {
                val rohstoffChart = spiel.marktpreise.toChart(Rohstoffe.entries.toSet())
                val bauteilChart = spiel.marktpreise.toBauteilPreis().toChart(Bauteil.entries.toSet())

                val legendIcon = ShapeComponent(margins = Insets(.5f.dp))
                val legendText = TextComponent(padding = Insets(5f.dp, 0f.dp, 10f.dp, 0f.dp))

                val bilanzModifier = ModiPad5.clickable { isBilanzExpanded.value = !isBilanzExpanded.value }

                if (isBilanzExpanded.value) {
                    val linien = Rohstoffe.entries.map { rohstoff ->
                        LineCartesianLayer.rememberLine(
                            fill = remember(rohstoff) {
                                LineCartesianLayer.LineFill.single(
                                    Fill(rohstoff.farbe)
                                )
                            },
                            interpolator = remember {
                                LineCartesianLayer.Interpolator.cubic(
                                    curvature = 0.5f
                                )
                            }
                        )
                    }

                    CartesianChartHost(
                        modifier = bilanzModifier,
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = series(*linien.toTypedArray())
                            ),
                            endAxis = VerticalAxis.rememberEnd(),
                            bottomAxis = HorizontalAxis.rememberBottom(),
                            legend = rememberHorizontalLegend(
                                items = {
                                    Rohstoffe.entries.forEach { rohstoff -> add(LegendItem(
                                        icon = ShapeComponent(
                                            fill = Fill(rohstoff.farbe),
                                            margins = Insets(.5f.dp)
                                        ),
                                        labelComponent = legendText,
                                        label = rohstoff.str
                                    )) }
                                },
                                iconSize = 8.dp,
                                iconLabelSpacing = 8.dp
                            )
                        ),
                        model = if (chart.intValue == 0) { rohstoffChart } else { bauteilChart },
                        scrollState = rememberVicoScrollState(),
                        zoomState = rememberVicoZoomState(initialZoom = remember { Zoom.Content })
                    )
                } else {
                    Card(modifier = bilanzModifier) {
                        Text(
                            text = "Marktbilanz",
                            fontSize = 40.sp,
                            modifier = ModiPad5
                        )
                    }
                }
            }
        } else {
            Card(modifier = ModiPad5) {
                Text(
                    text = "Noch keine Marktpreise vorhanden",
                    modifier = ModiPad5
                )
            }
        }

        if (isBuying.value || isSelling.value) {
            Card(modifier = ModiPad5) {
                Column(
                    modifier = ModiPad5,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val gewählterRohstoff = inputResource.value
                    val gewählterSpieler = eingabeSpieler.value
                    val saldo = spiel.spielerSaldo.lastOrNull()?.map { it.key.name to it.value }?.toMap() ?: emptyMap()
//                    val spielerListe = saldo?.keys.orEmpty().toList()

                    val art = if(isBuying.value) "Kauf" else "Verkauf"
                    Text(
                        text = "$art von ${gewählterRohstoff?.str ?: "?"} für %.2f Mark je".format(inputPrice.floatValue),
                        fontSize = 15.sp
                    )

                    var expandedPlayer by remember { mutableStateOf(false) }

                    Box(
                        modifier = ModiPad15.clickable { expandedPlayer = !expandedPlayer }
                    ) {
                        Text(
                            text = gewählterSpieler ?: "Spieler wählen",
                            fontSize = 25.sp
                        )
                    }

                    DropdownMenu(
                        expanded = expandedPlayer,
                        onDismissRequest = { expandedPlayer = false }
                    ) {
                        spiel.spielerStringListe.forEach { spieler ->
                            DropdownMenuItem(
                                text = { Text(text = spieler) },
                                onClick = {
                                    eingabeSpieler.value = spieler
                                    expandedPlayer = false
                                }
                            )
                        }
                    }

                    if (gewählterSpieler != null) {
                        Text(text = "$gewählterSpieler hat noch ${saldo[gewählterSpieler] ?: 0} Mark")
                    }

                    TextField(
                        value = inputAmountText.value,
                        onValueChange = { newAmount ->
                            val gefiltert = newAmount.filter { it.isDigit() }
                            inputAmountText.value = gefiltert
                            inputAmount.intValue = gefiltert.toIntOrNull() ?: 0
                        },
                        label = {
                            Text("Anzahl an ${gewählterRohstoff?.str ?: "Rohstoff"} eingeben")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    if (
                        gewählterSpieler != null &&
                        gewählterRohstoff != null &&
                        inputAmount.intValue > 0
                    ) {
                        Box(
                            modifier = ModiPad5.clickable {
                                if (isBuying.value) {
/*                                    onTrade(
                                        HandelsDaten(0,0,gewählterSpieler to "-ausland-",gewählterRohstoff to inputAmount.intValue,
                                            Zahlungsmittel(inputPrice.floatValue.toInt()))
                                    )*/
                                } else if (isSelling.value) {
/*                                    onTrade(
                                        HandelsDaten(0,0,"-ausland-" to gewählterSpieler,gewählterRohstoff to inputAmount.intValue,
                                            Zahlungsmittel(inputPrice.floatValue.toInt()))
                                    )*/
                                }

                                isBuying.value = false
                                isSelling.value = false
                                inputResource.value = null
                                eingabeSpieler.value = null
                                inputAmount.intValue = 0
                                inputAmountText.value = ""
                            }
                        ) {
                            Text(text = "Speichern", fontSize = 30.sp)
                        }
                    }
                }
            }
        } else {
/*            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp)
            ) {
                items(
                    items = hafenListe,
                    key = { hafen -> hafen }
                ) { hafen ->
                    zeigeHafenPreis(hafen, marketPrices) { aktion, rohstoff, preis ->
                        inputResource.value = rohstoff
                        inputPrice.floatValue = preis
                        inputAmount.intValue = 0
                        inputAmountText.value = ""
                        inputPlayer.value = null

                        isBuying.value = aktion == "konsumiert"
                        isSelling.value = aktion == "veräußert"
                    }
                }
            }*/
        }
    }
}

@Preview(
    widthDp = 1920/2,
    heightDp = 1080
)
@Composable
private fun PreviewMarktplatz() {
    val spiel = remember { TestSpiel }
    Column {
        zeigeMarktplatz(spiel) {}
//        zeigeHafenPreis("3 : 1 Hafen",marketprices,{})
        /*
        zeigeWarenkorb(
            marketprices,
            playerSaldoTestList,
        ) { }*/
    }
}
