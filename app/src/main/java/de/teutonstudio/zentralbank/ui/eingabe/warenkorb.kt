package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.TextCard
import de.teutonstudio.zentralbank.ui.ausgabe.zeigeRohstoff

@Composable
fun definiereRohstoffMenge(
    rohstoff: Rohstoffe,
    anzahl: MutableIntState,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.minus),
            contentDescription = "Aus dem Warenkorb entnehmen",
            modifier = ModiPad5.clickable { anzahl.intValue -= 1 }
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
) {
    Column(ModiPad5, horizontalAlignment = Alignment.CenterHorizontally) {
        TextCard(text = "Warenkorb",fontSize = 40.sp) {}
        Card(modifier = ModiPad5) { LazyColumn(
            horizontalAlignment = Alignment.Start,
            modifier = ModiPad10
        ) { Rohstoffe.entries.forEach { item {
            val menge = remember { mutableIntStateOf(inhalt[it]?:0) }
            LaunchedEffect(menge.intValue) { inhalt[it] = menge.intValue }
            definiereRohstoffMenge(it,menge)
        } } } }
    }
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
