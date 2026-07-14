package de.teutonstudio.zentralbank.schnittstelle.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import de.teutonstudio.zentralbank.R

import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.LeftText
import de.teutonstudio.zentralbank.schnittstelle.ModiPad10
import de.teutonstudio.zentralbank.schnittstelle.ModiPad15
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.RightText
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarben
import de.teutonstudio.zentralbank.schnittstelle.markAchsenFormatter
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus

@Composable
fun SpielerBilanz(
    //spielerListe: List<Spieler>,
    spielerSaldo: List<Map<Spieler, Zahlungsmittel>>,
    isBilanzExpanded: MutableState<Boolean> = remember { mutableStateOf(true) }
) {
    val spielerListe = spielerSaldo.first().keys.toList()
    if (isBilanzExpanded.value) {
        Card(modifier = ModiPad5.clickable { isBilanzExpanded.value = !isBilanzExpanded.value }) {
            //val spielerNamen = spielerListe.map { it.erhalteNamen() }
            val spielerFarben = erhalteSpielerFarben(spielerListe)

            val scrollState = rememberVicoScrollState()
            val zoomState = rememberVicoZoomState(initialZoom = remember { Zoom.Content })

            val endAxis = VerticalAxis.rememberEnd(
                valueFormatter = markAchsenFormatter,
            )
            val bottomAxis = HorizontalAxis.rememberBottom()

            val serien = remember(spielerListe, spielerSaldo) {
                spielerListe.mapNotNull { spieler ->
                    val yWerte: List<Number> = spielerSaldo.map { saldoProRunde ->
                        saldoProRunde[spieler]?.toIntOderNull() ?: 0
                    }

                    if (yWerte.isEmpty()) null else spieler to yWerte
                }
            }

            val legende = spielerListe.map { spieler ->
                DiagrammLegendenEintrag(
                    id = "spieler:${spieler.name}",
                    bezeichnung = spieler.name,
                    farbe = spielerFarben[spieler] ?: Color.Black,
                )
            }
            val legendenStatus = rememberDiagrammLegendenStatus(legende)
            val sichtbareSerien = serien.filter { (spieler, _) ->
                legendenStatus.istSichtbar("spieler:${spieler.name}")
            }

            val chartModel = remember(sichtbareSerien) {
                if (sichtbareSerien.isEmpty()) {
                    null
                } else {
                    CartesianChartModel(
                        LineCartesianLayerModel.build {
                            sichtbareSerien.forEach { (_, yWerte) ->
                                series(
                                    x = yWerte.indices.toList(),
                                    y = yWerte
                                )
                            }
                        }
                    )
                }
            }

            Column(modifier = ModiPad5) {
                when {
                    serien.isEmpty() -> Text(
                        text = "Noch keine Bilanzdaten vorhanden",
                        modifier = Modifier.padding(16.dp)
                    )

                    chartModel == null -> Text(
                        text = "Keine Datenreihe ausgewählt",
                        modifier = Modifier.padding(16.dp),
                    )

                    else -> CartesianChartHost(
                        modifier = ModiPad5,
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    sichtbareSerien.map { (spieler, _) ->
                                        val farbe = spielerFarben[spieler] ?: Color.Black

                                        LineCartesianLayer.rememberLine(
                                            fill = remember(farbe) {
                                                LineCartesianLayer.LineFill.single(
                                                    Fill(farbe)
                                                )
                                            },
                                            interpolator = remember {
                                                LineCartesianLayer.Interpolator.cubic(
                                                    curvature = 0.5f,
                                                )
                                            },
                                        )
                                    }
                                )
                            ),
                            endAxis = endAxis,
                            bottomAxis = bottomAxis,
                        ),
                        model = chartModel,
                        scrollState = scrollState,
                        zoomState = zoomState
                    )
                }

                if (serien.isNotEmpty()) {
                    UmschaltbareDiagrammLegende(
                        eintraege = legende,
                        status = legendenStatus,
                    )
                }

            }
        }
    } else {
        Card(modifier = ModiPad5.clickable { isBilanzExpanded.value = !isBilanzExpanded.value }) {
            Text(
                text = "Spielerbilanz",
                fontSize = 40.sp,
                modifier = ModiPad5
            )
        }
    }
}

