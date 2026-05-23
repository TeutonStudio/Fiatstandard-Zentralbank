package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Ausland
import de.teutonstudio.zentralbank.datenbank.Geschäftsbank
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.TextCard
import java.util.stream.IntStream.range
import kotlin.math.max
import kotlin.streams.toList


@Composable
fun SpielerAnzahlAuswahl(
    maxAnzahl: Int = 7,
    anzahl: MutableIntState = remember { mutableIntStateOf(maxAnzahl) },
) {
    Row(modifier = ModiPad5.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    TextCard("Spieler Anzahl: ", darfKlick = false) { }
    LazyRow() { items(maxAnzahl+1) {
        if (it !in (0..2)) { TextCard(it.toString()) { anzahl.intValue = it } }
    } } }
}

@Composable
fun SpielerDaten(spieler: MutableState<Pair<String, Zahlungsmittel>>) {
    val eingabeBetrag = remember { mutableIntStateOf(spieler.value.second.toIntOderNull()) }
    LaunchedEffect(eingabeBetrag.intValue) { spieler.value = spieler.value.first to eingabeBetrag.intValue.toZahlungsmittel() }
    Card( modifier = ModiPad5) { Row(verticalAlignment = Alignment.CenterVertically, modifier = ModiPad5) {
        Image(painter = painterResource(id = R.drawable.siedler), contentDescription = null)
        Column() {
            TextField( // Name des Spielers
                value = spieler.value.first,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                onValueChange = { spieler.value = it to spieler.value.second },
                label = { Text("Name des Spielers") },
                modifier = ModiPad5,
            )
            ZahlungsmittelEingabe("Startguthaben des Spielers", eingabeBetrag)
        } }
    }
}

private fun spielerDaten(idx: Int): MutableState<Pair<String, Zahlungsmittel>> = mutableStateOf(Pair("Spieler $idx", Zahlungsmittel()))
@Composable
fun definiereSpieler(
    gültig: MutableState<Boolean>,
    spieler: MutableMap<String, Zahlungsmittel>,
) {
    val maxSpieler = 7
    val spielerAnzahl = remember { mutableIntStateOf(spieler.size) }
    val spielerWerte = remember { List(maxSpieler) {
        val liste = spieler.toList()
        if (it in liste.indices) mutableStateOf(liste[it])
        else spielerDaten(it+1)
    }.toMutableList() }
    val istGültig by remember {
        derivedStateOf {
            val namen = spielerWerte.take(spielerAnzahl.intValue).map { it.value.first.trim() }
            namen.all { it.isNotBlank() } && namen.size == namen.filter { it != Geschäftsbank.name && it != Ausland.name }.toSet().size
        }
    }
    LaunchedEffect(istGültig) {
        gültig.value = istGültig
    }
    LaunchedEffect(spielerWerte) {
        snapshotFlow { spielerWerte.take(spielerAnzahl.intValue).map { it.value } }.collect { werte ->
            spieler.clear()
            werte.forEach { spieler[it.first.trim()] = it.second }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SpielerAnzahlAuswahl(maxSpieler,spielerAnzahl)
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally) {
            items(spielerAnzahl.intValue) {
                SpielerDaten(spielerWerte[it])
            }
        }
    }
}

@Preview
@Composable
private fun SpielerPreview() {
    val gültig = remember { mutableStateOf(true) }
    val siedler = mutableMapOf<String, Zahlungsmittel>(
        "TestSpieler1" to 100.toZahlungsmittel(),
        "TestSpieler2" to 150.toZahlungsmittel(),
        "TestSpieler3" to 500.toZahlungsmittel(),
    )
    definiereSpieler(gültig,siedler)
}