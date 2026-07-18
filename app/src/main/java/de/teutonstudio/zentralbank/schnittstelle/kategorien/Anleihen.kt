package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import de.teutonstudio.zentralbank.datenbank.AnleiheStatus
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
import de.teutonstudio.zentralbank.schnittstelle.AblaufDialog
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.LeftText
import de.teutonstudio.zentralbank.schnittstelle.ModiPad15
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.RightText
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarben
import de.teutonstudio.zentralbank.schnittstelle.ganzzahligerStueckAchsenItemPlacer
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe
import de.teutonstudio.zentralbank.schnittstelle.markBeschriftung
import de.teutonstudio.zentralbank.schnittstelle.mitAblaufRundentrenner
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus
import de.teutonstudio.zentralbank.schnittstelle.rememberAblaufSpaltenbreite
import de.teutonstudio.zentralbank.schnittstelle.rememberLinienMitGepunkteterAktuellerRunde
import de.teutonstudio.zentralbank.schnittstelle.rememberRundenachse
import de.teutonstudio.zentralbank.schnittstelle.rememberSaeulenMitGepunkteterAktuellerRunde
import de.teutonstudio.zentralbank.schnittstelle.seriesMitGepunkteterAktuellerRunde
import de.teutonstudio.zentralbank.schnittstelle.stueckAchsenFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

private const val GLOBAL_PLAYER = "Global"
private val anleiheFaelligFarbe = Color(0xFFD8C28F)
private val anleiheOffenFarbe = Color(0xFF9EB5C7)
private val anleiheAbgelaufenFarbe = Color(0xFFC7C7C7)
private const val ZINSWERTE_ERKLAERUNG =
    "Anleihenzinssatz bei Leitzinssatz: prozentuale Abweichung"
private const val MAX_Y_ACHSE_EXPONENT = 5f

private enum class SchuldenGraphTab(val titel: String) {
    BILANZ("Bilanz"),
    ZINSEN_UND_EMISSIONEN("Leitzins & Emissionen"),
}

private data class SchuldenDiagrammReihe(
    val eintrag: DiagrammLegendenEintrag,
    val runden: List<Int>,
    val werte: List<Int>,
)

internal fun <T> List<T>.bisRundeFortgeschrieben(letzteRunde: Int): List<T> {
    if (isEmpty() || lastIndex >= letzteRunde) return this
    return this + List(letzteRunde - lastIndex) { last() }
}

internal data class AnleiheZinsvergleich(
    val leitzins: Double,
    val anleihenzins: Double,
    val relativeAbweichung: Double?,
)

internal fun berechneAnleiheZinsvergleich(
    anleihe: Anleihe,
    leitzins: Float,
): AnleiheZinsvergleich {
    val nennwert = anleihe.sondervermögen.toDoubleOderNull()
    val anleihenzins = if (nennwert == 0.0) {
        0.0
    } else {
        anleihe.unvermögen.toDoubleOderNull() / nennwert * 100.0
    }
    val relativeAbweichung = if (leitzins == 0f) {
        null
    } else {
        (anleihenzins / leitzins.toDouble() - 1.0) * 100.0
    }
    return AnleiheZinsvergleich(
        leitzins = leitzins.toDouble(),
        anleihenzins = anleihenzins,
        relativeAbweichung = relativeAbweichung,
    )
}

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

private fun Number.aufExponentielleAchse(
    exponent: Float,
    maximalbetrag: Double,
): Double {
    val wert = toDouble()
    if (exponent <= 1f || maximalbetrag <= 0.0 || wert == 0.0) return wert
    return wert.sign * maximalbetrag *
        (abs(wert) / maximalbetrag).pow(1.0 / exponent.toDouble())
}

private fun Double.vonExponentiellerAchse(
    exponent: Float,
    maximalbetrag: Double,
): Double {
    if (exponent <= 1f || maximalbetrag <= 0.0 || this == 0.0) return this
    return sign * maximalbetrag *
        (abs(this) / maximalbetrag).pow(exponent.toDouble())
}

private fun exponentiellerMarkAchsenFormatter(
    exponent: Float,
    maximalbetrag: Double,
) = CartesianValueFormatter { _, value, _ ->
    val originalwert = value.vonExponentiellerAchse(exponent, maximalbetrag)
    val gerundet = originalwert.roundToLong()
    val beschriftung = if (abs(originalwert - gerundet) < 0.0001) {
        gerundet.toString()
    } else {
        String.format(Locale.GERMANY, "%.1f", originalwert)
    }
    markBeschriftung(beschriftung)
}

