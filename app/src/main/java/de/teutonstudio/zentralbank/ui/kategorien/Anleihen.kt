package com.example.czboracle.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
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
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.AnleiheAnzeige
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Runde
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.summeGeld
import de.teutonstudio.zentralbank.datenbank.toY
import de.teutonstudio.zentralbank.datenbank.zuMark
import de.teutonstudio.zentralbank.datenbank.zuZinssatz
import de.teutonstudio.zentralbank.ui.LeftText
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.RightText
import de.teutonstudio.zentralbank.ui.erhalteSpielerFarben
import kotlin.math.abs

private const val GLOBAL_PLAYER = "Global"

private val relevanceStrings = listOf(
    "ausgelaufen",
    "fällige",
    "unfällige",
    "fällige & unfällige",
    "alle",
)

private val durationStrings = listOf(
    "kurzfristig",
    "mittelfristig",
    "langfristig",
    "alle",
)

fun List<Spieler>.bauSaldoZurRunde(runde: Int): Map<String, Map<out Bauteil, Int>> {
    return associate { spieler ->
        spieler.name to spieler.erhalteBauSaldoZurRunde(runde)
    }
}

private fun nonZeroPoints(values: List<Int>): Pair<List<Int>, List<Int>> {
    val points = values.mapIndexedNotNull { index, value ->
        if (value == 0) null else index + 1 to value
    }

    return points.map { it.first } to points.map { it.second }
}

private fun Color.abgedunkelt(anteil: Float): Color = Color(
    red = red * (1f - anteil),
    green = green * (1f - anteil),
    blue = blue * (1f - anteil),
    alpha = alpha,
)

fun fiatWoodoo(
    liste: List<Int>,
    saldodebt: List<Int>,
    maxG: Int,
): List<Int> {
    if (liste.isEmpty() || saldodebt.isEmpty()) return emptyList()

    val reversed = saldodebt.reversed()

    val sd = List(saldodebt.size) { idx ->
        when {
            idx + 2 < reversed.size ->
                abs((3 * reversed[idx] + 2 * reversed[idx + 1] + reversed[idx + 2]) / 6f).toInt()

            idx + 1 < reversed.size ->
                abs((2 * reversed[idx] + reversed[idx + 1]) / 3f).toInt()

            else ->
                abs(reversed[idx])
        }.coerceAtLeast(1)
    }.reversed()

    return liste.mapIndexed { idx, y ->
        val sdx = sd.getOrNull(idx)?.coerceAtLeast(1) ?: 1
        val max = abs(maxG.toFloat() / sdx.toFloat()).coerceAtLeast(1f)
        val rel = abs(y.toFloat() / sdx.toFloat())

        if (rel > 0.9f && max > 1f) {
            ((rel - 1f) / (3f * max - 3f) + 2f / 3f).toInt()
        } else {
            (rel * 2f / 3f).toInt()
        }
    }
}

@Composable
fun Header(
    modifierChoseLegende: Modifier = Modifier.scale(.75f),
    modifierChoseLabel: Modifier = Modifier.scale(1.1f),
    spiel: Spiel,
    spielerBauSaldo: Map<String, Map<Bauteil, Int>>,
    ausgewählterSpieler: String,
    selectedRound: Int?,
    selectedRelevance: String,
    selectedDuration: String,
    showManagementView: Boolean,
    onChosePlayer: (String) -> Unit,
    onChoseRound: (Int) -> Unit,
    onChoseRelevance: (String) -> Unit,
    onChoseDuration: (String) -> Unit,
    onChangeView: () -> Unit,
    content: @Composable () -> Unit,
) {
    var isBilanzExpanded by remember { mutableStateOf(true) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = ModiPad5.clickable {
                isBilanzExpanded = !isBilanzExpanded
            }
        ) {
            if (isBilanzExpanded) {
                if (showManagementView) {
                    ManagementChart(
                        selectedPlayer = ausgewählterSpieler,
                        selectedRound = selectedRound,
                        aktuelleRunde = spiel.aktuelleRunde,
                        spielerBauSaldo = spielerBauSaldo,
                        anleihen = spiel.anleihen,
                    )
                } else {
                    BalanceChart(
                        spiel = spiel,
                        ausgewählterSpieler = ausgewählterSpieler,
                        anleihen = spiel.anleihen,
                    )
                }
            } else {
                Card(modifier = ModiPad5) {
                    Text(
                        text = "Anleihenbilanz",
                        fontSize = 40.sp,
                        modifier = ModiPad5,
                    )
                }
            }
        }

        HeaderControls(
            spiel = spiel,
            modifierChoseLegende = modifierChoseLegende,
            modifierChoseLabel = modifierChoseLabel,
            selectedPlayer = ausgewählterSpieler,
            selectedRound = selectedRound,
            selectedRelevance = selectedRelevance,
            selectedDuration = selectedDuration,
            showManagementView = showManagementView,
            onChosePlayer = onChosePlayer,
            onChoseRound = onChoseRound,
            onChoseRelevance = onChoseRelevance,
            onChoseDuration = onChoseDuration,
            onChangeView = onChangeView,
        )

        content()
    }
}

