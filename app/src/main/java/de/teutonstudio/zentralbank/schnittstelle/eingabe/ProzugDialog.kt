package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.schnittstelle.domain.ProzugAnzeigeZustand

@Composable
fun ProzugDialog(
    zustand: ProzugAnzeigeZustand,
    onVerarbeiten: (KartenFeld, Int) -> Unit,
    onVersorgen: (KartenEcke) -> Unit,
    onBezahlen: (VerbindlichkeitId) -> Unit,
    onHandel: () -> Unit,
    onAussenhandel: () -> Unit,
    onAnleihe: () -> Unit,
    onAbschliessen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("${zustand.spielerName} · Runde ${zustand.runde} · Prozug") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Text(zustand.fortschritt, fontWeight = FontWeight.Bold)
                Text("Geld: ${zustand.geld}")
                Text("Bestand: ${zustand.rohstoffbestand}")
                Abschnitt("Automatischer Abbau") {
                    if (zustand.abbauErtraege.isEmpty()) Text("Keine Abbauerträge.")
                    zustand.abbauErtraege.forEach { Text(it) }
                }
                Abschnitt("Freiwillige Verarbeitung") {
                    if (zustand.produktion.isEmpty()) Text("Keine nutzbaren Verarbeitungsstandorte.")
                    zustand.produktion.forEach { standort ->
                        Text(standort.titel, fontWeight = FontWeight.SemiBold)
                        Text("${standort.rezept} · ${standort.kapazitaet}")
                        Row {
                            (1..standort.moeglicheLaeufe).forEach { laeufe ->
                                TextButton(onClick = { onVerarbeiten(standort.feld, laeufe) }) {
                                    Text("$laeufe Lauf" + if (laeufe == 1) "" else "e")
                                }
                            }
                        }
                    }
                }
                Abschnitt("Verwaltungsversorgung") {
                    if (zustand.verwaltung.isEmpty()) Text("Keine Versorgungspflichten.")
                    zustand.verwaltung.forEach { eintrag ->
                        Text(eintrag.text)
                        if (eintrag.versorgt) {
                            Text("Versorgt")
                        } else {
                            TextButton(
                                enabled = eintrag.deckbar,
                                onClick = { onVersorgen(eintrag.ecke) },
                            ) { Text("Versorgen") }
                        }
                    }
                }
                Abschnitt("Fällige Verbindlichkeiten") {
                    if (zustand.verbindlichkeiten.isEmpty()) Text("Keine fälligen Zahlungen.")
                    zustand.verbindlichkeiten.forEach { eintrag ->
                        Text(eintrag.text)
                        if (eintrag.bezahlt) {
                            Text("Bezahlt")
                        } else {
                            TextButton(
                                enabled = eintrag.deckbar,
                                onClick = { onBezahlen(eintrag.id) },
                            ) { Text("Bezahlen") }
                        }
                    }
                }
                zustand.defizite.forEach { Text(it, fontWeight = FontWeight.SemiBold) }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Liquidität beschaffen", fontWeight = FontWeight.Bold)
                TextButton(onClick = onHandel) { Text("Rohstoff / Anleihe handeln") }
                TextButton(onClick = onAussenhandel) { Text("Außenhandel") }
                TextButton(onClick = onAnleihe) { Text("Anleihe emittieren") }
                if (!zustand.kannAbschliessen) {
                    zustand.sperrgruende.forEach { Text(it) }
                }
            }
        },
        confirmButton = {
            Button(enabled = zustand.kannAbschliessen, onClick = onAbschliessen) {
                Text("Prozug abschließen")
            }
        },
    )
}

@Composable
private fun Abschnitt(
    titel: String,
    inhalt: @Composable () -> Unit,
) {
    Text(
        text = titel,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    inhalt()
}
