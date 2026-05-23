package de.teutonstudio.zentralbank.ui.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.SpielDaten
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.eingabe.SteuerContainer

@Composable
fun SpielLaden(
    beiAbbruch: () -> Unit,
    speicher: Map<SpielDaten, Pair<Int, List<String>>>,
    beiLöschen: (SpielDaten) -> Unit = {},
    beiLaden: (SpielDaten) -> Unit,
) {
    var spielstand by remember { mutableStateOf<SpielDaten?>(null) }
    val valideAuswahl = remember { derivedStateOf { spielstand != null } }
    SteuerContainer(
        hatLöschen = remember { derivedStateOf { spielstand != null } },
        beiLöschen = { beiLöschen(spielstand!!) },
        beiZurück = beiAbbruch,
        darfWeiter = valideAuswahl,
        beiWeiter = { spielstand?.let { beiLaden(it) } },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            speicher.forEach { daten, (laufzeit,spieler) ->
                item(key = daten.spielID) { Card(modifier = ModiPad5, onClick = { spielstand = daten }, colors = if (spielstand == daten) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) } else { CardDefaults.cardColors() }
                ) {
                    Column(modifier = ModiPad5, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Spielnummer: ${daten.spielID}",fontSize=25.sp)
                        Text(text = "Die Siedler: ${spieler.joinToString(", ")}")
                        Text(text = "Siedeln seit: $laufzeit Runden")
                    }
                } }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun LoadGamePreview() {
    val spiel = remember { TestSpiel }
    Column() {
        SpielLaden({},mapOf(),{}) {}
    }
}