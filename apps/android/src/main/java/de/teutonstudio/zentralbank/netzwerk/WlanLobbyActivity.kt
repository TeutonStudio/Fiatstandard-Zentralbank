package de.teutonstudio.zentralbank.netzwerk

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoff
import de.teutonstudio.zentralbank.datenbank.Rohstoffe as WarenkorbRohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerFarbe
import de.teutonstudio.zentralbank.protokoll.LobbyKonfigurationDto
import de.teutonstudio.zentralbank.schnittstelle.kategorien.KartenAuswahl
import de.teutonstudio.zentralbank.schnittstelle.eingabe.WarenkorbBearbeitenDialog
import de.teutonstudio.zentralbank.schnittstelle.theme.CZBOracleRechnerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WlanSpielErstellenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WlanLobbyLaufzeit.initialisieren(applicationContext)
        setContent {
            CZBOracleRechnerTheme {
                val zustand by WlanLobbyLaufzeit.zustand.collectAsState()
                WlanSpielErstellenBildschirm(zustand = zustand, beiZurueck = ::finish)
            }
        }
    }
}

class WlanSpielBeitretenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WlanLobbyLaufzeit.initialisieren(applicationContext)
        setContent {
            CZBOracleRechnerTheme {
                val zustand by WlanLobbyLaufzeit.zustand.collectAsState()
                WlanSpielBeitretenBildschirm(zustand = zustand, beiZurueck = ::finish)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) WlanLobbyLaufzeit.sucheBeenden()
        super.onDestroy()
    }
}

