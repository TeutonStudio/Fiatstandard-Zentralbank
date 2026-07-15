package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.zuMark
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeRohstoff
import de.teutonstudio.zentralbank.schnittstelle.ganzzahligerStueckAchsenItemPlacer
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus
import de.teutonstudio.zentralbank.schnittstelle.rememberLinienMitGepunkteterAktuellerRunde
import de.teutonstudio.zentralbank.schnittstelle.rememberRundenachse
import de.teutonstudio.zentralbank.schnittstelle.rememberSaeulenMitGepunkteterAktuellerRunde
import de.teutonstudio.zentralbank.schnittstelle.rememberVollrundenachse
import de.teutonstudio.zentralbank.schnittstelle.richtungsAchsenFormatter
import de.teutonstudio.zentralbank.schnittstelle.rundenXWerteOhneSubrunden
import de.teutonstudio.zentralbank.schnittstelle.seriesMitGepunkteterAktuellerRunde

private data class HafenTarif(
    val bezeichnung: String,
    val preisfaktor: Float,
)

private enum class BilanzEinheit(val bezeichnung: String) {
    PREIS("Preis"),
    STUECK("Stk"),
}

private val aussenhandelGesamtFarbe = Color(0xFF29434E)

private val hafenTarife = listOf(
    HafenTarif("3 : 1 Hafen", 1f + 1f / 3f),
    HafenTarif("2 : 1 Hafen", 1f + 1f / 2f),
    HafenTarif("6 : 1 Hafen", 1f + 1f / 6f),
    HafenTarif("5 : 1 Hafen", 1f + 1f / 5f),
    HafenTarif("8 : 1 Hafen", 1f + 1f / 8f),
    HafenTarif("9 : 1 Hafen", 1f + 1f / 9f),
    HafenTarif("11 : 1 Hafen", 1f + 1f / 11f),
    HafenTarif("1 : 1 Hafen", 1f),
)

@Composable
fun zeigeAussenhandel(spiel: Spiel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AussenhandelsbilanzDiagramm(spiel)
        HafenPreisKarte(spiel.aktuelleMarktpreise)
    }
}

@Composable
private fun AussenhandelsbilanzDiagramm(spiel: Spiel) {
    if (spiel.aussenhandelsbilanzNachRohstoff.isEmpty()) {
        HinweisKarte("Noch keine Rohstoffdaten für den Außenhandel vorhanden")
        return
    }

    var einheit by remember { mutableStateOf(BilanzEinheit.PREIS) }
    val rohstoffe = Rohstoffe.entries.toList()
    val legende = rohstoffe.map { rohstoff ->
        DiagrammLegendenEintrag(
            id = "aussenhandel-rohstoff:${rohstoff.name}",
            bezeichnung = rohstoff.str,
            farbe = rohstoff.farbe,
        )
    } + DiagrammLegendenEintrag(
        id = "aussenhandel-gesamt",
        bezeichnung = "Alle (Summe)",
        farbe = aussenhandelGesamtFarbe,
    )
    val legendenStatus = rememberDiagrammLegendenStatus(legende)

    val werteNachRohstoff: List<Map<Rohstoffe, Int>> = when (einheit) {
        BilanzEinheit.PREIS -> spiel.aussenhandelsbilanzNachRohstoff.map { bilanz ->
            Rohstoffe.entries.associateWith { rohstoff ->
                bilanz[rohstoff]?.toIntOderNull() ?: 0
            }
        }

        BilanzEinheit.STUECK -> spiel.aussenhandelsbilanzStueckNachRohstoff
    }

    val gesamtWerte = if (einheit == BilanzEinheit.PREIS) {
        spiel.aussenhandelsbilanzGesamt.map { saldo -> saldo.toIntOderNull() ?: 0 }
    } else {
        emptyList()
    }

    val sichtbareReihen = buildList {
        rohstoffe.forEachIndexed { index, rohstoff ->
            val eintrag = legende[index]
            if (legendenStatus.istSichtbar(eintrag.id)) {
                add(eintrag to werteNachRohstoff.map { bilanz -> bilanz[rohstoff] ?: 0 })
            }
        }
        if (einheit == BilanzEinheit.PREIS) {
            val gesamtEintrag = legende.last()
            if (legendenStatus.istSichtbar(gesamtEintrag.id)) {
                add(gesamtEintrag to gesamtWerte)
            }
        }
    }

    val zeitpunkt = spiel.aktuellerZeitpunkt
    val chartModel = remember(
        einheit,
        werteNachRohstoff,
        gesamtWerte,
        sichtbareReihen,
        zeitpunkt,
    ) {
        if (sichtbareReihen.isEmpty()) {
            null
        } else {
            when (einheit) {
                BilanzEinheit.PREIS -> CartesianChartModel(
                    LineCartesianLayerModel.build {
                        sichtbareReihen.forEach { (_, werte) ->
                            seriesMitGepunkteterAktuellerRunde(
                                x = werte.indices.toList(),
                                y = werte,
                                zeitpunkt = zeitpunkt,
                            )
                        }
                    }
                )

                BilanzEinheit.STUECK -> CartesianChartModel(
                    ColumnCartesianLayerModel.build {
                        sichtbareReihen.forEach { (_, werte) ->
                            series(
                                x = rundenXWerteOhneSubrunden(
                                    anzahl = werte.size,
                                ),
                                y = werte,
                            )
                        }
                    }
                )
            }
        }
    }

    Card(modifier = ModiPad5) {
        Column(modifier = ModiPad5) {
            Row(
                modifier = ModiPad5,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BilanzEinheit.entries.forEach { auswahl ->
                    FilterChip(
                        selected = einheit == auswahl,
                        onClick = { einheit = auswahl },
                        label = { Text(auswahl.bezeichnung) },
                    )
                }
            }
            Text(
                text = when (einheit) {
                    BilanzEinheit.PREIS -> "Außenhandelsbilanz (kumuliert)"
                    BilanzEinheit.STUECK -> "Außenmengenbilanz (kumuliert)"
                },
                fontSize = 24.sp,
                modifier = ModiPad5,
            )
            if (chartModel == null) {
                Text(text = "Keine Datenreihe ausgewählt", modifier = ModiPad5)
            } else {
                CartesianChartHost(
                    modifier = ModiPad5,
                    chart = rememberCartesianChart(
                        when (einheit) {
                            BilanzEinheit.PREIS -> rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(
                                    rememberLinienMitGepunkteterAktuellerRunde(
                                        sichtbareReihen.map { (eintrag, _) -> eintrag }
                                    )
                                )
                            )

                            BilanzEinheit.STUECK -> rememberColumnCartesianLayer(
                                columnProvider = rememberSaeulenMitGepunkteterAktuellerRunde(
                                    eintraege = sichtbareReihen.map { (eintrag, _) -> eintrag },
                                    prognoseAbX = zeitpunkt.runde,
                                )
                            )
                        },
                        endAxis = VerticalAxis.rememberEnd(
                            valueFormatter = richtungsAchsenFormatter(
                                positiveRichtung = "Export",
                                negativeRichtung = "Import",
                                einheit = when (einheit) {
                                    BilanzEinheit.PREIS -> "M"
                                    BilanzEinheit.STUECK -> "Stk"
                                },
                            ),
                            itemPlacer = if (einheit == BilanzEinheit.STUECK) {
                                ganzzahligerStueckAchsenItemPlacer
                            } else {
                                VerticalAxis.ItemPlacer.step()
                            },
                        ),
                        bottomAxis = when (einheit) {
                            BilanzEinheit.PREIS -> rememberRundenachse(zeitpunkt)
                            BilanzEinheit.STUECK -> rememberVollrundenachse()
                        },
                    ),
                    model = chartModel,
                    scrollState = rememberVicoScrollState(),
                    zoomState = rememberVicoZoomState(initialZoom = Zoom.Content),
                )
            }

            UmschaltbareDiagrammLegende(
                eintraege = if (einheit == BilanzEinheit.PREIS) {
                    legende
                } else {
                    legende.dropLast(1)
                },
                status = legendenStatus,
            )
        }
    }
}

