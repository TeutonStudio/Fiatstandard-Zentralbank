package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.LineProvider.Companion.series
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.HandelsDaten
import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.farbe
import de.teutonstudio.zentralbank.datenbank.summeGeld
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.ModiPad15
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeBauteilPreis
import de.teutonstudio.zentralbank.schnittstelle.eingabe.WarenkorbBearbeitenDialog
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarben
import de.teutonstudio.zentralbank.schnittstelle.markAchsenFormatter
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus
import de.teutonstudio.zentralbank.schnittstelle.rememberExklusivenDiagrammLegendenStatus
import de.teutonstudio.zentralbank.schnittstelle.stueckAchsenFormatter

private enum class MarktpreisKategorie(val titel: String) {
    ROHSTOFFE("Alle Rohstoffe"),
    BAUWERKE("Alle Bauwerke"),
    WARENKORB("Warenkorb"),
    HANDELSDIFFERENZ("Handelsdifferenz"),
}

private enum class WarenkorbReihe {
    WARENKORB,
    PREISINFLATIONSKORB,
}

private val warenkorbMarktfarbe = Color(0xFF75658A)
private val preisinflationswarenkorbFarbe = Color(0xFF4D7A70)

private fun List<Map<Rohstoffe, Zahlungsmittel>>.zuWarenkorbpreisen(
    warenkorb: Map<Rohstoffe, Int>,
    preisinflationswarenkorb: Map<Rohstoffe, Int>,
): List<Map<WarenkorbReihe, Zahlungsmittel>> = map { preise ->
    mapOf(
        WarenkorbReihe.WARENKORB to warenkorb.entries.summeGeld { (rohstoff, anzahl) ->
            (preise[rohstoff] ?: Zahlungsmittel()) * anzahl
        },
        WarenkorbReihe.PREISINFLATIONSKORB to
            preisinflationswarenkorb.entries.summeGeld { (rohstoff, anzahl) ->
                (preise[rohstoff] ?: Zahlungsmittel()) * anzahl
            },
    )
}

@Composable
private fun <A> List<Map<A, Zahlungsmittel>>.toChart(args: List<A>): CartesianChartModel =
    remember(this, args) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                args.forEach { arg ->
                    series(
                        x = indices.toList(),
                        y = map { preisMap ->
                            preisMap[arg]?.toIntOderNull() ?: 0
                        },
                    )
                }
            }
        )
    }

@Composable
private fun List<Map<Rohstoffe, Int>>.zuStueckChart(
    rohstoffe: List<Rohstoffe>,
): CartesianChartModel = remember(this, rohstoffe) {
    CartesianChartModel(
        ColumnCartesianLayerModel.build {
            rohstoffe.forEach { rohstoff ->
                series(
                    x = indices.toList(),
                    y = map { differenz -> differenz[rohstoff] ?: 0 },
                )
            }
        }
    )
}

