package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
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
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.LineProvider.Companion.series
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.HandelsDaten
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.summeGeld
import de.teutonstudio.zentralbank.datenbank.toBauteilPreis
import de.teutonstudio.zentralbank.schnittstelle.DiagrammLegendenEintrag
import de.teutonstudio.zentralbank.schnittstelle.ModiPad15
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.UmschaltbareDiagrammLegende
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeBauteilPreis
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeRohstoff
import de.teutonstudio.zentralbank.schnittstelle.eingabe.WarenkorbBearbeitenDialog
import de.teutonstudio.zentralbank.schnittstelle.markBy
import de.teutonstudio.zentralbank.schnittstelle.rememberDiagrammLegendenStatus

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

private enum class MarktpreisKategorie(val titel: String) {
    ROHSTOFFE("Alle Rohstoffe"),
    BAUWERKE("Alle Bauwerke"),
    WARENKORB("Warenkorb"),
}

private val rohstoffMarktfarben = mapOf(
    Rohstoffe.NAHRUNG to Color(0xFF8A6D8F),
    Rohstoffe.LEHM to Color(0xFFB58B5A),
    Rohstoffe.ZIEGEL to Color(0xFFA75D5D),
    Rohstoffe.HOLZ to Color(0xFF6F8061),
    Rohstoffe.ROHÖL to Color(0xFF4F565D),
    Rohstoffe.SCHWERÖL to Color(0xFF6D5A70),
    Rohstoffe.DIESEL to Color(0xFF8A7A45),
    Rohstoffe.KOHLE to Color(0xFF343A40),
    Rohstoffe.STAHL to Color(0xFF718096),
    Rohstoffe.EISEN to Color(0xFF8C6F63),
)

private val bauwerkMarktfarben = Bauteil.entries.toList().zip(
    listOf(
        Color(0xFF527681),
        Color(0xFF8F6D56),
        Color(0xFF687B55),
        Color(0xFF946570),
        Color(0xFF5F7392),
        Color(0xFF88764F),
        Color(0xFF6B6288),
        Color(0xFF47756C),
        Color(0xFF8A604D),
        Color(0xFF5F784F),
        Color(0xFF786183),
        Color(0xFF466D87),
        Color(0xFF8B7943),
        Color(0xFF4D7164),
        Color(0xFF865B63),
        Color(0xFF666A7B),
        Color(0xFF796149),
    )
).toMap()

private val warenkorbMarktfarbe = Color(0xFF75658A)

private fun List<Map<Rohstoffe, Zahlungsmittel>>.zuWarenkorbpreisen(
    warenkorb: Map<Rohstoffe, Int>,
): List<Map<MarktpreisKategorie, Zahlungsmittel>> = map { preise ->
    mapOf(
        MarktpreisKategorie.WARENKORB to warenkorb.entries.summeGeld { (rohstoff, anzahl) ->
            (preise[rohstoff] ?: Zahlungsmittel()) * anzahl
        }
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
fun zeigeMarktplatz(
    spiel: Spiel,
    onTrade: (HandelsDaten) -> Unit = {},
    onWarenkorbAendern: (Map<Rohstoffe, Int>) -> Unit = {},
) {
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
                val bauwerke = Bauteil.entries.toList()
                val warenkorbpreise = remember(spiel.marktpreise, spiel.warenkorb) {
                    spiel.marktpreise.zuWarenkorbpreisen(spiel.warenkorb)
                }

                val legende = when (marktpreisKategorie) {
                    MarktpreisKategorie.ROHSTOFFE -> rohstoffe.map { rohstoff ->
                        DiagrammLegendenEintrag(
                            id = "rohstoff:${rohstoff.name}",
                            bezeichnung = rohstoff.str,
                            farbe = rohstoffMarktfarben.getValue(rohstoff),
                        )
                    }

                    MarktpreisKategorie.BAUWERKE -> bauwerke.map { bauwerk ->
                        DiagrammLegendenEintrag(
                            id = "bauwerk:${bauwerk.str}",
                            bezeichnung = bauwerk.str,
                            farbe = bauwerkMarktfarben.getValue(bauwerk),
                        )
                    }

                    MarktpreisKategorie.WARENKORB -> listOf(
                        DiagrammLegendenEintrag(
                            id = "warenkorb",
                            bezeichnung = "Warenkorb",
                            farbe = warenkorbMarktfarbe,
                        )
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

                        MarktpreisKategorie.BAUWERKE -> spiel.marktpreise.toBauteilPreis().toChart(
                            sichtbareIndizes.map(bauwerke::get)
                        )

                        MarktpreisKategorie.WARENKORB -> warenkorbpreise.toChart(
                            listOf(MarktpreisKategorie.WARENKORB)
                        )
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
                        Row(
                            modifier = ModiPad5,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            CartesianChartHost(
                                modifier = bilanzModifier,
                                chart = rememberCartesianChart(
                                    rememberLineCartesianLayer(
                                        lineProvider = series(*linien.toTypedArray())
                                    ),
                                    endAxis = VerticalAxis.rememberEnd(),
                                    bottomAxis = HorizontalAxis.rememberBottom(),
                                ),
                                model = chartModel,
                                scrollState = rememberVicoScrollState(),
                                zoomState = rememberVicoZoomState(
                                    initialZoom = remember { Zoom.Content }
                                )
                            )
                        }

                        UmschaltbareDiagrammLegende(
                            eintraege = legende,
                            status = legendenStatus,
                            beiKlick = if (marktpreisKategorie == MarktpreisKategorie.WARENKORB) {
                                { _ -> warenkorbDialogOffen = true }
                            } else {
                                null
                            },
                            beiLangemKlick = if (marktpreisKategorie == MarktpreisKategorie.WARENKORB) {
                                { _ -> warenkorbDialogOffen = true }
                            } else {
                                null
                            },
                            klickBeschreibung = if (
                                marktpreisKategorie == MarktpreisKategorie.WARENKORB
                            ) {
                                "Warenkorb bearbeiten"
                            } else {
                                "Datenreihe ein- oder ausblenden"
                            },
                            langerKlickBeschreibung = if (
                                marktpreisKategorie == MarktpreisKategorie.WARENKORB
                            ) {
                                "Warenkorb bearbeiten"
                            } else {
                                "Nur diese Datenreihe anzeigen"
                            },
                        )
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
                        marktpreise = spiel.aktuelleMarktpreise,
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
//        zeigeHafenPreis("3 : 1 Hafen",marketprices,{})
        /*
        zeigeWarenkorb(
            marketprices,
            playerSaldoTestList,
        ) { }*/
    }
}
