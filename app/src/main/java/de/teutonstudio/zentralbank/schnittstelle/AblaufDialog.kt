package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AblaufDialog(
    titel: String,
    breitenAnteil: Float = 0.92f,
    onDismiss: () -> Unit,
    inhalt: @Composable () -> Unit,
) {
    val konfiguration = LocalConfiguration.current
    val bildschirmbreite = konfiguration.screenWidthDp.dp
    val bildschirmhoehe = konfiguration.screenHeightDp.dp
    val minimaleDialogbreite = minOf(640.dp, bildschirmbreite * 0.92f)
    val minimaleDialoghoehe = minOf(360.dp, bildschirmhoehe * 0.60f)
    val maximaleDialoghoehe = bildschirmhoehe * 0.88f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = minimaleDialogbreite)
                .fillMaxWidth(breitenAnteil)
                .heightIn(
                    min = minimaleDialoghoehe,
                    max = maximaleDialoghoehe,
                ),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = titel,
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Text(text = "×", fontSize = 30.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    inhalt()
                }
            }
        }
    }
}

@Composable
fun rememberAblaufSpaltenbreite(
    texte: List<String>,
    schriftgroesse: TextUnit,
    innenabstand: Dp = 10.dp,
    schriftgewicht: FontWeight? = null,
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textbreite = remember(texte, schriftgroesse, schriftgewicht, textMeasurer, density) {
        texte.maxOfOrNull { text ->
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = TextStyle(
                    fontSize = schriftgroesse,
                    fontWeight = schriftgewicht,
                ),
                maxLines = 1,
            ).size.width
        } ?: 0
    }
    return with(density) { textbreite.toDp() } + innenabstand
}
