package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.datenbank.farbe
import de.teutonstudio.zentralbank.schnittstelle.ModiPad10
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.TextCard
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeBauteil
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe


@Composable
fun definiereBauteilMenge(
    bauteil: Bauteil,
    anzahl: MutableIntState,
) {
    val minimum = if (bauteil == Verwaltungsstandort.HAUPTBAHNHOF) 1 else 0
    val maximum = if (bauteil == Verwaltungsstandort.HAUPTBAHNHOF) 1 else Int.MAX_VALUE
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.minus),
            contentDescription = "Aus dem Warenkorb entnehmen",
            modifier = ModiPad5.clickable(enabled = anzahl.intValue > minimum) {
                anzahl.intValue -= 1
            },
        )
        Image(
            painter = painterResource(R.drawable.plus),
            contentDescription = "Dem Warenkorb hinzufügen",
            modifier = ModiPad5.clickable(enabled = anzahl.intValue < maximum) {
                anzahl.intValue += 1
            },
        )
        Box(ModiPad10) { Text(text = "${anzahl.intValue}") }
        zeigeBauteil(bauteil, 25.sp, 36.dp, modifier = ModiPad5)
    }
}

private val einträge = mapOf(
    "Handelslinien" to Handelslinie.entries as Iterable<Bauteil>,
    "Verwaltungsstandorte" to Verwaltungsstandort.entries as Iterable<Bauteil>,
    "Wirtschaftsregionen" to Wirtschaftsregionen.entries as Iterable<Bauteil>,
)
@Composable
fun definiereBauteile(
    fürWenn: String = "",
    inhalt: MutableMap<Bauteil, Int> = remember { mutableStateMapOf() },
) {
    Column(ModiPad5, horizontalAlignment = Alignment.CenterHorizontally) {
        val str = "Bauteile $fürWenn"
        TextCard(text = str,fontSize = 40.sp) {}
        Card(modifier = ModiPad5) { LazyColumn(
            horizontalAlignment = Alignment.Start,
            modifier = ModiPad10
        ) {
            einträge.forEach { (kategorie,liste) ->
                item { Column() {
                    val ausgeklappt = remember { mutableStateOf(true) }
                    Row(ModiPad5.clickable { ausgeklappt.value = !ausgeklappt.value }) {
                        val pfad = if (ausgeklappt.value) R.drawable.empty else R.drawable.empty // TODO
                        Image(painter = painterResource(pfad), contentDescription = null)
                        Text(kategorie)
                    }
                    if (ausgeklappt.value) { liste.forEach {
                        key(fürWenn) {
                            val menge = remember { mutableIntStateOf(inhalt[it] ?: 0) }
                            LaunchedEffect(menge.intValue) { inhalt[it] = menge.intValue }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = it.farbe,
                                    contentColor = it.farbe.lesbareSchriftfarbe(),
                                ),
                            ) {
                                definiereBauteilMenge(it, menge)
                            }
                        }
                    } }
                } }
            }
        } }
    }
}


@Preview
@Composable
private fun WarenkorbPreview() {
    val inhalt = mutableMapOf<Bauteil,Int>(
        Handelslinie.LAND to 6,
        Wirtschaftsregionen.GESCHÄFTSBANK to 1,
    )
    Column() {
        definiereBauteile("",inhalt)
    }
}