@Composable
private fun HafenPreisKarte(marktpreise: Map<Rohstoffe, Zahlungsmittel>) {
    var ausgewählterTarif by remember { mutableStateOf(hafenTarife.first()) }
    var tarifAuswahlOffen by remember { mutableStateOf(false) }

    Card(modifier = ModiPad5.fillMaxWidth()) {
        Column(modifier = ModiPad5) {
            Text(
                text = "Import- und Exportpreise nach Hafen",
                fontSize = 24.sp,
                modifier = ModiPad5,
            )

            Box {
                Card(
                    modifier = ModiPad5.clickable { tarifAuswahlOffen = true },
                ) {
                    Text(text = ausgewählterTarif.bezeichnung, modifier = ModiPad5)
                }
                DropdownMenu(
                    expanded = tarifAuswahlOffen,
                    onDismissRequest = { tarifAuswahlOffen = false },
                ) {
                    hafenTarife.forEach { tarif ->
                        DropdownMenuItem(
                            text = { Text(tarif.bezeichnung) },
                            onClick = {
                                ausgewählterTarif = tarif
                                tarifAuswahlOffen = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = ModiPad5.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Rohstoff", modifier = Modifier.weight(1.4f))
                Text(text = "Import", modifier = Modifier.weight(1f))
                Text(text = "Export", modifier = Modifier.weight(1f))
            }

            Rohstoffe.entries.forEach { rohstoff ->
                val marktpreis = marktpreise[rohstoff] ?: Zahlungsmittel()
                val importPreis = marktpreis * ausgewählterTarif.preisfaktor
                val exportPreis = marktpreis / ausgewählterTarif.preisfaktor

                Card(
                    modifier = ModiPad5.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = rohstoff.farbe,
                        contentColor = rohstoff.farbe.lesbareSchriftfarbe(),
                    ),
                ) {
                    Row(
                        modifier = ModiPad5.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1.4f)) {
                            zeigeRohstoff(
                                rohstoff = rohstoff,
                                fontsize = 14.sp,
                                iconSize = 28.dp,
                            )
                        }
                        Text(text = importPreis.zuMark(), modifier = Modifier.weight(1f))
                        Text(text = exportPreis.zuMark(), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HinweisKarte(text: String) {
    Card(modifier = ModiPad5) {
        Text(text = text, modifier = ModiPad5)
    }
}

@Preview
@Composable
private fun PreviewAussenhandel() {
    zeigeAussenhandel(TestSpiel)
}
