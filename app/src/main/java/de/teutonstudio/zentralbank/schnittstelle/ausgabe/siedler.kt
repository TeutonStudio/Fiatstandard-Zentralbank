package de.teutonstudio.zentralbank.schnittstelle.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import de.teutonstudio.zentralbank.R

import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.SpielerAblaufArt
import de.teutonstudio.zentralbank.datenbank.SpielerAblaufEintrag
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.zuMark
import de.teutonstudio.zentralbank.schnittstelle.AblaufDialog
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.LeftText
import de.teutonstudio.zentralbank.schnittstelle.ModiPad10
import de.teutonstudio.zentralbank.schnittstelle.ModiPad15
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.RightText
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarben
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe
import de.teutonstudio.zentralbank.schnittstelle.markAchsenFormatter
import de.teutonstudio.zentralbank.schnittstelle.mitAblaufRundentrenner
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus
import de.teutonstudio.zentralbank.schnittstelle.rememberAblaufSpaltenbreite
import de.teutonstudio.zentralbank.schnittstelle.rememberLinienMitGepunkteterAktuellerRunde
import de.teutonstudio.zentralbank.schnittstelle.seriesMitGepunkteterAktuellerRunde
import java.util.Locale
import kotlin.math.abs

private val spielerKartenMindestbreite = 340.dp

