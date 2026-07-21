package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.schnittstelle.ModiPad10
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.TextCard
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeRohstoff
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe
import de.teutonstudio.zentralbank.schnittstelle.farbe

@Composable
fun definiereRohstoffMenge(
    rohstoff: Rohstoffe,
    anzahl: MutableIntState,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.minus),
            contentDescription = "Aus dem Warenkorb entnehmen",
            modifier = ModiPad5.clickable {
                anzahl.intValue = (anzahl.intValue - 1).coerceAtLeast(0)
            }
        )
        Image(
            painter = painterResource(R.drawable.plus),
            contentDescription = "Dem Warenkorb hinzufügen",
            modifier = ModiPad5.clickable { anzahl.intValue += 1 }
        )
        Box(ModiPad10) { Text(text = "${anzahl.intValue}") }
        zeigeRohstoff(rohstoff, 25.sp, 36.dp, modifier = ModiPad5)
    }
}

@Composable
fun definiereWarenkorb(
    inhalt: MutableMap<Rohstoffe, Int> = remember { mutableStateMapOf<Rohstoffe, Int>() },
    titel: String = "Warenkorb",
) {
    Column(ModiPad5, horizontalAlignment = Alignment.CenterHorizontally) {
        TextCard(text = titel,fontSize = 40.sp) {}
        Card(modifier = ModiPad5) { LazyColumn(
            horizontalAlignment = Alignment.Start,
            modifier = ModiPad10
        ) { Rohstoffe.entries.forEach { item {
            val menge = remember { mutableIntStateOf(inhalt[it]?:0) }
            LaunchedEffect(menge.intValue) { inhalt[it] = menge.intValue }
            Card(
                modifier = ModiPad5,
                colors = CardDefaults.cardColors(
                    containerColor = it.farbe,
                    contentColor = it.farbe.lesbareSchriftfarbe(),
                ),
            ) {
                definiereRohstoffMenge(it,menge)
            }
        } } } }
    }
}

@Composable
fun WarenkorbBearbeitenDialog(
    warenkorb: Map<Rohstoffe, Int>,
    beiAbbruch: () -> Unit,
    beiSpeichern: (Map<Rohstoffe, Int>) -> Unit,
) {
    val inhalt = remember(warenkorb) {
        mutableStateMapOf<Rohstoffe, Int>().apply {
            Rohstoffe.entries.forEach { rohstoff ->
                this[rohstoff] = (warenkorb[rohstoff] ?: 0).coerceAtLeast(0)
            }
        }
    }

    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text("Warenkorb bearbeiten") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 560.dp)) {
                items(
                    items = Rohstoffe.entries,
                    key = { rohstoff -> rohstoff.name },
                ) { rohstoff ->
                    val anzahl = inhalt[rohstoff] ?: 0

                    Card(
                        modifier = ModiPad5,
                        colors = CardDefaults.cardColors(
                            containerColor = rohstoff.farbe,
                            contentColor = rohstoff.farbe.lesbareSchriftfarbe(),
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = ModiPad5,
                        ) {
                            IconButton(
                                onClick = {
                                    inhalt[rohstoff] = (anzahl - 1).coerceAtLeast(0)
                                },
                                enabled = anzahl > 0,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.minus),
                                    contentDescription = "${rohstoff.str} aus dem Warenkorb entnehmen",
                                )
                            }

                            Text(
                                text = anzahl.toString(),
                                modifier = Modifier.width(48.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 20.sp,
                            )

                            IconButton(
                                onClick = { inhalt[rohstoff] = anzahl + 1 },
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.plus),
                                    contentDescription = "${rohstoff.str} dem Warenkorb hinzufügen",
                                )
                            }

                            zeigeRohstoff(
                                rohstoff = rohstoff,
                                fontsize = 20.sp,
                                iconSize = 36.dp,
                                modifier = ModiPad5,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    beiSpeichern(inhalt.filterValues { anzahl -> anzahl > 0 })
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = beiAbbruch) {
                Text("Abbrechen")
            }
        },
    )
}


@Preview
@Composable
private fun WarenkorbPreview() {
    val inhalt = mutableMapOf<Rohstoffe,Int>(
        Rohstoffe.HOLZ to 5,
        Rohstoffe.ZIEGEL to 1,
    )
    Column() {
        definiereWarenkorb(inhalt)
    }
}
