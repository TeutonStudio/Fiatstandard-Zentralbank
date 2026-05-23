package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.ausgabe.zeigeHandbuch


@Composable
fun SteuerContainer( hatZurückWeiter: Boolean = true,
    darfWeiter: State<Boolean> = remember { mutableStateOf(true) }, beiWeiter: () -> Unit = {},
    darfZurück: State<Boolean> = remember { mutableStateOf(true) }, beiZurück: () -> Unit = {},
    hatLöschen: State<Boolean> = remember { mutableStateOf(false) }, beiLöschen: () -> Unit = {},
    inhalt: @Composable () -> Unit,
) {
    val schriftzug = @Composable { str: String -> Box(modifier = ModiPad10, contentAlignment = Alignment.Center) { Text(text = str, fontSize = 30.sp, textAlign = TextAlign.Center) } }
    var zeigeAnleitung by remember { mutableStateOf(false) }
    val schalter = { zeigeAnleitung = !zeigeAnleitung }
    if (zeigeAnleitung) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            zeigeHandbuch(Modifier.weight(1f))
            Card(modifier = ModiPad5, onClick = schalter, enabled = darfZurück.value) { schriftzug("Schließen") }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f)) { inhalt() }
            Row(
                modifier = ModiPad5.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val onMitte = { if (hatLöschen.value) beiLöschen() else schalter() }
                val size = 75.dp
                val modi = if (hatLöschen.value) ModiPad5 else ModiPad5.size(75.dp)
                if (hatZurückWeiter) Card(modifier = ModiPad5, onClick = beiZurück, enabled = darfZurück.value) { schriftzug("Zurück") }
                Card(modifier = modi, onClick = onMitte) { if (hatLöschen.value) { schriftzug("Löschen")
                } else { Image(painter = painterResource(R.drawable.anleitung), modifier = ModiPad5, contentDescription = null) } }
                if (hatZurückWeiter) Card(modifier = ModiPad5, onClick = beiWeiter, enabled = darfWeiter.value) { schriftzug("Weiter") }
            }
        }
    }
}

@Preview
@Composable
private fun SteuerContainerPreview() {
    SteuerContainer(true,remember { mutableStateOf(true) },{}, remember { mutableStateOf(true) },{}, remember { mutableStateOf(true) }, {}) { }
}