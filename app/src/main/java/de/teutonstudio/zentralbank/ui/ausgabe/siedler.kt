package de.teutonstudio.zentralbank.ui.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import com.patrykandpatrick.vico.compose.common.Fill
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Rohstoffe

import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.SpielerDaten
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.ui.LeftText
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.RightText


private fun erhalteSiedlerFarben(spielerListe: List<Spieler>): Map<Spieler,Color> {
    return spielerListe.associateWith {
        if (spielerListe.indexOf(it) == 0) {
            Color.White
        } else if (spielerListe.indexOf(it) == 1) {
            Color.Blue
        } else if (spielerListe.indexOf(it) == 2) {
            Color.Red
        } else if (spielerListe.indexOf(it) == 3) {
            Color.Green
        } else if (spielerListe.indexOf(it) == 4) {
            Color.Cyan
        } else if (spielerListe.indexOf(it) == 5) {
            Color.Yellow
        } else if (spielerListe.indexOf(it) == 6) {
            Color.Magenta
        } else {
            Color.Black
        }
    }
}

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
            val spielerFarben = erhalteSiedlerFarben(spielerListe)

            val scrollState = rememberVicoScrollState()
            val zoomState = rememberVicoZoomState(initialZoom = remember { Zoom.Content })

            val endAxis = VerticalAxis.rememberEnd()
            val bottomAxis = HorizontalAxis.rememberBottom()

            val serien = remember(spielerListe, spielerSaldo) {
                spielerListe.mapNotNull { spieler ->
                    val yWerte: List<Number> = spielerSaldo.map { saldoProRunde ->
                        saldoProRunde[spieler]?.toIntOderNull() ?: 0
                    }

                    if (yWerte.isEmpty()) null else spieler to yWerte
                }
            }

            val chartModel = remember(serien) {
                if (serien.isEmpty()) {
                    null
                } else {
                    CartesianChartModel(
                        LineCartesianLayerModel.build {
                            serien.forEach { (_, yWerte) ->
                                series(
                                    x = yWerte.indices.toList(),
                                    y = yWerte
                                )
                            }
                        }
                    )
                }
            }

            val legendIcon = ShapeComponent(margins = Insets(0.5.dp))
            val legendText = TextComponent(
                padding = Insets(
                    start = 5.dp,
                    top = 0.dp,
                    end = 10.dp,
                    bottom = 0.dp,
                )
            )

            Column(modifier = ModiPad5) {
                if (chartModel == null) {
                    Text(
                        text = "Noch keine Bilanzdaten vorhanden",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    CartesianChartHost(
                        modifier = ModiPad5,
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    spielerListe.map {
                                        val farbe = spielerFarben[it] ?: Color.Black

                                        LineCartesianLayer.rememberLine(
                                            fill = remember(farbe) {
                                                LineCartesianLayer.LineFill.single(
                                                    Fill(farbe)
                                                )
                                            }
                                        )
                                    }
                                )
                            ),
                            endAxis = endAxis,
                            bottomAxis = bottomAxis,
                            legend = rememberHorizontalLegend(
                                items = {
                                    spielerListe.map {
                                        val farbe = spielerFarben[it] ?: Color.Black

                                        add( LegendItem(
                                            icon = legendIcon.copy(Fill(farbe)),
                                            labelComponent = legendText,
                                            label = it.name,
                                        ) )
                                    }
                                },
                                iconSize = 8.dp,
                                padding = Insets(8.dp),
                            )
                        ),
                        model = chartModel,
                        scrollState = scrollState,
                        zoomState = zoomState
                    )
                }

                LazyRow(modifier = ModiPad5) {
                    spielerListe.forEach { item {
                        val farbe = spielerFarben[it] ?: Color.Black
                        zeigeSpielerDaten(
                            siedlerName = it.name,
                            siedlerFarbe = farbe,
                            siedlerBauteile = it.erhalteBauSaldoZurRunde(),
                            istBearbeitbar = false,
                            onManipulateData = { _, _, _ -> }
                        )
                    } }
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
    val siedlerFarben = erhalteSiedlerFarben(spiel.spielerListe)
    val marktpreise = spiel.marktpreise

    var isWarExpanded by remember { mutableStateOf(false) }
    var isMilitaryExpanded by remember { mutableStateOf(false) }
    var isPeaceExpanded by remember { mutableStateOf(false) }

    if (!isWarExpanded && !isMilitaryExpanded && !isPeaceExpanded) {
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally) {
            val hAnzahl = 7
            item { SpielerBilanz(spiel.spielerSaldo) }
            item { VerticalGrid(
                columns = SimpleGridCells.Adaptive(hAnzahl*45.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                Bauteil.entries.forEach { zeigeBauteilPreis(
                    it, hAnzahl, ModiPad5,
                    spiel.aktuelleMarktpreise, false,
                    false,onBuild
                ) }
            } }
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
        modifier = Modifier.padding(5.dp),
        onClick = { isPlayerExpanded.value = !isPlayerExpanded.value }
    ) {
        Box(
            modifier = Modifier
                .background(siedlerFarbe)
                .offset(20.dp, 0.dp)
        ) {
            VerticalGrid(
                columns = SimpleGridCells.Fixed(2),
                modifier = Modifier
                    .padding(5.dp)
                    .width(280.dp)
            ) {
                Text(
                    text = siedlerName,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .span(2)
                        .padding(5.dp)
                        .offset((-25).dp, 0.dp),
                    textAlign = TextAlign.Center
                )

                if (isPlayerExpanded.value && istBearbeitbar) {
                    Bauteil.entries.forEach { bauteil ->
                        RightText(text = "$bauteil: ")

                        Row {
                            val amount = playerBauteilAmount[bauteil] ?: 0

                            LeftText(
                                text = "$amount stk",
                                modifier = Modifier.padding(5.dp, 0.dp)
                            )

                            Image(
                                painter = painterResource(id = R.drawable.plus),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(5.dp, 0.dp)
                                    .clickable {
                                        playerBauteilAmount[bauteil] = amount + 1
                                        onManipulateData(siedlerName, bauteil, true)
                                    }
                            )

                            Image(
                                painter = painterResource(id = R.drawable.minus),
                                contentDescription = null,
                                alpha = 0.5f,
                                modifier = Modifier
                                    .padding(5.dp, 0.dp)
                                    .clickable {
                                        playerBauteilAmount[bauteil] = amount - 1
                                        onManipulateData(siedlerName, bauteil, false)
                                    }
                            )
                        }
                    }
                } else {
                    val belegteBauteile = Bauteil.entries
                        .map { it to (playerBauteilAmount[it] ?: 0) }
                        .filter { (_, anzahl) -> anzahl != 0 }

                    if (belegteBauteile.isEmpty()) {
                        Text(
                            text = "Keine Bauteile",
                            modifier = Modifier.span(2)
                        )
                    } else {
                        belegteBauteile.take(5).forEach { (bauteil, anzahl) ->
                            RightText(text = "$bauteil: ")
                            LeftText(text = "$anzahl stk")
                        }

                        if (belegteBauteile.size > 5) {
                            Text(
                                text = "+ ${belegteBauteile.size - 5} weitere",
                                modifier = Modifier.span(2)
                            )
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