@Composable
private fun WlanSpielErstellenBildschirm(
    zustand: WlanLobbyZustand,
    beiZurueck: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val berechtigung = lokaleNetzwerkBerechtigung()
    var seite by remember { mutableIntStateOf(1) }
    var lobbyName by remember { mutableStateOf("Fiatstandard WLAN-Spiel") }
    var maximaleSpieler by remember { mutableStateOf("7") }
    var hostName by remember { mutableStateOf("") }
    var hostPasswort by remember { mutableStateOf("") }
    var hostFarbe by remember { mutableStateOf(SpielerFarbe.ORANGE) }
    var leitzins by remember { mutableStateOf("15") }
    var inflationsziel by remember { mutableStateOf("2") }
    var normaleAbweichung by remember { mutableStateOf("0,5") }
    var starkeAbweichung by remember { mutableStateOf("2") }
    var leitzinsSchritt by remember { mutableStateOf("1") }
    var startGuthaben by remember { mutableStateOf("100") }
    var karte by remember { mutableStateOf<KartenVorlage?>(null) }
    val warenkorb = remember { mutableStateMapOf<WarenkorbRohstoff, Int>() }
    var warenkorbDialogOffen by remember { mutableStateOf(false) }
    val startRohstoffe = remember { mutableStateMapOf<Rohstoff, String>() }
    val startBauteile = remember {
        mutableStateMapOf<BauteilTyp, String>().apply { put(BauteilTyp.HAUPTBAHNHOF, "1") }
    }

    LaunchedEffect(zustand.sitzung?.sessionToken) {
        if (zustand.sitzung == null) return@LaunchedEffect
        while (isActive) {
            delay(AKTUALISIERUNGSINTERVALL_MS)
            WlanLobbyLaufzeit.aktualisieren()
        }
    }

    if (zustand.host != null && zustand.lobby != null) {
        LobbyBildschirm(
            zustand = zustand,
            istHost = true,
            beiZurueck = beiZurueck,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Kopf("Neues Spiel (WLAN)", beiZurueck)
        FehlerUndMeldung(zustand)

        when (seite) {
            1 -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text("Lobby und eigener Spieler", style = MaterialTheme.typography.titleLarge) }
                item { Text("Der Host legt die Partie fest; jeder Spieler legt sein eigenes Profil und Passwort fest.") }
                item { Eingabe("Lobbyname", lobbyName) { lobbyName = it } }
                item { Eingabe("Maximale Spieler (2–7)", maximaleSpieler) { maximaleSpieler = it } }
                item { Eingabe("Dein Spielername", hostName) { hostName = it } }
                item {
                    OutlinedTextField(
                        value = hostPasswort,
                        onValueChange = { hostPasswort = it },
                        label = { Text("Dein Passwort") },
                        supportingText = { Text("Wird für Wiederverbindung und spätere Fortsetzung benötigt.") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { FarbAuswahl(hostFarbe) { hostFarbe = it } }
                item { Text("Zentralbank", style = MaterialTheme.typography.titleMedium) }
                item { Eingabe("Leitzins in %", leitzins) { leitzins = it } }
                item { Eingabe("Inflationsziel in %", inflationsziel) { inflationsziel = it } }
                item { Eingabe("Normale Abweichung in %", normaleAbweichung) { normaleAbweichung = it } }
                item { Eingabe("Starke Abweichung in %", starkeAbweichung) { starkeAbweichung = it } }
                item { Eingabe("Leitzinsschritt in %", leitzinsSchritt) { leitzinsSchritt = it } }
            }
            2 -> Column(modifier = Modifier.weight(1f)) {
                KartenAuswahl(
                    ausgewaehlteKarte = karte,
                    beiAuswahl = { karte = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            3 -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("Warenkorb", style = MaterialTheme.typography.titleMedium)
                                Text("${warenkorb.count { it.value > 0 }} Rohstoffe ausgewählt")
                            }
                            OutlinedButton(onClick = { warenkorbDialogOffen = true }) {
                                Text("Bearbeiten")
                            }
                        }
                    }
                }
                item { Text("Gemeinsame Startrohstoffe je Spieler", style = MaterialTheme.typography.titleLarge) }
                items(Rohstoff.entries) { rohstoff ->
                    MengenEingabe(rohstoff.name, startRohstoffe[rohstoff].orEmpty()) { startRohstoffe[rohstoff] = it }
                }
                item { Eingabe("Startguthaben je Spieler in Mark", startGuthaben) { startGuthaben = it } }
                item { Text("Startbauteile je Spieler", style = MaterialTheme.typography.titleLarge) }
                items(BauteilTyp.entries) { bauteil ->
                    MengenEingabe(bauteil.name, startBauteile[bauteil].orEmpty()) { startBauteile[bauteil] = it }
                }
            }
            else -> Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Lobby öffnen", style = MaterialTheme.typography.titleLarge)
                Text("${karte?.name ?: "Keine Karte"} · bis $maximaleSpieler Spieler · Leitzins $leitzins %")
                Text("$hostName · ${hostFarbe.name}")
                Text("Der Spielstand wird erst erzeugt, wenn in der Lobby alle Spieler bereit sind und du das Spiel startest.")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = { if (seite > 1) seite-- else beiZurueck() }) { Text("Zurück") }
            if (seite < 4) {
                Button(
                    onClick = { seite++ },
                    enabled = seite != 2 || karte != null,
                ) { Text("Weiter") }
            } else {
                Button(onClick = {
                    val ausgewaehlteKarte = karte ?: return@Button
                    berechtigung {
                        scope.launch {
                            runCatching {
                                LobbyKonfigurationDto(
                                    name = lobbyName,
                                    maximaleSpieler = maximaleSpieler.toInt(),
                                    karte = ausgewaehlteKarte,
                                    leitzinsBasispunkte = prozentZuBasispunkte(leitzins),
                                    inflationszielBasispunkte = prozentZuBasispunkte(inflationsziel),
                                    normaleAbweichungBasispunkte = prozentZuBasispunkte(normaleAbweichung),
                                    starkeAbweichungBasispunkte = prozentZuBasispunkte(starkeAbweichung),
                                    leitzinsSchrittBasispunkte = prozentZuBasispunkte(leitzinsSchritt),
                                    warenkorb = warenkorb.alsLobbyWarenkorb(),
                                    startGuthabenCent = markZuCent(startGuthaben),
                                    startRohstoffe = startRohstoffe.positiveWerte(),
                                    startBauteile = startBauteile.positiveWerte(),
                                )
                            }.onSuccess { konfiguration ->
                                WlanLobbyLaufzeit.neueLobbyHosten(
                                    konfiguration = konfiguration,
                                    spielerName = hostName,
                                    passwort = hostPasswort,
                                    farbe = hostFarbe.name,
                                )
                            }
                        }
                    }
                }) { Text("Lobby öffnen") }
            }
        }
    }

    if (warenkorbDialogOffen) {
        WarenkorbBearbeitenDialog(
            warenkorb = warenkorb,
            beiAbbruch = { warenkorbDialogOffen = false },
            beiSpeichern = { neuerWarenkorb ->
                warenkorb.clear()
                warenkorb.putAll(neuerWarenkorb)
                warenkorbDialogOffen = false
            },
        )
    }
}