@Composable
fun zeigeMarktplatz(
    spiel: Spiel,
    onTrade: (HandelsDaten) -> Unit = {},
    onWarenkorbAendern: (Map<Rohstoffe, Int>) -> Unit = {},
) {
    val isBilanzExpanded = remember { mutableStateOf(true) }

    var marktpreisKategorie by remember { mutableStateOf(MarktpreisKategorie.ROHSTOFFE) }
    var warenkorbDialogOffen by remember { mutableStateOf(false) }
    val isBuying = remember { mutableStateOf(false) }
    val isSelling = remember { mutableStateOf(false) }

    val inputResource = remember { mutableStateOf<Rohstoffe?>(null) }
    val inputPrice = remember { mutableFloatStateOf(0f) }
    val inputAmount = remember { mutableIntStateOf(0) }
    val inputAmountText = remember { mutableStateOf("") }
    val eingabeSpieler = remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        if (spiel.marktpreise.isNotEmpty()) {
            Card(
                modifier = ModiPad5.clickable {
                    isBuying.value = false
                    isSelling.value = false
                }
            ) {
                val rohstoffe = Rohstoffe.entries.toList()
                val bauwerke = remember {
                    listOf(
                        Wirtschaftsregionen.entries.toList(),
                        Verwaltungsstandort.entries.toList(),
                        Handelslinie.entries.toList(),
                    ).flatten()
                }
                val warenkorbReihen = WarenkorbReihe.entries.toList()
                val warenkorbpreise = remember(
                    spiel.marktpreise,
                    spiel.warenkorb,
                    spiel.preisinflationswarenkorb,
                ) {
                    spiel.marktpreise.zuWarenkorbpreisen(
                        warenkorb = spiel.warenkorb,
                        preisinflationswarenkorb = spiel.preisinflationswarenkorb,
                    )
                }
                val spielerFarben = erhalteSpielerFarben(spiel.spielerListe)
                val spielerLegende = spiel.spielerListe.map { spieler ->
                    DiagrammLegendenEintrag(
                        id = "handelsdifferenz-spieler:${spieler.name}",
                        bezeichnung = spieler.name,
                        farbe = spielerFarben.getValue(spieler),
                    )
                }
                val spielerLegendenStatus = rememberExklusivenDiagrammLegendenStatus(spielerLegende)
                val ausgewaehlterSpieler = spielerLegende
                    .firstOrNull { eintrag -> spielerLegendenStatus.istSichtbar(eintrag.id) }
                    ?.let { eintrag ->
                        spiel.spielerListe.firstOrNull { spieler ->
                            eintrag.id == "handelsdifferenz-spieler:${spieler.name}"
                        }
                    }

                val legende = when (marktpreisKategorie) {
                    MarktpreisKategorie.ROHSTOFFE,
                    MarktpreisKategorie.HANDELSDIFFERENZ -> rohstoffe.map { rohstoff ->
                        DiagrammLegendenEintrag(
                            id = "rohstoff:${rohstoff.name}",
                            bezeichnung = rohstoff.str,
                            farbe = rohstoff.farbe,
                        )
                    }

                    MarktpreisKategorie.BAUWERKE -> bauwerke.map { bauwerk ->
                        DiagrammLegendenEintrag(
                            id = "bauwerk:${bauwerk.str}",
                            bezeichnung = bauwerk.str,
                            farbe = bauwerk.farbe,
                        )
                    }

                    MarktpreisKategorie.WARENKORB -> listOf(
                        DiagrammLegendenEintrag(
                            id = "warenkorb",
                            bezeichnung = "Warenkorb",
                            farbe = warenkorbMarktfarbe,
                        ),
                        DiagrammLegendenEintrag(
                            id = "preisinflationswarenkorb",
                            bezeichnung = "Preisinflationswarenkorb",
                            farbe = preisinflationswarenkorbFarbe,
                        ),
                    )
                }

                val legendenStatus = rememberDiagrammLegendenStatus(legende)
                val sichtbareIndizes = legende.indices.filter { index ->
                    legendenStatus.istSichtbar(legende[index].id)
                }
                val sichtbareLegende = sichtbareIndizes.map(legende::get)
                val chartModel = if (sichtbareIndizes.isEmpty()) {
                    null
                } else {
                    when (marktpreisKategorie) {
                        MarktpreisKategorie.ROHSTOFFE -> spiel.marktpreise.toChart(
                            sichtbareIndizes.map(rohstoffe::get)
                        )

                        MarktpreisKategorie.BAUWERKE -> spiel.bauwerkMarktpreise.toChart(
                            sichtbareIndizes.map(bauwerke::get)
                        )

                        MarktpreisKategorie.WARENKORB -> warenkorbpreise.toChart(
                            sichtbareIndizes.map(warenkorbReihen::get)
                        )

                        MarktpreisKategorie.HANDELSDIFFERENZ -> ausgewaehlterSpieler
                            ?.let { spieler ->
                                spiel.erhalteRohstoffHandelsstueckDifferenz(spieler)
                                    .zuStueckChart(sichtbareIndizes.map(rohstoffe::get))
                            }
                    }
                }

                val bilanzModifier = ModiPad5.clickable { isBilanzExpanded.value = !isBilanzExpanded.value }

                if (isBilanzExpanded.value) {
                    val linien = sichtbareLegende.map { eintrag ->
                        LineCartesianLayer.rememberLine(
                            fill = remember(eintrag.farbe) {
                                LineCartesianLayer.LineFill.single(
                                    Fill(eintrag.farbe)
                                )
                            },
                            interpolator = remember {
                                LineCartesianLayer.Interpolator.cubic(
                                    curvature = 0.5f
                                )
                            }
                        )
                    }

                    Column {
                        FlowRow(
                            modifier = ModiPad5,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MarktpreisKategorie.entries.forEach { kategorie ->
                                FilterChip(
                                    selected = marktpreisKategorie == kategorie,
                                    onClick = { marktpreisKategorie = kategorie },
                                    label = { Text(kategorie.titel) },
                                )
                            }
                        }

                        if (chartModel == null) {
                            Text(
                                text = "Keine Datenreihe ausgewählt",
                                modifier = ModiPad5,
                            )
                        } else {
                            if (marktpreisKategorie == MarktpreisKategorie.HANDELSDIFFERENZ) {
                                CartesianChartHost(
                                    modifier = bilanzModifier,
                                    chart = rememberCartesianChart(
                                        rememberColumnCartesianLayer(
                                            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                                sichtbareLegende.map { eintrag ->
                                                    rememberLineComponent(
                                                        fill = Fill(eintrag.farbe),
                                                        thickness = 8.dp,
                                                    )
                                                }
                                            )
                                        ),
                                        endAxis = VerticalAxis.rememberEnd(
                                            valueFormatter = stueckAchsenFormatter,
                                        ),
                                        bottomAxis = HorizontalAxis.rememberBottom(),
                                    ),
                                    model = chartModel,
                                    scrollState = rememberVicoScrollState(),
                                    zoomState = rememberVicoZoomState(
                                        initialZoom = remember { Zoom.Content }
                                    )
                                )
                            } else {
                                CartesianChartHost(
                                    modifier = bilanzModifier,
                                    chart = rememberCartesianChart(
                                        rememberLineCartesianLayer(
                                            lineProvider = series(*linien.toTypedArray())
                                        ),
                                        endAxis = VerticalAxis.rememberEnd(
                                            valueFormatter = markAchsenFormatter,
                                        ),
                                        bottomAxis = HorizontalAxis.rememberBottom(),
                                    ),
                                    model = chartModel,
                                    scrollState = rememberVicoScrollState(),
                                    zoomState = rememberVicoZoomState(
                                        initialZoom = remember { Zoom.Content }
                                    )
                                )
                            }
                        }

                        if (marktpreisKategorie == MarktpreisKategorie.BAUWERKE) {
                            val gruppen = listOf(
                                "Land" to Wirtschaftsregionen.entries.map { "bauwerk:${it.str}" }.toSet(),
                                "Ecken" to Verwaltungsstandort.entries.map { "bauwerk:${it.str}" }.toSet(),
                                "Linien" to Handelslinie.entries.map { "bauwerk:${it.str}" }.toSet(),
                            )
                            gruppen.forEach { (titel, ids) ->
                                Text(
                                    text = titel,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                                )
                                UmschaltbareDiagrammLegende(
                                    eintraege = legende.filter { eintrag -> eintrag.id in ids },
                                    status = legendenStatus,
                                )
                            }
                        } else {
                            UmschaltbareDiagrammLegende(
                                eintraege = legende,
                                status = legendenStatus,
                                beiKlick = if (marktpreisKategorie == MarktpreisKategorie.WARENKORB) {
                                    { eintrag ->
                                        if (eintrag.id == "warenkorb") {
                                            warenkorbDialogOffen = true
                                        } else {
                                            legendenStatus.umschalten(eintrag.id)
                                        }
                                    }
                                } else {
                                    null
                                },
                                beiLangemKlick = if (marktpreisKategorie == MarktpreisKategorie.WARENKORB) {
                                    { eintrag ->
                                        if (eintrag.id == "warenkorb") {
                                            warenkorbDialogOffen = true
                                        } else {
                                            legendenStatus.nurAnzeigen(eintrag.id)
                                        }
                                    }
                                } else {
                                    null
                                },
                                klickBeschreibung = if (
                                    marktpreisKategorie == MarktpreisKategorie.WARENKORB
                                ) {
                                    "Warenkorb bearbeiten oder Datenreihe umschalten"
                                } else {
                                    "Datenreihe ein- oder ausblenden"
                                },
                                langerKlickBeschreibung = if (
                                    marktpreisKategorie == MarktpreisKategorie.WARENKORB
                                ) {
                                    "Warenkorb bearbeiten oder Datenreihe einzeln anzeigen"
                                } else {
                                    "Nur diese Datenreihe anzeigen"
                                },
                            )
                        }
                        if (marktpreisKategorie == MarktpreisKategorie.HANDELSDIFFERENZ) {
                            Text(
                                text = "Spieler",
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                            UmschaltbareDiagrammLegende(
                                eintraege = spielerLegende,
                                status = spielerLegendenStatus,
                                beiKlick = { eintrag ->
                                    spielerLegendenStatus.nurAnzeigen(eintrag.id)
                                },
                                beiLangemKlick = { eintrag ->
                                    spielerLegendenStatus.nurAnzeigen(eintrag.id)
                                },
                                klickBeschreibung = "Spieler auswählen",
                                langerKlickBeschreibung = "Spieler auswählen",
                            )
                        }
                    }
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

        if (spiel.marktpreise.isNotEmpty()) {
            val spaltenBreite = 7 * 45.dp
            VerticalGrid(
                columns = SimpleGridCells.Adaptive(spaltenBreite),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                Bauteil.entries.forEach { bauwerk ->
                    zeigeBauteilPreis(
                        bauteil = bauwerk,
                        hAnzahl = 7,
                        modifier = ModiPad5,
                        marktpreise = spiel.aktuelleBauwerkBewertungspreise,
                        nicht_null_preise = false,
                        nicht_null_zeilen = false,
                        beiKlick = {},
                    )
                }
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
        }
    }

    if (warenkorbDialogOffen) {
        WarenkorbBearbeitenDialog(
            warenkorb = spiel.warenkorb,
            beiAbbruch = { warenkorbDialogOffen = false },
            beiSpeichern = { neuerWarenkorb ->
                onWarenkorbAendern(neuerWarenkorb)
                warenkorbDialogOffen = false
            },
        )
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
        zeigeMarktplatz(spiel)
    }
}
