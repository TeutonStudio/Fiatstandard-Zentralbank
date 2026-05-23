package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.ui.ModiPad5


@Composable
fun definiereGanzzahl(label: String, angezeigteGanzzahl: MutableIntState, breite_fraction: Float = 1f) {
    TextField(
        modifier = ModiPad5.fillMaxWidth(breite_fraction),
        label = { Text(label) },
        value = angezeigteGanzzahl.intValue.toString(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { it.toIntOrNull()?.let { angezeigteGanzzahl.intValue = it } },
    )
}

@Preview
@Composable
private fun GanzzahlPreview() {
    Column() {
        definiereGanzzahl("Eingabe wert:", remember { mutableIntStateOf(0) })
    }
}