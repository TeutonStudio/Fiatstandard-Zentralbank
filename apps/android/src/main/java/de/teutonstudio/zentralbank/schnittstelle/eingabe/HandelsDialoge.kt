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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.AnleiheAnzeige
import de.teutonstudio.zentralbank.datenbank.AnleiheStatus
import de.teutonstudio.zentralbank.datenbank.Anleihenhandel
import de.teutonstudio.zentralbank.datenbank.Ausland
import de.teutonstudio.zentralbank.datenbank.Geschäftsbank
import de.teutonstudio.zentralbank.datenbank.JuristischePerson
import de.teutonstudio.zentralbank.datenbank.RohstoffHandel
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.datenbank.zuMark
import java.util.Locale
import kotlin.math.abs

private enum class AnleiheVorgang {
    EMISSION,
    VERKAUF,
    RUECKKAUF,
}

private data class AnleiheAuswahl(
    val vorgang: AnleiheVorgang,
    val anleihe: AnleiheAnzeige? = null,
) {
    fun bezeichnung(): String = when (vorgang) {
        AnleiheVorgang.EMISSION -> "Neue Anleihe emittieren"
        AnleiheVorgang.VERKAUF -> {
            val eintrag = requireNotNull(anleihe)
            "Besitz verkaufen · ${eintrag.schuldiger.name} · ${eintrag.sondervermoegen.zuMark()}"
        }
        AnleiheVorgang.RUECKKAUF -> {
            val eintrag = requireNotNull(anleihe)
            "Eigene Anleihe zurückkaufen · bei ${eintrag.aktuellerBesitzer.name} · " +
                eintrag.sondervermoegen.zuMark()
        }
    }
}

private sealed interface HandelsgutAuswahl {
    data class Rohstoff(val rohstoff: Rohstoffe) : HandelsgutAuswahl
    data class AnleiheWert(val anleihe: AnleiheAnzeige) : HandelsgutAuswahl

    fun bezeichnung(): String = when (this) {
        is Rohstoff -> rohstoff.str
        is AnleiheWert ->
            "${anleihe.schuldiger.name} · ${anleihe.sondervermoegen.zuMark()} · " +
                "fällig R${anleihe.faelligkeit}"
    }
}

private fun relativeProzentabweichung(
    aktuellerWert: Double?,
    referenzwert: Double?,
): Double? {
    if (aktuellerWert == null || referenzwert == null || referenzwert == 0.0) return null
    return (aktuellerWert / referenzwert - 1.0) * 100.0
}

private fun prozentText(wert: Double): String {
    val normalisiert = if (abs(wert) < 0.05) 0.0 else wert
    return if (normalisiert == 0.0) {
        "0,0 %"
    } else {
        String.format(Locale.GERMANY, "%+.1f %%", normalisiert)
    }
}

private fun prozentwertText(wert: Double): String =
    String.format(Locale.GERMANY, "%.1f %%", wert)

