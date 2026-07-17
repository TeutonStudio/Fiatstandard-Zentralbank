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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.fachlogik.ereignis.KartenAenderungsGrund
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk

private enum class SpielKartenWerkzeug(
    val beschriftung: String,
    val ziel: KartenZielModus,
) {
    HAUPTBAHNHOF("Hauptbahnhof", KartenZielModus.ECKE),
    BAHNHOF("Bahnhof", KartenZielModus.ECKE),
    GROSSBAHNHOF("Großbahnhof", KartenZielModus.ECKE),
    HAFEN("Hafen", KartenZielModus.ECKE),
    GROSSHAFEN("Großhafen", KartenZielModus.ECKE),
    AUFWERTEN("Aufwerten", KartenZielModus.ECKE),
    ECKE_BELAGERN("Belagern", KartenZielModus.ECKE),
    ECKE_ZERSTOEREN("Zerstören", KartenZielModus.ECKE),
    ECKE_REPARIEREN("Reparieren", KartenZielModus.ECKE),
    ECKE_ENTFERNEN("Entfernen", KartenZielModus.ECKE),
    SCHIENE("Handelslinie", KartenZielModus.KANTE),
    KANTE_ZERSTOEREN("Zerstören", KartenZielModus.KANTE),
    KANTE_REPARIEREN("Reparieren", KartenZielModus.KANTE),
    KANTE_ENTFERNEN("Entfernen", KartenZielModus.KANTE),
    ABBAUEINHEIT("Abbaueinheit", KartenZielModus.FELD),
    GESCHAEFTSBANK("Geschäftsbank", KartenZielModus.FELD),
    FELD_ZERSTOEREN("Zerstören", KartenZielModus.FELD),
    FELD_REAKTIVIEREN("Reaktivieren", KartenZielModus.FELD),
    FELD_ENTFERNEN("Entfernen", KartenZielModus.FELD),
}

