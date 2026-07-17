package de.teutonstudio.zentralbank.spielbrett

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.teutonstudio.zentralbank.daten.karten.KartenAblage
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import kotlinx.coroutines.launch

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
    var kameraModus by remember { mutableStateOf(KameraInteraktionsModus.DREHEN) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }
    var wirdGespeichert by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun uebernehme(neu: KartenVorlage) {
        if (neu == entwurf) return
        while (verlauf.lastIndex > verlaufIndex) verlauf.removeAt(verlauf.lastIndex)
        verlauf += neu
        verlaufIndex = verlauf.lastIndex
    }

    Dialog(
        onDismissRequest = beiAbbruch,
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
                    Text("3D-Spielkarte", style = MaterialTheme.typography.headlineSmall)
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
                    "Das Dreiecksraster ist unbegrenzt. Sichtbar sind nur Raster und Gelände; " +
                        "Wasser bleibt transparent.",
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
                            kameraModus = kameraModus,
                            beiKameraModus = { kameraModus = it },
                            kannRueckgaengig = verlaufIndex > 0,
                            kannWiederholen = verlaufIndex < verlauf.lastIndex,
                            beiRueckgaengig = { if (verlaufIndex > 0) verlaufIndex-- },
                            beiWiederholen = { if (verlaufIndex < verlauf.lastIndex) verlaufIndex++ },
                        )
                    }
                    val editor: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier = modifier) {
                            Spielbrett3D(
                                modell = entwurf.zu3DModell(zeigeBearbeitungsRaster = true),
                                modifier = Modifier.fillMaxSize(),
                                kameraInteraktionsModus = kameraModus,
                                onDreieckBeruehrt = { treffer ->
                                    fehlermeldung = null
                                    uebernehme(entwurf.wendeWerkzeugAn(treffer, werkzeug))
                                },
                            )
                            Text(
                                text = when (kameraModus) {
                                    KameraInteraktionsModus.DREHEN ->
                                        "Tippen: Gelände bearbeiten · Ziehen: drehen · Zwei Finger: verschieben/zoomen"
                                    KameraInteraktionsModus.VERSCHIEBEN ->
                                        "Tippen: Gelände bearbeiten · Ziehen: Fokus verschieben · Pinch: zoomen"
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
                    OutlinedButton(onClick = beiAbbruch, enabled = !wirdGespeichert) {
                        Text("Abbrechen")
                    }
                    Button(
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = name.isNotBlank() && !wirdGespeichert,
                        onClick = {
                            wirdGespeichert = true
                            fehlermeldung = null
                            scope.launch {
                                runCatching {
                                    ablage.eigeneKarteSpeichern(
                                        vorlage = entwurf.copy(name = name.trim()),
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
    kameraModus: KameraInteraktionsModus,
    beiKameraModus: (KameraInteraktionsModus) -> Unit,
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
        Text("Kamera", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilterChip(
                selected = kameraModus == KameraInteraktionsModus.DREHEN,
                onClick = { beiKameraModus(KameraInteraktionsModus.DREHEN) },
                label = { Text("Drehen") },
            )
            FilterChip(
                selected = kameraModus == KameraInteraktionsModus.VERSCHIEBEN,
                onClick = { beiKameraModus(KameraInteraktionsModus.VERSCHIEBEN) },
                label = { Text("Verschieben") },
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

private fun Map<KartenFeld, GelaendeTyp>.zuSortiertenGelaendefeldern(): List<GelaendeFeld> =
    entries
        .sortedWith(
            compareBy<Map.Entry<KartenFeld, GelaendeTyp>> { it.key.zeile }
                .thenBy { it.key.spalte }
                .thenBy { it.key.haelfte.ordinal },
        )
        .map { (position, gelaende) -> GelaendeFeld(position, gelaende) }
