package de.teutonstudio.zentralbank.ui.kategorien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.ui.FlowByOrientation
import de.teutonstudio.zentralbank.ui.GridByOrientation
import de.teutonstudio.zentralbank.ui.ModiPad10
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.TextCard
import de.teutonstudio.zentralbank.ui.eingabe.ImageCard
import de.teutonstudio.zentralbank.ui.eingabe.Titel

@OptIn(ExperimentalGridApi::class)
@Composable
fun Spielmenü(
    beiVermogenSaldo: () -> Unit,
    beiSchuldenSaldo: () -> Unit,
    beiMarktSaldo: () -> Unit,
    beiAuslandSaldo: () -> Unit,
    beiHandel: () -> Unit,
    beiAnleihe: () -> Unit,
    beiNächsteRunde: () -> Unit,
    zugText: String = "nächste Runde",
) {
    Titel() {
        GridByOrientation() { idx: Int, modifier -> when(idx) {
            0 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.saldo, beiKlick = beiVermogenSaldo)
            1 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.debt, beiKlick = beiSchuldenSaldo)
            2 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.market, beiKlick = beiMarktSaldo)
            3 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.foreign, beiKlick = beiAuslandSaldo)
            4 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.handel, beiKlick = beiHandel)
            5 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.anleihe, beiKlick = beiAnleihe)
            6 -> TextCard(zugText, modifier(null), fillMaxWidth = true,beiKlick=beiNächsteRunde)
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
