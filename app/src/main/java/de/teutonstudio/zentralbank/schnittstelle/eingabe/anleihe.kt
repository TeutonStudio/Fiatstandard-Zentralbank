package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.Anleihenhandel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.schnittstelle.ModiGray
import de.teutonstudio.zentralbank.schnittstelle.ModiPad10
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import kotlin.math.floor

/*@Composable
fun definiereRunde(neueRunde: (runde: Int) -> Unit) {
    val angezeigteRunde = remember { mutableStateOf("") }
    TextField( // Erhalt des Sondervermögens
        value = angezeigteRunde.value,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { neueRunde ->
            angezeigteRunde.value = neueRunde
            neueRunde.toIntOrNull()?.let { neueRunde(it) }
        },
        label = { Text("aktuelle Runde: ${angezeigteRunde.value}") },
        modifier = ModiPad5
    )
}*/

@Composable
fun wähleLaufzeit(
    laufzeit: MutableIntState,
    istAusgeklappt: Boolean = false
) {
    val expanded = remember { mutableStateOf<Boolean>(istAusgeklappt) }
    if (expanded.value) {
        VerticalGrid(
            columns = SimpleGridCells.Fixed(3),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
        ) {
            List(12) {it+1}.forEach { idx ->
                Card(modifier = ModiPad5.wrapContentSize().clickable {
                    laufzeit.intValue = idx
                    expanded.value = false
                }) { Text(
                    text = "$idx",
                    modifier = ModiGray.fillMaxSize(),
                    textAlign = TextAlign.Center,
                    fontSize = 25.sp
                ) }
            }
        }
    } else { Card(modifier = ModiPad5.clickable {
        expanded.value = true
    }) { Box(modifier = ModiGray) {
        Text(
            text = "Laufzeit: ${laufzeit.intValue}",
            modifier = ModiPad10.fillMaxWidth(.5f),
            fontSize = 25.sp, textAlign = TextAlign.Center,
        )
    } } }
}

@Composable
fun definiereAnleihe(
    spielerListe: List<Spieler>,
    onCreate: (anleihe: Anleihenhandel) -> Unit
) {
    val eingabeSondervermogen = remember { mutableIntStateOf(0) }
    val eingabeUnvermogen = remember { mutableIntStateOf(0) }
    val eingabeLaufzeit = remember { mutableIntStateOf(5) }
    val eingabeEmittent = remember { mutableStateOf("Emittent")}
    val eingabeErwerber = remember { mutableStateOf("Erwerber")}
    Card(modifier = Modifier.width(400.dp),) {
        Column  {
            Row( verticalAlignment = Alignment.CenterVertically ) {
                Image(painter = painterResource(id = R.drawable.anleihe), contentDescription = null, modifier = Modifier.clickable {
                    onCreate(Anleihenhandel(
                        spielerListe.find { it.name == eingabeEmittent.value }!!,
                        spielerListe.find { it.name == eingabeErwerber.value }!!,
                        Anleihe(
                            spielerListe.find { it.name == eingabeEmittent.value }!!,
                            eingabeSondervermogen.intValue.toZahlungsmittel(),
                            eingabeUnvermogen.intValue.toZahlungsmittel(),
                            eingabeLaufzeit.intValue,
                        ),
                        eingabeSondervermogen.intValue.toZahlungsmittel(),
                    ))
                } )
            }
            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    wähleSiedler(spielerListe.map { it.name },eingabeEmittent)
                    ZahlungsmittelEingabe("Sondervermögen",eingabeSondervermogen)
                } }
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    wähleSiedler(spielerListe.map { it.name },eingabeErwerber)
                    val zins = (floor(eingabeUnvermogen.intValue * 1000f / eingabeSondervermogen.intValue )/10f).toInt()
                    val label = "Unvermögen " + if (eingabeSondervermogen.intValue != 0) "${zins} % " else ""
                    ZahlungsmittelEingabe(label,eingabeUnvermogen)
                } }
                item { wähleLaufzeit(eingabeLaufzeit) }
            }
        }
    }
}

@Preview
@Composable
private fun AnleihePreview() {
    //definiereAnleihe(listOf("Siedler 1","Siedler 2","Siedler 3"),2) { }
}
