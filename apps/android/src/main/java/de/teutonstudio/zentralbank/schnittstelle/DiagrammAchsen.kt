package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import de.teutonstudio.zentralbank.schnittstelle.theme.MarkZeichenSchrift
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val MARK_ZEICHEN = "ℳ"

val markAchsenFormatter = einheitenAchsenFormatter(MARK_ZEICHEN)
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
    val beschriftung = abs(value).alsAchsenwert()
    val beschriftungMitRichtung = if (richtung == null) beschriftung else "$richtung $beschriftung"
    beschriftungMitEinheit(beschriftungMitRichtung, einheit)
}

private fun einheitenAchsenFormatter(einheit: String) =
    CartesianValueFormatter { _, value, _ ->
        beschriftungMitEinheit(value.alsAchsenwert(), einheit)
    }

internal fun markBeschriftung(beschriftung: String): AnnotatedString = buildAnnotatedString {
    append(beschriftung)
    append(' ')
    withStyle(SpanStyle(fontFamily = MarkZeichenSchrift)) {
        append(MARK_ZEICHEN)
    }
}

private fun beschriftungMitEinheit(
    beschriftung: String,
    einheit: String,
): CharSequence = if (einheit == MARK_ZEICHEN) {
    markBeschriftung(beschriftung)
} else {
    "$beschriftung $einheit"
}

private fun Double.alsAchsenwert(): String {
    val gerundet = roundToLong()
    return if (abs(this - gerundet) < 0.0001) {
        gerundet.toString()
    } else {
        String.format(Locale.GERMANY, "%.1f", this)
    }
}