@Composable
private fun BalanceChart(
    spiel: Spiel,
    ausgewählterSpieler: String,
    anleihen: Iterable<AnleiheAnzeige>,
) {
    if (spiel.spielerSaldo.isEmpty() || spiel.spielerMarktwert.isEmpty() || spiel.spielerSchulden.isEmpty()) {
        EmptyInfoCard("Keine Bilanzdaten vorhanden.")
        return
    }

    // TODO durch Spiel irrelevant geworden
    val sameLength = spiel.spielerSaldo.size == spiel.spielerMarktwert.size && spiel.spielerSaldo.size == spiel.spielerSchulden.size

    if (!sameLength) {
        EmptyInfoCard("Bilanzdaten haben unterschiedliche Längen.")
        return
    }

    if (ausgewählterSpieler == GLOBAL_PLAYER) {
        GlobalBalanceChart(
            spielerSchulden = spiel.spielerSchulden,
            anleihen = anleihen,
        )
        return
    }

    val runden = List(spiel.aktuelleRunde) { it }
    val modelProducer = remember { CartesianChartModelProducer() }
    val spieler = spiel.spielerListe.firstOrNull { it.name == ausgewählterSpieler }
    val spielerFarbe = erhalteSpielerFarben(spiel.spielerListe)[spieler] ?: Color.LightGray
    val serien = listOf(
        "Barvermögen" to spielerFarbe,
        "Marktwert" to spielerFarbe.abgedunkelt(0.2f),
        "Verbindlichkeiten" to spielerFarbe.abgedunkelt(0.45f),
    )
    val legendIcon = ShapeComponent(margins = Insets(0.5.dp))
    val legendText = TextComponent(
        padding = Insets(
            start = 5.dp,
            top = 0.dp,
            end = 10.dp,
            bottom = 0.dp,
        )
    )

    LaunchedEffect(runden, spiel) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = runden, y = spiel.spielerSaldo.toY(ausgewählterSpieler))
                series(x = runden, y = spiel.spielerMarktwert.toY(ausgewählterSpieler))
                series(x = runden, y = spiel.spielerSchulden.toY(ausgewählterSpieler))
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            CartesianChartHost(
                modifier = ModiPad5,
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            serien.map { (_, farbe) ->
                                LineCartesianLayer.rememberLine(
                                    fill = remember(farbe) {
                                        LineCartesianLayer.LineFill.single(Fill(farbe))
                                    }
                                )
                            }
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                    legend = rememberHorizontalLegend(
                        items = {
                            serien.forEach { (label, farbe) ->
                                add(
                                    LegendItem(
                                        icon = legendIcon.copy(Fill(farbe)),
                                        labelComponent = legendText,
                                        label = label,
                                    )
                                )
                            }
                        },
                        iconSize = 8.dp,
                        padding = Insets(8.dp),
                    ),
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(),
                zoomState = rememberVicoZoomState(
                    initialZoom = Zoom.Content,
                ),
            )
        }
    }
}

