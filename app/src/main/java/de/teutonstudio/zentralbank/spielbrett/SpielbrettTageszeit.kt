package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val SPIELTAG_BEGINN_STUNDE = 6f
internal const val SPIELTAG_ENDE_STUNDE = 18f
internal const val NACHTANIMATION_DAUER_MILLIS = 4_000

/** Das dem Spielerzug zugeordnete Zeitfenster innerhalb des Spieltags. */
@Immutable
data class SpielzugZeitfenster(
    val beginnStunde: Float,
    val endeStunde: Float,
) {
    init {
        require(beginnStunde.isFinite() && endeStunde.isFinite())
        require(beginnStunde in SPIELTAG_BEGINN_STUNDE..SPIELTAG_ENDE_STUNDE)
        require(endeStunde in SPIELTAG_BEGINN_STUNDE..SPIELTAG_ENDE_STUNDE)
        require(endeStunde > beginnStunde)
    }

    val text: String
        get() = "${formatiereUhrzeit(beginnStunde)}–${formatiereUhrzeit(endeStunde)}"
}

/**
 * Verteilt den zwölfstündigen Spieltag gleichmäßig auf alle Spieler. Der erste Zug beginnt um
 * 06:00 Uhr; 18:00 Uhr ist die exklusive Endzeit des letzten Zuges.
 */
fun spielzugZeitfenster(
    spielerIndex: Int,
    spielerAnzahl: Int,
): SpielzugZeitfenster {
    require(spielerAnzahl > 0) { "Ein Spieltag braucht mindestens einen Spieler." }
    require(spielerIndex in 0 until spielerAnzahl) { "Der Spielerindex liegt außerhalb der Runde." }
    val schritt = (SPIELTAG_ENDE_STUNDE - SPIELTAG_BEGINN_STUNDE) / spielerAnzahl
    return SpielzugZeitfenster(
        beginnStunde = SPIELTAG_BEGINN_STUNDE + spielerIndex * schritt,
        endeStunde = SPIELTAG_BEGINN_STUNDE + (spielerIndex + 1) * schritt,
    )
}

/** Alle für Himmel, Himmelskörper, Wasser und Licht benötigten Darstellungswerte. */
@Immutable
data class HimmelsDarstellung(
    val uhrzeitStunde: Float,
    val lichtAzimutGrad: Float,
    val lichtHoeheGrad: Float,
    val sonnenSichtbarkeit: Float,
    val mondSichtbarkeit: Float,
    val sterneSichtbarkeit: Float,
    val nachtAnteil: Float,
) {
    init {
        require(uhrzeitStunde.isFinite())
        require(lichtAzimutGrad.isFinite() && lichtHoeheGrad.isFinite())
        require(sonnenSichtbarkeit in 0f..1f)
        require(mondSichtbarkeit in 0f..1f)
        require(sterneSichtbarkeit in 0f..1f)
        require(nachtAnteil in 0f..1f)
    }

    val uhrzeitText: String
        get() = formatiereUhrzeit(uhrzeitStunde)

    val sichtbarerHimmelskoerper: String
        get() = if (mondSichtbarkeit > sonnenSichtbarkeit) "Mond" else "Sonne"

    companion object {
        /** Statische Tagesbeleuchtung für einen Spielerzug. */
        fun fuerSpielzug(zeitfenster: SpielzugZeitfenster): HimmelsDarstellung =
            fuerUhrzeit(zeitfenster.beginnStunde)

        /** Statische Vorschau einer Uhrzeit zwischen 06:00 und 18:00 Uhr. */
        fun fuerUhrzeit(stunde: Float): HimmelsDarstellung {
            require(stunde in SPIELTAG_BEGINN_STUNDE..SPIELTAG_ENDE_STUNDE)
            val tagesFortschritt =
                (stunde - SPIELTAG_BEGINN_STUNDE) /
                    (SPIELTAG_ENDE_STUNDE - SPIELTAG_BEGINN_STUNDE)
            val hoehe = sin(PI * tagesFortschritt).toFloat().coerceAtLeast(0f) * 58f
            return HimmelsDarstellung(
                uhrzeitStunde = stunde,
                lichtAzimutGrad = 90f + 180f * tagesFortschritt,
                lichtHoeheGrad = hoehe,
                sonnenSichtbarkeit = 1f,
                mondSichtbarkeit = 0f,
                sterneSichtbarkeit = 0f,
                nachtAnteil = 0f,
            )
        }

        /** Beschleunigter Lauf von 18:00 Uhr bis 06:00 Uhr des Folgetags. */
        fun fuerNachtFortschritt(fortschritt: Float): HimmelsDarstellung {
            val t = fortschritt.coerceIn(0f, 1f)
            val einblendung = glatterUebergang((t / 0.16f).coerceIn(0f, 1f))
            val ausblendung = glatterUebergang(((1f - t) / 0.16f).coerceIn(0f, 1f))
            val nacht = minOf(einblendung, ausblendung)
            val mondHoehe = sin(PI * t).toFloat().coerceAtLeast(0f) * 48f
            return HimmelsDarstellung(
                uhrzeitStunde = SPIELTAG_ENDE_STUNDE + 12f * t,
                lichtAzimutGrad = 90f + 180f * t,
                lichtHoeheGrad = mondHoehe,
                sonnenSichtbarkeit = 0f,
                mondSichtbarkeit = nacht,
                sterneSichtbarkeit = nacht,
                nachtAnteil = nacht,
            )
        }
    }
}

internal fun formatiereUhrzeit(stunde: Float): String {
    val minutenGesamt = (normalisiereStunde(stunde) * 60f).roundToInt() % (24 * 60)
    val stunden = minutenGesamt / 60
    val minuten = minutenGesamt % 60
    return "%02d:%02d".format(stunden, minuten)
}

private fun normalisiereStunde(stunde: Float): Float = ((stunde % 24f) + 24f) % 24f

private fun glatterUebergang(wert: Float): Float = wert * wert * (3f - 2f * wert)

internal fun HimmelsDarstellung.lichtVektor(): LichtVektor {
    val azimut = Math.toRadians(lichtAzimutGrad.toDouble())
    val hoehe = Math.toRadians(lichtHoeheGrad.coerceAtLeast(4f).toDouble())
    val horizontal = cos(hoehe).toFloat()
    return LichtVektor(
        x = sin(azimut).toFloat() * horizontal,
        y = sin(hoehe).toFloat(),
        z = -cos(azimut).toFloat() * horizontal,
    )
}

@Immutable
internal data class LichtVektor(
    val x: Float,
    val y: Float,
    val z: Float,
)
