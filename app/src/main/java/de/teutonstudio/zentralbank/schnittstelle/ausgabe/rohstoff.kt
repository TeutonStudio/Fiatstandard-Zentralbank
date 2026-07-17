package de.teutonstudio.zentralbank.schnittstelle.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5


fun Rohstoffe.zuPfad(): Int = when (this) {
    Rohstoffe.NAHRUNG -> R.drawable.nahrung
    Rohstoffe.LEHM -> R.drawable.lehm
    Rohstoffe.ZIEGEL -> R.drawable.ziegel
    Rohstoffe.HOLZ -> R.drawable.holz
    Rohstoffe.ROHÖL -> R.drawable.rohol
    Rohstoffe.SCHWERÖL -> R.drawable.schwerol
    Rohstoffe.DIESEL -> R.drawable.diesel
    Rohstoffe.KOHLE -> R.drawable.kohle
    Rohstoffe.STAHL -> R.drawable.stahl
    Rohstoffe.EISEN -> R.drawable.eisen
}
fun Bauteil.zuPfad(): Int = when (this) {
    else -> R.drawable.leer
}

@Composable
fun Rohstoffe.zuPainter(): Painter = painterResource(this.zuPfad())

@Composable
fun Bauteil.zuPainter(): Painter = painterResource(this.zuPfad())


@Composable
fun bauteilIcon(bauteil: Bauteil): Painter {
    return when (bauteil) {
        Handelslinie.LAND -> painterResource(R.drawable.empty) // TODO
        Handelslinie.SEE -> painterResource(R.drawable.empty) // TODO
        Verwaltungsstandort.HAUPTBAHNHOF -> painterResource(R.drawable.empty) // TODO
        Verwaltungsstandort.BAHNHOF -> painterResource(R.drawable.empty) // TODO
        Verwaltungsstandort.GROSSBAHNHOF -> painterResource(R.drawable.empty) // TODO
        Verwaltungsstandort.HAFEN -> painterResource(R.drawable.empty) // TODO
        Verwaltungsstandort.GROSSHAFEN -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.ZIEGELBRENNER -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.BOHRTURM -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.RAFFINERIE -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.EISENMINE -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.KOHLEMINE -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.LEHMINE -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.GESCHÄFTSBANK -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.FÖRSTER -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.SRAFINNERIE -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.STAHLFABRIK -> painterResource(R.drawable.empty) // TODO
        Wirtschaftsregionen.VIEHHOF -> painterResource(R.drawable.empty) // TODO
    }
}

@Composable
fun zeigeRohstoff(
    rohstoff: Rohstoffe,
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
                    text = rohstoff.str.uppercase(),
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
                        painter = rohstoff.zuPainter(),
                        contentDescription = rohstoff.str.uppercase(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RohstoffPreview() {
    Column() {
        zeigeRohstoff(Rohstoffe.NAHRUNG)
        zeigeRohstoff(Rohstoffe.LEHM)
        zeigeRohstoff(Rohstoffe.ZIEGEL)
        zeigeRohstoff(Rohstoffe.HOLZ)
        zeigeRohstoff(Rohstoffe.ROHÖL)
        zeigeRohstoff(Rohstoffe.SCHWERÖL)
        zeigeRohstoff(Rohstoffe.DIESEL)
        zeigeRohstoff(Rohstoffe.KOHLE)
        zeigeRohstoff(Rohstoffe.STAHL)
        zeigeRohstoff(Rohstoffe.EISEN)
        zeigeRohstoff(Rohstoffe.KOHLE)
    }
}