@Composable
private fun GlobalBalanceChart(
    spielerSchulden: List<Map<Spieler, Zahlungsmittel>>,
    anleihen: Iterable<AnleiheAnzeige>,
) {
    val globalDebt = spielerSchulden.map { map ->
        map.values.summeGeld { it }.toIntOderNull() ?: 0
    }

    val maxRoundCount = globalDebt.size
    val globalUnvermoegen = List(maxRoundCount) { idx ->
        val runde = idx + 1
        anleihen
            .filter { it.emittiert <= runde && it.faelligkeit >= runde }
            .summeGeld { it.unvermoegen }
            .toIntOderNull() ?: 0
    }

    val x = List(globalDebt.size) { it + 1 }

    if (globalDebt.isEmpty() && globalUnvermoegen.isEmpty()) {
        EmptyInfoCard("Keine globalen Bilanzwerte vorhanden.")
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(globalDebt, globalUnvermoegen) {
        modelProducer.runTransaction {
            lineSeries {
                if (globalDebt.isNotEmpty()) {
                    series(x = x, y = globalDebt)
                }
                if (globalUnvermoegen.isNotEmpty()) {
                    series(x = x, y = globalUnvermoegen)
                }
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            CartesianChartHost(
                modifier = ModiPad5,
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(),
                zoomState = rememberVicoZoomState(
                    initialZoom = Zoom.Content,
                ),
            )

            SimpleLegend(
                labels = listOf(
                    "globales Unvermögen",
                    "offenes Anleihe-Unvermögen",
                )
            )
        }
    }
}

@Composable
private fun ManagementChart(
    selectedPlayer: String,
    selectedRound: Int?,
    aktuelleRunde: Int,
    spielerBauSaldo: Map<String, Map<Bauteil, Int>>,
    anleihen: Iterable<AnleiheAnzeige>,
//    runden: List<Runde>,
) {
    val currentRound = selectedRound ?: aktuelleRunde

    if (selectedPlayer == GLOBAL_PLAYER) {
        EmptyInfoCard("Für die Verwaltungsansicht bitte einen Spieler wählen.")
        return
    }

    val bauSaldo = spielerBauSaldo[selectedPlayer].orEmpty()

    val aktiveAnleihen = anleihen.filter {
        it.schuldiger.name == selectedPlayer && it.faelligkeit >= currentRound
    }

    val anleihenSortiert = listOf(
        aktiveAnleihen.count { it.laufzeit in 1..2 },
        aktiveAnleihen.count { it.laufzeit in 3..6 },
        aktiveAnleihen.count { it.laufzeit >= 7 },
    )

    val verwaltungsLimit = listOf(
        bauSaldo.sumOf(Verwaltungsstandort.BAHNHOF, Verwaltungsstandort.HAFEN),
        bauSaldo.sumOf(Verwaltungsstandort.GROSSBAHNHOF, Verwaltungsstandort.GROSSHAFEN),
        bauSaldo[Wirtschaftsregionen.GESCHÄFTSBANK] ?: 0,
    )

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(anleihenSortiert, verwaltungsLimit) {
        modelProducer.runTransaction {
            columnSeries {
                series(anleihenSortiert)
                series(verwaltungsLimit)
            }
        }
    }

    val labels = listOf(
        "kurzfristig",
        "mittelfristig",
        "langfristig",
    )

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            CartesianChartHost(
                modifier = ModiPad5,
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            labels.getOrNull(value.toInt()) ?: ""
                        }
                    ),
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(),
                zoomState = rememberVicoZoomState(
                    initialZoom = Zoom.Content,
                ),
            )

            SimpleLegend(
                labels = listOf(
                    "Anleihen",
                    "Standorte / Banken",
                )
            )
        }
    }
}

private fun Map<Bauteil, Int>.sumOf(vararg bauteile: Bauteil): Int {
    return bauteile.sumOf { this[it] ?: 0 }
}