@Composable
private fun SchuldenGraphTabAuswahl(
    ausgewählterTab: SchuldenGraphTab,
    onTabAuswahl: (SchuldenGraphTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SchuldenGraphTab.entries.forEach { tab ->
            FilterChip(
                selected = ausgewählterTab == tab,
                onClick = { onTabAuswahl(tab) },
                label = { Text(tab.titel) },
            )
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
    var yAchsenExponent by remember { mutableFloatStateOf(1f) }
    var graphTab by remember { mutableStateOf(SchuldenGraphTab.BILANZ) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showManagementView) {
            ManagementChart(
                selectedPlayer = ausgewählterSpieler,
                selectedRound = selectedRound,
                aktuelleRunde = spiel.aktuelleRunde,
                spielerBauSaldo = spielerBauSaldo,
                anleihen = spiel.anleihen,
            )
        } else {
            val globaleAnsicht = ausgewählterSpieler == GLOBAL_PLAYER
            val angezeigterGraphTab = if (globaleAnsicht) {
                graphTab
            } else {
                SchuldenGraphTab.BILANZ
            }
            val tabAuswahl: (@Composable () -> Unit)? = if (globaleAnsicht) {
                {
                    SchuldenGraphTabAuswahl(
                        ausgewählterTab = graphTab,
                        onTabAuswahl = { graphTab = it },
                    )
                }
            } else {
                null
            }

            when (angezeigterGraphTab) {
                SchuldenGraphTab.BILANZ -> BalanceChart(
                    spiel = spiel,
                    ausgewählterSpieler = ausgewählterSpieler,
                    yAchsenExponent = yAchsenExponent,
                    onYAchsenExponentChange = { yAchsenExponent = it },
                    tabAuswahl = tabAuswahl,
                )
                SchuldenGraphTab.ZINSEN_UND_EMISSIONEN -> LeitzinsEmissionenChart(
                    spiel = spiel,
                    tabAuswahl = tabAuswahl,
                )
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
private fun SchuldenGraphHinweisCard(
    text: String,
    tabAuswahl: (@Composable () -> Unit)?,
) {
    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            tabAuswahl?.invoke()
            Text(text = text, modifier = ModiPad5)
        }
    }
}

@Composable
private fun BalanceChart(
    spiel: Spiel,
    ausgewählterSpieler: String,
    yAchsenExponent: Float,
    onYAchsenExponentChange: (Float) -> Unit,
    tabAuswahl: (@Composable () -> Unit)?,
) {
    val reihenLängen = listOf(
        spiel.spielerSaldo.size,
        spiel.spielerMarktwert.size,
        spiel.spielerKombinierteSchulden.size,
    )
    if (reihenLängen.any { länge -> länge == 0 }) {
        SchuldenGraphHinweisCard("Keine Bilanzdaten vorhanden.", tabAuswahl)
        return
    }

    if (reihenLängen.distinct().size != 1) {
        SchuldenGraphHinweisCard(
            "Bilanzdaten haben unterschiedliche Längen.",
            tabAuswahl,
        )
        return
    }

    if (ausgewählterSpieler == GLOBAL_PLAYER) {
        GlobalBalanceChart(
            spiel = spiel,
            yAchsenExponent = yAchsenExponent,
            onYAchsenExponentChange = onYAchsenExponentChange,
            tabAuswahl = tabAuswahl,
        )
        return
    }

    val prognoseRunden = spiel.schuldenProjektionsrunden
    val letztePrognoseRunde = spiel.letzteSchuldenProjektionsrunde
    val zeitpunkt = spiel.aktuellerZeitpunkt
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
            id = "bilanz-bankschulden",
            bezeichnung = "Bankschulden",
            farbe = spielerFarbe.abgedunkelt(0.65f),
        ),
    )
    val reihen = listOf(
        SchuldenDiagrammReihe(
            legende[0],
            prognoseRunden,
            spiel.spielerSaldo.toY(ausgewählterSpieler)
                .bisRundeFortgeschrieben(letztePrognoseRunde),
        ),
        SchuldenDiagrammReihe(
            legende[1],
            prognoseRunden,
            spiel.spielerMarktwert.toY(ausgewählterSpieler)
                .bisRundeFortgeschrieben(letztePrognoseRunde),
        ),
        SchuldenDiagrammReihe(
            legende[2],
            prognoseRunden,
            spiel.spielerKombinierteSchuldenMitProjektion.toY(ausgewählterSpieler)
                .bisRundeFortgeschrieben(letztePrognoseRunde),
        ),
        SchuldenDiagrammReihe(
            legende[3],
            prognoseRunden,
            spiel.spielerKombinierteBankschuldenMitProjektion
                .toY(ausgewählterSpieler)
                .bisRundeFortgeschrieben(letztePrognoseRunde),
        ),
    )
    val legendenStatus = rememberDiagrammLegendenStatus(legende)
    val sichtbareReihen = reihen.filter { reihe ->
        legendenStatus.istSichtbar(reihe.eintrag.id)
    }
    val maximalbetrag = sichtbareReihen
        .flatMap { reihe -> reihe.werte }
        .maxOfOrNull { wert -> abs(wert.toDouble()) }
        ?: 0.0

    LaunchedEffect(sichtbareReihen, yAchsenExponent, maximalbetrag, zeitpunkt) {
        if (sichtbareReihen.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    sichtbareReihen.forEach { reihe ->
                        seriesMitGepunkteterAktuellerRunde(
                            x = reihe.runden,
                            y = reihe.werte.map { wert ->
                                wert.aufExponentielleAchse(yAchsenExponent, maximalbetrag)
                            },
                            zeitpunkt = zeitpunkt,
                        )
                    }
                }
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            tabAuswahl?.invoke()
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
                                rememberLinienMitGepunkteterAktuellerRunde(
                                    eintraege = sichtbareReihen.map { reihe -> reihe.eintrag },
                                )
                            )
                        ),
                        endAxis = VerticalAxis.rememberEnd(
                            valueFormatter = exponentiellerMarkAchsenFormatter(
                                exponent = yAchsenExponent,
                                maximalbetrag = maximalbetrag,
                            ),
                        ),
                        bottomAxis = rememberRundenachse(zeitpunkt),
                    ),
                    modelProducer = modelProducer,
                    scrollState = rememberVicoScrollState(),
                    zoomState = rememberVicoZoomState(
                        initialZoom = Zoom.Content,
                    ),
                )
            }

            SchuldenLegendenZeile(
                yAchsenExponent = yAchsenExponent,
                onYAchsenExponentChange = onYAchsenExponentChange,
                legende = legende,
                legendenStatus = legendenStatus,
            )
        }
    }
}

