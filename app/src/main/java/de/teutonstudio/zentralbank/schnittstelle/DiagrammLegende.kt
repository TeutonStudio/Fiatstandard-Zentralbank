package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class DiagrammLegendenEintrag(
    val id: String,
    val bezeichnung: String,
    val farbe: Color,
)

@Stable
class DiagrammLegendenStatus internal constructor(ids: Collection<String>) {
    var sichtbareIds by mutableStateOf(ids.toSet())
        private set

    fun istSichtbar(id: String): Boolean = id in sichtbareIds

    fun umschalten(id: String) {
        sichtbareIds = if (istSichtbar(id)) {
            sichtbareIds - id
        } else {
            sichtbareIds + id
        }
    }

    fun nurAnzeigen(id: String) {
        sichtbareIds = setOf(id)
    }

    fun sichtbarkeitSetzen(ids: Collection<String>, sichtbar: Boolean) {
        sichtbareIds = if (sichtbar) sichtbareIds + ids else sichtbareIds - ids.toSet()
    }
}

@Composable
fun rememberDiagrammLegendenStatus(
    eintraege: List<DiagrammLegendenEintrag>,
): DiagrammLegendenStatus {
    val ids = eintraege.map { it.id }
    return remember(ids) { DiagrammLegendenStatus(ids) }
}

@Composable
fun rememberExklusivenDiagrammLegendenStatus(
    eintraege: List<DiagrammLegendenEintrag>,
): DiagrammLegendenStatus {
    val ids = eintraege.map { it.id }
    return remember(ids) { DiagrammLegendenStatus(ids.take(1)) }
}

@Composable
fun UmschaltbareDiagrammLegende(
    eintraege: List<DiagrammLegendenEintrag>,
    status: DiagrammLegendenStatus,
    modifier: Modifier = Modifier,
    beiKlick: ((DiagrammLegendenEintrag) -> Unit)? = null,
    beiLangemKlick: ((DiagrammLegendenEintrag) -> Unit)? = null,
    klickBeschreibung: String = "Datenreihe ein- oder ausblenden",
    langerKlickBeschreibung: String = "Nur diese Datenreihe anzeigen",
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        eintraege.forEach { eintrag ->
            val sichtbar = status.istSichtbar(eintrag.id)
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .combinedClickable(
                        role = if (beiKlick == null) Role.Checkbox else Role.Button,
                        onClickLabel = klickBeschreibung,
                        onLongClickLabel = langerKlickBeschreibung,
                        onClick = {
                            beiKlick?.invoke(eintrag) ?: status.umschalten(eintrag.id)
                        },
                        onLongClick = {
                            beiLangemKlick?.invoke(eintrag) ?: status.nurAnzeigen(eintrag.id)
                        },
                    ),
                shape = RoundedCornerShape(8.dp),
                color = if (sichtbar) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (sichtbar) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (sichtbar) eintrag.farbe else eintrag.farbe.copy(alpha = 0.3f)
                            )
                    )
                    Text(
                        text = eintrag.bezeichnung,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(if (sichtbar) 1f else 0.55f),
                    )
                }
            }
        }
    }
}
