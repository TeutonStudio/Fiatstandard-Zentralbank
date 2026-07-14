package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.schnittstelle.GridByOrientation
import de.teutonstudio.zentralbank.schnittstelle.TextCard
import de.teutonstudio.zentralbank.schnittstelle.eingabe.ImageCard
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel

@OptIn(ExperimentalGridApi::class)
@Composable
fun Spielmenü(
    beiVermogenSaldo: () -> Unit,
    beiSchuldenSaldo: () -> Unit,
    beiMarktSaldo: () -> Unit,
    beiAuslandSaldo: () -> Unit,
    beiHandel: () -> Unit,
    beiAnleihe: () -> Unit,
    beiNaechstemZugabschnitt: () -> Unit,
    zugText: String = "Kein Zug aktiv",
) {
    Titel(anleitung = remember { mutableStateOf(true) }) {
        GridByOrientation() { idx: Int, modifier -> when(idx) {
            0 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.saldo, beiKlick = beiVermogenSaldo)
            1 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.debt, beiKlick = beiSchuldenSaldo)
            2 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.market, beiKlick = beiMarktSaldo)
            3 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.foreign, beiKlick = beiAuslandSaldo)
            4 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.handel, beiKlick = beiHandel)
            5 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.anleihe, beiKlick = beiAnleihe)
            6 -> TextCard(
                zugText,
                modifier(null),
                fillMaxWidth = true,
                beiKlick = beiNaechstemZugabschnitt,
            )
        } }
    }
}

@Preview(

)
@Composable
private fun SpielmenüPreview() {
    Column() {
        Spielmenü({},{},{},{},{},{},{})
    }
}
