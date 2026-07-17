package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun KartenAuswahl(
    ausgewaehlteKarte: KartenVorlage?,
    beiAuswahl: (KartenVorlage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kontext = LocalContext.current
    val ablage = remember(kontext) { KartenAblage(kontext) }
    var eintraege by remember { mutableStateOf<List<KartenEintrag>>(emptyList()) }
    var wirdGeladen by remember { mutableStateOf(true) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }
    var editorEintrag by remember { mutableStateOf<KartenEintrag?>(null) }

    LaunchedEffect(ablage) {
        runCatching { ablage.alleKartenLaden() }
            .onSuccess { geladen ->
                eintraege = geladen
                wirdGeladen = false
                if (ausgewaehlteKarte == null) {
                    geladen.firstOrNull()?.vorlage?.let(beiAuswahl)
                }
            }
            .onFailure { fehler ->
                wirdGeladen = false
                fehlermeldung = fehler.message ?: "Kartenvorlagen konnten nicht geladen werden."
            }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Spielkarte auswählen", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Die gewählte Karte wird als eigener Bestandteil dieses Spielstands gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    editorEintrag = KartenEintrag(
                        vorlage = KartenVorlage(
                            id = "neuer-entwurf",
                            name = "Neue Karte",
                            zeilen = 8,
                            spalten = 8,
                        ),
                        quelle = KartenQuelle.EIGENE_KARTE,
                    )
                },
            ) {
                Text("Neue Karte bauen")
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val breiteAnsicht = maxWidth >= 720.dp
            val liste: @Composable (Modifier) -> Unit = { listenModifier ->
                KartenListe(
                    eintraege = eintraege,
                    ausgewaehlteKarte = ausgewaehlteKarte,
                    beiAuswahl = beiAuswahl,
                    modifier = listenModifier,
                )
            }
            val vorschau: @Composable (Modifier) -> Unit = { vorschauModifier ->
                KartenVorschau(
                    karte = ausgewaehlteKarte,
                    beiBearbeiten = { karte ->
                        editorEintrag = eintraege.firstOrNull { it.vorlage.id == karte.id }
                            ?: KartenEintrag(karte, KartenQuelle.EIGENE_KARTE)
                    },
                    modifier = vorschauModifier,
                )
            }

            when {
                wirdGeladen -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                fehlermeldung != null -> Text(
                    text = requireNotNull(fehlermeldung),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                breiteAnsicht -> Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    liste(Modifier.widthIn(max = 340.dp).fillMaxSize())
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
            migrationsHinweise = eintrag.migrationsHinweise,
            beiAbbruch = { editorEintrag = null },
            beiGespeichert = { karte ->
                eintraege = (eintraege.filterNot { it.vorlage.id == karte.id } +
                    KartenEintrag(karte, KartenQuelle.EIGENE_KARTE))
                    .sortedBy { it.vorlage.name.lowercase() }
                beiAuswahl(karte)
                editorEintrag = null
            },
        )
    }
}

@Composable
private fun KartenListe(
    eintraege: List<KartenEintrag>,
    ausgewaehlteKarte: KartenVorlage?,
    beiAuswahl: (KartenVorlage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(eintraege, key = { eintrag -> "${eintrag.quelle}-${eintrag.vorlage.id}" }) { eintrag ->
            val istAusgewaehlt = eintrag.vorlage.id == ausgewaehlteKarte?.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { beiAuswahl(eintrag.vorlage) },
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
                        if (eintrag.quelle == KartenQuelle.VORLAGE) "Vorlage" else "Eigene Karte",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${eintrag.vorlage.zeilen} × ${eintrag.vorlage.spalten} Rauten · " +
                            "${eintrag.vorlage.gelaendefelder.size} Geländedreiecke",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (eintrag.migrationsHinweise.isNotEmpty()) {
                        Text(
                            "Altes Format · beim Bearbeiten als Kopie speichern",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KartenVorschau(
    karte: KartenVorlage?,
    beiBearbeiten: (KartenVorlage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        if (karte == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Karte ausgewählt")
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
                    Text(karte.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${karte.gelaendefelder.size} Geländedreiecke · unbelegte Vorlage",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { beiBearbeiten(karte) }) {
                    Text("Kopie bearbeiten")
                }
            }
            Spielbrett3D(
                modell = karte.zu3DModell(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}