@Composable
private fun HeaderControls(
    spiel: Spiel,
    modifierChoseLegende: Modifier,
    modifierChoseLabel: Modifier,
    selectedPlayer: String,
    selectedRound: Int?,
    selectedRelevance: String,
    selectedDuration: String,
    showManagementView: Boolean,
    onChosePlayer: (String) -> Unit,
    onChoseRound: (Int) -> Unit,
    onChoseRelevance: (String) -> Unit,
    onChoseDuration: (String) -> Unit,
    onChangeView: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        HeaderDropdownCard(
            title = "Siedler:",
            value = selectedPlayer,
            liste = spiel.spielerStringListe + GLOBAL_PLAYER,
            modifierChoseLegende = modifierChoseLegende,
            modifierChoseLabel = modifierChoseLabel,
            beiAuswahl = onChosePlayer,
        )
        HeaderDropdownCard(
            title = "Dauer:",
            value = selectedDuration,
            liste = durationStrings,
            modifierChoseLegende = modifierChoseLegende,
            modifierChoseLabel = modifierChoseLabel,
            beiAuswahl = onChoseDuration,
        )

        if (showManagementView) {
            HeaderDropdownCard(
                title = "Leitzins:",
                value = selectedRound?.let { "${spiel.leitzinssatz(selectedRound)} %" } ?: "",
                liste = List(spiel.aktuelleRunde) { it.toString() }.reversed(),
                modifierChoseLegende = modifierChoseLegende,
                modifierChoseLabel = modifierChoseLabel,
                beiAuswahl = { onChoseRound(it.toInt()) }
            )
        } else {
            HeaderDropdownCard(
                title = "Relevanz:",
                value = selectedRelevance,
                liste = relevanceStrings,
                modifierChoseLegende = modifierChoseLegende,
                modifierChoseLabel = modifierChoseLabel,
                centerValue = true,
                beiAuswahl = onChoseRelevance,
            )
        }

        Card(
            modifier = ModiPad5,
            onClick = onChangeView,
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Text(
                    text = "Ansicht:",
                    modifier = modifierChoseLegende,
                )
                Text(
                    text = if (showManagementView) "Verwaltung" else "Kreditwürdigkeit",
                    fontSize = 25.sp,
                    modifier = modifierChoseLabel,
                )
            }
        }
    }
}


@Composable
private fun HeaderDropdownCard(
    title: String, value: String,
    liste: Iterable<String>,
    modifierChoseLegende: Modifier,
    modifierChoseLabel: Modifier,
    centerValue: Boolean = false,
    beiAuswahl: (String) -> Unit,
) {
    var ausgeklappt by remember { mutableStateOf(false) }
    Card(modifier = ModiPad5, onClick = { ausgeklappt = !ausgeklappt }) {
        Column(modifier = ModiPad15) {
            Text(text = title, modifier = modifierChoseLegende)
            Text(
                text = value,
                fontSize = 25.sp,
                modifier = if (centerValue) {
                    modifierChoseLabel.align(Alignment.CenterHorizontally)
                } else {
                    modifierChoseLabel
                },
            )
        }
        DropdownMenu(expanded = ausgeklappt, onDismissRequest = { ausgeklappt = false }) {
            liste.forEach { DropdownMenuItem(text = { Text(text = it) }, onClick = {
                beiAuswahl(it)
                ausgeklappt = false
            }) }
        }
    }
}

