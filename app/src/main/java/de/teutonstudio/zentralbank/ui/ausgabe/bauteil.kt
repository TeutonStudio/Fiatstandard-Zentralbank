package de.teutonstudio.zentralbank.ui.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.associateWith
import de.teutonstudio.zentralbank.datenbank.zuMark
import de.teutonstudio.zentralbank.datenbank.zuPreis
import de.teutonstudio.zentralbank.ui.LeftText
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.RightText
import de.teutonstudio.zentralbank.ui.markBy
import java.util.EnumMap


@Composable
fun zeigeBauteil(
    bauteil: Bauteil,
    fontsize: TextUnit = 10.sp,
    iconSize: Dp = 24.dp,
    text: Boolean = true,
    icon_rechts: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val index = if (icon_rechts) listOf(0, 1) else listOf(1, 0)

        index.forEach {
            if (it == 0 && text) {
                Text(
                    modifier = ModiPad5,
                    text = bauteil.str.uppercase(),
                    fontSize = fontsize
                )
            }

            if (it == 1) {
                Box(
                    modifier = Modifier.size(iconSize),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = bauteilIcon(bauteil),
                        contentDescription = bauteil.str.uppercase(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGridApi::class)
@Composable
fun zeigeBauteilPreis(
    bauteil: Bauteil, hAnzahl: Int,
    modifier: Modifier = ModiPad5,
    marktpreise: EnumMap<Rohstoffe, Zahlungsmittel>,
    nicht_null_preise: Boolean = false,
    nicht_null_zeilen: Boolean = false,
    beiKlick: (Bauteil) -> Unit
) {
    Card(modifier = modifier, onClick = { beiKlick(bauteil) }) {
        val inhalt = bauteil.kosten.filter { if (nicht_null_zeilen) true else it.value != 0 }.map { it.key to (it.value to marktpreise[it.key]!!.zuMark()) }.toMap()
        Grid({
            repeat(hAnzahl) { column(40.dp) }
            repeat(inhalt.size+1) { row(40.dp) }
            gap(5.dp)
        }, ModiPad5) {
            inhalt.forEach { (rohstoff,z) ->
                val (anzahl,preis) = z
                if (nicht_null_zeilen || anzahl != 0) {
                    RightText(text = anzahl.toString(), fontSize = 20.sp, modifier = ModiPad5.fillMaxSize())
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) { zeigeRohstoff(
                        rohstoff = rohstoff, iconSize = 36.dp, fontsize = 25.sp, text = false,
                    ) }
                    if ((nicht_null_preise && anzahl != 0) || !nicht_null_preise) {
                        Text(text = " zu je ",fontSize = 20.sp, modifier = ModiPad5.gridItem(columnSpan = 2))
                        RightText(text = preis, fontSize = 20.sp, modifier = ModiPad5.fillMaxSize(.9f).gridItem(columnSpan = hAnzahl-4))
                    } else { Box(modifier = Modifier) }
                }
            }
            Text(
                modifier = Modifier.padding(5.dp,0.dp).fillMaxSize().gridItem(columnSpan = hAnzahl),
                text = "${bauteil.str.replaceFirstChar{ it.uppercase() }}: ${bauteil.kosten.zuPreis(marktpreise).zuMark()}",
                textAlign = TextAlign.Center,
                fontSize = 25.sp,
            )
        }
    }
}


@Preview
@Composable
fun BauwerkPreview() {
    Column() {
        zeigeBauteilPreis(Handelslinie.LAND,7,Modifier,marktpreisListe) { }
    }
}


private val marktpreisListe = Rohstoffe.associateWith { Zahlungsmittel() }
