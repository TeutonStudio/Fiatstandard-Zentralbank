package de.teutonstudio.zentralbank.spielbrett

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.teutonstudio.zentralbank.daten.karten.KartenAblage
import de.teutonstudio.zentralbank.daten.karten.KartenReferenz
import de.teutonstudio.zentralbank.daten.karten.KartenReferenzMetadaten
import de.teutonstudio.zentralbank.daten.karten.MAX_REFERENZ_BREITE
import de.teutonstudio.zentralbank.daten.karten.MIN_REFERENZ_BREITE
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

enum class KartenModus(val beschriftung: String) {
    BAUEN("Bauen"),
    SPIELEN("Spielen"),
}

internal enum class KartenWerkzeug(val beschriftung: String) {
    WASSER("Wasser"),
    EBENE("Ebene"),
    WALD("Wald"),
    GEBIRGE("Gebirge"),
    WUESTE("Wüste"),
    SUMPF("Sumpf"),
}

@Composable
fun KartenEditorDialog(
    ausgangsvorlage: KartenVorlage,
    ablage: KartenAblage,
    beiAbbruch: () -> Unit,
    beiGespeichert: (KartenVorlage) -> Unit,
) {
    val verlauf = remember(ausgangsvorlage) { mutableStateListOf(ausgangsvorlage) }
    var verlaufIndex by remember(ausgangsvorlage) { mutableIntStateOf(0) }
    val entwurf = verlauf[verlaufIndex]
    var name by remember(ausgangsvorlage) { mutableStateOf(ausgangsvorlage.name) }
    var werkzeug by remember { mutableStateOf(KartenWerkzeug.EBENE) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }
    var wirdGespeichert by remember { mutableStateOf(false) }
    var referenzWirdGeladen by remember(ausgangsvorlage) { mutableStateOf(true) }
    var referenzBild by remember(ausgangsvorlage) { mutableStateOf<ImageBitmap?>(null) }
    var referenzAusrichten by remember(ausgangsvorlage) { mutableStateOf(false) }
    val draufsichtStatus = rememberKartenDraufsichtStatus()
    val referenzStatus = remember(ausgangsvorlage) { KartenReferenzEditorStatus() }
    val bauHimmel = remember { HimmelsDarstellung.fuerUhrzeit(12f) }
    val scope = rememberCoroutineScope()
    val bildAuswahl = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            referenzWirdGeladen = true
            fehlermeldung = null
            scope.launch {
                var importierteReferenz: KartenReferenz? = null
                runCatching {
                    val referenz = ablage.referenzImportieren(
                        uri = uri,
                        initialeBreiteInBrettEinheiten = entwurf.empfohleneReferenzBreite(),
                    )
                    importierteReferenz = referenz
                    referenz to referenz.bildDatei.ladeReferenzImageBitmap()
                }.onSuccess { (referenz, bild) ->
                    ablage.referenzEntwurfVerwerfen(referenzStatus.referenz)
                    referenzStatus.setze(referenz)
                    referenzBild = bild
                    referenzAusrichten = true
                    referenzWirdGeladen = false
                }.onFailure { fehler ->
                    ablage.referenzEntwurfVerwerfen(importierteReferenz)
                    referenzWirdGeladen = false
                    fehlermeldung = fehler.message ?: "Referenzbild konnte nicht geladen werden."
                }
            }
        }
    }

    LaunchedEffect(ablage, ausgangsvorlage.id) {
        if (referenzStatus.initialisiert) {
            referenzWirdGeladen = false
            return@LaunchedEffect
        }
        runCatching {
            val referenz = ablage.referenzLaden(ausgangsvorlage.id)
            referenz to referenz?.bildDatei?.ladeReferenzImageBitmap()
        }.onSuccess { (referenz, bild) ->
            referenzStatus.initialisiere(referenz)
            referenzBild = bild
            referenzWirdGeladen = false
        }.onFailure { fehler ->
            referenzStatus.initialisiere(null)
            referenzWirdGeladen = false
            fehlermeldung = fehler.message ?: "Referenzbild konnte nicht geladen werden."
        }
    }

    fun abbrechen() {
        ablage.referenzEntwurfVerwerfen(referenzStatus.referenz)
        beiAbbruch()
    }

    fun uebernehme(neu: KartenVorlage) {
        if (neu == entwurf) return
        while (verlauf.lastIndex > verlaufIndex) verlauf.removeAt(verlauf.lastIndex)
        verlauf += neu
        verlaufIndex = verlauf.lastIndex
    }

    Dialog(
        onDismissRequest = { if (!wirdGespeichert) abbrechen() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Spielkarte – Draufsicht", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Bauen") })
                        FilterChip(
                            selected = false,
                            enabled = false,
                            onClick = {},
                            label = { Text("Spielen") },
                        )
                    }
                }
                Text(
                    "Das Dreiecksraster ist unbegrenzt. Nicht belegte Dreiecke stellen Wasser dar.",
                    style = MaterialTheme.typography.bodySmall,
                )
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val breiteAnsicht = maxWidth >= 840.dp
                    val werkzeuge: @Composable (Modifier) -> Unit = { modifier ->
                        KartenWerkzeugleiste(
                            modifier = modifier,
                            name = name,
                            beiName = { name = it },
                            karte = entwurf,
                            werkzeug = werkzeug,
                            beiWerkzeug = { werkzeug = it },
                            beiAnsichtZuruecksetzen = draufsichtStatus::zuruecksetzen,
                            referenz = referenzStatus.referenz,
                            referenzWirdGeladen = referenzWirdGeladen,
                            referenzAusrichten = referenzAusrichten,
                            beiReferenzAuswaehlen = { bildAuswahl.launch("image/*") },
                            beiReferenzAusrichten = { referenzAusrichten = it },
                            beiReferenzMetadaten = { metadaten ->
                                referenzStatus.aktualisiereMetadaten { metadaten }
                            },
                            beiReferenzEntfernen = {
                                ablage.referenzEntwurfVerwerfen(referenzStatus.referenz)
                                referenzStatus.setze(null)
                                referenzBild = null
                                referenzAusrichten = false
                            },
                            kannRueckgaengig = verlaufIndex > 0,
                            kannWiederholen = verlaufIndex < verlauf.lastIndex,
                            beiRueckgaengig = { if (verlaufIndex > 0) verlaufIndex-- },
                            beiWiederholen = { if (verlaufIndex < verlauf.lastIndex) verlaufIndex++ },
                        )
                    }
                    val editor: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier = modifier) {
                            KartenEditorDraufsicht(
                                karte = entwurf,
                                status = draufsichtStatus,
                                referenzStatus = referenzStatus,
                                referenzBild = referenzBild,
                                referenzAusrichten = referenzAusrichten,
                                modifier = Modifier.fillMaxSize(),
                                onDreieckBeruehrt = { treffer ->
                                    fehlermeldung = null
                                    uebernehme(entwurf.wendeWerkzeugAn(treffer, werkzeug))
                                },
                            )
                            SpielbrettKompass(
                                himmel = bauHimmel,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            )
                            Text(
                                text = if (referenzAusrichten) {
                                    "Referenz ausrichten · Ziehen: verschieben · Pinch: skalieren"
                                } else {
                                    "Tippen: Gelände bearbeiten · Ziehen: verschieben · Pinch: zoomen"
                                },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    if (breiteAnsicht) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            werkzeuge(Modifier.widthIn(max = 360.dp).fillMaxSize())
                            editor(Modifier.weight(1f).fillMaxSize())
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            werkzeuge(Modifier.fillMaxWidth().heightIn(max = 330.dp))
                            editor(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                fehlermeldung?.let { meldung ->
                    Text(meldung, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = ::abbrechen, enabled = !wirdGespeichert) {
                        Text("Abbrechen")
                    }
                    Button(
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = name.isNotBlank() && !wirdGespeichert && !referenzWirdGeladen,
                        onClick = {
                            wirdGespeichert = true
                            fehlermeldung = null
                            scope.launch {
                                runCatching {
                                    ablage.eigeneKarteSpeichern(
                                        vorlage = entwurf.copy(name = name.trim()),
                                        referenz = referenzStatus.referenz,
                                    )
                                }.onSuccess(beiGespeichert).onFailure { fehler ->
                                    wirdGespeichert = false
                                    fehlermeldung = fehler.message ?: "Karte konnte nicht gespeichert werden."
                                }
                            }
                        },
                    ) {
                        Text(if (wirdGespeichert) "Speichert …" else "Als eigene Karte speichern")
                    }
                }
            }
        }
    }

}

