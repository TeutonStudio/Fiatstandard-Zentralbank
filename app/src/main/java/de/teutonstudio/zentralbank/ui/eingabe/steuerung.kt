package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.ausgabe.zeigeHandbuch
import de.teutonstudio.zentralbank.ui.isLandscape


@Composable
fun Titel(
    beiKlick: () -> Unit = {},
    beiWeiter: (() -> Unit)? = null,
    beiZurück: (() -> Unit)? = null,
    beiLöschen: (() -> Unit)? = null,
    anleitung: State<Boolean> = remember { mutableStateOf(true) },
    inhalt: @Composable BoxScope.() -> Unit
) {
    val schriftzug = @Composable { str: String, onClick: () -> Unit -> Card(modifier = ModiPad5, onClick = onClick) { Text(text = str, modifier = ModiPad5, fontSize = 30.sp, textAlign = TextAlign.Center) } }
    var zeigeAnleitung by remember { mutableStateOf(false) }
    val Modi = { it: PaddingValues -> Modifier
        .fillMaxSize()
        .padding(it)
        .consumeWindowInsets(it) }
    val schalter = { zeigeAnleitung = !zeigeAnleitung }
    if (zeigeAnleitung) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            zeigeHandbuch(Modifier.fillMaxSize())
            schriftzug("Schließen",schalter)
            /*Card(
                modifier = ModiPad5.align(Alignment.BottomCenter).zIndex(1f),
                onClick = schalter,
                enabled = true
            ) {  }*/
        }
    } else {
        Scaffold(
            modifier = ModiPad5.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Card(modifier = ModiPad15.fillMaxWidth(), onClick = beiKlick) {
                    val text = if (LocalConfiguration.current.isLandscape()) "Fiatreich Zentralbank" else "FZB"
                    Text(text,ModiPad15.fillMaxWidth(),Color.Unspecified,null)
                }
            },
            bottomBar = {
                Row(modifier = ModiPad5.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (beiZurück != null) schriftzug("Zurück",beiZurück)
                    if (beiLöschen != null) schriftzug("Löschen",beiLöschen)
                    if (beiWeiter != null) schriftzug("Weiter",beiWeiter)
                }
            },
            floatingActionButton = { if (!zeigeAnleitung && anleitung.value) {
                FloatingActionButton(onClick = schalter, modifier = Modifier) {
                    Icon(painterResource(R.drawable.anleitung),null,null,ModiPad5)
                }
            } }
        ) { Box(modifier = Modi(it), contentAlignment = Alignment.Center, content = inhalt) }
    }
}

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
    if (zeigeAnleitung) { Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        zeigeHandbuch(Modifier.fillMaxSize())
        Card(
            modifier = ModiPad5
                .align(Alignment.BottomCenter)
                .zIndex(1f),
            onClick = schalter,
            enabled = true
        ) { schriftzug("Schließen") }
    } } } else { Column(
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
    } }
}

@Preview
@Composable
private fun SteuerContainerPreview() {
    Titel({}) { }
    // SteuerContainer(true,remember { mutableStateOf(true) },{}, remember { mutableStateOf(true) },{}, remember { mutableStateOf(true) }, {}) { }
}