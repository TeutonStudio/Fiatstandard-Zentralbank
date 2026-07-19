package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SpielerPasswortDialog(
    spielerNamen: List<String>,
    beiAbbruch: () -> Unit,
    beiBestaetigen: (Map<String, String>) -> Boolean,
) {
    val eingaben = remember(spielerNamen) {
        mutableStateMapOf<String, String>().apply {
            spielerNamen.forEach { name -> put(name, "") }
        }
    }
    var fehler by remember(spielerNamen) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text("Willenserklärung bestätigen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Die beteiligten Spieler bestätigen die Aktion mit ihrem Passwort.")
                spielerNamen.forEach { name ->
                    OutlinedTextField(
                        value = eingaben[name].orEmpty(),
                        onValueChange = {
                            eingaben[name] = it
                            fehler = false
                        },
                        label = { Text("Passwort von $name") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = fehler,
                    )
                }
                if (fehler) Text("Mindestens ein Passwort ist falsch.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!beiBestaetigen(eingaben.toMap())) fehler = true
                },
                enabled = eingaben.values.all(String::isNotBlank),
            ) {
                Text("Bestätigen")
            }
        },
        dismissButton = {
            TextButton(onClick = beiAbbruch) { Text("Abbrechen") }
        },
    )
}