@Composable
private fun GlobalBalanceChart(
    spiel: Spiel,
    yAchsenExponent: Float,
    onYAchsenExponentChange: (Float) -> Unit,
    tabAuswahl: (@Composable () -> Unit)?,
) {
    val letztePrognoseRunde = spiel.letzteSchuldenProjektionsrunde
    val prognoseRunden = spiel.schuldenProjektionsrunden
    val spielerBarvermögen = spiel.spielerBarvermögen
        .map { it.toIntOderNull() ?: 0 }
        .bisRundeFortgeschrieben(letztePrognoseRunde)
    val globalesBarvermögen = spiel.globalesBarvermögen
        .map { it.toIntOderNull() ?: 0 }
        .bisRundeFortgeschrieben(letztePrognoseRunde)
    val globaleSchulden = spiel.globaleKombinierteSchuldenMitProjektion
        .map { it.toIntOderNull() ?: 0 }
        .bisRundeFortgeschrieben(letztePrognoseRunde)
    val zinsgewinne = spiel.bankZinsgewinneMitProjektion
        .map { it.toIntOderNull() ?: 0 }
        .bisRundeFortgeschrieben(letztePrognoseRunde)
    val zeitpunkt = spiel.aktuellerZeitpunkt

    if (globalesBarvermögen.isEmpty()) {
        SchuldenGraphHinweisCard("Keine globalen Bilanzwerte vorhanden.", tabAuswahl)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val legende = listOf(
        DiagrammLegendenEintrag(
            id = "spieler-barvermoegen",
            bezeichnung = "Spieler-Barvermögen",
            farbe = Color(0xFF90A4AE),
        ),
        DiagrammLegendenEintrag(
            id = "global-barvermoegen",
            bezeichnung = "Globales Barvermögen",
            farbe = Color(0xFF607D8B),
        ),
        DiagrammLegendenEintrag(
            id = "global-schulden",
            bezeichnung = "Globale Schulden",
            farbe = Color(0xFF9A6B5A),
        ),
        DiagrammLegendenEintrag(
            id = "global-zinsgewinne",
            bezeichnung = "Zinsgewinne",
            farbe = Color(0xFF8A7552),
        ),
    )
    val reihen = listOf(
        SchuldenDiagrammReihe(legende[0], prognoseRunden, spielerBarvermögen),
        SchuldenDiagrammReihe(legende[1], prognoseRunden, globalesBarvermögen),
        SchuldenDiagrammReihe(legende[2], prognoseRunden, globaleSchulden),
        SchuldenDiagrammReihe(legende[3], prognoseRunden, zinsgewinne),
    )
    val legendenStatus = rememberDiagrammLegendenStatus(legende)
    val sichtbareReihen = reihen.filter { reihe ->
        legendenStatus.istSichtbar(reihe.eintrag.id)
    }
    val maximalbetrag = sichtbareReihen
        .flatMap { reihe -> reihe.werte }
        .maxOfOrNull { wert -> abs(wert.toDouble()) }
        ?: 0.0

    LaunchedEffect(sichtbareReihen, yAchsenExponent, maximalbetrag, zeitpunkt) {
        if (sichtbareReihen.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    sichtbareReihen.forEach { reihe ->
                        seriesMitGepunkteterAktuellerRunde(
                            x = reihe.runden,
                            y = reihe.werte.map { wert ->
                                wert.aufExponentielleAchse(yAchsenExponent, maximalbetrag)
                            },
                            zeitpunkt = zeitpunkt,
                        )
                    }
                }
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            tabAuswahl?.invoke()
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
                                rememberLinienMitGepunkteterAktuellerRunde(
                                    eintraege = sichtbareReihen.map { reihe -> reihe.eintrag },
                                )
                            )
                        ),
                        endAxis = VerticalAxis.rememberEnd(
                            valueFormatter = exponentiellerMarkAchsenFormatter(
                                exponent = yAchsenExponent,
                                maximalbetrag = maximalbetrag,
                            ),
                        ),
                        bottomAxis = rememberRundenachse(zeitpunkt),
                    ),
                    modelProducer = modelProducer,
                    scrollState = rememberVicoScrollState(),
                    zoomState = rememberVicoZoomState(
                        initialZoom = Zoom.Content,
                    ),
                )
            }

            SchuldenLegendenZeile(
                yAchsenExponent = yAchsenExponent,
                onYAchsenExponentChange = onYAchsenExponentChange,
                legende = legende,
                legendenStatus = legendenStatus,
            )
        }
    }
}