@Composable
private fun WlanSpielBeitretenBildschirm(
    zustand: WlanLobbyZustand,
    beiZurueck: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val berechtigung = lokaleNetzwerkBerechtigung()
    var name by remember { mutableStateOf("") }
    var passwort by remember { mutableStateOf("") }
    var farbe by remember { mutableStateOf(SpielerFarbe.ORANGE) }

    LaunchedEffect(zustand.sitzung?.sessionToken) {
        if (zustand.sitzung == null) return@LaunchedEffect
        while (isActive) {
            delay(AKTUALISIERUNGSINTERVALL_MS)
            WlanLobbyLaufzeit.aktualisieren()
        }
    }

    if (zustand.sitzung != null && zustand.lobby != null) {
        LobbyBildschirm(zustand = zustand, istHost = false, beiZurueck = beiZurueck)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Kopf("Spiel beitreten", beiZurueck)
        FehlerUndMeldung(zustand)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Eigenes Spielerprofil", style = MaterialTheme.typography.titleLarge) }
            item { Eingabe("Spielername", name) { name = it } }
            item {
                OutlinedTextField(
                    value = passwort,
                    onValueChange = { passwort = it },
                    label = { Text("Passwort") },
                    supportingText = { Text("Identifiziert deinen Spieler bei Wiederverbindung und späterer Fortsetzung.") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { FarbAuswahl(farbe) { farbe = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        berechtigung { WlanLobbyLaufzeit.sucheStarten() }
                    }) { Text(if (zustand.sucheAktiv) "Erneut suchen" else "WLAN-Spiele suchen") }
                    if (zustand.sucheAktiv) {
                        OutlinedButton(onClick = WlanLobbyLaufzeit::sucheBeenden) { Text("Suche stoppen") }
                    }
                }
            }

            if (zustand.gefundeneLobbys.isEmpty() && zustand.gefundeneSpiele.isEmpty()) {
                item { Text("Keine offene Lobby und kein fortgesetztes WLAN-Spiel gefunden.") }
            }

            if (zustand.gefundeneLobbys.isNotEmpty()) {
                item { Text("Neue Spiele", style = MaterialTheme.typography.titleLarge) }
            }
            items(zustand.gefundeneLobbys) { lobby ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lobby.name, style = MaterialTheme.typography.titleMedium)
                            Text("Offene Lobby · ${lobby.host}:${lobby.port}")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    WlanLobbyLaufzeit.beitreten(lobby, name, passwort, farbe.name)
                                }
                            },
                            enabled = name.isNotBlank() && passwort.isNotBlank(),
                        ) { Text("Beitreten") }
                    }
                }
            }

            if (zustand.gefundeneSpiele.isNotEmpty()) {
                item { Text("Fortgesetzte Spiele", style = MaterialTheme.typography.titleLarge) }
            }
            items(zustand.gefundeneSpiele) { spiel ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(spiel.name, style = MaterialTheme.typography.titleMedium)
                            Text("Spiel ${spiel.spielId} · ${spiel.host}:${spiel.port}")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    WlanLobbyLaufzeit.laufendemSpielBeitreten(spiel, name, passwort)
                                    if (WlanMehrspielerLaufzeit.zustand.value.sitzung != null) {
                                        context.startActivity(Intent(context, WlanMehrspielerActivity::class.java))
                                    }
                                }
                            },
                            enabled = name.isNotBlank(),
                        ) { Text("Fortsetzen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyBildschirm(
    zustand: WlanLobbyZustand,
    istHost: Boolean,
    beiZurueck: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lobby = requireNotNull(zustand.lobby)
    val eigenerSpieler = lobby.spieler.firstOrNull { it.id == zustand.sitzung?.spielerId }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Kopf(lobby.konfiguration.name, beiZurueck)
        FehlerUndMeldung(zustand)
        Text(
            "${lobby.spieler.size}/${lobby.konfiguration.maximaleSpieler} Spieler · ${lobby.konfiguration.karte.name}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("Leitzins ${basispunkteText(lobby.konfiguration.leitzinsBasispunkte)}")
        if (zustand.host != null) {
            Text("Host: ${zustand.host.adresse}:${zustand.host.port}", style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lobby.spieler) { spieler ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(spieler.name)
                            Text(spieler.farbe)
                        }
                        Text(if (spieler.bereit) "Bereit" else "Nicht bereit")
                    }
                }
            }
        }

        if (lobby.spielId == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { scope.launch { WlanLobbyLaufzeit.bereitSetzen(eigenerSpieler?.bereit != true) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (eigenerSpieler?.bereit == true) "Nicht bereit" else "Bereit")
                }
                if (istHost) {
                    Button(
                        onClick = { scope.launch { WlanLobbyLaufzeit.spielStarten() } },
                        enabled = lobby.spieler.size >= MehrspielerLobbyDienst.MINDEST_SPIELER &&
                            lobby.spieler.all { it.bereit },
                        modifier = Modifier.weight(1f),
                    ) { Text("Spiel starten") }
                }
            }
        } else {
            Text("Spiel ${lobby.spielId} wurde gestartet.", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = {
                    context.startActivity(Intent(context, WlanMehrspielerActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zur WLAN-Spielansicht") }
        }
    }
}

@Composable
private fun Kopf(titel: String, beiZurueck: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(titel, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = beiZurueck) { Text("Zurück") }
    }
}

@Composable
private fun FehlerUndMeldung(zustand: WlanLobbyZustand) {
    zustand.fehler?.let { fehler ->
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text(fehler, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = WlanLobbyLaufzeit::fehlerVerwerfen) { Text("Schließen") }
            }
        }
    }
    zustand.meldung?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
}

