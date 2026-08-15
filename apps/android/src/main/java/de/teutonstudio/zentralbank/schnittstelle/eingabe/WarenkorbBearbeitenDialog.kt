package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.datenbank.Rohstoffe

data class WarenkorbVorlage(
    val bezeichnung: String,
    val warenkorb: Map<Rohstoffe, Int>,
)

@Composable
fun WarenkorbBearbeitenDialog(
    warenkorb: Map<Rohstoffe, Int>,
    vorlagen: List<WarenkorbVorlage> = emptyList(),
    beiAbbruch: () -> Unit,
    beiSpeichern: (Map<Rohstoffe, Int>) -> Unit,
) {
    var mengen by remember(warenkorb) {
        mutableStateOf(
            Rohstoffe.entries.associateWith { rohstoff ->
                warenkorb[rohstoff]?.takeIf { menge -> menge > 0 }?.toString().orEmpty()
            },
        )
    }
    val istGueltig = mengen.values.all { text ->
        text.isBlank() || (text.all(Char::isDigit) && (text.toIntOrNull() ?: -1) >= 0)
    }
    val aktuellerWarenkorb = if (istGueltig) {
        Rohstoffe.entries.mapNotNull { rohstoff ->
            val menge = mengen[rohstoff]?.toIntOrNull() ?: 0
            if (menge > 0) rohstoff to menge else null
        }.toMap()
    } else {
        emptyMap()
    }

    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text("Warenkorb bearbeiten") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (vorlagen.isNotEmpty()) {
                    Text("Vordefinierte Warenkörbe")
                    vorlagen.forEach { vorlage ->
                        OutlinedButton(
                            onClick = {
                                mengen = Rohstoffe.entries.associateWith { rohstoff ->
                                    vorlage.warenkorb[rohstoff]
                                        ?.takeIf { menge -> menge > 0 }
                                        ?.toString()
                                        .orEmpty()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(vorlage.bezeichnung)
                        }
                    }
                    Text(
                        "Die Vorlage füllt nur die Mengen. Änderungen werden erst mit Speichern übernommen.",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                Rohstoffe.entries.forEach { rohstoff ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = rohstoff.str,
                            modifier = Modifier.weight(1f).padding(top = 16.dp),
                        )
                        OutlinedTextField(
                            value = mengen[rohstoff].orEmpty(),
                            onValueChange = { neu ->
                                if (neu.isEmpty() || neu.all(Char::isDigit)) {
                                    mengen = mengen + (rohstoff to neu)
                                }
                            },
                            label = { Text("Gewicht") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = istGueltig && aktuellerWarenkorb.isNotEmpty(),
                onClick = { beiSpeichern(aktuellerWarenkorb) },
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = beiAbbruch) {
                Text("Abbrechen")
            }
        },
    )
}
