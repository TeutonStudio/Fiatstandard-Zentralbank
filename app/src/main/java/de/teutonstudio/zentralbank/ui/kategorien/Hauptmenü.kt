package de.teutonstudio.zentralbank.ui.kategorien

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.ui.RowOrColumnByOrientation
import de.teutonstudio.zentralbank.ui.eingabe.ImageCard


/*
@Composable
fun SpielCard(text: String, picture: Int, beiKlick: () -> Unit ) {
    Card( modifier = Modifier.padding(5.dp), onClick = beiKlick ) {
        Column( horizontalAlignment = Alignment.CenterHorizontally ) {
            Image(painter = painterResource(id = picture), contentDescription = null, modifier = Modifier.padding(5.dp))
            Text(text = text)
        }
    }
}
*/


@Composable
fun Hauptmenü(beiNeu: () -> Unit, beiLade: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RowOrColumnByOrientation(
            {},
            { ImageCard(bild_index = R.drawable.newgameicon, bild_label = "Neues Spiel", beiKlick = beiNeu ) },
            { ImageCard(bild_index = R.drawable.loadgameicon, bild_label = "Spiel Laden", beiKlick = beiLade ) },
        )
    }
}


@Preview()
@Composable
private fun HauptmenüPreview() {
    Column() {
        Hauptmenü({},{})
    }
}