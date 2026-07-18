package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.schnittstelle.RowOrColumnByOrientation
import de.teutonstudio.zentralbank.schnittstelle.eingabe.ImageCard
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel

@Composable
fun Hauptmenü(
    beiNeu: () -> Unit,
    beiLade: () -> Unit,
    beiLebensraeume: () -> Unit,
) {
    Titel { RowOrColumnByOrientation(
        {},
        { ImageCard(bild_index = R.drawable.newgameicon, bild_label = "Neues Spiel", beiKlick = beiNeu ) },
        { ImageCard(bild_index = R.drawable.loadgameicon, bild_label = "Spiel Laden", beiKlick = beiLade ) },
        {
            ImageCard(
                bild_index = R.drawable.lebensraeume,
                bild_label = "Lebensräume Verwalten",
                beiKlick = beiLebensraeume,
            )
        },
    ) }
}


@Preview()
@Composable
private fun HauptmenüPreview() {
    Column() {
        Hauptmenü({}, {}, {})
    }
}
