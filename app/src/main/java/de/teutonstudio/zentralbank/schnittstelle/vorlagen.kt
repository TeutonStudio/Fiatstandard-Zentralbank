package de.teutonstudio.zentralbank.schnittstelle

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridConfigurationScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import kotlin.math.abs

public val ModiPad5 = Modifier.padding(5.dp)
public val ModiPad10 = Modifier.padding(10.dp)
public val ModiPad15 = Modifier.padding(15.dp)
public val ModiOrange = Modifier.background(Color(255, 165, 0))
public val ModiGray = Modifier.background(Color.LightGray)


@Composable
fun TextCard(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 25.sp, fillMaxWidth: Boolean = false, darfKlick: Boolean = true, beiKlick: () -> Unit) {
    val boxModifier = if (fillMaxWidth) ModiPad5.fillMaxWidth() else ModiPad5
    val inhalt = @Composable { Box(modifier=boxModifier,contentAlignment=Alignment.Center) { Text(text,fontSize=fontSize,textAlign=TextAlign.Center) } }
    if (darfKlick) { Card(onClick = beiKlick, modifier) { inhalt() } } else { Card(modifier) { inhalt() } }
}

public fun Configuration.isLandscape(): Boolean = orientation == Configuration.ORIENTATION_LANDSCAPE

@OptIn(ExperimentalGridApi::class)
@Composable
fun GridByOrientation(verteilungVertical: Int = 2, verteilungHorizontal: Int = 3, contents: @Composable (Int,(Modifier?) -> Modifier) -> Unit) {
    val isLandscape = LocalConfiguration.current.isLandscape() // or true
    fun config():  GridConfigurationScope.() -> Unit = run {({
        repeat(if(isLandscape) verteilungHorizontal else verteilungVertical) { column(100.dp) }
        repeat(if(isLandscape) verteilungVertical else verteilungHorizontal) { row(100.dp) }
        rowGap(5.dp)
        columnGap(5.dp)
    }) }
    fun idx(idx:Int): Int {
        if (isLandscape) {
            val permutation = listOf(0, 3, 5, 1, 2, 4, 6)
            return permutation.getOrElse(idx) {
                error("idx muss zwischen 0 und 6 liegen, nicht $idx")
            }
        }; return idx
    }
    Grid(config()) {
        contents(idx(0)) { it ?: Modifier }
        contents(idx(1)) { it ?: Modifier }
        contents(idx(2)) { it ?: Modifier }
        contents(idx(3)) { it ?: Modifier }
        contents(idx(4)) { it ?: Modifier }
        contents(idx(5)) { it ?: Modifier }
        contents(idx(6)) { (it ?: Modifier).gridItem(columnSpan = 3, rowSpan = 3) }
    }
}

@Composable
fun FlowByOrientation(
    verteilungRow: Int = 2,
    verteilungColumn: Int = 3,
    content: @Composable (FlowRowScope.() -> Unit)
) {
    FlowRow(
        modifier = Modifier.wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        maxItemsInEachRow = if (LocalConfiguration.current.isLandscape()) verteilungRow else verteilungColumn,
        content = content,
    )
}

@Composable
fun RowOrColumnByOrientation(vararg contents: @Composable () -> Unit) {
    if (LocalConfiguration.current.isLandscape()) {
        Row {
            contents.forEach { content ->
                content()
            }
        }
    } else {
        Column {
            contents.forEach { content ->
                content()
            }
        }
    }
}

@Composable
fun ColumnOrRowByOrientation(vararg contents: @Composable () -> Unit) {
    if (LocalConfiguration.current.isLandscape()) {
        Column {
            contents.forEach { content ->
                content()
            }
        }
    } else {
        Row {
            contents.forEach { content ->
                content()
            }
        }
    }
}

fun markBy(betrag: Zahlungsmittel?): String {
    if (betrag == null) return ""
    val tsd = 1000
    val mio = tsd * tsd
    val mia = tsd * mio
    val bio = mio * mio
    val bia = tsd * bio
    val trio = mio * bio
    val tria = mia * bio

    var ausgabe: String = if (betrag.istNegativ()) "-" else ""

    val numerischerBetrag = abs(betrag.toIntOderNull()?: 0)
    if (false) { return "" }
    else if (0 <= numerischerBetrag && numerischerBetrag < tsd) {
        ausgabe += "${numerischerBetrag}"
    } else if (tsd <= numerischerBetrag && numerischerBetrag < mio) {
        ausgabe +=  "${numerischerBetrag / tsd} Tsd"
    } else if (mio <= numerischerBetrag && numerischerBetrag < mia) {
        ausgabe +=  "${numerischerBetrag / mio} Mio"
    } else if (mia <= numerischerBetrag && numerischerBetrag < bio) {
        ausgabe +=  "${numerischerBetrag / mia} Mia"
    } else if (bio <= numerischerBetrag && numerischerBetrag < bia) {
        ausgabe +=  "${numerischerBetrag / bio} Bio"
    } else if (bia <= numerischerBetrag && numerischerBetrag < trio) {
        ausgabe +=  "${numerischerBetrag / bia} Bia"
    } else if (trio <= numerischerBetrag && numerischerBetrag < tria) {
        ausgabe +=  "${numerischerBetrag / trio} Trio"
    } else if (tria <= numerischerBetrag) {
        ausgabe +=  "${numerischerBetrag / tria} Tria"
    } else ausgabe += "${numerischerBetrag}"

    return ausgabe
}

@Composable
fun LeftText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) { Text(text = text, modifier = modifier, color = color, fontSize = fontSize, textAlign = TextAlign.Left) }

@Composable
fun RightText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) { Text(text = text, modifier = modifier, color = color, fontSize = fontSize, textAlign = TextAlign.Right) }