@Composable
fun zeigeSpieler(
    spiel: Spiel,
    onBuild: (Bauteil) -> Unit,
    onBuildByPlayer: (String, Bauteil, Boolean) -> Unit,
    onDeclareWar: (Pair<String, String>) -> Unit,
    onDeclareMilitary: (Pair<Pair<String, Int>, Pair<String, Int>>) -> Unit,
    onDeclarePeace: (Pair<String, String>) -> Unit,
) {
    val siedlerFarben = erhalteSpielerFarben(spiel.spielerListe)
    var isWarExpanded by remember { mutableStateOf(false) }
    var isMilitaryExpanded by remember { mutableStateOf(false) }
    var isPeaceExpanded by remember { mutableStateOf(false) }

    if (!isWarExpanded && !isMilitaryExpanded && !isPeaceExpanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpielerBilanz(spiel.spielerSaldo)
            VerticalGrid(
                columns = SimpleGridCells.Adaptive(225.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                spiel.spielerListe.forEach { spieler ->
                    val farbe = siedlerFarben[spieler] ?: Color.Black
                    zeigeSpielerDaten(
                        siedlerName = spieler.name,
                        siedlerFarbe = farbe,
                        siedlerBauteile = spieler.erhalteBauSaldoZurRunde(),
                        istBearbeitbar = false,
                        onManipulateData = { _, _, _ -> },
                    )
                }
            }
        }
    } else if (isWarExpanded) {
        Card(modifier = Modifier.padding(25.dp)) {
            val inputAggressor = remember { mutableStateOf("spieler wählen") }
            val inputVerteidiger = remember { mutableStateOf("spieler wählen") }

            Column {
                Text(
                    text = "Kriegserklärung",
                    fontSize = 40.sp,
                    modifier = ModiPad10,
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    item { Text(text = "Aggressor: ", fontSize = 25.sp) }
                    item { spielerAuswahl(spiel.spielerListe,inputAggressor) }

                    item { Text(text = "Verteidiger: ", fontSize = 25.sp) }
                    item { spielerAuswahl(spiel.spielerListe,inputVerteidiger) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onDeclareWar(inputAggressor.value to inputVerteidiger.value)
                        isWarExpanded = false
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Krieg erklären",
                        fontSize = 30.sp,
                        modifier = ModiPad10
                    )
                }
            }
        }
    } else if (isMilitaryExpanded) {
        Card(modifier = Modifier.padding(25.dp)) {
            val military = listOf("Ritter lvl 1", "Ritter lvl 2", "Ritter lvl 3")

            val inputAggressor = remember { mutableStateOf("Aggressor wählen") }
            val inputVerteidiger = remember { mutableStateOf("Verteidiger wählen") }
            val inputAggressorRitter = remember { mutableStateOf("Ritter wählen") }
            val inputVerteidigerRitter = remember { mutableStateOf("Ritter wählen") }

            Column {
                Text(
                    text = "Militäreinsatz",
                    fontSize = 40.sp,
                    modifier = ModiPad10,
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = ModiPad15,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item { Text(text = "Aggressor: ", fontSize = 25.sp) }

                    /*item {
                        Column {
                            var expandedAggressor by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .padding(15.dp)
                                    .clickable { expandedAggressor = !expandedAggressor }
                            ) {
                                Text(text = inputAggressor.value, fontSize = 25.sp)
                            }

                            DropdownMenu(
                                expanded = expandedAggressor,
                                onDismissRequest = { expandedAggressor = false }
                            ) {
                                siedlerListe.forEach { siedler ->
                                    DropdownMenuItem(
                                        text = { Text(text = siedler) },
                                        onClick = {
                                            inputAggressor.value = siedler
                                            expandedAggressor = false
                                        }
                                    )
                                }
                            }

                            var expandedAggressorRitter by remember { mutableStateOf(false) }

                            Box(
                                modifier = ModiPad15.clickable { expandedAggressorRitter = !expandedAggressorRitter }
                            ) {
                                Text(text = inputAggressorRitter.value, fontSize = 25.sp)
                            }

                            DropdownMenu(
                                expanded = expandedAggressorRitter,
                                onDismissRequest = { expandedAggressorRitter = false }
                            ) {
                                military.forEach { ritter ->
                                    DropdownMenuItem(
                                        text = { Text(text = ritter) },
                                        onClick = {
                                            inputAggressorRitter.value = ritter
                                            expandedAggressorRitter = false
                                        }
                                    )
                                }
                            }
                        }
                    }*/

                    item { Text(text = "Verteidiger: ", fontSize = 25.sp) }

                    /*item {
                        Column {
                            var expandedVerteidiger by remember { mutableStateOf(false) }

                            Box(
                                modifier = ModiPad15.clickable { expandedVerteidiger = !expandedVerteidiger }
                            ) {
                                Text(text = inputVerteidiger.value, fontSize = 25.sp)
                            }

                            DropdownMenu(
                                expanded = expandedVerteidiger,
                                onDismissRequest = { expandedVerteidiger = false }
                            ) {
                                siedlerListe.forEach { siedler ->
                                    DropdownMenuItem(
                                        text = { Text(text = siedler) },
                                        onClick = {
                                            inputVerteidiger.value = siedler
                                            expandedVerteidiger = false
                                        }
                                    )
                                }
                            }

                            var expandedVerteidigerRitter by remember { mutableStateOf(false) }

                            Box(
                                modifier = ModiPad15.clickable { expandedVerteidigerRitter = !expandedVerteidigerRitter }
                            ) {
                                Text(text = inputVerteidigerRitter.value, fontSize = 25.sp)
                            }

                            DropdownMenu(
                                expanded = expandedVerteidigerRitter,
                                onDismissRequest = { expandedVerteidigerRitter = false }
                            ) {
                                military.forEach { ritter ->
                                    DropdownMenuItem(
                                        text = { Text(text = ritter) },
                                        onClick = {
                                            inputVerteidigerRitter.value = ritter
                                            expandedVerteidigerRitter = false
                                        }
                                    )
                                }
                            }
                        }
                    }*/
                }

                Column(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onDeclareMilitary(
                            Pair( Pair(
                                inputAggressor.value,
                                military.indexOf(inputAggressorRitter.value) + 1
                            ), Pair(
                                inputVerteidiger.value,
                                military.indexOf(inputVerteidigerRitter.value) + 1
                            ) )
                        )
                        isMilitaryExpanded = false
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Schlacht führen",
                        fontSize = 30.sp,
                        modifier = ModiPad10,
                    )
                }
            }
        }
    } else if (isPeaceExpanded) {
        Card(modifier = Modifier.padding(25.dp)) {
            val inputPeaceVariant = remember { mutableStateOf("Friedenserklärung wählen") }

            Column {
                var expandedPeaceTreaty by remember { mutableStateOf(false) }

                Text(
                    text = inputPeaceVariant.value,
                    fontSize = 40.sp,
                    modifier = ModiPad10.clickable { expandedPeaceTreaty = true },
                )

                DropdownMenu(
                    expanded = expandedPeaceTreaty,
                    onDismissRequest = { expandedPeaceTreaty = false }
                ) {
                    listOf("Hegemonialfrieden", "Verhandlungsfrieden").forEach { frieden ->
                        DropdownMenuItem(
                            text = { Text(text = frieden) },
                            onClick = {
                                inputPeaceVariant.value = frieden
                                expandedPeaceTreaty = false
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onDeclarePeace(inputPeaceVariant.value to inputPeaceVariant.value)
                        isPeaceExpanded = false
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Frieden schließen",
                        fontSize = 30.sp,
                        modifier = ModiPad10
                    )
                }
            }
        }
    }
}

@Composable
fun spielerAuswahl(
    spielerListe: List<Spieler>,
    eingabe: MutableState<String>,
    ausgeklappt: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    Box(modifier = ModiPad15.clickable { ausgeklappt.value = !ausgeklappt.value }) {
        Text(text = eingabe.value, fontSize = 25.sp)
    }
    DropdownMenu(
        expanded = ausgeklappt.value,
        onDismissRequest = { ausgeklappt.value = false }
    ) { spielerListe.forEach { DropdownMenuItem(
        text = { Text(text = it.name) },
        onClick = {
            eingabe.value = it.name
            ausgeklappt.value = false
        }
    ) } }
}

@Composable
fun zeigeSpielerDaten(
    siedlerName: String,
    siedlerFarbe: Color,
    siedlerBauteile: Map<out Bauteil, Int> = Bauteil.entries.associateWith { 0 },
    istBearbeitbar: Boolean = true,
    onManipulateData: (String, Bauteil, Boolean) -> Unit
) {
    val isPlayerExpanded = remember { mutableStateOf(false) }

    val playerBauteilAmount = remember(siedlerBauteile) {
        mutableStateMapOf<Bauteil, Int>().apply {
            Bauteil.entries.forEach { bauteil ->
                this[bauteil] = siedlerBauteile[bauteil] ?: 0
            }
        }
    }

    Card(
        modifier = ModiPad5,
        onClick = { isPlayerExpanded.value = !isPlayerExpanded.value }
    ) {
        Box(
            modifier = Modifier.background(siedlerFarbe).fillMaxSize().offset(20.dp, 0.dp)
        ) {
            VerticalGrid(
                columns = SimpleGridCells.Fixed(2),
                modifier = ModiPad5.width(280.dp)
            ) {
                Text(
                    text = siedlerName,
                    fontSize = 20.sp,
                    modifier = ModiPad5.span(2).offset((-25).dp, 0.dp),
                    textAlign = TextAlign.Center
                )

                if (isPlayerExpanded.value && istBearbeitbar) {
                    Bauteil.entries.forEach { bauteil ->
                        RightText(text = "$bauteil: ")

                        Row {
                            val amount = playerBauteilAmount[bauteil] ?: 0
                            val modi = Modifier.padding(5.dp, 0.dp)

                            LeftText(text = "$amount stk",modifier = modi)

                            Image(
                                painter = painterResource(id = R.drawable.plus),
                                contentDescription = null,
                                modifier = modi.clickable {
                                    playerBauteilAmount[bauteil] = amount + 1
                                    onManipulateData(siedlerName, bauteil, true)
                                }
                            )

                            Image(
                                painter = painterResource(id = R.drawable.minus),
                                contentDescription = null,
                                alpha = 0.5f,
                                modifier = modi.clickable {
                                    playerBauteilAmount[bauteil] = amount - 1
                                    onManipulateData(siedlerName, bauteil, false)
                                }
                            )
                        }
                    }
                } else {
                    val belegteBauteile = Bauteil.entries.map { it to (playerBauteilAmount[it] ?: 0) }.filter { (_, anzahl) -> anzahl != 0 }

                    if (belegteBauteile.isEmpty()) {
                        Text(text = "Keine Bauteile",modifier = Modifier.span(2))
                    } else {
                        belegteBauteile.take(5).forEach { (bauteil, anzahl) ->
                            RightText(text = "${bauteil.str}: ")
                            LeftText(text = "$anzahl stk")
                        }

                        if (belegteBauteile.size > 5) {
                            Text(text = "+ ${belegteBauteile.size - 5} weitere", modifier = Modifier.span(2))
                        }
                    }
                }
            }
        }
    }
}



@Preview(
    widthDp = 1920/2,
    heightDp = 1080
)
@Composable
fun SpielerPreview() {
    val spiel = remember { TestSpiel }
    Column {
        zeigeSpieler(spiel,{},{spieler,bauteil,wahr -> },{},{},{})
    }
}
