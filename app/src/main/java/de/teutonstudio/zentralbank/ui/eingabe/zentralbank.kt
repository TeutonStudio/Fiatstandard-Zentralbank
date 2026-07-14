package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.TextCard



@Composable
fun definiereLeitzinsatzZiele(
    inhalt: MutableList<Float> = remember { mutableListOf(0f,0f,0f,0f) },
) {
    Column(ModiPad5, horizontalAlignment = Alignment.CenterHorizontally) {
        TextCard(text = "Zentralbank",fontSize = 40.sp) {}
        Card(modifier = ModiPad5) { LazyColumn(
            horizontalAlignment = Alignment.Start,
            modifier = ModiPad10
        ) {
            item {
                val idx = 0
                val eingabe = remember { mutableFloatStateOf(inhalt[idx]) }
                LaunchedEffect(eingabe.floatValue) { inhalt[idx] = eingabe.floatValue }
                ZinsEingabe("Leitzinssatz",eingabe)
            }
            item {
                val idx = 1
                val eingabe = remember { mutableFloatStateOf(inhalt[idx]) }
                LaunchedEffect(eingabe.floatValue) { inhalt[idx] = eingabe.floatValue }
                ZinsEingabe("Inflationsziel",eingabe)
            }
            item {
                val idx = 2
                val eingabe = remember { mutableFloatStateOf(inhalt[idx]) }
                LaunchedEffect(eingabe.floatValue) { inhalt[idx] = eingabe.floatValue }
                ZinsEingabe("normale Abweichung",eingabe)
            }
            item {
                val idx = 3
                val eingabe = remember { mutableFloatStateOf(inhalt[idx]) }
                LaunchedEffect(eingabe.floatValue) { inhalt[idx] = eingabe.floatValue }
                ZinsEingabe("gravierende Abweichung",eingabe)
            }
        } }
    }
}


@Preview
@Composable
private fun WarenkorbPreview() {
    val inhalt = remember { mutableListOf(15f,2f, .5f, 2f) }
    Column() {
        definiereLeitzinsatzZiele(inhalt)
    }
}