private data class EmissionsZinsBalken(
    val x: Double,
    val zins: Int,
    val legendenEintrag: DiagrammLegendenEintrag,
)

@Composable
private fun LeitzinsEmissionenChart(
    spiel: Spiel,
    tabAuswahl: (@Composable () -> Unit)?,
) {
    val zeitpunkt = spiel.aktuellerZeitpunkt
    val leitzinsWerte = List(spiel.letzteSchuldenProjektionsrunde + 1) { runde ->
        spiel.leitzinssatz(runde) ?: spiel.aktuellerLeitzinssatz
    }
    val spielerFarben = erhalteSpielerFarben(spiel.spielerListe)
    val spielerLegende = spiel.spielerListe.associate { spieler ->
        spieler.name to DiagrammLegendenEintrag(
            id = "emissionszins:${spieler.name}",
            bezeichnung = spieler.name,
            farbe = spielerFarben[spieler] ?: Color.Gray,
        )
    }
    val emissionsBalken = spiel.anleihen
        .sortedWith(
            compareBy<AnleiheAnzeige> { anleihe -> anleihe.emittiert }
                .thenBy { anleihe -> spiel.spielerIndex(anleihe.schuldiger.name) }
                .thenBy { anleihe -> anleihe.sondervermoegen.toIntOderNull() },
        )
        .mapNotNull { anleihe ->
            val legendenEintrag = spielerLegende[anleihe.schuldiger.name]
                ?: return@mapNotNull null
            val spielerIndex = spiel.spielerIndex(anleihe.schuldiger.name)
            EmissionsZinsBalken(
                x = zeitpunkt.xNachZug(anleihe.emittiert, spielerIndex),
                zins = anleihe.anleihe.erhalteZinssatz(),
                legendenEintrag = legendenEintrag,
            )
        }
    val leitzinsLegende = DiagrammLegendenEintrag(
        id = "leitzins",
        bezeichnung = "Leitzins",
        farbe = Color(0xFF37474F),
    )
    val emissionsLegende = spiel.spielerListe.mapNotNull { spieler ->
        spielerLegende[spieler.name]?.takeIf { eintrag ->
            emissionsBalken.any { balken -> balken.legendenEintrag.id == eintrag.id }
        }
    }
    val legende = listOf(leitzinsLegende) + emissionsLegende
    val legendenStatus = rememberDiagrammLegendenStatus(legende)
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(leitzinsWerte, emissionsBalken, zeitpunkt) {
        modelProducer.runTransaction {
            if (emissionsBalken.isNotEmpty()) {
                columnSeries {
                    emissionsBalken.forEach { balken ->
                        series(x = listOf(balken.x), y = listOf(balken.zins))
                    }
                }
            }
            lineSeries {
                seriesMitGepunkteterAktuellerRunde(
                    x = leitzinsWerte.indices.toList(),
                    y = leitzinsWerte,
                    zeitpunkt = zeitpunkt,
                )
            }
        }
    }

    val linienLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            rememberLinienMitGepunkteterAktuellerRunde(
                eintraege = listOf(leitzinsLegende),
            )
        )
    )
    val prozentFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            String.format(Locale.GERMANY, "%.1f %%", value)
        }
    }
    val endAchse = VerticalAxis.rememberEnd(valueFormatter = prozentFormatter)
    val rundenAchse = rememberRundenachse(zeitpunkt)
    val chart = if (emissionsBalken.isEmpty()) {
        rememberCartesianChart(
            linienLayer,
            endAxis = endAchse,
            bottomAxis = rundenAchse,
        )
    } else {
        rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = rememberSaeulenMitGepunkteterAktuellerRunde(
                    eintraege = emissionsBalken.map { balken -> balken.legendenEintrag },
                    prognoseAbX = zeitpunkt.prognoseAbX,
                ),
            ),
            linienLayer,
            endAxis = endAchse,
            bottomAxis = rundenAchse,
        )
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            tabAuswahl?.invoke()
            Text(
                text = "Leitzins und Anleihenzins bei Emission",
                fontSize = 18.sp,
                modifier = ModiPad5,
            )
            CartesianChartHost(
                modifier = ModiPad5,
                chart = chart,
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(),
                zoomState = rememberVicoZoomState(initialZoom = Zoom.Content),
            )
            UmschaltbareDiagrammLegende(
                eintraege = legende,
                status = legendenStatus,
                interaktiv = false,
            )
        }
    }
}

