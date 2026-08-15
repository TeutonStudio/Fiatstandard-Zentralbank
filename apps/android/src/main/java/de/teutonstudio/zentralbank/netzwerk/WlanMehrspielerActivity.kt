package de.teutonstudio.zentralbank.netzwerk

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import de.teutonstudio.zentralbank.protokoll.SpielAktionDto
import de.teutonstudio.zentralbank.schnittstelle.theme.CZBOracleRechnerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WlanMehrspielerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WlanMehrspielerLaufzeit.initialisieren(applicationContext)
        setContent {
            CZBOracleRechnerTheme {
                val zustand by WlanMehrspielerLaufzeit.zustand.collectAsState()
                val spielstaende by remember(applicationContext) {
                    WlanMehrspielerLaufzeit.spielstaende(applicationContext)
                }.collectAsState(initial = emptyList())
                WlanMehrspielerBildschirm(zustand, spielstaende, ::finish)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) WlanMehrspielerLaufzeit.sucheBeenden()
        super.onDestroy()
    }
}

@Composable
private fun WlanMehrspielerBildschirm(
    zustand: WlanMehrspielerZustand,
    spielstaende: List<SpielstandUebersicht>,
    beiZurueck: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var spielerName by remember { mutableStateOf("") }
    var passwort by remember { mutableStateOf("") }
    var manuellerHost by remember { mutableStateOf("") }
    var manuellerPort by remember { mutableStateOf("") }
    var manuelleSpielId by remember { mutableStateOf("") }
    var berechtigungsFehler by remember { mutableStateOf<String?>(null) }
    var nachBerechtigung by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(zustand.sitzung?.sessionToken) {
        if (zustand.sitzung == null) return@LaunchedEffect
        while (isActive) {
            delay(AKTUALISIERUNGSINTERVALL_MS)
            WlanMehrspielerLaufzeit.aktualisieren()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { erlaubt ->
        val aktion = nachBerechtigung
        nachBerechtigung = null
        if (erlaubt) {
            berechtigungsFehler = null
            aktion?.invoke()
        } else {
            berechtigungsFehler = "Zugriff auf das lokale Netzwerk wurde nicht erlaubt."
        }
    }

    fun mitWlanBerechtigung(aktion: () -> Unit) {
        val bereitsErlaubt = ContextCompat.checkSelfPermission(context, WLAN_BERECHTIGUNG) ==
            PackageManager.PERMISSION_GRANTED
        if (bereitsErlaubt) {
            aktion()
        } else {
            nachBerechtigung = aktion
            permissionLauncher.launch(WLAN_BERECHTIGUNG)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("WLAN-Mehrspieler", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = beiZurueck) { Text("Zurück") }
        }
        Text("Ein Gerät hostet den autoritativen Spielstand; andere Geräte treten im selben WLAN als vorhandener Spieler bei.")

        berechtigungsFehler?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        zustand.fehler?.let { meldung ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(meldung, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = WlanMehrspielerLaufzeit::fehlerVerwerfen) {
                        Text("Meldung schließen")
                    }
                }
            }
        }
        zustand.meldung?.let { Text(it) }

        Text("Spiel hosten", style = MaterialTheme.typography.titleLarge)
        Text("Der Host verwendet einen gespeicherten Spielstand und läuft weiter, wenn diese Lobby geschlossen wird.")
        val hostbareSpiele = spielstaende.filter { it.id >= 0 && it.istLadbar }
        val defekteSpiele = spielstaende.filter { it.id >= 0 && !it.istLadbar }
        if (hostbareSpiele.isEmpty()) {
            Text(
                if (defekteSpiele.isEmpty()) {
                    "Noch kein gespeicherter Spielstand vorhanden."
                } else {
                    "Kein rekonstruierbarer Spielstand kann derzeit gehostet werden."
                },
            )
        }
        hostbareSpiele.forEach { spiel ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Spiel ${spiel.id}")
                        Text("Runde ${spiel.runde} · ${spiel.spielerNamen.joinToString()}")
                    }
                    Button(onClick = {
                        mitWlanBerechtigung {
                            scope.launch { WlanMehrspielerLaufzeit.hosten(spiel.id) }
                        }
                    }) { Text("Hosten") }
                }
            }
        }
        defekteSpiele.forEach { spiel ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Spiel ${spiel.id} · nicht ladbar")
                    if (spiel.spielerNamen.isNotEmpty()) {
                        Text(spiel.spielerNamen.joinToString(prefix = "Spieler: "))
                    }
                    Text(
                        spiel.ladeFehler ?: "Der Spielstand kann nicht rekonstruiert werden.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text("Der Spielstand kann unter „Spielstände verwalten“ gelöscht werden.")
                }
            }
        }
        zustand.host?.let { host ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Host aktiv", style = MaterialTheme.typography.titleMedium)
                    Text("Spiel ${host.spielId} · ${host.adresse}:${host.port}")
                    Text(host.spieler.joinToString(prefix = "Spieler: "))
                    Button(onClick = WlanMehrspielerLaufzeit::stoppeHost) { Text("Host beenden") }
                }
            }
        }

        Text("Spiel beitreten", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = spielerName,
            onValueChange = { spielerName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Eigener Spielername") },
            singleLine = true,
        )
        OutlinedTextField(
            value = passwort,
            onValueChange = { passwort = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Spielerpasswort, falls gesetzt") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                mitWlanBerechtigung(WlanMehrspielerLaufzeit::sucheStarten)
            }) { Text(if (zustand.sucheAktiv) "Erneut suchen" else "Spiele im WLAN suchen") }
            if (zustand.sucheAktiv) {
                TextButton(onClick = WlanMehrspielerLaufzeit::sucheBeenden) { Text("Suche stoppen") }
            }
        }
        zustand.gefundeneSpiele.forEach { fund ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(fund.name)
                        Text("Spiel ${fund.spielId} · ${fund.host}:${fund.port}")
                    }
                    Button(onClick = {
                        mitWlanBerechtigung {
                            scope.launch { WlanMehrspielerLaufzeit.beitreten(fund, spielerName, passwort) }
                        }
                    }) { Text("Beitreten") }
                }
            }
        }

        Text("Manuell verbinden", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = manuellerHost,
            onValueChange = { manuellerHost = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Host-IP, z. B. 192.168.178.42") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manuellerPort,
                onValueChange = { manuellerPort = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                label = { Text("Port") },
                singleLine = true,
            )
            OutlinedTextField(
                value = manuelleSpielId,
                onValueChange = { manuelleSpielId = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                label = { Text("Spiel-ID") },
                singleLine = true,
            )
        }
        val portGueltig = manuellerPort.toIntOrNull()?.let { it in 1..65535 } == true
        Button(
            enabled = manuellerHost.isNotBlank() && portGueltig && manuelleSpielId.toLongOrNull() != null,
            onClick = {
                mitWlanBerechtigung {
                    scope.launch {
                        WlanMehrspielerLaufzeit.manuellBeitreten(
                            host = manuellerHost,
                            port = requireNotNull(manuellerPort.toIntOrNull()),
                            spielId = requireNotNull(manuelleSpielId.toLongOrNull()),
                            spielerName = spielerName,
                            passwort = passwort,
                        )
                    }
                }
            },
        ) { Text("Manuell beitreten") }

        zustand.sitzung?.let { sitzung ->
            Text("Verbunden als ${sitzung.spielerId}", style = MaterialTheme.typography.titleLarge)
            zustand.beobachtung?.let { antwort ->
                val beobachtung = antwort.beobachtung
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Revision ${antwort.revision} · Runde ${beobachtung.runde}")
                        Text("Am Zug: ${beobachtung.zug?.aktiverSpieler?.wert ?: "niemand"}")
                        Text("Eigenes Geld: ${beobachtung.eigeneWirtschaft.geld}")
                    }
                }
            }
            Button(onClick = { scope.launch { WlanMehrspielerLaufzeit.aktualisieren() } }) {
                Text("Stand aktualisieren")
            }
            Text("Aktuell erlaubte Aktionen", style = MaterialTheme.typography.titleMedium)
            val aktionen = zustand.erlaubteAktionen?.aktionen.orEmpty()
            if (aktionen.isEmpty()) {
                Text("Für diesen Spieler ist im aktuellen Zustand keine Aktion freigegeben.")
            }
            aktionen.forEach { aktion ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { scope.launch { WlanMehrspielerLaufzeit.aktionAusfuehren(aktion) } },
                ) { Text(aktion.bezeichnung()) }
            }
        }
    }
}

private fun SpielAktionDto.bezeichnung(): String = javaClass.simpleName
    .replace(Regex("([a-z])([A-Z])"), "$1 $2")

private const val WLAN_BERECHTIGUNG = "android.permission.NEARBY_WIFI_DEVICES"
private const val AKTUALISIERUNGSINTERVALL_MS = 1_500L
