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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.ausgabe.zeigeRohstoff


@Composable
fun wähleRohstoff(
    gewählterRohstoff: MutableState<Rohstoffe?>,
    fontsize: TextUnit = 10.sp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier,
    istExpanded: Boolean = false,
) {
    val str = "Wähle Rohstoff"
    val expanded = remember { mutableStateOf(istExpanded) }
    Box(modifier = ModiPad5.clickable { expanded.value = !expanded.value }) {
        if (gewählterRohstoff.value != null) {
            zeigeRohstoff(gewählterRohstoff.value!!,fontsize,iconSize,modifier = modifier)
        } else { Text(str) }
    }
    DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }, modifier = Modifier ) {
        Rohstoffe.entries.forEach { rohstoff ->
            DropdownMenuItem( text = { zeigeRohstoff(rohstoff,fontsize,iconSize,modifier = modifier) }, onClick = {
                gewählterRohstoff.value = rohstoff
                expanded.value = false
            })
        }
        DropdownMenuItem( text = { Text(str) }, onClick = {
            gewählterRohstoff.value = null
            expanded.value = false
        })
    }
}

@Preview
@Composable
private fun RohstoffPreview() {
    val rohstoff = remember { mutableStateOf<Rohstoffe?>(null) }
    Column() {
        wähleRohstoff(rohstoff)
        wähleRohstoff(rohstoff, istExpanded = true)

    }
}