package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.ui.ModiPad5

@Composable
fun ZahlungsmittelEingabe(
    label: String = "Warenpreis",
    eingabe: MutableIntState, // TODO Zahlungsmittel
) {
    OutlinedTextField(
        value = eingabe.intValue.toString(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { neuerPreis: String -> neuerPreis.toIntOrNull()?.let { eingabe.intValue = it } },
        label = { Text(label) },
        modifier = ModiPad5,
    )
}


@Preview
@Composable
private fun PreviewTextFelder() {
    val input = remember { mutableIntStateOf(0) }
    ZahlungsmittelEingabe(
        label = "Test label",
        eingabe = input,
    )
}