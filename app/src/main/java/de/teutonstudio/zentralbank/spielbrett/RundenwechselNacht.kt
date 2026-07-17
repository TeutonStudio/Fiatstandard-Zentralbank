package de.teutonstudio.zentralbank.spielbrett

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

/** Vollbildsequenz zwischen zwei Spieltagen. Sie endet nach exakt vier Sekunden selbstständig. */
@Composable
fun RundenwechselNacht(
    zustand: SpielZustand,
    beiAbgeschlossen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { }
    val fortschritt = remember(zustand.rundenzähler) { Animatable(0f) }
    val aktuellerAbschluss by rememberUpdatedState(beiAbgeschlossen)
    val karte = zustand.karte
    val modell = remember(karte, zustand.spieler) {
        karte?.zu3DModell(spielerReihenfolge = zustand.spieler.map { it.id })
    }
    val betrachtungsStatus = rememberBetrachtungsTransformationsStatus(
        BetrachtungsTransformation(
            zoom = 0.82f,
            azimutGrad = 32f,
            neigungGrad = 42f,
        ),
    )

    LaunchedEffect(zustand.rundenzähler) {
        fortschritt.snapTo(0f)
        fortschritt.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = NACHTANIMATION_DAUER_MILLIS,
                easing = LinearEasing,
            ),
        )
        aktuellerAbschluss()
    }

    val himmel = HimmelsDarstellung.fuerNachtFortschritt(fortschritt.value)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF020817),
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            if (modell == null) {
                Text(
                    text = "Für diesen Spielstand ist keine Karte verfügbar.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            } else {
                Spielbrett3D(
                    modell = modell,
                    modifier = Modifier.fillMaxSize(),
                    betrachtungsStatus = betrachtungsStatus,
                    himmel = himmel,
                    eingabeAktiv = false,
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "Nacht vor Runde ${zustand.rundenzähler}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Die Karte durchläuft 18:00–06:00 Uhr",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            SpielbrettKompass(
                himmel = himmel,
                kameraAzimutGrad = betrachtungsStatus.azimutGrad,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
            LinearProgressIndicator(
                progress = { fortschritt.value },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = Color(0xFFD7E7FF),
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        }
    }
}
