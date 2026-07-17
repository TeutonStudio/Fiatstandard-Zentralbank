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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
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

internal enum class KartenRichtung(val beschriftung: String) {
    NORDEN("Norden +1"),
    SUEDEN("Süden +1"),
    WESTEN("Westen +1"),
    OSTEN("Osten +1"),
}

@Composable
fun KartenEditorDialog(
    ausgangsvorlage: KartenVorlage,
    ablage: KartenAblage,
    migrationsHinweise: List<String> = emptyList(),
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
    var ausstehendeAusdehnung by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
                    "Im Baumodus werden ausschließlich Wasser und Geländefelder verändert.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (migrationsHinweise.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Hinweis zur alten Karte", style = MaterialTheme.typography.titleSmall)
                            migrationsHinweise.forEach { hinweis ->
                                Text("• $hinweis", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Das Original bleibt erhalten; gespeichert wird eine neue Geländevorlage.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val breiteAnsicht = maxWidth >= 840.dp
                    val werkzeuge: @Composable (Modifier) -> Unit = { modifier ->
                        KartenWerkzeugleiste(
                            modifier = modifier,
                            name = name,
                            beiName = { name = it },
                            karte = entwurf,
                            beiErweitern = { richtung -> uebernehme(entwurf.erweitert(richtung)) },
                            beiAusdehnung = { zeilen, spalten ->
                                val entfernt = entwurf.anzahlEntfallenderFelder(zeilen, spalten)
                                if (entfernt > 0) ausstehendeAusdehnung = zeilen to spalten
                                else uebernehme(entwurf.mitAusdehnung(zeilen, spalten))
                            },
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
                                        alsNeueKopie = migrationsHinweise.isNotEmpty(),
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

    ausstehendeAusdehnung?.let { (zeilen, spalten) ->
        val anzahl = entwurf.anzahlEntfallenderFelder(zeilen, spalten)
        AlertDialog(
            onDismissRequest = { ausstehendeAusdehnung = null },
            title = { Text("Kartenbereich verkleinern?") },
            text = { Text("Dabei werden $anzahl Geländefelder außerhalb des neuen Bereichs entfernt.") },
            dismissButton = {
                TextButton(onClick = { ausstehendeAusdehnung = null }) { Text("Abbrechen") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uebernehme(entwurf.mitAusdehnung(zeilen, spalten))
                        ausstehendeAusdehnung = null
                    },
                ) { Text("Verkleinern") }
            },
        )
    }
}

@Composable
private fun KartenWerkzeugleiste(
    modifier: Modifier,
    name: String,
    beiName: (String) -> Unit,
    karte: KartenVorlage,
    beiErweitern: (KartenRichtung) -> Unit,
    beiAusdehnung: (Int, Int) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AusdehnungsFeld(
                bezeichnung = "Zeilen",
                wert = karte.zeilen,
                beiWert = { beiAusdehnung(it, karte.spalten) },
                modifier = Modifier.weight(1f),
            )
            AusdehnungsFeld(
                bezeichnung = "Spalten",
                wert = karte.spalten,
                beiWert = { beiAusdehnung(karte.zeilen, it) },
                modifier = Modifier.weight(1f),
            )
        }
        Text("Bearbeitungsbereich erweitern", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KartenRichtung.entries.forEach { richtung ->
                OutlinedButton(onClick = { beiErweitern(richtung) }) {
                    Text(richtung.beschriftung)
                }
            }
        }
        Text(
            "Zeilen ${karte.startZeile} bis ${karte.endeZeileExklusiv - 1} · " +
                "Spalten ${karte.startSpalte} bis ${karte.endeSpalteExklusiv - 1}",
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
            "Wasser ist die unbegrenzte Grundebene. Gespeichert werden nur gesetzte Geländedreiecke.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${karte.gelaendefelder.size} Geländedreiecke",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AusdehnungsFeld(
    bezeichnung: String,
    wert: Int,
    beiWert: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = wert.toString(),
        onValueChange = { eingabe -> eingabe.toIntOrNull()?.takeIf { it > 0 }?.let(beiWert) },
        modifier = modifier,
        label = { Text(bezeichnung) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("Mindestens 1") },
    )
}

internal fun KartenVorlage.anzahlEntfallenderFelder(neueZeilen: Int, neueSpalten: Int): Int {
    val neueEndZeile = startZeile.toLong() + neueZeilen
    val neueEndSpalte = startSpalte.toLong() + neueSpalten
    return gelaendefelder.count { feld ->
        feld.position.zeile.toLong() !in startZeile.toLong() until neueEndZeile ||
            feld.position.spalte.toLong() !in startSpalte.toLong() until neueEndSpalte
    }
}

internal fun KartenVorlage.mitAusdehnung(neueZeilen: Int, neueSpalten: Int): KartenVorlage {
    require(neueZeilen > 0)
    require(neueSpalten > 0)
    val neueEndZeile = startZeile.toLong() + neueZeilen
    val neueEndSpalte = startSpalte.toLong() + neueSpalten
    return copy(
        zeilen = neueZeilen,
        spalten = neueSpalten,
        gelaendefelder = gelaendefelder.filter { feld ->
            feld.position.zeile.toLong() in startZeile.toLong() until neueEndZeile &&
                feld.position.spalte.toLong() in startSpalte.toLong() until neueEndSpalte
        },
    )
}

internal fun KartenVorlage.erweitert(
    richtung: KartenRichtung,
    anzahl: Int = 1,
): KartenVorlage {
    require(anzahl > 0) { "Es muss um mindestens eine Reihe erweitert werden." }
    return copy(
        zeilen = if (richtung in setOf(KartenRichtung.NORDEN, KartenRichtung.SUEDEN)) {
            Math.addExact(zeilen, anzahl)
        } else zeilen,
        spalten = if (richtung in setOf(KartenRichtung.WESTEN, KartenRichtung.OSTEN)) {
            Math.addExact(spalten, anzahl)
        } else spalten,
        startZeile = if (richtung == KartenRichtung.NORDEN) {
            Math.subtractExact(startZeile, anzahl)
        } else startZeile,
        startSpalte = if (richtung == KartenRichtung.WESTEN) {
            Math.subtractExact(startSpalte, anzahl)
        } else startSpalte,
    )
}

internal fun KartenVorlage.wendeWerkzeugAn(
    treffer: DreieckTreffer,
    werkzeug: KartenWerkzeug,
): KartenVorlage {
    val position = treffer.position.zuKartenFeld()
    val gelaende = werkzeug.gelaendeOderNull()
    if (gelaende == null) {
        return copy(gelaendefelder = gelaendefelder.filterNot { feld -> feld.position == position })
    }
    val land = landNachPosition.toMutableMap().apply { put(position, gelaende) }
    return copy(gelaendefelder = land.zuSortiertenGelaendefeldern())
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
