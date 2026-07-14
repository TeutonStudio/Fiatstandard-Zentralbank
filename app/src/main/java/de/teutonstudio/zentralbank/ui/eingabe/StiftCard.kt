package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
// import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import kotlinx.serialization.Serializable

@Serializable
data class StiftZeichnung(
    val striche: List<StiftStrich> = emptyList()
)

@Serializable
data class StiftStrich(
    val punkte: List<StiftPunkt>,
    val dickeDp: Float = 3f
)

@Serializable
data class StiftPunkt(
    val x: Float,
    val y: Float
)

@Composable
fun StiftCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    zeichnung: MutableState<StiftZeichnung>,
    stiftFarbe: Color = Color.Black,
    stiftDicke: Dp = 3.dp,
    nurStift: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var aktuellerStrich by remember {
        mutableStateOf<StiftStrich?>(null)
    }

    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 160.dp)
                .pointerInput(nurStift, stiftDicke) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val istStift =
                            down.type == PointerType.Stylus ||
                                    down.type == PointerType.Eraser

                        if (nurStift && !istStift) {
                            return@awaitEachGesture
                        }

                        down.consume()

                        fun Offset.normalisiert(): StiftPunkt {
                            val breite = size.width.coerceAtLeast(1).toFloat()
                            val hoehe = size.height.coerceAtLeast(1).toFloat()

                            return StiftPunkt(
                                x = (x / breite).coerceIn(0f, 1f),
                                y = (y / hoehe).coerceIn(0f, 1f)
                            )
                        }

                        var letztePosition = down.position

                        var punkte = listOf(
                            down.position.normalisiert()
                        )

                        aktuellerStrich = StiftStrich(
                            punkte = punkte,
                            dickeDp = stiftDicke.value
                        )

                        while (true) {
                            val event = awaitPointerEvent()

                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break

                            if (!change.pressed) break

                            val neuePosition = change.position
                            val distanz = (neuePosition - letztePosition).getDistance()

                            if (distanz >= 1.5f) {
                                letztePosition = neuePosition

                                punkte = punkte + neuePosition.normalisiert()

                                aktuellerStrich = StiftStrich(
                                    punkte = punkte,
                                    dickeDp = stiftDicke.value
                                )

                                change.consume()
                            }
                        }

                        aktuellerStrich?.let { fertigerStrich ->
                            if (fertigerStrich.punkte.size > 1) {
                                zeichnung.value = zeichnung.value.copy(
                                    striche = zeichnung.value.striche + fertigerStrich
                                )
                            }
                        }

                        aktuellerStrich = null
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }

            Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                zeichnung.value.striche.forEach { strich ->
                    drawStiftStrich(
                        strich = strich,
                        farbe = stiftFarbe,
                        canvasSize = size
                    )
                }

                aktuellerStrich?.let { strich ->
                    drawStiftStrich(
                        strich = strich,
                        farbe = stiftFarbe,
                        canvasSize = size
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawStiftStrich(
    strich: StiftStrich,
    farbe: Color,
    canvasSize: Size
) {
    val punkte = strich.punkte

    if (punkte.isEmpty()) return

    fun StiftPunkt.toOffset(): Offset {
        return Offset(
            x = x * canvasSize.width,
            y = y * canvasSize.height
        )
    }

    val dickePx = strich.dickeDp.dp.toPx()

    if (punkte.size == 1) {
        drawCircle(
            color = farbe,
            radius = dickePx / 2f,
            center = punkte.first().toOffset()
        )
        return
    }

    val path = Path()

    val ersterPunkt = punkte.first().toOffset()
    path.moveTo(ersterPunkt.x, ersterPunkt.y)

    for (i in 1 until punkte.lastIndex) {
        val aktuellerPunkt = punkte[i].toOffset()
        val naechsterPunkt = punkte[i + 1].toOffset()

        val mittelpunkt = Offset(
            x = (aktuellerPunkt.x + naechsterPunkt.x) / 2f,
            y = (aktuellerPunkt.y + naechsterPunkt.y) / 2f
        )

        path.quadraticTo(
            x1 = aktuellerPunkt.x,
            y1 = aktuellerPunkt.y,
            x2 = mittelpunkt.x,
            y2 = mittelpunkt.y
        )
    }

    val letzterPunkt = punkte.last().toOffset()
    path.lineTo(letzterPunkt.x, letzterPunkt.y)

    drawPath(
        path = path,
        color = farbe,
        style = Stroke(
            width = dickePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
