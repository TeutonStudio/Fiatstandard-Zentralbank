package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.Anleihenhandel
import de.teutonstudio.zentralbank.datenbank.Ausland
import de.teutonstudio.zentralbank.datenbank.Geschäftsbank
import de.teutonstudio.zentralbank.datenbank.JuristischePerson
import de.teutonstudio.zentralbank.datenbank.RohstoffHandel
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel

@Composable
fun RohstoffHandelDialog(
    spiel: Spiel,
    onDismiss: () -> Unit,
    onCreate: (RohstoffHandel) -> Unit,
) {
    val personen = remember(spiel.spielerListe) {
        spiel.spielerListe.map { it as JuristischePerson } + Ausland
    }
    var besitzer by remember(personen) { mutableStateOf(personen.firstOrNull()) }
    var erwerber by remember(personen) {
        mutableStateOf(personen.getOrNull(1) ?: personen.firstOrNull())
    }
    var rohstoff by remember { mutableStateOf(Rohstoffe.entries.first()) }
    var menge by remember { mutableStateOf("1") }
    var gesamtpreis by remember { mutableStateOf("") }

    val mengeWert = menge.toIntOrNull()
    val preisWert = gesamtpreis.toIntOrNull()
    val fehler = when {
        besitzer == null || erwerber == null -> "Es sind keine Handelspartner vorhanden."
        besitzer == erwerber -> "Verkäufer und Erwerber müssen verschieden sein."
        mengeWert == null || mengeWert <= 0 -> "Die Menge muss größer als 0 sein."
        preisWert == null || preisWert <= 0 -> "Der Gesamtpreis muss größer als 0 sein."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handel erfassen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Laufende Runde: ${(spiel.aktuelleRunde - 1).coerceAtLeast(0)}")
                PersonenAuswahl("Verkäufer", personen, besitzer) { besitzer = it }
                PersonenAuswahl("Erwerber", personen, erwerber) { erwerber = it }
                RohstoffAuswahl(rohstoff) { rohstoff = it }
                Zahlenfeld("Menge (Stk)", menge) { menge = it }
                Zahlenfeld("Gesamtpreis (Mark)", gesamtpreis) { gesamtpreis = it }
                Text(
                    text = "Der Handel wird in der laufenden Runde bezahlt. " +
                        "Der daraus berechnete Rohstoffpreis gilt ab der Folgerunde.",
                    modifier = Modifier.padding(top = 12.dp),
                )
                fehler?.let {
                    Text(
                        text = it,
                        color = Color(0xFF9B3C3C),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = fehler == null,
                onClick = {
                    onCreate(
                        RohstoffHandel(
                            besitzer = requireNotNull(besitzer),
                            erwerber = requireNotNull(erwerber),
                            betrag = requireNotNull(preisWert).toZahlungsmittel(),
                            anzahl = requireNotNull(mengeWert),
                            rohstoff = rohstoff,
                        )
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
fun AnleiheDialog(
    spiel: Spiel,
    onDismiss: () -> Unit,
    onCreate: (Anleihenhandel) -> Unit,
) {
    val emittenten = remember(spiel.spielerListe) {
        spiel.spielerListe.map { it as JuristischePerson }
    }
    val erwerberOptionen = remember(spiel.spielerListe) {
        spiel.spielerListe.map { it as JuristischePerson } + Geschäftsbank + Ausland
    }
    var emittent by remember(emittenten) { mutableStateOf(emittenten.firstOrNull()) }
    var erwerber by remember(erwerberOptionen) {
        mutableStateOf<JuristischePerson?>(Geschäftsbank)
    }
    var nennwert by remember { mutableStateOf("") }
    var zins by remember { mutableStateOf("0") }
    var emissionspreis by remember { mutableStateOf("") }
    var laufzeit by remember { mutableStateOf("1") }

    val nennwertWert = nennwert.toIntOrNull()
    val zinsWert = zins.toIntOrNull()
    val preisWert = emissionspreis.toIntOrNull()
    val laufzeitWert = laufzeit.toIntOrNull()
    val fehler = when {
        emittent == null || erwerber == null -> "Es sind keine passenden Beteiligten vorhanden."
        emittent == erwerber -> "Emittent und Erwerber müssen verschieden sein."
        nennwertWert == null || nennwertWert <= 0 -> "Der Nennwert muss größer als 0 sein."
        zinsWert == null || zinsWert < 0 -> "Der Zins darf nicht negativ sein."
        preisWert == null || preisWert <= 0 -> "Der Emissionspreis muss größer als 0 sein."
        laufzeitWert == null || laufzeitWert <= 0 -> "Die Laufzeit muss größer als 0 sein."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anleihe emittieren") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Emissionsrunde: ${(spiel.aktuelleRunde - 1).coerceAtLeast(0)}")
                PersonenAuswahl("Emittent", emittenten, emittent) { emittent = it }
                PersonenAuswahl("Erster Erwerber", erwerberOptionen, erwerber) { erwerber = it }
                Zahlenfeld("Nennwert / Rückkauf (Mark)", nennwert) { nennwert = it }
                Zahlenfeld("Zins je Runde (Mark)", zins) { zins = it }
                Zahlenfeld("Emissionspreis (Mark)", emissionspreis) { emissionspreis = it }
                Zahlenfeld("Laufzeit (Runden)", laufzeit) { laufzeit = it }
                Text(
                    text = "Zinsen werden ab der nächsten Runde bis einschließlich " +
                        "der Fälligkeitsrunde gezahlt.",
                    modifier = Modifier.padding(top = 12.dp),
                )
                fehler?.let {
                    Text(
                        text = it,
                        color = Color(0xFF9B3C3C),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = fehler == null,
                onClick = {
                    val schuldiger = requireNotNull(emittent)
                    onCreate(
                        Anleihenhandel(
                            besitzer = schuldiger,
                            erwerber = requireNotNull(erwerber),
                            anleihe = Anleihe(
                                schuldiger = schuldiger,
                                sondervermögen = requireNotNull(nennwertWert).toZahlungsmittel(),
                                unvermögen = requireNotNull(zinsWert).toZahlungsmittel(),
                                laufzeit = requireNotNull(laufzeitWert),
                            ),
                            preis = requireNotNull(preisWert).toZahlungsmittel(),
                        )
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun PersonenAuswahl(
    label: String,
    optionen: List<JuristischePerson>,
    auswahl: JuristischePerson?,
    onSelect: (JuristischePerson) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: ${auswahl?.name ?: "–"}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            optionen.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.name) },
                    onClick = {
                        onSelect(person)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RohstoffAuswahl(
    auswahl: Rohstoffe,
    onSelect: (Rohstoffe) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Rohstoff: ${auswahl.str}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Rohstoffe.entries.forEach { rohstoff ->
                DropdownMenuItem(
                    text = { Text(rohstoff.str) },
                    onClick = {
                        onSelect(rohstoff)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Zahlenfeld(
    label: String,
    wert: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = { neu ->
            if (neu.all(Char::isDigit)) onValueChange(neu)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
