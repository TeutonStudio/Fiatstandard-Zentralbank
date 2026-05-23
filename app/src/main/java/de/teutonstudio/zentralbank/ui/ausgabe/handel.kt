package de.teutonstudio.zentralbank.ui.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.cheonjaeung.compose.grid.HorizontalGrid
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.HandelsDaten
import de.teutonstudio.zentralbank.ui.ModiPad5

@Composable
fun ZeigeHandel(aktuelleRunde: Int, siederListe: List<String>, handelsDaten: HandelsDaten) {
    Card { Row(verticalAlignment = Alignment.CenterVertically,modifier = ModiPad5) {
/*        val saldoWährung = handelsDaten.erhalteSiedlerWährungsSaldo()
        val saldoRohstoff = handelsDaten.erhalteSiedlerRohstoffSaldo()
        val itemModifier = ModiPad5.wrapContentHeight()

        HorizontalGrid(SimpleGridCells.Fixed(3), modifier = Modifier.wrapContentSize()) {
            Image(painter = painterResource(id = R.drawable.handel), contentDescription = null, modifier = itemModifier.span(3))
            listOf(handelsDaten.erhalteKäufer(),handelsDaten.erhalteVerkäufer()).forEach {
                Text(text = it, modifier = itemModifier.span(1))
                Text(text = saldoWährung[it]!!.erhalteString, modifier = itemModifier.span(1))
                Row(modifier = itemModifier.span(1)) {
                    Text(saldoRohstoff[it].toString())
                    Spacer(modifier = ModiPad5)
                    Image(painter = resourceIcon(handelsDaten.erhalteRohstoff()), contentDescription = null, modifier = Modifier)
                }
            }
        } */
    } }
}


@Preview
@Composable
private fun PreviewHandel() {
    val siedlerListe = listOf("Spieler 1","Spieler 2","Spieler 3")
    // val handelsDaten = HandelsDaten(-1,5,"Spieler 3" to "Spieler 2",Rohstoffe.ZIEGEL to 3,Zahlungsmittel(280))
    // ZeigeHandel(7,siedlerListe,handelsDaten)
}