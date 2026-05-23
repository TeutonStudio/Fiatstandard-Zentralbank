package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.ui.ModiPad5


@Composable
fun ZinsEingabe(
    label: String = "Zinssatz",
    eingabe: MutableFloatState,
    breite_fraction: Float = 1f,
) {
    OutlinedTextField(
        value = eingabe.floatValue.toString(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { neuerPreis: String -> neuerPreis.toFloatOrNull()?.let { eingabe.floatValue = it } },
        label = { Text(label) },
        modifier = ModiPad5.fillMaxWidth(breite_fraction),
    )
}


@Preview
@Composable
private fun PreviewTextFelder() {
    val input = remember { mutableFloatStateOf(0f) }
    ZinsEingabe(
        label = "Test label",
        eingabe = input,
    )
}