@Composable
fun KartenSpielBildschirm(
    zustand: SpielZustand,
    beiEreignis: (SpielEreignis) -> Unit,
    beiRueckgaengig: () -> Unit,
    beiWiederholen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val karte = zustand.karte
    if (karte == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Dieser ältere Spielstand enthält noch keine Spielkarte.")
        }
        return
    }
    val aktiverSpieler = zustand.aktiverSpieler
    if (aktiverSpieler == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Es ist kein aktiver Spieler vorhanden.")
        }
        return
    }

    var werkzeug by remember(zustand.spielabschnitt) {
        mutableStateOf(
            if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
                SpielKartenWerkzeug.HAUPTBAHNHOF
            } else {
                SpielKartenWerkzeug.BAHNHOF
            },
        )
    }
    var rohstoff by remember { mutableStateOf(Rohstoff.NAHRUNG) }
    var kameraModus by remember { mutableStateOf(KameraInteraktionsModus.DREHEN) }
    var ausgewaehltesZiel by remember { mutableStateOf<KartenOrt?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Spielkarte", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
                        "Runde 0 · ${aktiverSpieler.wert} platziert den Hauptbahnhof"
                    } else {
                        "Aktiver Spieler: ${aktiverSpieler.wert}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    enabled = false,
                    onClick = {},
                    label = { Text("Bauen") },
                )
                FilterChip(selected = true, onClick = {}, label = { Text("Spielen") })
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val breiteAnsicht = maxWidth >= 840.dp
            val werkzeugleiste: @Composable (Modifier) -> Unit = { leistenModifier ->
                SpielWerkzeugleiste(
                    modifier = leistenModifier,
                    werkzeug = werkzeug,
                    beiWerkzeug = { neu ->
                        werkzeug = neu
                        ausgewaehltesZiel = null
                    },
                    rohstoff = rohstoff,
                    beiRohstoff = { rohstoff = it },
                    kameraModus = kameraModus,
                    beiKameraModus = { kameraModus = it },
                    beiRueckgaengig = beiRueckgaengig,
                    beiWiederholen = beiWiederholen,
                    rundeNull = zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL,
                )
            }
            val brett: @Composable (Modifier) -> Unit = { brettModifier ->
                Box(modifier = brettModifier) {
                    Spielbrett3D(
                        modell = karte.zu3DModell(
                            spielerReihenfolge = zustand.spieler.map { it.id },
                            hervorhebung = ausgewaehltesZiel,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        kameraInteraktionsModus = kameraModus,
                        onDreieckBeruehrt = { treffer ->
                            ausgewaehltesZiel = treffer.zuKartenOrt(werkzeug.ziel)
                        },
                    )
                    Text(
                        "Tippen: ${werkzeug.ziel.name.lowercase()} wählen · " +
                            "Ziehen: ${if (kameraModus == KameraInteraktionsModus.DREHEN) "drehen" else "verschieben"} · " +
                            "Zwei Finger: verschieben/zoomen",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (breiteAnsicht) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    werkzeugleiste(Modifier.widthIn(max = 360.dp).fillMaxSize())
                    brett(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    werkzeugleiste(Modifier.fillMaxWidth().heightIn(max = 300.dp))
                    brett(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    ausgewaehltesZiel?.let { ziel ->
        val ereignisErgebnis = runCatching {
            werkzeug.erstelleEreignis(
                zustand = zustand,
                ziel = ziel,
                rohstoff = rohstoff,
            )
        }
        val pruefung = ereignisErgebnis.mapCatching { ereignis ->
            SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
            ereignis
        }
        AlertDialog(
            onDismissRequest = { ausgewaehltesZiel = null },
            title = { Text(werkzeug.beschriftung) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ziel.anzeigeText())
                    if (pruefung.isSuccess) {
                        Text(
                            "Die Aktion ist regelkonform. Hinterlegte Baukosten und Bestand " +
                                "werden gemeinsam mit der Kartenbelegung geändert.",
                        )
                    } else {
                        Text(
                            pruefung.exceptionOrNull()?.message ?: "Die Aktion ist nicht erlaubt.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { ausgewaehltesZiel = null }) { Text("Abbrechen") }
            },
            confirmButton = {
                TextButton(
                    enabled = pruefung.isSuccess,
                    onClick = {
                        pruefung.getOrNull()?.let(beiEreignis)
                        ausgewaehltesZiel = null
                    },
                ) { Text("Bestätigen") }
            },
        )
    }
}

@Composable
private fun SpielWerkzeugleiste(
    modifier: Modifier,
    werkzeug: SpielKartenWerkzeug,
    beiWerkzeug: (SpielKartenWerkzeug) -> Unit,
    rohstoff: Rohstoff,
    beiRohstoff: (Rohstoff) -> Unit,
    kameraModus: KameraInteraktionsModus,
    beiKameraModus: (KameraInteraktionsModus) -> Unit,
    beiRueckgaengig: () -> Unit,
    beiWiederholen: () -> Unit,
    rundeNull: Boolean,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = beiRueckgaengig) { Text("Rückgängig") }
            OutlinedButton(onClick = beiWiederholen) { Text("Wiederholen") }
        }
        Text("Kamera", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KameraInteraktionsModus.entries.forEach { modus ->
                FilterChip(
                    selected = kameraModus == modus,
                    onClick = { beiKameraModus(modus) },
                    label = {
                        Text(if (modus == KameraInteraktionsModus.DREHEN) "Drehen" else "Verschieben")
                    },
                )
            }
        }
        if (rundeNull) {
            Text("Runde 0", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = listOf(SpielKartenWerkzeug.HAUPTBAHNHOF),
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
        } else {
            Text("Ecke", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter { it.ziel == KartenZielModus.ECKE }
                    .filterNot { it == SpielKartenWerkzeug.HAUPTBAHNHOF },
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
            Text("Kante", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter { it.ziel == KartenZielModus.KANTE },
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
            Text("Feld", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter { it.ziel == KartenZielModus.FELD },
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
            if (werkzeug == SpielKartenWerkzeug.ABBAUEINHEIT) {
                Text("Rohstoff", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Rohstoff.entries.forEach { eintrag ->
                        FilterChip(
                            selected = rohstoff == eintrag,
                            onClick = { beiRohstoff(eintrag) },
                            label = { Text(eintrag.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WerkzeugChips(
    werkzeuge: List<SpielKartenWerkzeug>,
    ausgewaehlt: SpielKartenWerkzeug,
    beiWerkzeug: (SpielKartenWerkzeug) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        werkzeuge.forEach { eintrag ->
            FilterChip(
                selected = ausgewaehlt == eintrag,
                onClick = { beiWerkzeug(eintrag) },
                label = { Text(eintrag.beschriftung) },
            )
        }
    }
}

private fun SpielKartenWerkzeug.erstelleEreignis(
    zustand: SpielZustand,
    ziel: KartenOrt,
    rohstoff: Rohstoff,
): SpielEreignis {
    val spieler = requireNotNull(zustand.aktiverSpieler) { "Es ist kein Spieler aktiv." }
    return when (this) {
        SpielKartenWerkzeug.HAUPTBAHNHOF -> SpielEreignis.HauptbahnhofPlatziert(
            spieler,
            (ziel as KartenOrt.Ecke).position,
        )
        SpielKartenWerkzeug.BAHNHOF -> eckGebaeude(spieler, ziel, EckGebaeudeTyp.BAHNHOF)
        SpielKartenWerkzeug.GROSSBAHNHOF ->
            eckGebaeude(spieler, ziel, EckGebaeudeTyp.GROSSBAHNHOF)
        SpielKartenWerkzeug.HAFEN -> eckGebaeude(spieler, ziel, EckGebaeudeTyp.HAFEN)
        SpielKartenWerkzeug.GROSSHAFEN -> eckGebaeude(spieler, ziel, EckGebaeudeTyp.GROSSHAFEN)
        SpielKartenWerkzeug.AUFWERTEN -> {
            val ecke = (ziel as KartenOrt.Ecke).position
            val bisher = requireNotNull(zustand.karte?.belegung?.eckenNachPosition?.get(ecke)) {
                "Auf der gewählten Ecke steht kein Gebäude."
            }
            val zu = when (bisher.typ) {
                EckGebaeudeTyp.BAHNHOF -> EckGebaeudeTyp.GROSSBAHNHOF
                EckGebaeudeTyp.HAFEN -> EckGebaeudeTyp.GROSSHAFEN
                else -> error("Dieses Gebäude kann nicht aufgewertet werden.")
            }
            SpielEreignis.EckGebaeudeAufgewertet(spieler, ecke, zu)
        }
        SpielKartenWerkzeug.ECKE_BELAGERN ->
            bauwerkZustand(spieler, ziel, BauwerkZustand.BELAGERT, KartenAenderungsGrund.BELAGERUNG)
        SpielKartenWerkzeug.ECKE_ZERSTOEREN,
        SpielKartenWerkzeug.KANTE_ZERSTOEREN ->
            bauwerkZustand(spieler, ziel, BauwerkZustand.ZERSTOERT, KartenAenderungsGrund.BELAGERUNG)
        SpielKartenWerkzeug.ECKE_REPARIEREN,
        SpielKartenWerkzeug.KANTE_REPARIEREN ->
            bauwerkZustand(spieler, ziel, BauwerkZustand.INTAKT, KartenAenderungsGrund.SPIELERAKTION)
        SpielKartenWerkzeug.ECKE_ENTFERNEN,
        SpielKartenWerkzeug.KANTE_ENTFERNEN,
        SpielKartenWerkzeug.FELD_ENTFERNEN -> SpielEreignis.KartenBelegungEntfernt(spieler, ziel)
        SpielKartenWerkzeug.SCHIENE -> SpielEreignis.SchieneGebaut(
            spieler,
            (ziel as KartenOrt.Kante).position,
        )
        SpielKartenWerkzeug.ABBAUEINHEIT -> SpielEreignis.NeutraleAnlageErrichtet(
            spieler,
            (ziel as KartenOrt.Feld).position,
            FeldAnlage.Abbaueinheit(rohstoff),
        )
        SpielKartenWerkzeug.GESCHAEFTSBANK -> SpielEreignis.NeutraleAnlageErrichtet(
            spieler,
            (ziel as KartenOrt.Feld).position,
            FeldAnlage.Geschaeftsbank,
        )
        SpielKartenWerkzeug.FELD_ZERSTOEREN -> SpielEreignis.FeldAnlagenZustandGeaendert(
            spieler,
            (ziel as KartenOrt.Feld).position,
            AnlagenZustand.ZERSTOERT,
            KartenAenderungsGrund.BELAGERUNG,
        )
        SpielKartenWerkzeug.FELD_REAKTIVIEREN -> SpielEreignis.FeldAnlagenZustandGeaendert(
            spieler,
            (ziel as KartenOrt.Feld).position,
            AnlagenZustand.AKTIV,
            KartenAenderungsGrund.SPIELERAKTION,
        )
    }
}

private fun eckGebaeude(
    spieler: de.teutonstudio.zentralbank.fachlogik.modell.SpielerId,
    ziel: KartenOrt,
    typ: EckGebaeudeTyp,
): SpielEreignis = SpielEreignis.EckGebaeudeGebaut(
    spieler = spieler,
    ecke = (ziel as KartenOrt.Ecke).position,
    typ = typ,
)

private fun bauwerkZustand(
    spieler: de.teutonstudio.zentralbank.fachlogik.modell.SpielerId,
    ziel: KartenOrt,
    zustand: BauwerkZustand,
    grund: KartenAenderungsGrund,
): SpielEreignis = SpielEreignis.KartenBauwerkZustandGeaendert(
    spieler = spieler,
    ort = ziel,
    zustand = zustand,
    grund = grund,
)

private fun KartenOrt.anzeigeText(): String = when (this) {
    is KartenOrt.Ecke -> "Ecke (${position.x}, ${position.y})"
    is KartenOrt.Kante ->
        "Kante (${position.anfang.x}, ${position.anfang.y}) – " +
            "(${position.ende.x}, ${position.ende.y})"
    is KartenOrt.Feld ->
        "Feld (${position.zeile}, ${position.spalte}, ${position.haelfte.name.lowercase()})"
}