@Composable
private fun Eingabe(label: String, wert: String, beiAenderung: (String) -> Unit) {
    OutlinedTextField(
        value = wert,
        onValueChange = beiAenderung,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MengenEingabe(label: String, wert: String, beiAenderung: (String) -> Unit) {
    OutlinedTextField(
        value = wert,
        onValueChange = { neu -> beiAenderung(neu.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FarbAuswahl(auswahl: SpielerFarbe, beiAuswahl: (SpielerFarbe) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Spielerfarbe")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SpielerFarbe.entries) { farbe ->
                if (farbe == auswahl) {
                    Button(onClick = { beiAuswahl(farbe) }) { Text(farbe.name) }
                } else {
                    OutlinedButton(onClick = { beiAuswahl(farbe) }) { Text(farbe.name) }
                }
            }
        }
    }
}

@Composable
private fun lokaleNetzwerkBerechtigung(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var ausstehend by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt ->
        val aktion = ausstehend
        ausstehend = null
        if (erlaubt) aktion?.invoke()
    }
    return { aktion ->
        val erlaubt = ContextCompat.checkSelfPermission(context, WLAN_BERECHTIGUNG) == PackageManager.PERMISSION_GRANTED
        if (erlaubt) aktion() else {
            ausstehend = aktion
            launcher.launch(WLAN_BERECHTIGUNG)
        }
    }
}

private fun prozentZuBasispunkte(text: String): Int =
    (text.trim().replace(',', '.').toDouble() * 100.0).roundToInt()

private fun markZuCent(text: String): Long =
    (text.trim().replace(',', '.').toDouble() * 100.0).roundToInt().toLong()

private fun <K : Enum<K>> Map<K, String>.positiveWerte(): Map<String, Int> = entries
    .mapNotNull { (schluessel, text) ->
        val menge = text.toIntOrNull() ?: 0
        if (menge > 0) schluessel.name to menge else null
    }
    .toMap()

private fun Map<WarenkorbRohstoff, Int>.alsLobbyWarenkorb(): Map<String, Int> = entries
    .mapNotNull { (rohstoff, menge) ->
        menge.takeIf { it > 0 }?.let { rohstoff.zuRohstoff().name to it }
    }
    .toMap()

private fun basispunkteText(wert: Int): String =
    "${wert / 100},${kotlin.math.abs(wert % 100).toString().padStart(2, '0')} %"

private const val WLAN_BERECHTIGUNG = "android.permission.NEARBY_WIFI_DEVICES"
private const val AKTUALISIERUNGSINTERVALL_MS = 1_500L
