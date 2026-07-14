package de.teutonstudio.zentralbank.ui.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.ui.RowOrColumnByOrientation
import de.teutonstudio.zentralbank.ui.eingabe.ImageCard
import de.teutonstudio.zentralbank.ui.eingabe.Titel

@Composable
fun Hauptmenü(beiNeu: () -> Unit, beiLade: () -> Unit) {
    Titel { RowOrColumnByOrientation(
        {},
        { ImageCard(bild_index = R.drawable.newgameicon, bild_label = "Neues Spiel", beiKlick = beiNeu ) },
        { ImageCard(bild_index = R.drawable.loadgameicon, bild_label = "Spiel Laden", beiKlick = beiLade ) },
    ) }
}


@Preview()
@Composable
private fun HauptmenüPreview() {
    Column() {
        Hauptmenü({},{})
    }
}