@Composable
private fun AbweichungsText(
    label: String,
    abweichung: Double?,
) {
    val farbe = when {
        abweichung == null || abs(abweichung) < 0.05 -> Color(0xFF6F6F6F)
        abweichung > 0.0 -> Color(0xFF2E7D32)
        else -> Color(0xFFB3261E)
    }
    Text(
        text = "$label: ${abweichung?.let(::prozentText) ?: "–"}",
        color = farbe,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun HandelDialog(
    spiel: Spiel,
    onDismiss: () -> Unit,
    onCreateRohstoff: (RohstoffHandel) -> Unit,
    onCreateAnleihe: (Anleihenhandel) -> Unit,
    initialerRohstoff: Rohstoffe? = null,
    initialerBesitzer: JuristischePerson? = null,
    initialerErwerber: JuristischePerson? = null,
    initialerGesamtpreis: Zahlungsmittel? = null,
) {
    val rohstoffPersonen = remember(spiel.spielerListe) {
        spiel.spielerListe.map { it as JuristischePerson } + Ausland
    }
    val anleihePersonen = remember(spiel.spielerListe) {
        spiel.spielerListe.map { it as JuristischePerson } + Geschäftsbank
    }
    var besitzer by remember(rohstoffPersonen, initialerBesitzer) {
        mutableStateOf(
            initialerBesitzer?.takeIf { person -> person in rohstoffPersonen }
                ?: rohstoffPersonen.firstOrNull()
        )
    }
    var erwerber by remember(rohstoffPersonen, initialerErwerber) {
        mutableStateOf(
            initialerErwerber?.takeIf { person -> person in rohstoffPersonen }
                ?: rohstoffPersonen.firstOrNull { person -> person != besitzer }
                ?: rohstoffPersonen.firstOrNull()
        )
    }
    var handelsgut by remember(initialerRohstoff) {
        mutableStateOf<HandelsgutAuswahl>(
            HandelsgutAuswahl.Rohstoff(initialerRohstoff ?: Rohstoffe.entries.first())
        )
    }
    var menge by remember { mutableStateOf("1") }
    var gesamtpreis by remember(initialerGesamtpreis) {
        mutableStateOf(
            initialerGesamtpreis?.toIntOderNull()?.takeIf { preis -> preis > 0 }?.toString().orEmpty()
        )
    }
    val personen = when (handelsgut) {
        is HandelsgutAuswahl.Rohstoff -> rohstoffPersonen
        is HandelsgutAuswahl.AnleiheWert -> anleihePersonen
    }

    val aktuelleRunde = (spiel.aktuelleRunde - 1).coerceAtLeast(0)
    val handelbareAnleihen = spiel.anleihen
        .filter { eintrag ->
            spiel.erhalteAnleiheStatus(eintrag) == AnleiheStatus.OFFEN &&
                aktuelleRunde !in eintrag.handelsverlauf
        }
        .sortedWith(
            compareBy<AnleiheAnzeige> { it.schuldiger.name }
                .thenBy { it.faelligkeit }
                .thenBy { it.sondervermoegen.toIntOderNull() }
        )
    val mengeWert = menge.toIntOrNull()
    val preisWert = gesamtpreis.toIntOrNull()
    val letzterMarktpreis = when (val auswahl = handelsgut) {
        is HandelsgutAuswahl.Rohstoff ->
            spiel.aktuelleMarktpreise[auswahl.rohstoff]
                ?.takeUnless { preis -> preis.toDoubleOderNull() == 0.0 }
        is HandelsgutAuswahl.AnleiheWert ->
            auswahl.anleihe.handelsverlauf.maxByOrNull { (runde, _) -> runde }?.value?.preis
    }
    val vertragswertJeEinheit = when (handelsgut) {
        is HandelsgutAuswahl.Rohstoff -> if (
            preisWert != null && mengeWert != null && mengeWert > 0
        ) {
            preisWert.toDouble() / mengeWert
        } else {
            null
        }
        is HandelsgutAuswahl.AnleiheWert -> preisWert?.toDouble()
    }
    val marktpreisabweichung = relativeProzentabweichung(
        aktuellerWert = vertragswertJeEinheit,
        referenzwert = letzterMarktpreis?.toDoubleOderNull(),
    )
    val fehler = when {
        besitzer == null || erwerber == null -> "Es sind keine Handelspartner vorhanden."
        besitzer !in personen || erwerber !in personen ->
            "Die gewählten Handelspartner dürfen dieses Handelsgut nicht handeln."
        besitzer == erwerber -> "Verkäufer und Erwerber müssen verschieden sein."
        handelsgut is HandelsgutAuswahl.Rohstoff && (mengeWert == null || mengeWert <= 0) ->
            "Die Menge muss größer als 0 sein."
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
                Text("Laufende Runde: $aktuelleRunde")
                PersonenAuswahl("Verkäufer", personen, besitzer) { neuerBesitzer ->
                    besitzer = neuerBesitzer
                    val ausgewaehlteAnleihe = handelsgut as? HandelsgutAuswahl.AnleiheWert
                    if (ausgewaehlteAnleihe?.anleihe?.aktuellerBesitzer != neuerBesitzer) {
                        handelsgut = HandelsgutAuswahl.Rohstoff(Rohstoffe.entries.first())
                        if (neuerBesitzer !in rohstoffPersonen) {
                            besitzer = rohstoffPersonen.firstOrNull()
                        }
                        if (erwerber !in rohstoffPersonen || erwerber == besitzer) {
                            erwerber = rohstoffPersonen.firstOrNull { person -> person != besitzer }
                        }
                    }
                }
                PersonenAuswahl("Erwerber", personen, erwerber) { erwerber = it }
                HandelsgutAuswahlFeld(
                    auswahl = handelsgut,
                    handelbareAnleihen = handelbareAnleihen,
                    onSelect = { neueAuswahl ->
                        handelsgut = neueAuswahl
                        val erlaubtePersonen = when (neueAuswahl) {
                            is HandelsgutAuswahl.Rohstoff -> rohstoffPersonen
                            is HandelsgutAuswahl.AnleiheWert -> anleihePersonen
                        }
                        besitzer = when (neueAuswahl) {
                            is HandelsgutAuswahl.Rohstoff ->
                                besitzer?.takeIf { person -> person in erlaubtePersonen }
                                    ?: erlaubtePersonen.firstOrNull()
                            is HandelsgutAuswahl.AnleiheWert ->
                                neueAuswahl.anleihe.aktuellerBesitzer
                        }
                        if (erwerber !in erlaubtePersonen || erwerber == besitzer) {
                            erwerber = erlaubtePersonen.firstOrNull { person -> person != besitzer }
                        }
                    },
                )
                if (handelsgut is HandelsgutAuswahl.Rohstoff) {
                    Zahlenfeld("Menge (Stk)", menge) { menge = it }
                }
                Zahlenfeld("Gesamtpreis (ℳ)", gesamtpreis) { gesamtpreis = it }
                letzterMarktpreis?.let { marktpreis ->
                    val bezug = if (handelsgut is HandelsgutAuswahl.Rohstoff) " je Stück" else ""
                    Text(
                        text = "Letzter Marktpreis: ${marktpreis.zuMark()}$bezug",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    AbweichungsText(
                        label = "Abweichung im Vertrag",
                        abweichung = marktpreisabweichung,
                    )
                }
                Text(
                    text = if (handelsgut is HandelsgutAuswahl.Rohstoff) {
                        "Der Handel wird in der laufenden Runde bezahlt. " +
                            "Der daraus berechnete Rohstoffpreis gilt ab der Folgerunde."
                    } else {
                        "Die Anleihe wechselt mit der Zahlung in der laufenden Runde den Besitzer."
                    },
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
                    when (val auswahl = handelsgut) {
                        is HandelsgutAuswahl.Rohstoff -> onCreateRohstoff(
                            RohstoffHandel(
                                besitzer = requireNotNull(besitzer),
                                erwerber = requireNotNull(erwerber),
                                betrag = requireNotNull(preisWert).toZahlungsmittel(),
                                anzahl = requireNotNull(mengeWert),
                                rohstoff = auswahl.rohstoff,
                            )
                        )
                        is HandelsgutAuswahl.AnleiheWert -> onCreateAnleihe(
                            Anleihenhandel(
                                besitzer = requireNotNull(besitzer),
                                erwerber = requireNotNull(erwerber),
                                anleihe = auswahl.anleihe.anleihe,
                                preis = requireNotNull(preisWert).toZahlungsmittel(),
                            )
                        )
                    }
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
    aktuellerSpielerName: String,
    onDismiss: () -> Unit,
    onCreate: (Anleihenhandel) -> Unit,
) {
    val aktuellerSpieler = remember(spiel.spielerListe, aktuellerSpielerName) {
        spiel.spielerListe.firstOrNull { spieler -> spieler.name == aktuellerSpielerName }
    }
    val personen = remember(spiel.spielerListe) {
        spiel.spielerListe.map { it as JuristischePerson } + Geschäftsbank
    }
    val moeglicheKaeufer = remember(personen, aktuellerSpieler) {
        personen.filter { person -> person != aktuellerSpieler }
    }
    val aktuelleRunde = (spiel.aktuelleRunde - 1).coerceAtLeast(0)
    val auswahlOptionen = remember(spiel.anleihen, aktuellerSpieler, aktuelleRunde) {
        if (aktuellerSpieler == null) {
            emptyList()
        } else {
            val handelbareAnleihen = spiel.anleihen.filter { eintrag ->
                spiel.erhalteAnleiheStatus(eintrag) == AnleiheStatus.OFFEN &&
                    aktuelleRunde !in eintrag.handelsverlauf
            }
            listOf(AnleiheAuswahl(AnleiheVorgang.EMISSION)) +
                handelbareAnleihen
                    .filter { eintrag -> eintrag.aktuellerBesitzer == aktuellerSpieler }
                    .map { eintrag -> AnleiheAuswahl(AnleiheVorgang.VERKAUF, eintrag) } +
                handelbareAnleihen
                    .filter { eintrag ->
                        eintrag.schuldiger == aktuellerSpieler &&
                            eintrag.aktuellerBesitzer != aktuellerSpieler
                    }
                    .map { eintrag -> AnleiheAuswahl(AnleiheVorgang.RUECKKAUF, eintrag) }
        }
    }
    var auswahl by remember(auswahlOptionen) { mutableStateOf(auswahlOptionen.firstOrNull()) }
    var auswahlGeoeffnet by remember { mutableStateOf(false) }
    var erwerber by remember(personen, aktuellerSpieler) {
        mutableStateOf<JuristischePerson?>(
            moeglicheKaeufer.firstOrNull { person -> person == Geschäftsbank }
                ?: moeglicheKaeufer.firstOrNull()
        )
    }
    var nennwert by remember { mutableStateOf("") }
    var zins by remember { mutableStateOf("0") }
    var preis by remember { mutableStateOf("") }
    var laufzeit by remember { mutableStateOf("1") }

    val vorgang = auswahl?.vorgang ?: AnleiheVorgang.EMISSION
    val ausgewaehlteAnleihe = auswahl?.anleihe
    val verkaeufer = when (vorgang) {
        AnleiheVorgang.EMISSION,
        AnleiheVorgang.VERKAUF -> aktuellerSpieler
        AnleiheVorgang.RUECKKAUF -> ausgewaehlteAnleihe?.aktuellerBesitzer
    }
    val kaeufer = when (vorgang) {
        AnleiheVorgang.EMISSION,
        AnleiheVorgang.VERKAUF -> erwerber
        AnleiheVorgang.RUECKKAUF -> aktuellerSpieler
    }
    val nennwertWert = nennwert.toIntOrNull()
    val zinsWert = zins.toIntOrNull()
    val preisWert = if (vorgang == AnleiheVorgang.EMISSION) {
        nennwertWert
    } else {
        preis.toIntOrNull()
    }
    val laufzeitWert = laufzeit.toIntOrNull()
    val anleiheZinssatz = when (vorgang) {
        AnleiheVorgang.EMISSION -> if (
            nennwertWert != null && nennwertWert > 0 && zinsWert != null
        ) {
            zinsWert.toDouble() / nennwertWert * 100.0
        } else {
            null
        }
        AnleiheVorgang.VERKAUF,
        AnleiheVorgang.RUECKKAUF -> ausgewaehlteAnleihe?.let { eintrag ->
            val nennwert = eintrag.sondervermoegen.toDoubleOderNull()
            if (nennwert == 0.0) null else eintrag.unvermoegen.toDoubleOderNull() / nennwert * 100.0
        }
    }
    val leitzins = spiel.leitzinssatz(aktuelleRunde)?.toDouble()
    val leitzinsabweichung = relativeProzentabweichung(
        aktuellerWert = anleiheZinssatz,
        referenzwert = leitzins,
    )
    val fehler = when {
        aktuellerSpieler == null || auswahl == null -> "Der aktive Spieler ist nicht verfügbar."
        verkaeufer == null || kaeufer == null -> "Es sind keine passenden Beteiligten vorhanden."
        verkaeufer == kaeufer -> "Verkäufer und Käufer müssen verschieden sein."
        vorgang == AnleiheVorgang.EMISSION && (nennwertWert == null || nennwertWert <= 0) ->
            "Der Nennwert muss größer als 0 sein."
        vorgang == AnleiheVorgang.EMISSION && (zinsWert == null || zinsWert < 0) ->
            "Der Zins darf nicht negativ sein."
        preisWert == null || preisWert <= 0 -> "Der Preis muss größer als 0 sein."
        vorgang == AnleiheVorgang.EMISSION && (laufzeitWert == null || laufzeitWert <= 0) ->
            "Die Laufzeit muss größer als 0 sein."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anleihe erfassen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Runde: $aktuelleRunde")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    OutlinedButton(
                        onClick = { auswahlGeoeffnet = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Emittent: ${aktuellerSpieler?.name ?: "–"}\n" +
                                (auswahl?.bezeichnung() ?: "Keine Anleihe verfügbar")
                        )
                    }
                    DropdownMenu(
                        expanded = auswahlGeoeffnet,
                        onDismissRequest = { auswahlGeoeffnet = false },
                    ) {
                        auswahlOptionen.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.bezeichnung()) },
                                onClick = {
                                    auswahl = option
                                    erwerber = when (option.vorgang) {
                                        AnleiheVorgang.RUECKKAUF -> aktuellerSpieler
                                        AnleiheVorgang.EMISSION,
                                        AnleiheVorgang.VERKAUF -> moeglicheKaeufer.firstOrNull { person ->
                                            person == Geschäftsbank
                                        } ?: moeglicheKaeufer.firstOrNull()
                                    }
                                    auswahlGeoeffnet = false
                                },
                            )
                        }
                    }
                }

                when (vorgang) {
                    AnleiheVorgang.EMISSION -> {
                        PersonenAuswahl("Erster Erwerber", moeglicheKaeufer, erwerber) {
                            erwerber = it
                        }
                        Zahlenfeld("Sondervermögen / Nennwert (ℳ)", nennwert) { nennwert = it }
                        Zahlenfeld("Unvermögen / Zahlung je Runde (ℳ)", zins) { zins = it }
                        Zahlenfeld("Laufzeit (Runden)", laufzeit) { laufzeit = it }
                        Text(
                            text = "Zinsen werden ab der nächsten Runde bis einschließlich " +
                                "der Fälligkeitsrunde gezahlt.",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    AnleiheVorgang.VERKAUF,
                    AnleiheVorgang.RUECKKAUF -> {
                        val eintrag = ausgewaehlteAnleihe
                        Text("Schuldiger: ${eintrag?.schuldiger?.name ?: "–"}")
                        Text("Aktueller Besitzer: ${eintrag?.aktuellerBesitzer?.name ?: "–"}")
                        Text("Nennwert: ${eintrag?.sondervermoegen?.zuMark() ?: "–"}")
                        Text("Zins je Runde: ${eintrag?.unvermoegen?.zuMark() ?: "–"}")
                        if (vorgang == AnleiheVorgang.VERKAUF) {
                            PersonenAuswahl("Käufer", moeglicheKaeufer, erwerber) { erwerber = it }
                        } else {
                            Text("Rückkäufer: ${aktuellerSpieler?.name ?: "–"}")
                        }
                        Zahlenfeld(
                            if (vorgang == AnleiheVorgang.VERKAUF) {
                                "Verkaufspreis (ℳ)"
                            } else {
                                "Rückkaufpreis (ℳ)"
                            },
                            preis,
                        ) { preis = it }
                    }
                }
                Text(
                    text = "Anleihezins: ${anleiheZinssatz?.let(::prozentwertText) ?: "–"}",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text("Leitzins: ${leitzins?.let(::prozentwertText) ?: "–"}")
                AbweichungsText(
                    label = "Abweichung zum Leitzins",
                    abweichung = leitzinsabweichung,
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
                    val aktiverSpieler = requireNotNull(aktuellerSpieler)
                    val anleihe = when (vorgang) {
                        AnleiheVorgang.EMISSION -> Anleihe(
                            schuldiger = aktiverSpieler,
                            sondervermögen = requireNotNull(nennwertWert).toZahlungsmittel(),
                            unvermögen = requireNotNull(zinsWert).toZahlungsmittel(),
                            laufzeit = requireNotNull(laufzeitWert),
                        )
                        AnleiheVorgang.VERKAUF,
                        AnleiheVorgang.RUECKKAUF -> requireNotNull(ausgewaehlteAnleihe).anleihe
                    }
                    onCreate(
                        Anleihenhandel(
                            besitzer = requireNotNull(verkaeufer),
                            erwerber = requireNotNull(kaeufer),
                            anleihe = anleihe,
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
private fun HandelsgutAuswahlFeld(
    auswahl: HandelsgutAuswahl,
    handelbareAnleihen: List<AnleiheAnzeige>,
    onSelect: (HandelsgutAuswahl) -> Unit,
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
            Text("Handelsgut: ${auswahl.bezeichnung()}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rohstoffe", fontWeight = FontWeight.Bold) },
                enabled = false,
                onClick = {},
            )
            Rohstoffe.entries.forEach { rohstoff ->
                DropdownMenuItem(
                    text = { Text(rohstoff.str) },
                    onClick = {
                        onSelect(HandelsgutAuswahl.Rohstoff(rohstoff))
                        expanded = false
                    },
                )
            }
            if (handelbareAnleihen.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Anleihen", fontWeight = FontWeight.Bold) },
                    enabled = false,
                    onClick = {},
                )
                handelbareAnleihen.forEach { anleihe ->
                    DropdownMenuItem(
                        text = { Text(HandelsgutAuswahl.AnleiheWert(anleihe).bezeichnung()) },
                        onClick = {
                            onSelect(HandelsgutAuswahl.AnleiheWert(anleihe))
                            expanded = false
                        },
                    )
                }
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
