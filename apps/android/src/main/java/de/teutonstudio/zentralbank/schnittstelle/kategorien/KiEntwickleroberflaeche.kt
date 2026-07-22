package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.BeobachtungsAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil

/**
 * Schlichte Entwickleroberfläche über dem autoritativen Aktionsraum. Sie führt keine eigene
 * Regelprüfung aus: Jeder sichtbare Knopf stammt unmittelbar aus [AktionsAuswertung].
 */
@Composable
fun KiEntwickleroberflaeche(
    zustand: SpielZustand,
    beiAktion: (SpielAktion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aktiver = zustand.aktiverSpieler
    val aktionsRaum = remember(zustand, aktiver) {
        aktiver?.let { AktionsAuswertung.erlaubteAktionen(zustand, it) }
    }
    val beobachtung = remember(zustand, aktiver) {
        aktiver?.let { BeobachtungsAuswertung.fuerSpieler(zustand, it) }
    }
    var stil by remember(zustand.ergebnis) {
        mutableStateOf(beobachtung?.eigeneWirtschaft?.spielstil ?: SpielerStil.VORSICHTIG)
    }
    val agenten = listOf("SICHERHEIT", "ZUFALL", "WIRTSCHAFT", "AGGRESSIV", "DEFENSIV", "ONNX")
    var agentIndex by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("SpielerKIModel v1", style = MaterialTheme.typography.titleLarge)
            Text(
                "Beobachtung v${beobachtung?.beobachtungsVersion ?: 2} · " +
                    "Aktionsschema v${aktionsRaum?.aktionsSchemaVersion ?: 2}",
            )
            OutlinedButton(
                onClick = {
                    stil = SpielerStil.entries[(stil.ordinal + 1) % SpielerStil.entries.size]
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Modellstil: ${stil.name}") }
            OutlinedButton(
                onClick = { agentIndex = (agentIndex + 1) % agenten.size },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Agent: ${agenten[agentIndex]}") }
            if (agenten[agentIndex] == "ONNX") {
                Text(
                    "Fallback: regelkonformer Sicherheitsagent bei fehlendem Modell, " +
                        "inkompatiblem Manifest oder ungültiger Inferenz.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            HorizontalDivider()
            Text(
                "Öffentlich: ${beobachtung?.spieler?.size ?: 0} Spieler, " +
                    "${beobachtung?.kriege?.size ?: 0} Kriege, " +
                    "${beobachtung?.belagerungen?.size ?: 0} Belagerungen, " +
                    "${beobachtung?.friedensvertraege?.size ?: 0} Friedensverträge",
                modifier = Modifier.padding(vertical = 8.dp),
            )
            beobachtung?.spieler?.forEach { spieler ->
                Text(
                    "${spieler.name}: ${spieler.geld.zuMarkString()}, " +
                        "Marktwert ${spieler.marktwert.zuMarkString()}, Stil ${spieler.spielstil}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            HorizontalDivider()
            Text(
                "Vollständige legale Kandidaten (${aktionsRaum?.aktionen?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(
            items = aktionsRaum?.aktionen.orEmpty(),
            key = AktionsAuswertung::aktionsSchluessel,
        ) { aktion ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { beiAktion(aktion) }, modifier = Modifier.fillMaxWidth()) {
                    Text(aktion::class.simpleName ?: "Aktion")
                }
                Text(
                    AktionsAuswertung.aktionsSchluessel(aktion),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