@Composable
private fun SchuldenLegendenZeile(
    yAchsenExponent: Float,
    onYAchsenExponentChange: (Float) -> Unit,
    legende: List<DiagrammLegendenEintrag>,
    legendenStatus: de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenStatus,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(150.dp).padding(start = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (yAchsenExponent <= 1f) {
                    "Y-Achse: linear"
                } else {
                    "Y-Exponent: ${String.format(Locale.GERMANY, "%.1f", yAchsenExponent)}"
                },
                fontSize = 10.sp,
            )
            Slider(
                value = yAchsenExponent,
                onValueChange = onYAchsenExponentChange,
                valueRange = 1f..MAX_Y_ACHSE_EXPONENT,
            )
        }
        UmschaltbareDiagrammLegende(
            eintraege = legende,
            status = legendenStatus,
            modifier = Modifier.weight(1f),
        )
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
                        endAxis = VerticalAxis.rememberEnd(
                            valueFormatter = stueckAchsenFormatter,
                            itemPlacer = ganzzahligerStueckAchsenItemPlacer,
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
    Column(modifier = Modifier.fillMaxWidth()) {
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
                title = "Runde:",
                value = selectedRound?.toString() ?: "–",
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
    beiEmission: () -> Unit = {},
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
                        val status = spiel.erhalteAnleiheStatus(anleihe, currentRound)
                        when (eingabeRelevanz) {
                            "gezahlte" -> status == AnleiheStatus.GEZAHLT
                            "fällige" -> status == AnleiheStatus.FAELLIG
                            "offene" -> status == AnleiheStatus.OFFEN
                            "fällige & offene" -> status != AnleiheStatus.GEZAHLT
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

                VerticalGrid(columns = SimpleGridCells.Adaptive(270.dp)) {
                    AnleiheEmissionKarte(
                        modifier = ModiPad5.height(270.dp),
                        beiKlick = beiEmission,
                    )
                    filteredDebt.forEach { eintrag -> AnleiheCard(
                        modifier = ModiPad5.height(270.dp),
                        eintrag = eintrag,
                        status = spiel.erhalteAnleiheStatus(eintrag, currentRound),
                        onShowAblauf = { geoeffneteAnleihe = eintrag },
                        onDelete = onDelete,
                    ) }
                }
            }
        }
    }
    geoeffneteAnleihe?.let { eintrag ->
        AblaufDialog(
            titel = "Anleihenablauf · ${eintrag.schuldiger.name} · " +
                eintrag.sondervermoegen.zuMark(),
            breitenAnteil = 0.72f,
            onDismiss = { geoeffneteAnleihe = null },
        ) {
            AnleiheAblauf(
                spiel = spiel,
                aktuelleRunde = eingabeRunde,
                eintrag = eintrag,
            )
        }
    }
}

@Composable
private fun AnleiheEmissionKarte(
    modifier: Modifier,
    beiKlick: () -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = beiKlick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = "Neue Anleihe emittieren",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Der aktive Spieler übernimmt die Rolle des Emittenten.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}


