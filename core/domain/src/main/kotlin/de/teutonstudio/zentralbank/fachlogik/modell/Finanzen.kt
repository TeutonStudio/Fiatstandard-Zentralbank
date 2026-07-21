package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class Basispunkte(
    val wert: Int,
) {
    fun zuProzentString(): String {
        val vorzeichen = if (wert < 0) "-" else ""
        val absolut = kotlin.math.abs(wert)
        val ganz = absolut / BASISPUNKTE_PRO_PROZENT
        val nachkomma = absolut % BASISPUNKTE_PRO_PROZENT
        return "$vorzeichen$ganz,${nachkomma.toString().padStart(2, '0')} %"
    }

    companion object {
        const val BASISPUNKTE_PRO_PROZENT = 100

        val NULL = Basispunkte(0)

        fun prozent(prozent: Int): Basispunkte = Basispunkte(prozent * BASISPUNKTE_PRO_PROZENT)
    }
}
