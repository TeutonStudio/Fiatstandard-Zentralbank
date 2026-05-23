package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.ui.ModiPad15

@Composable
fun wähleSiedler(
    spielerListe: List<String>,
    gewählterSiedler: MutableState<String>,
    istExpanded: Boolean = false,
    aufgeklappt: MutableState<Boolean> = remember { mutableStateOf(istExpanded) }
) {
    Box(modifier = ModiPad15.clickable { aufgeklappt.value = !aufgeklappt.value }) {
        Text(text = gewählterSiedler.value, fontSize = 25.sp)
    }
    DropdownMenu(expanded = aufgeklappt.value, onDismissRequest = { aufgeklappt.value = false }, modifier = Modifier ) {
        spielerListe.forEach { DropdownMenuItem( text = { Text(it) }, onClick = {
            gewählterSiedler.value = it
            aufgeklappt.value = false
        } ) }
    }
}

@Preview
@Composable
private fun siedlerPreview() {
    val siedlerListe = listOf<String>("Siedler 1", "Siedler 2", "Siedler 3")
    val siedler = remember { mutableStateOf<String>("Siedler wählen") }
    Column() {
        wähleSiedler(siedlerListe,siedler)
        wähleSiedler(siedlerListe,siedler,true)

    }
}