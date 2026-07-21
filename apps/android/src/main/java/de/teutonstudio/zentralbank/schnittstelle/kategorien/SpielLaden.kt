package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel

@Composable
fun SpielLaden(
    beiAbbruch: () -> Unit,
    beiLaden: (Long, () -> Unit) -> Unit,
    nachLaden: () -> Unit,
    spielstaende: List<SpielstandUebersicht>,
) {
    var spielstand by remember { mutableStateOf<SpielstandUebersicht?>(null) }
    val valideAuswahl = remember { derivedStateOf { spielstand != null } }
    Titel(
        beiZurück = beiAbbruch,
        beiWeiter = {
            if (valideAuswahl.value) {
                spielstand?.let { beiLaden(it.id, nachLaden) }
            }
        },
        anleitung = remember { mutableStateOf(false) }
    ) {
        SpielstandListe(
            spielstaende = spielstaende,
            auswahl = spielstand,
            beiAuswahl = { spielstand = it },
        )
    }
}

@Composable
fun SpielstaendeVerwalten(
    beiAbbruch: () -> Unit,
    beiLoeschen: (Long) -> Unit,
    spielstaende: List<SpielstandUebersicht>,
) {
    var spielstand by remember { mutableStateOf<SpielstandUebersicht?>(null) }
    var zuLoeschenderSpielstand by remember { mutableStateOf<SpielstandUebersicht?>(null) }
    Titel(
        beiLöschen = {
            zuLoeschenderSpielstand = spielstand?.takeIf { auswahl -> auswahl.id >= 0 }
        },
        beiZurück = beiAbbruch,
        anleitung = remember { mutableStateOf(false) },
    ) {
        SpielstandListe(
            spielstaende = spielstaende,
            auswahl = spielstand,
            beiAuswahl = { spielstand = it },
        )
    }

    zuLoeschenderSpielstand?.let { auswahl ->
        AlertDialog(
            onDismissRequest = { zuLoeschenderSpielstand = null },
            title = { Text("Spielstand löschen?") },
            text = { Text("Spielstand ${auswahl.id} wird dauerhaft gelöscht.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        beiLoeschen(auswahl.id)
                        spielstand = null
                        zuLoeschenderSpielstand = null
                    },
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { zuLoeschenderSpielstand = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

@Composable
private fun SpielstandListe(
    spielstaende: List<SpielstandUebersicht>,
    auswahl: SpielstandUebersicht?,
    beiAuswahl: (SpielstandUebersicht) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        spielstaende.forEach { daten ->
            item(key = daten.id) {
                Card(
                    modifier = ModiPad5,
                    onClick = { beiAuswahl(daten) },
                    colors = if (auswahl == daten) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Column(modifier = ModiPad5, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (daten.id == -1L) "Testspiel" else "Spielnummer: ${daten.id}",
                            fontSize = 25.sp,
                        )
                        Text(text = "Die Siedler: ${daten.spielerNamen.joinToString(", ")}")
                        Text(text = "Siedeln seit: ${daten.runde} Runden")
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun LoadGamePreview() {
    Column {
        SpielLaden({}, { _, _ -> }, {}, emptyList())
    }
}
