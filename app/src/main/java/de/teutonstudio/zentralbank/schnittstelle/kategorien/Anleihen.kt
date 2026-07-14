package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.AnleiheAblaufArt
import de.teutonstudio.zentralbank.datenbank.AnleiheAnzeige
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Runde
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.toY
import de.teutonstudio.zentralbank.datenbank.zuMark
import de.teutonstudio.zentralbank.datenbank.zuZinssatz
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.LeftText
import de.teutonstudio.zentralbank.schnittstelle.ModiPad15
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.RightText
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarben
import de.teutonstudio.zentralbank.schnittstelle.markAchsenFormatter
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus
import de.teutonstudio.zentralbank.schnittstelle.stueckAchsenFormatter
import kotlin.math.abs

private const val GLOBAL_PLAYER = "Global"
private val anleiheFaelligFarbe = Color(0xFFD8C28F)
private val anleiheOffenFarbe = Color(0xFF9EB5C7)
private val anleiheAbgelaufenFarbe = Color(0xFFC7C7C7)
private val anleiheHandelOhneSpielerFarbe = Color(0xFFE1E1E1)

private val relevanceStrings = listOf(
    "gezahlte",
    "fällige",
    "offene",
    "fällige & offene",
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
) {
    val reihenLängen = listOf(
        spiel.spielerSaldo.size,
        spiel.spielerMarktwert.size,
        spiel.spielerSchulden.size,
        spiel.spielerZinsschulden.size,
        spiel.spielerKombinierteSchulden.size,
    )
    if (reihenLängen.any { länge -> länge == 0 }) {
        EmptyInfoCard("Keine Bilanzdaten vorhanden.")
        return
    }

    if (reihenLängen.distinct().size != 1) {
        EmptyInfoCard("Bilanzdaten haben unterschiedliche Längen.")
        return
    }

    if (ausgewählterSpieler == GLOBAL_PLAYER) {
        GlobalBalanceChart(spiel)
        return
    }

    val runden = List(spiel.aktuelleRunde) { it }
    val modelProducer = remember { CartesianChartModelProducer() }
    val spieler = spiel.spielerListe.firstOrNull { it.name == ausgewählterSpieler }
    val spielerFarbe = erhalteSpielerFarben(spiel.spielerListe)[spieler] ?: Color.LightGray
    val legende = listOf(
        DiagrammLegendenEintrag(
            id = "bilanz-barvermoegen",
            bezeichnung = "Barvermögen",
            farbe = spielerFarbe,
        ),
        DiagrammLegendenEintrag(
            id = "bilanz-marktwert",
            bezeichnung = "Marktwert",
            farbe = spielerFarbe.abgedunkelt(0.2f),
        ),
        DiagrammLegendenEintrag(
            id = "bilanz-schulden",
            bezeichnung = "Schulden",
            farbe = spielerFarbe.abgedunkelt(0.45f),
        ),
        DiagrammLegendenEintrag(
            id = "bilanz-zinsschulden",
            bezeichnung = "Zinsschulden",
            farbe = Color(0xFF8A7552),
        ),
        DiagrammLegendenEintrag(
            id = "bilanz-kombinierte-schulden",
            bezeichnung = "Kombinierte Schulden",
            farbe = Color(0xFF8A5D65),
        ),
    )
    val reihen = listOf(
        legende[0] to spiel.spielerSaldo.toY(ausgewählterSpieler),
        legende[1] to spiel.spielerMarktwert.toY(ausgewählterSpieler),
        legende[2] to spiel.spielerSchulden.toY(ausgewählterSpieler),
        legende[3] to spiel.spielerZinsschulden.toY(ausgewählterSpieler),
        legende[4] to spiel.spielerKombinierteSchulden.toY(ausgewählterSpieler),
    )
    val legendenStatus = rememberDiagrammLegendenStatus(legende)
    val sichtbareReihen = reihen.filter { (eintrag, _) ->
        legendenStatus.istSichtbar(eintrag.id)
    }

    LaunchedEffect(runden, sichtbareReihen) {
        if (sichtbareReihen.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    sichtbareReihen.forEach { (_, werte) ->
                        series(x = runden, y = werte)
                    }
                }
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            if (sichtbareReihen.isEmpty()) {
                Text(
                    text = "Keine Datenreihe ausgewählt",
                    modifier = ModiPad5,
                )
            } else {
                CartesianChartHost(
                    modifier = ModiPad5,
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                sichtbareReihen.map { (eintrag, _) ->
                                    val farbe = eintrag.farbe

                                    LineCartesianLayer.rememberLine(
                                        fill = remember(farbe) {
                                            LineCartesianLayer.LineFill.single(Fill(farbe))
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
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = markAchsenFormatter,
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(),
                    ),
                    modelProducer = modelProducer,
                    scrollState = rememberVicoScrollState(),
                    zoomState = rememberVicoZoomState(
                        initialZoom = Zoom.Content,
                    ),
                )
            }

            UmschaltbareDiagrammLegende(
                eintraege = legende,
                status = legendenStatus,
            )
        }
    }
}

@Composable
private fun GlobalBalanceChart(
    spiel: Spiel,
) {
    val globalesBarvermögen = spiel.globalesBarvermögen.map { it.toIntOderNull() ?: 0 }
    val globaleSchulden = spiel.globaleSchulden.map { it.toIntOderNull() ?: 0 }
    val globaleZinsschulden = spiel.globaleZinsschulden.map { it.toIntOderNull() ?: 0 }
    val globaleKombinierteSchulden = spiel.globaleKombinierteSchulden.map {
        it.toIntOderNull() ?: 0
    }
    val x = globalesBarvermögen.indices.toList()

    if (x.isEmpty()) {
        EmptyInfoCard("Keine globalen Bilanzwerte vorhanden.")
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val legende = listOf(
        DiagrammLegendenEintrag(
            id = "global-barvermoegen",
            bezeichnung = "Globales Barvermögen (M2)",
            farbe = Color(0xFF607D8B),
        ),
        DiagrammLegendenEintrag(
            id = "global-schulden",
            bezeichnung = "Globale Schulden",
            farbe = Color(0xFF9A6B5A),
        ),
        DiagrammLegendenEintrag(
            id = "global-zinsschulden",
            bezeichnung = "Globale Zinsschulden",
            farbe = Color(0xFF8A7552),
        ),
        DiagrammLegendenEintrag(
            id = "global-kombinierte-schulden",
            bezeichnung = "Globale kombinierte Schulden",
            farbe = Color(0xFF8A5D65),
        ),
    )
    val reihen = listOf(
        legende[0] to globalesBarvermögen,
        legende[1] to globaleSchulden,
        legende[2] to globaleZinsschulden,
        legende[3] to globaleKombinierteSchulden,
    )
    val legendenStatus = rememberDiagrammLegendenStatus(legende)
    val sichtbareReihen = reihen.filter { (eintrag, _) ->
        legendenStatus.istSichtbar(eintrag.id)
    }

    LaunchedEffect(x, sichtbareReihen) {
        if (sichtbareReihen.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    sichtbareReihen.forEach { (_, werte) ->
                        series(x = x, y = werte)
                    }
                }
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            if (sichtbareReihen.isEmpty()) {
                Text(
                    text = "Keine Datenreihe ausgewählt",
                    modifier = ModiPad5,
                )
            } else {
                CartesianChartHost(
                    modifier = ModiPad5,
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                sichtbareReihen.map { (eintrag, _) ->
                                    val farbe = eintrag.farbe

                                    LineCartesianLayer.rememberLine(
                                        fill = remember(farbe) {
                                            LineCartesianLayer.LineFill.single(Fill(farbe))
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
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = markAchsenFormatter,
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(),
                    ),
                    modelProducer = modelProducer,
                    scrollState = rememberVicoScrollState(),
                    zoomState = rememberVicoZoomState(
                        initialZoom = Zoom.Content,
                    ),
                )
            }

            UmschaltbareDiagrammLegende(
                eintraege = legende,
                status = legendenStatus,
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
    val legende = listOf(
        DiagrammLegendenEintrag(
            id = "verwaltung-anleihen",
            bezeichnung = "Anleihen",
            farbe = Color(0xFF607D8B),
        ),
        DiagrammLegendenEintrag(
            id = "verwaltung-standorte",
            bezeichnung = "Standorte / Banken",
            farbe = Color(0xFF8A7552),
        ),
    )
    val reihen = listOf(
        legende[0] to anleihenSortiert,
        legende[1] to verwaltungsLimit,
    )
    val legendenStatus = rememberDiagrammLegendenStatus(legende)
    val sichtbareReihen = reihen.filter { (eintrag, _) ->
        legendenStatus.istSichtbar(eintrag.id)
    }

    LaunchedEffect(sichtbareReihen) {
        if (sichtbareReihen.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    sichtbareReihen.forEach { (_, werte) ->
                        series(werte)
                    }
                }
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
            if (sichtbareReihen.isEmpty()) {
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
                                sichtbareReihen.map { (eintrag, _) ->
                                    rememberLineComponent(
                                        fill = Fill(eintrag.farbe),
                                        thickness = 8.dp,
                                    )
                                }
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = stueckAchsenFormatter,
                        ),
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
            }

            UmschaltbareDiagrammLegende(
                eintraege = legende,
                status = legendenStatus,
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
    var eingabeRunde by remember(spiel.aktuelleRunde) {
        mutableIntStateOf((spiel.aktuelleRunde - 1).coerceAtLeast(0))
    }
    var eingabeRelevanz by remember { mutableStateOf("alle") }
    var eingabeLaufzeit by remember { mutableStateOf("alle") }
    var zeigeVerwaltung by remember { mutableStateOf(false) }
    var geoeffneteAnleihe by remember(spiel) { mutableStateOf<AnleiheAnzeige?>(null) }

    val anleihen = spiel.anleihen
    val spielerFarben = remember(spiel.spielerListe) {
        erhalteSpielerFarben(spiel.spielerListe).mapKeys { (spieler, _) -> spieler.name }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Header(
            spiel = spiel,
            spielerBauSaldo = spielerBauSaldo,
            ausgewählterSpieler = eingabeSpieler,
            selectedRound = eingabeRunde,
            selectedRelevance = eingabeRelevanz,
            selectedDuration = eingabeLaufzeit,
            showManagementView = zeigeVerwaltung,
            onChosePlayer = {
                eingabeSpieler = it
                geoeffneteAnleihe = null
            },
            onChoseRound = {
                eingabeRunde = it
                geoeffneteAnleihe = null
            },
            onChoseRelevance = {
                eingabeRelevanz = it
                geoeffneteAnleihe = null
            },
            onChoseDuration = {
                eingabeLaufzeit = it
                geoeffneteAnleihe = null
            },
            onChangeView = {
                zeigeVerwaltung = !zeigeVerwaltung
                geoeffneteAnleihe = null
            },
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
                val currentRound = eingabeRunde
                val filteredDebt = anleihen
                    .filter { anleihe ->
                        when (eingabeRelevanz) {
                            "gezahlte" -> anleihe.faelligkeit < currentRound
                            "fällige" -> anleihe.faelligkeit == currentRound
                            "offene" -> anleihe.faelligkeit > currentRound
                            "fällige & offene" -> anleihe.faelligkeit >= currentRound
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
                } else if (geoeffneteAnleihe in filteredDebt) {
                    AnleiheCard(
                        modifier = ModiPad5.fillMaxWidth(),
                        aktuelleRunde = currentRound,
                        eintrag = requireNotNull(geoeffneteAnleihe),
                        spielerFarben = spielerFarben,
                        zeigeAblauf = true,
                        onToggle = { geoeffneteAnleihe = null },
                        onDelete = onDelete,
                    )
                } else {
                    VerticalGrid(columns = SimpleGridCells.Adaptive(200.dp)) {
                        filteredDebt.forEach { eintrag -> AnleiheCard(
                            modifier = ModiPad5,
                            aktuelleRunde = currentRound,
                            eintrag = eintrag,
                            spielerFarben = spielerFarben,
                            zeigeAblauf = false,
                            onToggle = { geoeffneteAnleihe = eintrag },
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
    modifier: Modifier,
    aktuelleRunde: Int,
    eintrag: AnleiheAnzeige,
    spielerFarben: Map<String, Color>,
    zeigeAblauf: Boolean,
    onToggle: () -> Unit,
    onDelete: (AnleiheAnzeige) -> Unit,
) {
    val restlaufzeit = eintrag.faelligkeit - aktuelleRunde
    val (status, statusFarbe) = when {
        restlaufzeit < 0 -> "gezahlt" to Color(0xFFA9C2A5)
        restlaufzeit == 0 -> "fällig" to anleiheFaelligFarbe
        else -> "offen" to anleiheOffenFarbe
    }

    Card(
        modifier = modifier,
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = statusFarbe),
    ) {
        if (zeigeAblauf) {
            AnleiheAblauf(
                aktuelleRunde = aktuelleRunde,
                eintrag = eintrag,
                spielerFarben = spielerFarben,
            )
        } else {
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
                Text(
                    text = "Ablauf anzeigen",
                    fontSize = 12.sp,
                    modifier = Modifier.gridItem(columnSpan = 2).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = { onDelete(eintrag) },
                    modifier = Modifier.gridItem(columnSpan = 2).fillMaxWidth(),
                ) {
                    Text(text = "löschen", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AnleiheAblauf(
    aktuelleRunde: Int,
    eintrag: AnleiheAnzeige,
    spielerFarben: Map<String, Color>,
) {
    val ablauf = remember(eintrag) { eintrag.erhalteAblauf() }
    Column(modifier = Modifier.fillMaxWidth().padding(5.dp)) {
        Text(
            text = "Ablauf der Anleihe · Tippen für Übersicht",
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            textAlign = TextAlign.Center,
        )
        AnleiheAblaufTabellenzeile(
            aktion = "Betrag / Runde",
            beguenstigter = "Begünstigter",
            benachteiligter = "Benachteiligter",
            hintergrund = Color(0xFFE1E1E1),
            istKopfzeile = true,
        )
        ablauf.forEach { ereignis ->
            val hintergrund = when (ereignis.art) {
                AnleiheAblaufArt.EMISSION,
                AnleiheAblaufArt.HANDEL ->
                    spielerFarben[ereignis.an.name] ?: anleiheHandelOhneSpielerFarbe
                AnleiheAblaufArt.ZINS,
                AnleiheAblaufArt.RUECKKAUF -> when {
                    ereignis.runde < aktuelleRunde -> anleiheAbgelaufenFarbe
                    ereignis.runde == aktuelleRunde -> anleiheFaelligFarbe
                    else -> anleiheOffenFarbe
                }
            }
            val (aktion, beguenstigter, benachteiligter) = when (ereignis.art) {
                AnleiheAblaufArt.EMISSION,
                AnleiheAblaufArt.HANDEL -> Triple(
                    "R. ${ereignis.runde} · ${ereignis.art.bezeichnung} ${ereignis.betrag.zuMark()}",
                    ereignis.von.name,
                    ereignis.an.name,
                )
                AnleiheAblaufArt.ZINS -> Triple(
                    "R. ${ereignis.runde} · Zins ${ereignis.betrag.zuMark()}",
                    ereignis.an.name,
                    ereignis.von.name,
                )
                AnleiheAblaufArt.RUECKKAUF -> Triple(
                    "R. ${ereignis.runde} · Rückkauf ${ereignis.betrag.zuMark()}",
                    ereignis.an.name,
                    ereignis.von.name,
                )
            }
            AnleiheAblaufTabellenzeile(
                aktion = aktion,
                beguenstigter = beguenstigter,
                benachteiligter = benachteiligter,
                hintergrund = hintergrund,
            )
        }
    }
}

@Composable
private fun AnleiheAblaufTabellenzeile(
    aktion: String,
    beguenstigter: String,
    benachteiligter: String,
    hintergrund: Color,
    istKopfzeile: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(hintergrund),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnleiheTabellenZelle(
            text = aktion,
            modifier = Modifier.weight(1.55f),
            textAlign = TextAlign.Start,
            istKopfzeile = istKopfzeile,
        )
        AnleiheTabellenZelle(
            text = beguenstigter,
            modifier = Modifier.weight(1f),
            istKopfzeile = istKopfzeile,
        )
        AnleiheTabellenZelle(
            text = benachteiligter,
            modifier = Modifier.weight(1f),
            istKopfzeile = istKopfzeile,
        )
    }
}

@Composable
private fun AnleiheTabellenZelle(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
    istKopfzeile: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier
            .border(0.5.dp, Color(0xFF8D8D8D))
            .padding(horizontal = 3.dp, vertical = if (istKopfzeile) 5.dp else 4.dp),
        fontSize = if (istKopfzeile) 9.sp else 10.sp,
        textAlign = textAlign,
    )
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
