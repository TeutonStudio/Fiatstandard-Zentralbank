package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Kompakte gemeinsame Richtungs- und Zeitinformation für Bau- und Spielmodus. */
@Composable
fun SpielbrettKompass(
    himmel: HimmelsDarstellung,
    kameraAzimutGrad: Float,
    modifier: Modifier = Modifier,
    zeitfenster: SpielzugZeitfenster? = null,
) {
    val himmelskoerperFarbe = if (himmel.mondSichtbarkeit > himmel.sonnenSichtbarkeit) {
        Color(0xFFD7E7FF)
    } else {
        Color(0xFFFFB300)
    }
    val linienFarbe = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.semantics {
            contentDescription = buildString {
                append("Kompass, Norden und ")
                append(himmel.sichtbarerHimmelskoerper)
                append(" bei ")
                append(himmel.lichtAzimutGrad.toInt())
                append(" Grad, Uhrzeit ")
                append(himmel.uhrzeitText)
            }
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KompassScheibe(
                kameraAzimutGrad = kameraAzimutGrad,
                himmelskoerperAzimutGrad = himmel.lichtAzimutGrad,
                himmelskoerperFarbe = himmelskoerperFarbe,
                linienFarbe = linienFarbe,
            )
            Column {
                Text(
                    text = zeitfenster?.let { "Spieltag ${it.text}" }
                        ?: "${himmel.uhrzeitText} Uhr",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${himmel.sichtbarerHimmelskoerper} · " +
                        "${himmel.lichtAzimutGrad.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "N · O · S · W",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun KompassScheibe(
    kameraAzimutGrad: Float,
    himmelskoerperAzimutGrad: Float,
    himmelskoerperFarbe: Color,
    linienFarbe: Color,
) {
    Box(modifier = Modifier.size(58.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val mitte = center
            val radius = size.minDimension * 0.39f
            drawCircle(
                color = linienFarbe.copy(alpha = 0.42f),
                radius = radius,
                center = mitte,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )

            val nordPunkt = richtungsPunkt(
                mitte = mitte,
                radius = radius * 0.82f,
                winkelGrad = -kameraAzimutGrad,
            )
            drawLine(
                color = Color(0xFFD32F2F),
                start = mitte,
                end = nordPunkt,
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(Color(0xFFD32F2F), 2.5.dp.toPx(), nordPunkt)

            val himmelsPunkt = richtungsPunkt(
                mitte = mitte,
                radius = radius * 0.68f,
                winkelGrad = himmelskoerperAzimutGrad - kameraAzimutGrad,
            )
            drawCircle(
                color = himmelskoerperFarbe,
                radius = 3.5.dp.toPx(),
                center = himmelsPunkt,
            )
            drawCircle(linienFarbe, 1.8.dp.toPx(), mitte)
        }
        Text(
            text = "N",
            modifier = Modifier.align(Alignment.TopCenter),
            color = Color(0xFFD32F2F),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun richtungsPunkt(
    mitte: Offset,
    radius: Float,
    winkelGrad: Float,
): Offset {
    val winkel = Math.toRadians(winkelGrad.toDouble())
    return Offset(
        x = mitte.x + sin(winkel).toFloat() * radius,
        y = mitte.y - cos(winkel).toFloat() * radius,
    )
}
