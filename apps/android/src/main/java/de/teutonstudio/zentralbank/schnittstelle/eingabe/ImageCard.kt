package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5


@Composable
fun ImageCard(
    bild_index: Int,
    modifier: Modifier = ModiPad5.wrapContentSize(),
    bild_label: String? = null,
    beiKlick: () -> Unit,
) {
    Card(modifier = modifier, onClick = beiKlick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = bild_index),
                contentDescription = bild_label,
                modifier = ModiPad5,
            )
            if (bild_label != null) {
                Text(text = bild_label, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}