@OptIn(ExperimentalGridApi::class)
@Composable
private fun AnleiheCard(
    modifier: Modifier,
    eintrag: AnleiheAnzeige,
    status: AnleiheStatus,
    onShowAblauf: () -> Unit,
    onDelete: (AnleiheAnzeige) -> Unit,
) {
    val (statusText, statusFarbe) = when (status) {
        AnleiheStatus.GEZAHLT -> "gezahlt" to Color(0xFFA9C2A5)
        AnleiheStatus.FAELLIG -> "fällig" to anleiheFaelligFarbe
        AnleiheStatus.OFFEN -> "offen" to anleiheOffenFarbe
    }

    Card(
        modifier = modifier,
        onClick = onShowAblauf,
        colors = CardDefaults.cardColors(
            containerColor = statusFarbe,
            contentColor = statusFarbe.lesbareSchriftfarbe(),
        ),
    ) {
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
                LeftText(text = statusText,fontSize = 14.sp,modifier = Modifier.fillMaxWidth())
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

@Composable
private fun AnleiheAblauf(
    spiel: Spiel,
    aktuelleRunde: Int,
    eintrag: AnleiheAnzeige,
) {
    val ablauf = remember(eintrag) { eintrag.erhalteAblauf() }
    var eingeklappteRunden by remember(eintrag) { mutableStateOf(emptySet<Int>()) }
    var zeigeBuchungssatz by remember(eintrag) { mutableStateOf(false) }
    val ablaufScrollState = rememberScrollState()
    val tabellenScrollState = rememberScrollState()
    val rundenGruppen = remember(ablauf) {
        ablauf
            .groupBy { ereignis -> ereignis.runde }
            .mapValues { (_, ereignisse) ->
                ereignisse.sortedByDescending { ereignis -> ereignis.art.reihenfolge }
            }
            .toSortedMap(reverseOrder())
    }
    val alleRundenEingeklappt = rundenGruppen.isNotEmpty() &&
        rundenGruppen.keys.all(eingeklappteRunden::contains)
    val beiAllenRundenKlick = {
        eingeklappteRunden = if (alleRundenEingeklappt) {
            emptySet()
        } else {
            rundenGruppen.keys
        }
    }
    val zinswertTexte = ablauf.mapNotNull { ereignis ->
        if (ereignis.art == AnleiheAblaufArt.HANDEL) {
            null
        } else {
            formatiereAnleiheZinsvergleich(
                berechneAnleiheZinsvergleich(
                    anleihe = eintrag.anleihe,
                    leitzins = spiel.leitzinssatz(ereignis.runde)
                        ?: spiel.aktuellerLeitzinssatz,
                )
            )
        }
    }
    val spaltenbreiten = AnleiheAblaufSpaltenbreiten(
        runde = maxOf(
            rememberAblaufSpaltenbreite(listOf("Runde"), 11.sp, 8.dp),
            rememberAblaufSpaltenbreite(rundenGruppen.keys.map(Int::toString), 12.sp, 8.dp),
        ),
        zinswerte = maxOf(
            rememberAblaufSpaltenbreite(
                listOf("Zinswerte"),
                11.sp,
                8.dp,
                FontWeight.SemiBold,
            ),
            rememberAblaufSpaltenbreite(listOf(ZINSWERTE_ERKLAERUNG), 9.sp, 8.dp),
            rememberAblaufSpaltenbreite(
                zinswertTexte,
                10.sp,
                8.dp,
                FontWeight.SemiBold,
            ),
        ),
        zahlungsempfaenger = maxOf(
            rememberAblaufSpaltenbreite(
                listOf(if (zeigeBuchungssatz) "Emittent an Halter" else "Zahlungsempfänger"),
                11.sp,
                84.dp,
            ),
            rememberAblaufSpaltenbreite(
                ablauf.map { ereignis ->
                    if (zeigeBuchungssatz) {
                        ereignis.buchungssatz
                    } else {
                        ereignis.zahlungsempfaenger.name
                    }
                } + rundenGruppen.values.map { ereignisse -> "${ereignisse.size} Zahlungen" },
                12.sp,
                16.dp,
            ),
        ),
        betrag = maxOf(
            rememberAblaufSpaltenbreite(listOf("Betrag"), 11.sp, 8.dp),
            rememberAblaufSpaltenbreite(
                ablauf.map { ereignis -> ereignis.betrag.zuMark() } + "eingeklappt",
                12.sp,
                8.dp,
            ),
        ),
    )
    val tabellenbreite = spaltenbreiten.runde + spaltenbreiten.zinswerte +
        spaltenbreiten.zahlungsempfaenger + spaltenbreiten.betrag
    LaunchedEffect(eintrag, aktuelleRunde) {
        ablaufScrollState.scrollTo(0)
    }
    Column(
        modifier = Modifier
            .padding(5.dp)
            .verticalScroll(ablaufScrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Vollständiger Ablauf bis zur Tilgung in Runde ${eintrag.faelligkeit} · " +
                "Runde antippen zum Ein-/Ausklappen\n" +
                "Für zukünftige Runden wird der aktuelle Leitzins fortgeschrieben.",
            modifier = Modifier.widthIn(max = tabellenbreite).padding(bottom = 5.dp),
            textAlign = TextAlign.Center,
        )
        Column(modifier = Modifier.horizontalScroll(tabellenScrollState)) {
            AnleiheAblaufTabellenzeile(
                runde = "Runde",
                zahlungsempfaenger = if (zeigeBuchungssatz) {
                    "Emittent an Halter"
                } else {
                    "Zahlungsempfänger"
                },
                betrag = "Betrag",
                hintergrund = MaterialTheme.colorScheme.surfaceVariant,
                spaltenbreiten = spaltenbreiten,
                istKopfzeile = true,
                beiRundenKlick = beiAllenRundenKlick,
                buchungssatzSchalterAktiv = zeigeBuchungssatz,
                beiBuchungssatzAenderung = { zeigeBuchungssatz = it },
            )
            rundenGruppen.entries.forEachIndexed { rundenIndex, (runde, ereignisse) ->
                val istEingeklappt = runde in eingeklappteRunden
                val rundenHintergrund = when (
                    spiel.erhalteZuggebundenenStatus(
                        ereignisRunde = runde,
                        spielerName = eintrag.schuldiger.name,
                        betrachteteRunde = aktuelleRunde,
                    )
                ) {
                    AnleiheStatus.GEZAHLT -> anleiheAbgelaufenFarbe
                    AnleiheStatus.FAELLIG -> anleiheFaelligFarbe
                    AnleiheStatus.OFFEN -> anleiheOffenFarbe
                }
                val beiRundenKlick = {
                    eingeklappteRunden = if (istEingeklappt) {
                        eingeklappteRunden - runde
                    } else {
                        eingeklappteRunden + runde
                    }
                }
                if (istEingeklappt) {
                    AnleiheAblaufTabellenzeile(
                        runde = runde.toString(),
                        zahlungsempfaenger = "${ereignisse.size} Zahlungen",
                        betrag = "eingeklappt",
                        hintergrund = rundenHintergrund,
                        spaltenbreiten = spaltenbreiten,
                        beiRundenKlick = beiRundenKlick,
                        istKompakt = true,
                        obereRundentrennung = rundenIndex > 0,
                    )
                } else {
                    ereignisse.forEachIndexed { ereignisIndex, ereignis ->
                        val zahlungsempfaenger = ereignis.zahlungsempfaenger
                        val zinsvergleich = if (ereignis.art == AnleiheAblaufArt.HANDEL) {
                            null
                        } else {
                            berechneAnleiheZinsvergleich(
                                anleihe = eintrag.anleihe,
                                leitzins = spiel.leitzinssatz(ereignis.runde)
                                    ?: spiel.aktuellerLeitzinssatz,
                            )
                        }
                        AnleiheAblaufTabellenzeile(
                            runde = ereignis.runde.toString(),
                            zinsvergleich = zinsvergleich,
                            zahlungsempfaenger = if (zeigeBuchungssatz) {
                                ereignis.buchungssatz
                            } else {
                                zahlungsempfaenger.name
                            },
                            betrag = ereignis.betrag.zuMark(),
                            hintergrund = rundenHintergrund,
                            spaltenbreiten = spaltenbreiten,
                            beiRundenKlick = beiRundenKlick,
                            obereRundentrennung = rundenIndex > 0 && ereignisIndex == 0,
                        )
                    }
                }
            }
        }
    }
}

private data class AnleiheAblaufSpaltenbreiten(
    val runde: Dp,
    val zinswerte: Dp,
    val zahlungsempfaenger: Dp,
    val betrag: Dp,
)

@Composable
private fun AnleiheAblaufTabellenzeile(
    runde: String,
    zahlungsempfaenger: String,
    betrag: String,
    hintergrund: Color,
    spaltenbreiten: AnleiheAblaufSpaltenbreiten,
    zinsvergleich: AnleiheZinsvergleich? = null,
    istKopfzeile: Boolean = false,
    beiRundenKlick: (() -> Unit)? = null,
    buchungssatzSchalterAktiv: Boolean? = null,
    beiBuchungssatzAenderung: ((Boolean) -> Unit)? = null,
    istKompakt: Boolean = false,
    obereRundentrennung: Boolean = false,
) {
    CompositionLocalProvider(LocalContentColor provides hintergrund.lesbareSchriftfarbe()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .background(hintergrund)
                .mitAblaufRundentrenner(obereRundentrennung),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnleiheTabellenZelle(
                text = runde,
                modifier = Modifier.width(spaltenbreiten.runde).fillMaxHeight(),
                istKopfzeile = istKopfzeile,
                beiKlick = beiRundenKlick,
                istKompakt = istKompakt,
            )
            if (istKopfzeile) {
                AnleiheZinswerteKopfzelle(
                    erklaerung = ZINSWERTE_ERKLAERUNG,
                    modifier = Modifier.width(spaltenbreiten.zinswerte).fillMaxHeight(),
                )
            } else {
                AnleiheZinswerteZelle(
                    zinsvergleich = zinsvergleich,
                    modifier = Modifier.width(spaltenbreiten.zinswerte).fillMaxHeight(),
                    istKompakt = istKompakt,
                )
            }
            if (buchungssatzSchalterAktiv != null && beiBuchungssatzAenderung != null) {
                AnleiheBuchungssatzKopfzelle(
                    text = zahlungsempfaenger,
                    aktiv = buchungssatzSchalterAktiv,
                    beiAenderung = beiBuchungssatzAenderung,
                    modifier = Modifier.width(spaltenbreiten.zahlungsempfaenger).fillMaxHeight(),
                )
            } else {
                AnleiheTabellenZelle(
                    text = zahlungsempfaenger,
                    modifier = Modifier.width(spaltenbreiten.zahlungsempfaenger).fillMaxHeight(),
                    istKopfzeile = istKopfzeile,
                    istKompakt = istKompakt,
                )
            }
            AnleiheTabellenZelle(
                text = betrag,
                modifier = Modifier.width(spaltenbreiten.betrag).fillMaxHeight(),
                textAlign = TextAlign.End,
                istKopfzeile = istKopfzeile,
                istKompakt = istKompakt,
            )
        }
    }
}

@Composable
private fun AnleiheZinswerteZelle(
    zinsvergleich: AnleiheZinsvergleich?,
    modifier: Modifier,
    istKompakt: Boolean,
) {
    val schriftgroesse = if (istKompakt) 9.sp else 10.sp
    val abweichung = zinsvergleich?.relativeAbweichung
    val abweichungsfarbe = when {
        abweichung == null || abs(abweichung) < 0.05 -> LocalContentColor.current
        abweichung > 0.0 -> Color(0xFF2E7D32)
        else -> Color(0xFFB3261E)
    }
    Text(
        text = zinsvergleich?.let(::formatiereAnleiheZinsvergleich).orEmpty(),
        modifier = modifier
            .border(0.5.dp, Color(0xFF8D8D8D))
            .padding(horizontal = 3.dp, vertical = if (istKompakt) 1.dp else 3.dp),
        color = abweichungsfarbe,
        fontSize = schriftgroesse,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun AnleiheZinswerteKopfzelle(
    erklaerung: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .border(0.5.dp, Color(0xFF8D8D8D))
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Zinswerte",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = erklaerung,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

internal fun formatiereAnleiheZinsvergleich(zinsvergleich: AnleiheZinsvergleich): String =
    "${zinsvergleich.anleihenzins.alsProzentwert()} bei " +
        "${zinsvergleich.leitzins.alsProzentwert()}: " +
        (zinsvergleich.relativeAbweichung?.alsAbweichung() ?: "–")

private fun Double.alsProzentwert(): String =
    String.format(Locale.GERMANY, "%.1f %%", this)

private fun Double.alsAbweichung(): String {
    val normalisiert = if (abs(this) < 0.05) 0.0 else this
    return if (normalisiert == 0.0) {
        "0,0 %"
    } else {
        String.format(Locale.GERMANY, "%+.1f %%", normalisiert)
    }
}

@Composable
private fun AnleiheBuchungssatzKopfzelle(
    text: String,
    aktiv: Boolean,
    beiAenderung: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val hintergrund = if (aktiv) Color(0xFFB8D8B2) else Color.Transparent
    val inhaltFarbe = if (aktiv) hintergrund.lesbareSchriftfarbe() else LocalContentColor.current
    CompositionLocalProvider(LocalContentColor provides inhaltFarbe) {
        Row(
            modifier = modifier
                .background(hintergrund)
                .border(0.5.dp, Color(0xFF8D8D8D))
                .padding(horizontal = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Switch(
                checked = aktiv,
                onCheckedChange = beiAenderung,
                modifier = Modifier.scale(0.65f),
            )
        }
    }
}

@Composable
private fun AnleiheTabellenZelle(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Center,
    istKopfzeile: Boolean = false,
    beiKlick: (() -> Unit)? = null,
    istKompakt: Boolean = false,
) {
    val zellenModifier = if (beiKlick == null) modifier else modifier.clickable(onClick = beiKlick)
    Text(
        text = text,
        modifier = zellenModifier
            .border(0.5.dp, Color(0xFF8D8D8D))
            .padding(
                horizontal = 3.dp,
                vertical = when {
                    istKopfzeile -> 5.dp
                    istKompakt -> 1.dp
                    else -> 4.dp
                },
            ),
        fontSize = when {
            istKopfzeile -> 11.sp
            istKompakt -> 10.sp
            else -> 12.sp
        },
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
