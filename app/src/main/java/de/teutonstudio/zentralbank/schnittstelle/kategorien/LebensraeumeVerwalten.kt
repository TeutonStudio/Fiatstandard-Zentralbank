package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.daten.karten.KartenAblage
import de.teutonstudio.zentralbank.daten.karten.KartenEintrag
import de.teutonstudio.zentralbank.daten.karten.KartenQuelle
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.spielbrett.KartenEditorDialog
import de.teutonstudio.zentralbank.spielbrett.Spielbrett3D
import de.teutonstudio.zentralbank.spielbrett.zu3DModell
import kotlinx.coroutines.launch

@Composable
fun LebensraeumeVerwalten(modifier: Modifier = Modifier) {
    val kontext = LocalContext.current
    val ablage = remember(kontext) { KartenAblage(kontext) }
    val scope = rememberCoroutineScope()
    var neuladen by remember { mutableIntStateOf(0) }
    var eintraege by remember { mutableStateOf<List<KartenEintrag>>(emptyList()) }
    var ausgewaehlt by remember { mutableStateOf<KartenEintrag?>(null) }
    var editorEintrag by remember { mutableStateOf<KartenEintrag?>(null) }
    var zuLoeschen by remember { mutableStateOf<KartenEintrag?>(null) }
    var wirdGeladen by remember { mutableStateOf(true) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ablage, neuladen) {
        wirdGeladen = true
        fehlermeldung = null
        runCatching { ablage.alleKartenLaden() }
            .onSuccess { geladen ->
                eintraege = geladen
                ausgewaehlt = ausgewaehlt?.let { bisher ->
                    geladen.firstOrNull { it.vorlage.id == bisher.vorlage.id }
                } ?: geladen.firstOrNull()
                wirdGeladen = false
            }
            .onFailure { fehler ->
                wirdGeladen = false
                fehlermeldung = fehler.message ?: "Lebensräume konnten nicht geladen werden."
            }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column {
                Text("Lebensräume verwalten", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Eigene Karten erstellen, bearbeiten und löschen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = {
                        editorEintrag = KartenEintrag(
                            vorlage = KartenVorlage(
                                id = "neuer-entwurf",
                                name = "Neuer Lebensraum",
                            ),
                            quelle = KartenQuelle.EIGENE_KARTE,
                        )
                    },
                ) {
                    Text("Neu")
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Empfangen – später")
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Senden – später")
                }
            }
        }

        fehlermeldung?.let { meldung ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(meldung, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { neuladen++ }) { Text("Erneut laden") }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val breiteAnsicht = maxWidth >= 760.dp
            val liste: @Composable (Modifier) -> Unit = { listenModifier ->
                LebensraumListe(
                    eintraege = eintraege,
                    ausgewaehlt = ausgewaehlt,
                    beiAuswahl = { ausgewaehlt = it },
                    modifier = listenModifier,
                )
            }
            val vorschau: @Composable (Modifier) -> Unit = { vorschauModifier ->
                LebensraumVorschau(
                    eintrag = ausgewaehlt,
                    beiBearbeiten = { eintrag -> editorEintrag = eintrag },
                    beiLoeschen = { eintrag -> zuLoeschen = eintrag },
                    modifier = vorschauModifier,
                )
            }

            when {
                wirdGeladen -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                breiteAnsicht -> Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    liste(Modifier.widthIn(max = 360.dp).fillMaxSize())
                    vorschau(Modifier.weight(1f).fillMaxSize())
                }
                else -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    liste(Modifier.weight(0.42f).fillMaxWidth())
                    vorschau(Modifier.weight(0.58f).fillMaxWidth())
                }
            }
        }
    }

    editorEintrag?.let { eintrag ->
        KartenEditorDialog(
            ausgangsvorlage = eintrag.vorlage,
            ablage = ablage,
            beiAbbruch = { editorEintrag = null },
            beiGespeichert = { gespeichert ->
                editorEintrag = null
                ausgewaehlt = KartenEintrag(gespeichert, KartenQuelle.EIGENE_KARTE)
                neuladen++
            },
        )
    }

    zuLoeschen?.let { eintrag ->
        AlertDialog(
            onDismissRequest = { zuLoeschen = null },
            title = { Text("Lebensraum löschen?") },
            text = {
                Text(
                    "„${eintrag.vorlage.name}“ und das zugehörige Referenzbild werden " +
                        "dauerhaft gelöscht. Bereits angelegte Spielstände bleiben unverändert.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        zuLoeschen = null
                        scope.launch {
                            runCatching { ablage.eigeneKarteLoeschen(eintrag.vorlage.id) }
                                .onSuccess {
                                    if (ausgewaehlt?.vorlage?.id == eintrag.vorlage.id) {
                                        ausgewaehlt = null
                                    }
                                    neuladen++
                                }
                                .onFailure { fehler ->
                                    fehlermeldung = fehler.message
                                        ?: "Lebensraum konnte nicht gelöscht werden."
                                }
                        }
                    },
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { zuLoeschen = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun LebensraumListe(
    eintraege: List<KartenEintrag>,
    ausgewaehlt: KartenEintrag?,
    beiAuswahl: (KartenEintrag) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(eintraege, key = { eintrag -> "${eintrag.quelle}-${eintrag.vorlage.id}" }) { eintrag ->
            val istAusgewaehlt = eintrag.vorlage.id == ausgewaehlt?.vorlage?.id &&
                eintrag.quelle == ausgewaehlt.quelle
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { beiAuswahl(eintrag) },
                colors = if (istAusgewaehlt) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(eintrag.vorlage.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (eintrag.quelle == KartenQuelle.VORLAGE) {
                            "Gebündelte Vorlage"
                        } else {
                            "Eigener Lebensraum"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${eintrag.vorlage.gelaendefelder.size} Geländedreiecke" +
                            if (eintrag.hatReferenzbild) " · mit Referenzbild" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LebensraumVorschau(
    eintrag: KartenEintrag?,
    beiBearbeiten: (KartenEintrag) -> Unit,
    beiLoeschen: (KartenEintrag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        if (eintrag == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kein Lebensraum ausgewählt")
            }
            return@Card
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(eintrag.vorlage.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hexagonradius ${eintrag.vorlage.hexagon.radius} · " +
                            "${eintrag.vorlage.hexagon.anzahlFelder} Dreiecke",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(onClick = { beiBearbeiten(eintrag) }) {
                        Text(
                            if (eintrag.quelle == KartenQuelle.VORLAGE) {
                                "Als Kopie bearbeiten"
                            } else {
                                "Bearbeiten"
                            },
                        )
                    }
                    if (eintrag.quelle == KartenQuelle.EIGENE_KARTE) {
                        OutlinedButton(onClick = { beiLoeschen(eintrag) }) {
                            Text("Löschen")
                        }
                    }
                }
            }
            Spielbrett3D(
                modell = eintrag.vorlage.zu3DModell(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                statischeVorschau = true,
            )
        }
    }
}