@Composable
fun SpielerBilanz(
    spielerSaldo: List<Map<Spieler, Zahlungsmittel>>,
) {
    val spielerListe = spielerSaldo.first().keys.toList()
    Card(modifier = ModiPad5) {
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
                                seriesMitGepunkteterAktuellerRunde(
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
                                    rememberLinienMitGepunkteterAktuellerRunde(
                                        sichtbareSerien.map { (spieler, _) ->
                                            DiagrammLegendenEintrag(
                                                id = "spieler:${spieler.name}",
                                                bezeichnung = spieler.name,
                                                farbe = spielerFarben[spieler] ?: Color.Black,
                                            )
                                        }
                                    )
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
                columns = SimpleGridCells.Adaptive(spielerKartenMindestbreite),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                spiel.spielerListe.forEach { spieler ->
                    val farbe = siedlerFarben[spieler] ?: Color.Black
                    zeigeSpielerDaten(
                        siedlerName = spieler.name,
                        siedlerFarbe = farbe,
                        siedlerBauteile = spieler.erhalteBauSaldoZurRunde(),
                        ablauf = spiel.erhalteSpielerAblauf(spieler),
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
    ablauf: List<SpielerAblaufEintrag> = emptyList(),
    istBearbeitbar: Boolean = true,
    onManipulateData: (String, Bauteil, Boolean) -> Unit
) {
    val isPlayerExpanded = remember { mutableStateOf(false) }
    var zeigeAblaufDialog by remember { mutableStateOf(false) }

    val playerBauteilAmount = remember(siedlerBauteile) {
        mutableStateMapOf<Bauteil, Int>().apply {
            Bauteil.entries.forEach { bauteil ->
                this[bauteil] = siedlerBauteile[bauteil] ?: 0
            }
        }
    }

    Card(
        modifier = Modifier
            .padding(5.dp)
            .widthIn(min = spielerKartenMindestbreite)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = siedlerFarbe,
            contentColor = siedlerFarbe.lesbareSchriftfarbe(),
        ),
        onClick = {
            if (istBearbeitbar) {
                isPlayerExpanded.value = !isPlayerExpanded.value
            } else {
                zeigeAblaufDialog = true
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VerticalGrid(
                    columns = SimpleGridCells.Fixed(2),
                    modifier = ModiPad5,
                ) {
                    Text(
                        text = siedlerName,
                        fontSize = 20.sp,
                        modifier = ModiPad5.span(2),
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
                        val belegteBauteile = Bauteil.entries
                            .map { it to (playerBauteilAmount[it] ?: 0) }
                            .filter { (_, anzahl) -> anzahl != 0 }

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
    if (zeigeAblaufDialog) {
        AblaufDialog(
            titel = "Spielerablauf · $siedlerName",
            breitenAnteil = 0.78f,
            onDismiss = { zeigeAblaufDialog = false },
        ) {
            SpielerAblauf(ablauf)
        }
    }
}

@Composable
private fun SpielerAblauf(ablauf: List<SpielerAblaufEintrag>) {
    var eingeklappteRunden by remember(ablauf) { mutableStateOf(emptySet<Int>()) }
    var geschaeftspartnerFilter by remember(ablauf) { mutableStateOf<String?>(null) }
    var rohstoffFilter by remember(ablauf) { mutableStateOf<String?>(null) }
    var minRundeEingabe by remember(ablauf) {
        mutableStateOf("0")
    }
    var geschaeftspartnerMenueOffen by remember { mutableStateOf(false) }
    var rohstoffMenueOffen by remember { mutableStateOf(false) }
    val tabellenScrollState = rememberScrollState()
    val geschaeftspartnerOptionen = remember(ablauf) {
        ablauf.map { eintrag -> eintrag.geschaeftspartner }.distinct().sorted()
    }
    val rohstoffOptionen = remember(ablauf) {
        ablauf.map { eintrag -> eintrag.rohstoffOderVorgang }.distinct().sorted()
    }
    val minRunde = minRundeEingabe.toIntOrNull() ?: 0
    val nachSachfilter = ablauf.filter { eintrag ->
        (geschaeftspartnerFilter == null ||
            eintrag.geschaeftspartner == geschaeftspartnerFilter) &&
            (rohstoffFilter == null || eintrag.rohstoffOderVorgang == rohstoffFilter)
    }
    val gefilterterAblauf = nachSachfilter.filter { eintrag ->
        if (minRunde == 0) eintrag.runde >= 0 else eintrag.runde > minRunde
    }
    val maximaleRunde = nachSachfilter.maxOfOrNull { eintrag -> eintrag.runde }
    val minRundenSaldo = nachSachfilter
        .filter { eintrag -> eintrag.runde <= minRunde }
        .fold(Zahlungsmittel()) { summe, eintrag -> summe + eintrag.preis }
    val rundenGruppen = gefilterterAblauf
        .groupBy { eintrag -> eintrag.runde }
        .toMutableMap()
        .apply {
            if (minRunde > 0 && maximaleRunde != null && minRunde <= maximaleRunde) {
                putIfAbsent(minRunde, emptyList())
            }
        }
        .toSortedMap(reverseOrder())
    val spaltenbreiten = SpielerAblaufSpaltenbreiten(
        runde = maxOf(
            rememberAblaufSpaltenbreite(listOf("Runde"), 12.sp),
            rememberAblaufSpaltenbreite(
                gefilterterAblauf.map { eintrag -> eintrag.runde.toString() },
                13.sp,
            ),
        ),
        geschaeftspartner = maxOf(
            rememberAblaufSpaltenbreite(
                listOf("Geschäftspartner: ${geschaeftspartnerFilter ?: "Alle"} ▾"),
                12.sp,
            ),
            rememberAblaufSpaltenbreite(
                gefilterterAblauf.map { eintrag -> eintrag.geschaeftspartner } +
                    rundenGruppen.values.map { zeilen -> "${zeilen.size} Zeilen" } +
                    "kumulativ",
                13.sp,
            ),
        ),
        vorgang = maxOf(
            rememberAblaufSpaltenbreite(
                listOf("Handelsgüter: ${rohstoffFilter ?: "Alle"} ▾"),
                12.sp,
            ),
            rememberAblaufSpaltenbreite(
                gefilterterAblauf
                    .filter { eintrag -> eintrag.ablaufRohstoff() == null }
                    .map(SpielerAblaufEintrag::ablaufVorgangstext) +
                    listOf("eingeklappt", "Saldo zum Rundenende"),
                13.sp,
            ),
            rememberAblaufSpaltenbreite(
                gefilterterAblauf
                    .filter { eintrag -> eintrag.ablaufRohstoff() != null }
                    .map(SpielerAblaufEintrag::ablaufVorgangstext),
                13.sp,
                34.dp,
            ),
        ),
        preis = maxOf(
            rememberAblaufSpaltenbreite(listOf("Preis"), 12.sp),
            rememberAblaufSpaltenbreite(
                gefilterterAblauf.map { eintrag -> eintrag.preis.zuMark() } +
                    rundenGruppen.values.map { zeilen ->
                        "Saldo: ${zeilen.fold(Zahlungsmittel()) { summe, zeile -> summe + zeile.preis }.zuMark()}"
                    } + listOf(minRundenSaldo.zuMark(), "Saldo: ${minRundenSaldo.zuMark()}"),
                13.sp,
            ),
        ),
    )
    val tabellenbreite = spaltenbreiten.runde + spaltenbreiten.geschaeftspartner +
        spaltenbreiten.vorgang + spaltenbreiten.preis
    val sichtbareRunden = rundenGruppen.keys
    val alleRundenEingeklappt = sichtbareRunden.isNotEmpty() &&
        sichtbareRunden.all(eingeklappteRunden::contains)
    val beiAllenRundenKlick = {
        eingeklappteRunden = if (alleRundenEingeklappt) {
            emptySet()
        } else {
            sichtbareRunden.toSet()
        }
    }

    Column(
        modifier = Modifier
            .padding(5.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ablauf.isEmpty()) {
            Text(
                text = "Noch keine Abläufe",
                modifier = Modifier.padding(8.dp),
            )
        } else {
            OutlinedTextField(
                value = minRundeEingabe,
                onValueChange = { neu ->
                    if (neu.all(Char::isDigit)) minRundeEingabe = neu
                },
                label = { Text("minRunde") },
                supportingText = { Text("Kleinste dargestellte Runde") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .width(minOf(320.dp, tabellenbreite))
                    .padding(bottom = 5.dp),
            )
            Column(modifier = Modifier.horizontalScroll(tabellenScrollState)) {
                SpielerAblaufKopfzeile(
                    geschaeftspartnerFilter = geschaeftspartnerFilter,
                    geschaeftspartnerOptionen = geschaeftspartnerOptionen,
                    geschaeftspartnerMenueOffen = geschaeftspartnerMenueOffen,
                    rohstoffFilter = rohstoffFilter,
                    rohstoffOptionen = rohstoffOptionen,
                    rohstoffMenueOffen = rohstoffMenueOffen,
                    spaltenbreiten = spaltenbreiten,
                    beiRundenKlick = beiAllenRundenKlick,
                    onGeschaeftspartnerMenue = { geschaeftspartnerMenueOffen = it },
                    onGeschaeftspartnerFilter = {
                        geschaeftspartnerFilter = it
                        geschaeftspartnerMenueOffen = false
                    },
                    onRohstoffMenue = { rohstoffMenueOffen = it },
                    onRohstoffFilter = {
                        rohstoffFilter = it
                        rohstoffMenueOffen = false
                    },
                )
                rundenGruppen.entries.forEachIndexed { rundenIndex, (runde, zeilen) ->
                    val istEingeklappt = runde in eingeklappteRunden
                    val hatKumulativeZeile = minRunde > 0 && runde == minRunde
                    val beiRundenKlick = {
                        eingeklappteRunden = if (istEingeklappt) {
                            eingeklappteRunden - runde
                        } else {
                            eingeklappteRunden + runde
                        }
                    }
                    if (istEingeklappt) {
                        val saldo = if (minRunde > 0 && runde == minRunde) {
                            minRundenSaldo
                        } else {
                            zeilen.fold(Zahlungsmittel()) { summe, zeile -> summe + zeile.preis }
                        }
                        SpielerAblaufTabellenzeile(
                            runde = runde.toString(),
                            geschaeftspartner = "${zeilen.size} Zeilen",
                            rohstoffOderVorgang = "eingeklappt",
                            preis = "Saldo: ${saldo.zuMark()}",
                            hintergrund = MaterialTheme.colorScheme.surfaceVariant,
                            spaltenbreiten = spaltenbreiten,
                            beiRundenKlick = beiRundenKlick,
                            istKompakt = true,
                            obereRundentrennung = rundenIndex > 0,
                        )
                    } else {
                        if (hatKumulativeZeile) {
                            SpielerAblaufTabellenzeile(
                                runde = runde.toString(),
                                geschaeftspartner = "kumulativ",
                                rohstoffOderVorgang = "Saldo zum Rundenende",
                                preis = minRundenSaldo.zuMark(),
                                hintergrund = MaterialTheme.colorScheme.surfaceVariant,
                                spaltenbreiten = spaltenbreiten,
                                beiRundenKlick = beiRundenKlick,
                                istKompakt = true,
                                obereRundentrennung = rundenIndex > 0,
                            )
                        }
                        zeilen.forEachIndexed { zeilenIndex, eintrag ->
                            val rendite = eintrag.erwarteteAnleihenRenditeProzent
                            val rohstoff = eintrag.ablaufRohstoff()
                            SpielerAblaufTabellenzeile(
                                runde = eintrag.runde.toString(),
                                geschaeftspartner = eintrag.geschaeftspartner,
                                rohstoffOderVorgang = eintrag.ablaufVorgangstext(),
                                preis = eintrag.preis.zuMark(),
                                hintergrund = if (eintrag.preis < Zahlungsmittel()) {
                                    Color(0xFFE8CACA)
                                } else {
                                    Color(0xFFD2E2CE)
                                },
                                spaltenbreiten = spaltenbreiten,
                                rohstoff = rohstoff,
                                vorgangsfarbe = rohstoff?.farbe ?: rendite?.renditeFarbe(),
                                beiRundenKlick = beiRundenKlick,
                                obereRundentrennung = rundenIndex > 0 &&
                                    !hatKumulativeZeile && zeilenIndex == 0,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SpielerAblaufSpaltenbreiten(
    val runde: Dp,
    val geschaeftspartner: Dp,
    val vorgang: Dp,
    val preis: Dp,
)

@Composable
private fun SpielerAblaufTabellenzeile(
    runde: String,
    geschaeftspartner: String,
    rohstoffOderVorgang: String,
    preis: String,
    hintergrund: Color,
    spaltenbreiten: SpielerAblaufSpaltenbreiten,
    istKopfzeile: Boolean = false,
    beiRundenKlick: (() -> Unit)? = null,
    istKompakt: Boolean = false,
    rohstoff: Rohstoffe? = null,
    vorgangsfarbe: Color? = null,
    obereRundentrennung: Boolean = false,
) {
    CompositionLocalProvider(LocalContentColor provides hintergrund.lesbareSchriftfarbe()) {
        Row(
            modifier = Modifier
                .background(hintergrund)
                .mitAblaufRundentrenner(obereRundentrennung),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabellenZelle(
                text = runde,
                modifier = Modifier.width(spaltenbreiten.runde),
                istKopfzeile = istKopfzeile,
                beiKlick = beiRundenKlick,
                istKompakt = istKompakt,
            )
            TabellenZelle(
                text = geschaeftspartner,
                modifier = Modifier.width(spaltenbreiten.geschaeftspartner),
                istKopfzeile = istKopfzeile,
                istKompakt = istKompakt,
            )
            HandelsgutTabellenZelle(
                text = rohstoffOderVorgang,
                rohstoff = rohstoff,
                modifier = Modifier.width(spaltenbreiten.vorgang),
                istKompakt = istKompakt,
                textfarbe = vorgangsfarbe,
            )
            TabellenZelle(
                text = preis,
                modifier = Modifier.width(spaltenbreiten.preis),
                textAlign = TextAlign.End,
                istKopfzeile = istKopfzeile,
                istKompakt = istKompakt,
            )
        }
    }
}

@Composable
private fun HandelsgutTabellenZelle(
    text: String,
    rohstoff: Rohstoffe?,
    modifier: Modifier,
    istKompakt: Boolean,
    textfarbe: Color?,
) {
    Row(
        modifier = modifier
            .border(0.5.dp, Color(0xFF9A9A9A))
            .padding(
                horizontal = 4.dp,
                vertical = if (istKompakt) 1.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rohstoff != null) {
            Image(
                painter = painterResource(rohstoff.zuPfad()),
                contentDescription = rohstoff.str,
                modifier = Modifier
                    .size(if (istKompakt) 13.dp else 16.dp)
                    .padding(end = 3.dp),
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = if (istKompakt) 11.sp else 13.sp,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textfarbe ?: LocalContentColor.current,
        )
    }
}

@Composable
private fun SpielerAblaufKopfzeile(
    geschaeftspartnerFilter: String?,
    geschaeftspartnerOptionen: List<String>,
    geschaeftspartnerMenueOffen: Boolean,
    rohstoffFilter: String?,
    rohstoffOptionen: List<String>,
    rohstoffMenueOffen: Boolean,
    spaltenbreiten: SpielerAblaufSpaltenbreiten,
    beiRundenKlick: () -> Unit,
    onGeschaeftspartnerMenue: (Boolean) -> Unit,
    onGeschaeftspartnerFilter: (String?) -> Unit,
    onRohstoffMenue: (Boolean) -> Unit,
    onRohstoffFilter: (String?) -> Unit,
) {
    val hintergrund = MaterialTheme.colorScheme.surfaceVariant
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(
            modifier = Modifier.background(hintergrund),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabellenZelle(
                text = "Runde",
                modifier = Modifier.width(spaltenbreiten.runde),
                istKopfzeile = true,
                beiKlick = beiRundenKlick,
            )
            AblaufFilterZelle(
                label = "Geschäftspartner",
                auswahl = geschaeftspartnerFilter,
                optionen = geschaeftspartnerOptionen,
                menueOffen = geschaeftspartnerMenueOffen,
                onMenueAendern = onGeschaeftspartnerMenue,
                onAuswahl = onGeschaeftspartnerFilter,
                modifier = Modifier.width(spaltenbreiten.geschaeftspartner),
            )
            AblaufFilterZelle(
                label = "Handelsgüter",
                auswahl = rohstoffFilter,
                optionen = rohstoffOptionen,
                menueOffen = rohstoffMenueOffen,
                onMenueAendern = onRohstoffMenue,
                onAuswahl = onRohstoffFilter,
                modifier = Modifier.width(spaltenbreiten.vorgang),
            )
            TabellenZelle(
                text = "Preis",
                modifier = Modifier.width(spaltenbreiten.preis),
                textAlign = TextAlign.End,
                istKopfzeile = true,
            )
        }
    }
}

@Composable
private fun AblaufFilterZelle(
    label: String,
    auswahl: String?,
    optionen: List<String>,
    menueOffen: Boolean,
    onMenueAendern: (Boolean) -> Unit,
    onAuswahl: (String?) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        TabellenZelle(
            text = "$label: ${auswahl ?: "Alle"} ▾",
            modifier = Modifier.fillMaxWidth(),
            istKopfzeile = true,
            beiKlick = { onMenueAendern(true) },
        )
        DropdownMenu(
            expanded = menueOffen,
            onDismissRequest = { onMenueAendern(false) },
        ) {
            DropdownMenuItem(
                text = { Text("Alle") },
                onClick = { onAuswahl(null) },
            )
            optionen.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onAuswahl(option) },
                )
            }
        }
    }
}

@Composable
private fun TabellenZelle(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
    istKopfzeile: Boolean = false,
    beiKlick: (() -> Unit)? = null,
    istKompakt: Boolean = false,
    textfarbe: Color? = null,
) {
    val zellenModifier = if (beiKlick == null) modifier else modifier.clickable(onClick = beiKlick)
    Text(
        text = text,
        modifier = zellenModifier
            .border(0.5.dp, Color(0xFF9A9A9A))
            .padding(
                horizontal = 4.dp,
                vertical = when {
                    istKopfzeile -> 5.dp
                    istKompakt -> 1.dp
                    else -> 4.dp
                },
            ),
        fontSize = when {
            istKopfzeile -> 12.sp
            istKompakt -> 11.sp
            else -> 13.sp
        },
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = textfarbe ?: LocalContentColor.current,
    )
}

private fun Float.alsRendite(): String {
    val normalisiert = if (abs(this) < 0.05f) 0f else this
    return if (normalisiert == 0f) {
        "0,0 %"
    } else {
        String.format(Locale.GERMANY, "%+.1f %%", normalisiert)
    }
}

private fun SpielerAblaufEintrag.ablaufVorgangstext(): String {
    anleihenAnzeigeZusatz?.let { zusatz ->
        return "$rohstoffOderVorgang ($zusatz)"
    }
    val rendite = erwarteteAnleihenRenditeProzent
    return if (rendite != null) {
        "$rohstoffOderVorgang (${rendite.alsRendite()})"
    } else {
        anzahl?.let { menge -> "$rohstoffOderVorgang ($menge Stk)" }
            ?: rohstoffOderVorgang
    }
}

private fun SpielerAblaufEintrag.ablaufRohstoff(): Rohstoffe? =
    if (art == SpielerAblaufArt.ROHSTOFFHANDEL) {
        Rohstoffe.entries.firstOrNull { rohstoff -> rohstoff.str == rohstoffOderVorgang }
    } else {
        null
    }

private fun Float.renditeFarbe(): Color? = when {
    this >= 0.05f -> Color(0xFF2E7D32)
    this <= -0.05f -> Color(0xFFB3261E)
    else -> null
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
