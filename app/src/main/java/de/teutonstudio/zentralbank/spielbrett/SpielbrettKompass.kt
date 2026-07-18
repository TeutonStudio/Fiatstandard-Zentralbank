package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Kompakte Zeit- und Himmelsinformation; die Richtungen stehen direkt auf dem Wasser. */
@Composable
fun SpielbrettKompass(
    himmel: HimmelsDarstellung,
    modifier: Modifier = Modifier,
    zeitfenster: SpielzugZeitfenster? = null,
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = buildString {
                append("Himmelsinformation, ")
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
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
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
        }
    }
}
