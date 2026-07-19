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
import androidx.compose.ui.draw.scale
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
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.schnittstelle.ModiPad5

internal const val ROHSTOFF_ICON_SKALIERUNG = 2.5f


fun Rohstoffe.zuPfad(): Int = rohstoffIconPfad()

fun Rohstoffe.rohstoffIconPfad(): Int = when (this) {
    Rohstoffe.NAHRUNG -> R.drawable.rohstoff_nahrung
    Rohstoffe.LEHM -> R.drawable.rohstoff_lehm
    Rohstoffe.ZIEGEL -> R.drawable.rohstoff_ziegel
    Rohstoffe.HOLZ -> R.drawable.rohstoff_holz
    Rohstoffe.ROHÖL -> R.drawable.rohstoff_rohoel
    Rohstoffe.SCHWERÖL -> R.drawable.rohstoff_schweroel
    Rohstoffe.DIESEL -> R.drawable.rohstoff_diesel
    Rohstoffe.KOHLE -> R.drawable.rohstoff_kohle
    Rohstoffe.STAHL -> R.drawable.rohstoff_stahl
    Rohstoffe.EISEN -> R.drawable.rohstoff_eisen
}

fun Rohstoff.rohstoffIconPfad(): Int = when (this) {
    Rohstoff.NAHRUNG -> R.drawable.rohstoff_nahrung
    Rohstoff.LEHM -> R.drawable.rohstoff_lehm
    Rohstoff.ZIEGEL -> R.drawable.rohstoff_ziegel
    Rohstoff.HOLZ -> R.drawable.rohstoff_holz
    Rohstoff.ROHOEL -> R.drawable.rohstoff_rohoel
    Rohstoff.SCHWEROEL -> R.drawable.rohstoff_schweroel
    Rohstoff.DIESEL -> R.drawable.rohstoff_diesel
    Rohstoff.KOHLE -> R.drawable.rohstoff_kohle
    Rohstoff.STAHL -> R.drawable.rohstoff_stahl
    Rohstoff.EISEN -> R.drawable.rohstoff_eisen
}

fun Bauteil.zuPfad(): Int = bauteilIconPfadOderNull() ?: R.drawable.leer

fun Bauteil.bauteilIconPfadOderNull(): Int? = when (this) {
    is Handelslinie -> handelslinieIconPfad()
    is Wirtschaftsregionen -> wirtschaftseinheitIconPfad()
    is Verwaltungsstandort -> verwaltungsstandortIconPfadOderNull()
}

fun Handelslinie.handelslinieIconPfad(): Int = when (this) {
    Handelslinie.LAND -> R.drawable.handelslinie_eisenbahnlinie
    Handelslinie.SEE -> R.drawable.handelslinie_frachtschiff
}

fun Wirtschaftsregionen.wirtschaftseinheitIconPfad(): Int = when (this) {
    Wirtschaftsregionen.GESCHÄFTSBANK -> R.drawable.wirtschaftseinheit_geschaeftsbank
    Wirtschaftsregionen.VIEHHOF -> R.drawable.wirtschaftseinheit_viehhof
    Wirtschaftsregionen.ANGLER -> R.drawable.leer
    Wirtschaftsregionen.ZIEGELBRENNER -> R.drawable.wirtschaftseinheit_ziegelbrenner
    Wirtschaftsregionen.LEHMINE -> R.drawable.wirtschaftseinheit_lehmmine
    Wirtschaftsregionen.FÖRSTER -> R.drawable.wirtschaftseinheit_foerster
    Wirtschaftsregionen.BOHRTURM -> R.drawable.wirtschaftseinheit_bohrturm
    Wirtschaftsregionen.RAFFINERIE -> R.drawable.wirtschaftseinheit_raffinerie
    Wirtschaftsregionen.SRAFINNERIE -> R.drawable.wirtschaftseinheit_synthetik_raffinerie
    Wirtschaftsregionen.KOHLEMINE -> R.drawable.wirtschaftseinheit_kohlemine
    Wirtschaftsregionen.STAHLFABRIK -> R.drawable.wirtschaftseinheit_stahlfabrik
    Wirtschaftsregionen.EISENMINE -> R.drawable.wirtschaftseinheit_eisenmine
}

