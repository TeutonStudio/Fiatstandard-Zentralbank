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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenDreieck
import de.teutonstudio.zentralbank.fachlogik.modell.Landfeld
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.Spezialfeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import java.util.UUID
import kotlinx.coroutines.launch

internal enum class KartenWerkzeug(val beschriftung: String) {
    WASSER("Wasser"),
    EBENE("Ebene"),
    WALD("Wald"),
    GEBIRGE("Gebirge"),
    WUESTE("Wüste"),
    SUMPF("Sumpf"),
    HEXAGON("Spezial: Hexagon"),
    STADT("Spezial: Stadt"),
    HAFEN("Spezial: Hafen"),
    SPEZIAL_ENTFERNEN("Spezial entfernen"),
}

internal enum class KartenRichtung(val beschriftung: String) {
    NORDEN("Norden +1"),
    SUEDEN("Süden +1"),
    WESTEN("Westen +1"),
    OSTEN("Osten +1"),
}

@Composable
fun KartenEditorDialog(
    ausgangskarte: Spielkarte,
    ablage: KartenAblage,
    beiAbbruch: () -> Unit,
    beiGespeichert: (Spielkarte) -> Unit,
) {
    var entwurf by remember(ausgangskarte) { mutableStateOf(ausgangskarte) }
    var name by remember(ausgangskarte) { mutableStateOf(ausgangskarte.name) }
    var werkzeug by remember { mutableStateOf(KartenWerkzeug.EBENE) }
    var kameraModus by remember { mutableStateOf(KameraInteraktionsModus.DREHEN) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }
    var wirdGespeichert by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                Text("3D-Kartenbauer", style = MaterialTheme.typography.headlineSmall)
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val breiteAnsicht = maxWidth >= 840.dp
                    val werkzeuge: @Composable (Modifier) -> Unit = { modifier ->
                        KartenWerkzeugleiste(
                            modifier = modifier,
                            name = name,
                            beiName = { name = it },
                            karte = entwurf,
                            beiErweitern = { richtung ->
                                entwurf = entwurf.erweitert(richtung)
                            },
                            beiAusdehnung = { zeilen, spalten ->
                                entwurf = entwurf.mitAusdehnung(zeilen, spalten)
                            },
                            werkzeug = werkzeug,
                            beiWerkzeug = { werkzeug = it },
                            kameraModus = kameraModus,
                            beiKameraModus = { kameraModus = it },
                        )
                    }
                    val editor: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier = modifier) {
                            Spielbrett3D(
                                modell = entwurf.zu3DModell(zeigeBearbeitungsRaster = true),
                                modifier = Modifier.fillMaxSize(),
                                kameraInteraktionsModus = kameraModus,
                                onDreieckBeruehrt = { treffer ->
                                    val bearbeitet = entwurf.wendeWerkzeugAn(
                                        treffer = treffer,
                                        werkzeug = werkzeug,
                                    )
                                    fehlermeldung = if (bearbeitet == entwurf && werkzeug.istSpezial()) {
                                        "Das Spezialfeld braucht einen inneren Eckpunkt mit sechs Dreiecken."
                                    } else {
                                        null
                                    }
                                    entwurf = bearbeitet
                                },
                            )
                            Text(
                                text = when (kameraModus) {
                                    KameraInteraktionsModus.DREHEN ->
                                        "Tippen: bearbeiten · Ziehen: drehen · Zwei Finger: verschieben/zoomen"
                                    KameraInteraktionsModus.VERSCHIEBEN ->
                                        "Tippen: bearbeiten · Ziehen: Fokus verschieben · Pinch: zoomen"
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
                            werkzeuge(Modifier.fillMaxWidth().heightIn(max = 310.dp))
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
                                    ablage.eigeneKarteSpeichern(entwurf.copy(name = name.trim()))
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
    karte: Spielkarte,
    beiErweitern: (KartenRichtung) -> Unit,
    beiAusdehnung: (Int, Int) -> Unit,
    werkzeug: KartenWerkzeug,
    beiWerkzeug: (KartenWerkzeug) -> Unit,
    kameraModus: KameraInteraktionsModus,
    beiKameraModus: (KameraInteraktionsModus) -> Unit,
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
        Text("Bearbeitungsbereich dynamisch erweitern", style = MaterialTheme.typography.titleSmall)
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
            "Wasser ist die unbegrenzte Grundebene. Land wird nur für gesetzte Dreiecke gespeichert. " +
                "Spezialfelder belegen die sechs Dreiecke um den beim Tippen nächsten Eckpunkt.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${karte.landfelder.size} Landdreiecke · ${karte.spezialfelder.size} Spezialfelder",
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
        onValueChange = { eingabe ->
            eingabe.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let(beiWert)
        },
        modifier = modifier,
        label = { Text(bezeichnung) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("Mindestens 1 · kein festes Maximum") },
    )
}

internal fun Spielkarte.mitAusdehnung(neueZeilen: Int, neueSpalten: Int): Spielkarte {
    require(neueZeilen > 0)
    require(neueSpalten > 0)
    val neueEndZeile = startZeile.toLong() + neueZeilen
    val neueEndSpalte = startSpalte.toLong() + neueSpalten
    val land = landfelder.filter { feld ->
        feld.position.zeile.toLong() in startZeile.toLong() until neueEndZeile &&
            feld.position.spalte.toLong() in startSpalte.toLong() until neueEndSpalte
    }
    val landPositionen = land.mapTo(mutableSetOf(), Landfeld::position)
    return copy(
        zeilen = neueZeilen,
        spalten = neueSpalten,
        landfelder = land,
        spezialfelder = spezialfelder.filter { feld ->
            feld.positionen.all { position -> position in landPositionen }
        },
    )
}

internal fun Spielkarte.erweitert(
    richtung: KartenRichtung,
    anzahl: Int = 1,
): Spielkarte {
    require(anzahl > 0) { "Es muss um mindestens eine Reihe erweitert werden." }
    val neueZeilen = when (richtung) {
        KartenRichtung.NORDEN, KartenRichtung.SUEDEN -> Math.addExact(zeilen, anzahl)
        KartenRichtung.WESTEN, KartenRichtung.OSTEN -> zeilen
    }
    val neueSpalten = when (richtung) {
        KartenRichtung.WESTEN, KartenRichtung.OSTEN -> Math.addExact(spalten, anzahl)
        KartenRichtung.NORDEN, KartenRichtung.SUEDEN -> spalten
    }
    val neueStartZeile = if (richtung == KartenRichtung.NORDEN) {
        Math.subtractExact(startZeile, anzahl)
    } else {
        startZeile
    }
    val neueStartSpalte = if (richtung == KartenRichtung.WESTEN) {
        Math.subtractExact(startSpalte, anzahl)
    } else {
        startSpalte
    }
    return copy(
        zeilen = neueZeilen,
        spalten = neueSpalten,
        startZeile = neueStartZeile,
        startSpalte = neueStartSpalte,
    )
}

internal fun Spielkarte.wendeWerkzeugAn(
    treffer: DreieckTreffer,
    werkzeug: KartenWerkzeug,
    neueSpezialId: () -> String = { "spezial-${UUID.randomUUID()}" },
): Spielkarte {
    val position = treffer.position.zuKartenDreieck()
    if (werkzeug == KartenWerkzeug.WASSER) {
        return copy(
            landfelder = landfelder.filterNot { feld -> feld.position == position },
            spezialfelder = spezialfelder.filterNot { feld -> position in feld.positionen },
        )
    }
    if (werkzeug == KartenWerkzeug.SPEZIAL_ENTFERNEN) {
        return copy(spezialfelder = spezialfelder.filterNot { feld -> position in feld.positionen })
    }

    werkzeug.gelaendeOderNull()?.let { gelaende ->
        val land = landNachPosition.toMutableMap().apply { put(position, gelaende) }
        return copy(landfelder = land.zuSortiertenLandfeldern())
    }

    val spezialTyp = werkzeug.spezialOderNull() ?: return this
    val positionen = berechneSpielbrettGeometrie(
        zeilen = zeilen,
        spalten = spalten,
        startZeile = startZeile,
        startSpalte = startSpalte,
    )
        .kartenHexagonUm(treffer)
    if (positionen.size != 6) return this

    val land = landNachPosition.toMutableMap()
    positionen.forEach { spezialPosition -> land.putIfAbsent(spezialPosition, GelaendeTyp.EBENE) }
    val ohneUeberlappung = spezialfelder.filter { feld ->
        feld.positionen.none(positionen::contains)
    }
    return copy(
        landfelder = land.zuSortiertenLandfeldern(),
        spezialfelder = ohneUeberlappung + Spezialfeld(
            id = neueSpezialId(),
            name = spezialTyp.anzeigeName(),
            typ = spezialTyp,
            positionen = positionen,
        ),
    )
}

private fun KartenWerkzeug.gelaendeOderNull(): GelaendeTyp? = when (this) {
    KartenWerkzeug.EBENE -> GelaendeTyp.EBENE
    KartenWerkzeug.WALD -> GelaendeTyp.WALD
    KartenWerkzeug.GEBIRGE -> GelaendeTyp.GEBIRGE
    KartenWerkzeug.WUESTE -> GelaendeTyp.WUESTE
    KartenWerkzeug.SUMPF -> GelaendeTyp.SUMPF
    else -> null
}

private fun KartenWerkzeug.spezialOderNull(): SpezialfeldTyp? = when (this) {
    KartenWerkzeug.HEXAGON -> SpezialfeldTyp.HEXAGON
    KartenWerkzeug.STADT -> SpezialfeldTyp.STADT
    KartenWerkzeug.HAFEN -> SpezialfeldTyp.HAFEN
    else -> null
}

private fun KartenWerkzeug.istSpezial(): Boolean = spezialOderNull() != null

private fun SpezialfeldTyp.anzeigeName(): String = when (this) {
    SpezialfeldTyp.HEXAGON -> "Spezial-Hexagon"
    SpezialfeldTyp.STADT -> "Stadt"
    SpezialfeldTyp.HAFEN -> "Hafen"
}

private fun Map<KartenDreieck, GelaendeTyp>.zuSortiertenLandfeldern(): List<Landfeld> =
    entries
        .sortedWith(
            compareBy<Map.Entry<KartenDreieck, GelaendeTyp>> { it.key.zeile }
                .thenBy { it.key.spalte }
                .thenBy { it.key.haelfte.ordinal },
        )
        .map { (position, gelaende) -> Landfeld(position, gelaende) }