@Composable
private fun KartenWerkzeugleiste(
    modifier: Modifier,
    name: String,
    beiName: (String) -> Unit,
    karte: KartenVorlage,
    werkzeug: KartenWerkzeug,
    beiWerkzeug: (KartenWerkzeug) -> Unit,
    beiAnsichtZuruecksetzen: () -> Unit,
    referenz: KartenReferenz?,
    referenzWirdGeladen: Boolean,
    referenzAusrichten: Boolean,
    beiReferenzAuswaehlen: () -> Unit,
    beiReferenzAusrichten: (Boolean) -> Unit,
    beiReferenzMetadaten: (KartenReferenzMetadaten) -> Unit,
    beiReferenzEntfernen: () -> Unit,
    kannRueckgaengig: Boolean,
    kannWiederholen: Boolean,
    beiRueckgaengig: () -> Unit,
    beiWiederholen: () -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = beiName,
            label = { Text("Kartenname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = beiRueckgaengig, enabled = kannRueckgaengig) {
                Text("Rückgängig")
            }
            OutlinedButton(onClick = beiWiederholen, enabled = kannWiederholen) {
                Text("Wiederholen")
            }
        }
        Text(
            "Gespeichertes Hexagon: Radius ${karte.hexagon.radius} · " +
                "${karte.hexagon.anzahlFelder} Dreiecke",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Ansicht", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = beiAnsichtZuruecksetzen) {
            Text("Zentrieren")
        }
        Text("Referenzbild", style = MaterialTheme.typography.titleSmall)
        when {
            referenzWirdGeladen -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Referenzbild wird geladen …")
            }
            referenz == null -> {
                OutlinedButton(onClick = beiReferenzAuswaehlen) {
                    Text("Referenzbild auswählen")
                }
                Text(
                    "Das Bild wird nur als lokale Hilfe im Baumodus verwendet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> ReferenzWerkzeuge(
                referenz = referenz,
                karte = karte,
                ausrichten = referenzAusrichten,
                beiAuswaehlen = beiReferenzAuswaehlen,
                beiAusrichten = beiReferenzAusrichten,
                beiMetadaten = beiReferenzMetadaten,
                beiEntfernen = beiReferenzEntfernen,
            )
        }
        Text("Gelände", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KartenWerkzeug.entries.forEach { eintrag ->
                FilterChip(
                    selected = werkzeug == eintrag,
                    onClick = { beiWerkzeug(eintrag) },
                    label = { Text(eintrag.beschriftung) },
                )
            }
        }
        Text(
            "Beim Setzen außerhalb wächst der Radius automatisch. Wasser wird nicht als Fläche " +
                "gespeichert oder dargestellt.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${karte.gelaendefelder.size} Geländedreiecke",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

internal fun KartenVorlage.wendeWerkzeugAn(
    treffer: DreieckTreffer,
    werkzeug: KartenWerkzeug,
): KartenVorlage {
    val position = treffer.position.zuKartenFeld()
    val gelaende = werkzeug.gelaendeOderNull()
    val land = landNachPosition.toMutableMap().apply {
        if (gelaende == null) remove(position) else put(position, gelaende)
    }
    val neueFelder = land.zuSortiertenGelaendefeldern()
    return copy(
        hexagon = hexagon.mitMindestradiusFuer(neueFelder.map(GelaendeFeld::position)),
        gelaendefelder = neueFelder,
    )
}

private fun KartenWerkzeug.gelaendeOderNull(): GelaendeTyp? = when (this) {
    KartenWerkzeug.WASSER -> null
    KartenWerkzeug.EBENE -> GelaendeTyp.EBENE
    KartenWerkzeug.WALD -> GelaendeTyp.WALD
    KartenWerkzeug.GEBIRGE -> GelaendeTyp.GEBIRGE
    KartenWerkzeug.WUESTE -> GelaendeTyp.WUESTE
    KartenWerkzeug.SUMPF -> GelaendeTyp.SUMPF
}

@Composable
private fun ReferenzWerkzeuge(
    referenz: KartenReferenz,
    karte: KartenVorlage,
    ausrichten: Boolean,
    beiAuswaehlen: () -> Unit,
    beiAusrichten: (Boolean) -> Unit,
    beiMetadaten: (KartenReferenzMetadaten) -> Unit,
    beiEntfernen: () -> Unit,
) {
    val metadaten = referenz.metadaten
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Anzeigen")
        Switch(
            checked = metadaten.sichtbar,
            onCheckedChange = { sichtbar ->
                beiMetadaten(metadaten.copy(sichtbar = sichtbar))
                if (!sichtbar) beiAusrichten(false)
            },
        )
    }
    FilterChip(
        selected = ausrichten,
        enabled = metadaten.sichtbar,
        onClick = { beiAusrichten(!ausrichten) },
        label = { Text("Bild ausrichten") },
    )
    Text(
        "Breite: ${metadaten.breiteInBrettEinheiten.anzeigeWert()} Brett-Einheiten",
        style = MaterialTheme.typography.bodySmall,
    )
    Slider(
        value = metadaten.breiteInBrettEinheiten.zuReferenzRegler(),
        onValueChange = { wert ->
            beiMetadaten(
                metadaten.copy(breiteInBrettEinheiten = wert.ausReferenzRegler()),
            )
        },
        valueRange = 0f..1f,
    )
    Text(
        "Deckkraft: ${(metadaten.deckkraft * 100).roundToInt()} %",
        style = MaterialTheme.typography.bodySmall,
    )
    Slider(
        value = metadaten.deckkraft,
        onValueChange = { deckkraft ->
            beiMetadaten(metadaten.copy(deckkraft = deckkraft))
        },
        valueRange = 0f..1f,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        OutlinedButton(
            onClick = {
                beiMetadaten(
                    metadaten.copy(
                        zentrumX = 0f,
                        zentrumZ = 0f,
                        breiteInBrettEinheiten = karte.empfohleneReferenzBreite(),
                    ),
                )
            },
        ) {
            Text("Auf Karte einpassen")
        }
        OutlinedButton(
            onClick = {
                beiMetadaten(metadaten.copy(zentrumX = 0f, zentrumZ = 0f))
            },
        ) {
            Text("Zentrieren")
        }
        OutlinedButton(onClick = beiAuswaehlen) {
            Text("Ersetzen")
        }
        OutlinedButton(onClick = beiEntfernen) {
            Text("Entfernen")
        }
    }
}

private fun KartenVorlage.empfohleneReferenzBreite(): Float =
    (hexagon.radius * 2f * GRUNDDREIECK_SEITENLAENGE)
        .coerceAtLeast(12f)
        .coerceIn(MIN_REFERENZ_BREITE, MAX_REFERENZ_BREITE)

private fun Float.zuReferenzRegler(): Float {
    val minimum = ln(MIN_REFERENZ_BREITE)
    val maximum = ln(MAX_REFERENZ_BREITE)
    return ((ln(coerceIn(MIN_REFERENZ_BREITE, MAX_REFERENZ_BREITE)) - minimum) /
        (maximum - minimum)).coerceIn(0f, 1f)
}

private fun Float.ausReferenzRegler(): Float {
    val minimum = ln(MIN_REFERENZ_BREITE)
    val maximum = ln(MAX_REFERENZ_BREITE)
    return exp(minimum + coerceIn(0f, 1f) * (maximum - minimum))
        .coerceIn(MIN_REFERENZ_BREITE, MAX_REFERENZ_BREITE)
}

private fun Float.anzeigeWert(): String = when {
    this >= 1_000f -> roundToInt().toString()
    this >= 10f -> ((this * 10).roundToInt() / 10f).toString()
    else -> ((this * 100).roundToInt() / 100f).toString()
}

private fun Map<KartenFeld, GelaendeTyp>.zuSortiertenGelaendefeldern(): List<GelaendeFeld> =
    entries
        .sortedWith(
            compareBy<Map.Entry<KartenFeld, GelaendeTyp>> { it.key.zeile }
                .thenBy { it.key.spalte }
                .thenBy { it.key.haelfte.ordinal },
        )
        .map { (position, gelaende) -> GelaendeFeld(position, gelaende) }
