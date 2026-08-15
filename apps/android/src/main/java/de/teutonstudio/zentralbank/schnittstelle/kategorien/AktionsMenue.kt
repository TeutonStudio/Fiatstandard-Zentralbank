package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil

/** Kleine, UI-eigene Projektion; sie hält nie den vollständigen Aktionsraum oder eine Beobachtung. */
data class AktionsMenueZustand(
    val aktiverSpieler: String,
    val bereiche: List<AktionsBereichEintrag>,
    val ausgewaehlterBereich: AktionsBereich? = null,
    val eintraege: List<AngezeigteAktion> = emptyList(),
    val wirdGeladen: Boolean = false,
    val fehler: String? = null,
    val bestaetigung: AktionsBestaetigung? = null,
    val kiStil: SpielerStil? = null,
)

data class AktionsBestaetigung(val titel: String, val zusammenfassung: String)

enum class AktionsMenueNavigationZiel { HANDEL, ANLEIHEN }

enum class AktionsBereich { KONFLIKT, DIPLOMATIE, TRUPPEN, HANDEL, ANLEIHEN, ZUG, KI }
data class AktionsBereichEintrag(val bereich: AktionsBereich, val beschriftung: String, val verfuegbar: Boolean, val anzahl: Int? = null)
data class AngezeigteAktion(val id: String, val titel: String, val beschreibung: String? = null, val aktiv: Boolean = true)

@Composable
fun AktionsMenue(
    zustand: AktionsMenueZustand,
    beiBereichAuswaehlen: (AktionsBereich) -> Unit,
    beiEintragAuswaehlen: (String) -> Unit,
    beiBestaetigen: () -> Unit,
    beiAbbrechen: () -> Unit,
    beiErneutVersuchen: () -> Unit,
    beiKiStil: (SpielerStil) -> Unit,
    beiSchliessen: () -> Unit,
    debugZugaenglich: Boolean,
    beiDebugOeffnen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Aktiver Spieler: ${zustand.aktiverSpieler}", style = MaterialTheme.typography.titleMedium)
        zustand.bestaetigung?.let { bestaetigung ->
            Text(bestaetigung.titel, style = MaterialTheme.typography.titleMedium)
            Text(bestaetigung.zusammenfassung)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = beiAbbrechen, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
                Button(onClick = beiBestaetigen, modifier = Modifier.weight(1f)) { Text("Bestätigen") }
            }
        } ?: when (zustand.ausgewaehlterBereich) {
            null -> AktionsBereiche(zustand.bereiche, beiBereichAuswaehlen)
            AktionsBereich.KI -> KiBereich(zustand.kiStil, beiKiStil, debugZugaenglich, beiDebugOeffnen)
            else -> AktionsEintraege(zustand, beiEintragAuswaehlen, beiErneutVersuchen)
        }
        OutlinedButton(onClick = beiSchliessen, modifier = Modifier.fillMaxWidth()) { Text("Schließen") }
    }
}

@Composable
private fun AktionsBereiche(bereiche: List<AktionsBereichEintrag>, beiAuswaehlen: (AktionsBereich) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(bereiche, key = { it.bereich }) { bereich ->
            Button(onClick = { beiAuswaehlen(bereich.bereich) }, enabled = bereich.verfuegbar, modifier = Modifier.fillMaxWidth()) {
                Text(bereich.beschriftung + (bereich.anzahl?.let { " ($it)" } ?: ""))
            }
        }
    }
}

@Composable
private fun AktionsEintraege(zustand: AktionsMenueZustand, beiAuswaehlen: (String) -> Unit, beiErneutVersuchen: () -> Unit) {
    if (zustand.wirdGeladen) {
        Text("Bereich wird geladen …")
        return
    }
    zustand.fehler?.let { fehler ->
        Text(fehler, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = beiErneutVersuchen) { Text("Erneut versuchen") }
        return
    }
    if (zustand.eintraege.isEmpty()) {
        Text("Für diesen Bereich sind aktuell keine Aktionen verfügbar.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(zustand.eintraege, key = { it.id }) { eintrag ->
            Button(onClick = { beiAuswaehlen(eintrag.id) }, enabled = eintrag.aktiv, modifier = Modifier.fillMaxWidth()) {
                Column { Text(eintrag.titel); eintrag.beschreibung?.let { Text(it) } }
            }
        }
    }
}

@Composable
private fun KiBereich(stil: SpielerStil?, beiStil: (SpielerStil) -> Unit, debugZugaenglich: Boolean, beiDebugOeffnen: () -> Unit) {
    Text("KI-Stil", style = MaterialTheme.typography.titleMedium)
    Text("Der Stil wird als Spielereignis gespeichert und für Simulationen sowie Replays verwendet.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SpielerStil.entries, key = { it.name }) { kandidat ->
            if (kandidat == stil) OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("${kandidat.name} (aktiv)") }
            else Button(onClick = { beiStil(kandidat) }, modifier = Modifier.fillMaxWidth()) { Text(kandidat.name) }
        }
        if (debugZugaenglich) item { OutlinedButton(onClick = beiDebugOeffnen, modifier = Modifier.fillMaxWidth()) { Text("KI-Debug öffnen") } }
    }
}
