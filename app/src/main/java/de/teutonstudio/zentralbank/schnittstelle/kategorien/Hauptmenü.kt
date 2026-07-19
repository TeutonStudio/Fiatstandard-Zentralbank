package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.schnittstelle.eingabe.ImageCard
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel

@Composable
fun Hauptmenü(
    beiNeu: () -> Unit,
    beiLade: () -> Unit,
    beiLebensraeume: () -> Unit,
) {
    Titel {
        Column(
            modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImageCard(
                    bild_index = R.drawable.newgameicon,
                    modifier = Modifier.weight(1f),
                    bild_label = "Neues Spiel",
                    beiKlick = beiNeu,
                )
                ImageCard(
                    bild_index = R.drawable.loadgameicon,
                    modifier = Modifier.weight(1f),
                    bild_label = "Spiel laden",
                    beiKlick = beiLade,
                )
            }
            ImageCard(
                bild_index = R.drawable.lebensraeume,
                modifier = Modifier.fillMaxWidth(),
                bild_label = "Lebensräume verwalten",
                beiKlick = beiLebensraeume,
            )
        }
    }
}


@Preview()
@Composable
private fun HauptmenüPreview() {
    Column {
        Hauptmenü({}, {}, {})
    }
}