fun Verwaltungsstandort.verwaltungsstandortIconPfadOderNull(): Int? = when (this) {
    Verwaltungsstandort.HAUPTBAHNHOF -> null
    Verwaltungsstandort.BAHNHOF -> R.drawable.verwaltungsstandort_bahnhof
    Verwaltungsstandort.GROSSBAHNHOF -> R.drawable.verwaltungsstandort_grossbahnhof
    Verwaltungsstandort.HAFEN -> R.drawable.verwaltungsstandort_hafen
    Verwaltungsstandort.GROSSHAFEN -> R.drawable.verwaltungsstandort_grosshafen
}

fun BauteilTyp.bauteilIconPfadOderNull(): Int? =
    handelslinieIconPfadOderNull()
        ?: verwaltungsstandortIconPfadOderNull()
        ?: wirtschaftseinheitIconPfadOderNull()

fun BauteilTyp.handelslinieIconPfadOderNull(): Int? = when (this) {
    BauteilTyp.EISENBAHNLINIE -> R.drawable.handelslinie_eisenbahnlinie
    BauteilTyp.FRACHTSCHIFF -> R.drawable.handelslinie_frachtschiff
    else -> null
}

fun BauteilTyp.wirtschaftseinheitIconPfadOderNull(): Int? = when (this) {
    BauteilTyp.GESCHAEFTSBANK -> R.drawable.wirtschaftseinheit_geschaeftsbank
    BauteilTyp.VIEHHOF -> R.drawable.wirtschaftseinheit_viehhof
    BauteilTyp.ANGLER -> R.drawable.leer
    BauteilTyp.ZIEGELBRENNER -> R.drawable.wirtschaftseinheit_ziegelbrenner
    BauteilTyp.LEHMINE -> R.drawable.wirtschaftseinheit_lehmmine
    BauteilTyp.FOERSTER -> R.drawable.wirtschaftseinheit_foerster
    BauteilTyp.BOHRTURM -> R.drawable.wirtschaftseinheit_bohrturm
    BauteilTyp.RAFFINERIE -> R.drawable.wirtschaftseinheit_raffinerie
    BauteilTyp.SYNTHETIK_RAFFINERIE -> R.drawable.wirtschaftseinheit_synthetik_raffinerie
    BauteilTyp.KOHLEMINE -> R.drawable.wirtschaftseinheit_kohlemine
    BauteilTyp.STAHLFABRIK -> R.drawable.wirtschaftseinheit_stahlfabrik
    BauteilTyp.EISENMINE -> R.drawable.wirtschaftseinheit_eisenmine
    else -> null
}

fun BauteilTyp.verwaltungsstandortIconPfadOderNull(): Int? = when (this) {
    BauteilTyp.BAHNHOF -> R.drawable.verwaltungsstandort_bahnhof
    BauteilTyp.GROSSBAHNHOF -> R.drawable.verwaltungsstandort_grossbahnhof
    BauteilTyp.HAFEN -> R.drawable.verwaltungsstandort_hafen
    BauteilTyp.GROSSHAFEN -> R.drawable.verwaltungsstandort_grosshafen
    else -> null
}

@Composable
fun Rohstoffe.zuPainter(): Painter = painterResource(this.zuPfad())

@Composable
fun Bauteil.zuPainter(): Painter = painterResource(this.zuPfad())


@Composable
fun bauteilIcon(bauteil: Bauteil): Painter {
    return painterResource(bauteil.zuPfad())
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
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(ROHSTOFF_ICON_SKALIERUNG),
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