@Composable
private fun SimpleLegend(labels: List<String>) {
    Row(
        modifier = ModiPad5,
        horizontalArrangement = Arrangement.Center,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun EmptyInfoCard(text: String) {
    Card(modifier = ModiPad5) {
        Text(
            text = text,
            modifier = ModiPad5,
        )
    }
}

@Composable
fun AnleihenRegister(
    spiel: Spiel,
    spielerBauSaldo: Map<String, Map<Bauteil, Int>>,
    runden: List<Runde>,
    onDelete: (AnleiheAnzeige) -> Unit = {},
    onNew: (Anleihe) -> Unit = {},
) {
    var eingabeSpieler by remember(spiel.spielerStringListe) { mutableStateOf(GLOBAL_PLAYER) }
    var eingabeRunde by remember { mutableIntStateOf(0) }
    var eingabeRelevanz by remember { mutableStateOf("alle") }
    var eingabeLaufzeit by remember { mutableStateOf("alle") }
    var zeigeVerwaltung by remember { mutableStateOf(false) }

    val anleihen = spiel.anleihen

    Column {
        Header(
            spiel = spiel,
            spielerBauSaldo = spielerBauSaldo,
            ausgewählterSpieler = eingabeSpieler,
            selectedRound = eingabeRunde,
            selectedRelevance = eingabeRelevanz,
            selectedDuration = eingabeLaufzeit,
            showManagementView = zeigeVerwaltung,
            onChosePlayer = { eingabeSpieler = it },
            onChoseRound = { eingabeRunde = it },
            onChoseRelevance = { eingabeRelevanz = it },
            onChoseDuration = { eingabeLaufzeit = it },
            onChangeView = { zeigeVerwaltung = !zeigeVerwaltung },
        ) {
            if (zeigeVerwaltung) {
                Card(
                    modifier = ModiPad5.clickable {
                        // Platzhalter: Erstellung gehört wahrscheinlich in einen Dialog.
                        // Der Callback bleibt drin, damit die UI nicht wieder heimlich Daten erzeugt.
                    }
                ) {
                    Text(
                        text = "Anleihen erstellen/bearbeiten kommt hier hin.",
                        modifier = ModiPad5,
                    )
                }
            } else {
                val currentRound = eingabeRunde ?: runden.lastOrNull()?.index
                val filteredDebt = anleihen
                    .filter { anleihe ->
                        val round = currentRound ?: return@filter true
                        when (eingabeRelevanz) {
                            "ausgelaufen" -> anleihe.faelligkeit < round
                            "fällige" -> anleihe.faelligkeit == round
                            "unfällige" -> anleihe.faelligkeit > round
                            "fällige & unfällige" -> anleihe.faelligkeit >= round
                            "alle" -> true
                            else -> true
                        }
                    }
                    .filter { anleihe ->
                        when (eingabeLaufzeit) {
                            "kurzfristig" -> anleihe.laufzeit <= 2
                            "mittelfristig" -> anleihe.laufzeit in 3..6
                            "langfristig" -> anleihe.laufzeit >= 7
                            "alle" -> true
                            else -> true
                        }
                    }
                    .filter { anleihe ->
                        eingabeSpieler == GLOBAL_PLAYER || anleihe.schuldiger.name == eingabeSpieler
                    }

                if (filteredDebt.isEmpty()) {
                    EmptyInfoCard("Keine Anleihen für diese Auswahl.")
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(200.dp)) {
                        items(filteredDebt) { eintrag -> AnleiheCard(
                            aktuelleRunde = currentRound ?: 0,
                            eintrag = eintrag,
                            onDelete = onDelete,
                        ) }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalGridApi::class)
@Composable
private fun AnleiheCard(
    aktuelleRunde: Int,
    eintrag: AnleiheAnzeige,
    onDelete: (AnleiheAnzeige) -> Unit,
) {
    val restlaufzeit = eintrag.faelligkeit - aktuelleRunde
    val status = when {
        restlaufzeit < 0 -> "ausgelaufen"
        restlaufzeit == 0 -> "fällig"
        else -> "offen"
    }

    Card(modifier = ModiPad5,) {
        Grid({
            repeat(2) { column(100.dp) }
            repeat(3) { row(20.dp) }
            rowGap(5.dp)
            columnGap(20.dp)
        },ModiPad15) {
            RightText(text = "Schuldiger:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = eintrag.schuldiger.name, fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            RightText(text = "Besitzer:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = eintrag.aktuellerBesitzer.name, fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            RightText(text = "Sondervermögen:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = eintrag.sondervermoegen.zuMark(), fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            RightText(text = "Unvermögen:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = eintrag.unvermoegen.zuMark(), fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            RightText(text = "Zinssatz:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = eintrag.anleihe.erhalteZinssatz().zuZinssatz(), fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            RightText(text = "Fällig zu:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = eintrag.faelligkeit.toString(), fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            RightText(text = "Status:", fontSize = 12.sp,modifier = Modifier.fillMaxWidth())
            LeftText(text = status,fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
            Text(text = "löschen",fontSize = 12.sp,modifier = Modifier.gridItem(columnSpan = 2).fillMaxWidth().clickable { onDelete(eintrag) }, textAlign = TextAlign.Center)

        }
    }
}

@Preview(
    widthDp = 1920/2,
    heightDp = 1080
)
@Composable
private fun AnleihenRegisterPreview() {
    val spiel = remember { TestSpiel }
    Column() {
        AnleihenRegister(spiel,emptyMap(),emptyList(),{},{})
    }
}
