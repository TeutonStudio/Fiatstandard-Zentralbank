package de.teutonstudio.zentralbank.schnittstelle

import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

val markAchsenFormatter = einheitenAchsenFormatter("M")
val stueckAchsenFormatter = CartesianValueFormatter { _, value, _ ->
    "${value.roundToLong()} Stk"
}
val ganzzahligerStueckAchsenItemPlacer = VerticalAxis.ItemPlacer.step(
    step = { 1.0 },
)

fun richtungsAchsenFormatter(
    positiveRichtung: String,
    negativeRichtung: String,
    einheit: String,
) = CartesianValueFormatter { _, value, _ ->
    val richtung = when {
        value > 0.0 -> positiveRichtung
        value < 0.0 -> negativeRichtung
        else -> null
    }
    val beschriftung = "${abs(value).alsAchsenwert()} $einheit"
    if (richtung == null) beschriftung else "$richtung $beschriftung"
}

private fun einheitenAchsenFormatter(einheit: String) =
    CartesianValueFormatter { _, value, _ ->
        "${value.alsAchsenwert()} $einheit"
    }

private fun Double.alsAchsenwert(): String {
    val gerundet = roundToLong()
    return if (abs(this - gerundet) < 0.0001) {
        gerundet.toString()
    } else {
        String.format(Locale.GERMANY, "%.1f", this)
    }
}
