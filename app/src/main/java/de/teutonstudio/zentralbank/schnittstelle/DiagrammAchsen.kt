package de.teutonstudio.zentralbank.schnittstelle

import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

val markAchsenFormatter = einheitenAchsenFormatter("M")
val stueckAchsenFormatter = einheitenAchsenFormatter("Stk")

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
