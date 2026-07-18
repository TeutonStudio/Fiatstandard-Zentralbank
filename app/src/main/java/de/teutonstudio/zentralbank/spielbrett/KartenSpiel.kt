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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk

private enum class SpielKartenWerkzeug(
    val beschriftung: String,
    val ziel: KartenZielModus,
    val startBauteil: BauteilTyp? = null,
    val nurRundeNull: Boolean = false,
) {
    HAUPTBAHNHOF("Hauptbahnhof", KartenZielModus.ECKE, BauteilTyp.HAUPTBAHNHOF),
    BAHNHOF("Bahnhof", KartenZielModus.ECKE, BauteilTyp.BAHNHOF),
    GROSSBAHNHOF("Großbahnhof", KartenZielModus.ECKE, BauteilTyp.GROSSBAHNHOF),
    HAFEN("Hafen", KartenZielModus.ECKE, BauteilTyp.HAFEN),
    GROSSHAFEN("Großhafen", KartenZielModus.ECKE, BauteilTyp.GROSSHAFEN),
    FRACHTSCHIFF("Frachtschiff", KartenZielModus.ECKE, BauteilTyp.FRACHTSCHIFF),
    AUFWERTEN("Aufwerten", KartenZielModus.ECKE),
    ECKE_BELAGERN("Belagern", KartenZielModus.ECKE),
    ECKE_ZERSTOEREN("Zerstören", KartenZielModus.ECKE),
    ECKE_REPARIEREN("Reparieren", KartenZielModus.ECKE),
    ECKE_ENTFERNEN("Entfernen", KartenZielModus.ECKE),
    SCHIENE("Handelslinie", KartenZielModus.KANTE, BauteilTyp.EISENBAHNLINIE),
    KANTE_ZERSTOEREN("Zerstören", KartenZielModus.KANTE),
    KANTE_REPARIEREN("Reparieren", KartenZielModus.KANTE),
    KANTE_ENTFERNEN("Entfernen", KartenZielModus.KANTE),
    ABBAUEINHEIT("Abbaueinheit", KartenZielModus.FELD),
    GESCHAEFTSBANK("Geschäftsbank", KartenZielModus.FELD, BauteilTyp.GESCHAEFTSBANK),
    VIEHHOF("Viehhof", KartenZielModus.FELD, BauteilTyp.VIEHHOF, nurRundeNull = true),
    ZIEGELBRENNER(
        "Ziegelbrenner",
        KartenZielModus.FELD,
        BauteilTyp.ZIEGELBRENNER,
        nurRundeNull = true,
    ),
    LEHMINE("Lehmmine", KartenZielModus.FELD, BauteilTyp.LEHMINE, nurRundeNull = true),
    FOERSTER("Förster", KartenZielModus.FELD, BauteilTyp.FOERSTER, nurRundeNull = true),
    BOHRTURM("Bohrturm", KartenZielModus.FELD, BauteilTyp.BOHRTURM, nurRundeNull = true),
    RAFFINERIE("Raffinerie", KartenZielModus.FELD, BauteilTyp.RAFFINERIE, nurRundeNull = true),
    SYNTHETIK_RAFFINERIE(
        "Synthetik-Raffinerie",
        KartenZielModus.FELD,
        BauteilTyp.SYNTHETIK_RAFFINERIE,
        nurRundeNull = true,
    ),
    KOHLEMINE("Kohlemine", KartenZielModus.FELD, BauteilTyp.KOHLEMINE, nurRundeNull = true),
    STAHLFABRIK("Stahlfabrik", KartenZielModus.FELD, BauteilTyp.STAHLFABRIK, nurRundeNull = true),
    EISENMINE("Eisenmine", KartenZielModus.FELD, BauteilTyp.EISENMINE, nurRundeNull = true),
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
    kompakteZentrale: Boolean = false,
    vorgewaehltesBauteil: BauteilTyp? = null,
    beiBauauftragBeendet: () -> Unit = {},
    beiBauAusFinanzmitteln: ((SpielEreignis, Map<Rohstoff, Int>) -> Boolean)? = null,
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
    val aktiverSpielerIndex = zustand.spieler.indexOfFirst { it.id == aktiverSpieler }
        .coerceAtLeast(0)
    val zeitfenster = spielzugZeitfenster(
        spielerIndex = aktiverSpielerIndex,
        spielerAnzahl = zustand.spieler.size.coerceAtLeast(1),
    )
    val himmel = HimmelsDarstellung.fuerSpielzug(zeitfenster)
    val betrachtungsStatus = rememberBetrachtungsTransformationsStatus()

    val rundeNullRestbestand = if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
        zustand.rundeNullRestbestand?.get(aktiverSpieler)
            ?: mapOf(BauteilTyp.HAUPTBAHNHOF to 1)
    } else {
        emptyMap()
    }
    val rundeNullWerkzeuge = SpielKartenWerkzeug.entries.filter { eintrag ->
        val bauteil = eintrag.startBauteil
        bauteil != null && rundeNullRestbestand.getOrDefault(bauteil, 0) > 0
    }
    var werkzeug by remember(
        zustand.spielabschnitt,
        aktiverSpieler,
        rundeNullRestbestand,
    ) {
        mutableStateOf(
            if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
                rundeNullWerkzeuge.firstOrNull() ?: SpielKartenWerkzeug.HAUPTBAHNHOF
            } else {
                SpielKartenWerkzeug.BAHNHOF
            },
        )
    }
    var rohstoff by remember { mutableStateOf(Rohstoff.NAHRUNG) }
    var kameraModus by remember { mutableStateOf(KameraInteraktionsModus.DREHEN) }
    var ausgewaehltesZiel by remember { mutableStateOf<KartenOrt?>(null) }
    var seewegStart by remember { mutableStateOf<KartenOrt.Ecke?>(null) }

    LaunchedEffect(vorgewaehltesBauteil) {
        vorgewaehltesBauteil?.let { bauteil ->
            SpielKartenWerkzeug.entries.firstOrNull { eintrag ->
                eintrag.startBauteil == bauteil
            }?.let { externesWerkzeug ->
                werkzeug = externesWerkzeug
                ausgewaehltesZiel = null
                seewegStart = null
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(if (kompakteZentrale) 0.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!kompakteZentrale) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Spielkarte", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
                            "Runde 0 · ${aktiverSpieler.wert} platziert Startbauwerke " +
                                "(${rundeNullRestbestand.values.sum()} verbleibend)"
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
                        seewegStart = null
                    },
                    rohstoff = rohstoff,
                    beiRohstoff = { rohstoff = it },
                    kameraModus = kameraModus,
                    beiKameraModus = { kameraModus = it },
                    beiRueckgaengig = beiRueckgaengig,
                    beiWiederholen = beiWiederholen,
                    rundeNull = zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL,
                    rundeNullWerkzeuge = rundeNullWerkzeuge,
                    rundeNullRestbestand = rundeNullRestbestand,
                )
            }
            val brett: @Composable (Modifier) -> Unit = { brettModifier ->
                Box(modifier = brettModifier) {
                    Spielbrett3D(
                        modell = karte.zu3DModell(
                            spielerReihenfolge = zustand.spieler.map { it.id },
                            hervorhebung = ausgewaehltesZiel ?: seewegStart,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        betrachtungsStatus = betrachtungsStatus,
                        kameraInteraktionsModus = kameraModus,
                        himmel = himmel,
                        onDreieckBeruehrt = { treffer ->
                            if (!kompakteZentrale || vorgewaehltesBauteil != null) {
                                val ziel = treffer.zuKartenOrt(werkzeug.ziel)
                                if (werkzeug == SpielKartenWerkzeug.FRACHTSCHIFF) {
                                    val hafen = ziel as KartenOrt.Ecke
                                    if (seewegStart == null) {
                                        seewegStart = hafen
                                    } else {
                                        ausgewaehltesZiel = hafen
                                    }
                                } else {
                                    ausgewaehltesZiel = ziel
                                }
                            }
                        },
                    )
                    SpielbrettKompass(
                        himmel = himmel,
                        kameraAzimutGrad = betrachtungsStatus.azimutGrad,
                        zeitfenster = zeitfenster,
                        modifier = if (kompakteZentrale) {
                            Modifier.align(Alignment.BottomEnd).padding(12.dp)
                        } else {
                            Modifier.align(Alignment.TopEnd).padding(8.dp)
                        },
                    )
                    if (!kompakteZentrale || vorgewaehltesBauteil != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ) {
                            if (vorgewaehltesBauteil != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        if (werkzeug == SpielKartenWerkzeug.FRACHTSCHIFF) {
                                            "Frachtschiff: ${if (seewegStart == null) "ersten" else "zweiten"} Hafen wählen"
                                        } else {
                                            "${werkzeug.beschriftung}: ${werkzeug.ziel.name.lowercase()} auf der Karte wählen"
                                        },
                                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    TextButton(onClick = {
                                        ausgewaehltesZiel = null
                                        seewegStart = null
                                        beiBauauftragBeendet()
                                    }) {
                                        Text("Auftrag abbrechen")
                                    }
                                }
                            } else {
                                Text(
                                    "Tippen: ${werkzeug.ziel.name.lowercase()} wählen · " +
                                        "Ziehen: ${if (kameraModus == KameraInteraktionsModus.DREHEN) "drehen" else "verschieben"} · " +
                                        "Zwei Finger: verschieben/zoomen",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            if (kompakteZentrale) {
                brett(Modifier.fillMaxSize())
            } else if (breiteAnsicht) {
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
                seewegStart = seewegStart,
            )
        }
        val pruefung = ereignisErgebnis.mapCatching { ereignis ->
            SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
            ereignis
        }
        val bauteil = werkzeug.startBauteil
            ?.takeIf { zustand.spielabschnitt == Spielabschnitt.REGULAER }
        val fehlendeRohstoffe = bauteil?.let { typ ->
            fehlendeBauRohstoffe(zustand, typ)
        }.orEmpty()
        val finanziertePruefung = if (bauteil == null) {
            null
        } else {
            ereignisErgebnis.mapCatching { ereignis ->
                val aufgefuellt = zustand.mitZusaetzlichenRohstoffen(fehlendeRohstoffe)
                SpielRegelwerk.wendeAn(aufgefuellt, ereignis).getOrThrow()
                ereignis
            }
        }
        AlertDialog(
            onDismissRequest = {
                ausgewaehltesZiel = null
                seewegStart = null
            },
            title = { Text(werkzeug.beschriftung) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ziel.anzeigeText())
                    if (pruefung.isSuccess) {
                        Text(
                            if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
                                "Das Startbauwerk wird ohne zusätzliche Kosten platziert."
                            } else {
                                "Die Aktion ist regelkonform. Hinterlegte Baukosten und Bestand " +
                                    "werden gemeinsam mit der Kartenbelegung geändert."
                            },
                        )
                    } else {
                        Text(
                            pruefung.exceptionOrNull()?.message ?: "Die Aktion ist nicht erlaubt.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (bauteil != null) {
                        Text(
                            text = if (bauteil.kosten.isEmpty()) {
                                "Für dieses Bauteil sind keine Rohstoffe hinterlegt."
                            } else {
                                "Baukosten: " + bauteil.kosten.entries.joinToString { (rohstoff, menge) ->
                                    "$menge × ${rohstoff.anzeigeName()}"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (fehlendeRohstoffe.isNotEmpty()) {
                            Text(
                                text = "Fehlt im Lager: " + fehlendeRohstoffe.entries.joinToString { (rohstoff, menge) ->
                                    "$menge × ${rohstoff.anzeigeName()}"
                                } + ". Der Finanzmittel-Bau kauft genau diese Mengen im Ausland.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ausgewaehltesZiel = null
                    seewegStart = null
                }) { Text("Abbrechen") }
            },
            confirmButton = {
                if (bauteil == null) {
                    TextButton(
                        enabled = pruefung.isSuccess,
                        onClick = {
                            pruefung.getOrNull()?.let(beiEreignis)
                            ausgewaehltesZiel = null
                            seewegStart = null
                        },
                    ) { Text("Bestätigen") }
                } else {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            enabled = pruefung.isSuccess,
                            onClick = {
                                pruefung.getOrNull()?.let(beiEreignis)
                                ausgewaehltesZiel = null
                                seewegStart = null
                                beiBauauftragBeendet()
                            },
                        ) { Text("Aus dem Lager bauen") }
                        Button(
                            enabled = finanziertePruefung?.isSuccess == true &&
                                beiBauAusFinanzmitteln != null,
                            onClick = {
                                val gebaut = finanziertePruefung?.getOrNull()?.let { ereignis ->
                                    beiBauAusFinanzmitteln?.invoke(ereignis, fehlendeRohstoffe)
                                } == true
                                if (gebaut) {
                                    ausgewaehltesZiel = null
                                    seewegStart = null
                                    beiBauauftragBeendet()
                                }
                            },
                        ) { Text("Aus Finanzmitteln bauen") }
                    }
                }
            },
        )
    }
}

internal fun fehlendeBauRohstoffe(
    zustand: SpielZustand,
    bauteil: BauteilTyp,
): Map<Rohstoff, Int> {
    val aktiverSpieler = zustand.spieler.firstOrNull { spieler ->
        spieler.id == zustand.aktiverSpieler
    } ?: return bauteil.kosten
    return bauteil.kosten.mapNotNull { (rohstoff, kosten) ->
        val fehlen = kosten - aktiverSpieler.rohstoffe.getOrDefault(rohstoff, 0)
        if (fehlen > 0) rohstoff to fehlen else null
    }.toMap()
}

private fun SpielZustand.mitZusaetzlichenRohstoffen(
    mengen: Map<Rohstoff, Int>,
): SpielZustand {
    val aktiver = aktiverSpieler ?: return this
    return copy(
        spieler = spieler.map { bestand ->
            if (bestand.id != aktiver) {
                bestand
            } else {
                bestand.copy(
                    rohstoffe = (bestand.rohstoffe.keys + mengen.keys).associateWith { rohstoff ->
                        bestand.rohstoffe.getOrDefault(rohstoff, 0) + mengen.getOrDefault(rohstoff, 0)
                    }.filterValues { menge -> menge > 0 },
                )
            }
        },
    )
}

private fun Rohstoff.anzeigeName(): String = name.lowercase().replace('_', ' ')
    .replaceFirstChar(Char::uppercase)

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
    rundeNullWerkzeuge: List<SpielKartenWerkzeug>,
    rundeNullRestbestand: Map<BauteilTyp, Int>,
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
                werkzeuge = rundeNullWerkzeuge,
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
                mengen = rundeNullRestbestand,
            )
        } else {
            Text("Ecke", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter { it.ziel == KartenZielModus.ECKE }
                    .filterNot { it == SpielKartenWerkzeug.HAUPTBAHNHOF || it.nurRundeNull },
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
            Text("Kante", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter {
                    it.ziel == KartenZielModus.KANTE && !it.nurRundeNull
                },
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
            Text("Feld", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter {
                    it.ziel == KartenZielModus.FELD && !it.nurRundeNull
                },
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
    mengen: Map<BauteilTyp, Int> = emptyMap(),
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        werkzeuge.forEach { eintrag ->
            FilterChip(
                selected = ausgewaehlt == eintrag,
                onClick = { beiWerkzeug(eintrag) },
                label = {
                    val menge = eintrag.startBauteil?.let { mengen[it] }
                    Text(if (menge == null) eintrag.beschriftung else "${eintrag.beschriftung} · $menge")
                },
            )
        }
    }
}

private fun SpielKartenWerkzeug.erstelleEreignis(
    zustand: SpielZustand,
    ziel: KartenOrt,
    rohstoff: Rohstoff,
    seewegStart: KartenOrt.Ecke?,
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
        SpielKartenWerkzeug.FRACHTSCHIFF -> {
            val hafenA = requireNotNull(seewegStart) { "Bitte zuerst den ersten Hafen wählen." }
            val hafenB = ziel as KartenOrt.Ecke
            val vorhandeneIds = zustand.karte?.belegung?.seewege.orEmpty().mapTo(mutableSetOf()) {
                it.id
            }
            val id = generateSequence(1) { nummer -> nummer + 1 }
                .map { nummer -> "frachtschiff-${spieler.wert}-$nummer" }
                .first { kandidat -> kandidat !in vorhandeneIds }
            SpielEreignis.SeewegEingerichtet(
                id = id,
                spieler = spieler,
                hafenA = hafenA.position,
                hafenB = hafenB.position,
                richtung = FrachtRichtung.A_NACH_B,
            )
        }
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
        SpielKartenWerkzeug.VIEHHOF,
        SpielKartenWerkzeug.ZIEGELBRENNER,
        SpielKartenWerkzeug.LEHMINE,
        SpielKartenWerkzeug.FOERSTER,
        SpielKartenWerkzeug.BOHRTURM,
        SpielKartenWerkzeug.RAFFINERIE,
        SpielKartenWerkzeug.SYNTHETIK_RAFFINERIE,
        SpielKartenWerkzeug.KOHLEMINE,
        SpielKartenWerkzeug.STAHLFABRIK,
        SpielKartenWerkzeug.EISENMINE -> SpielEreignis.NeutraleAnlageErrichtet(
            spieler,
            (ziel as KartenOrt.Feld).position,
            FeldAnlage.Wirtschaftsregion(requireNotNull(startBauteil)),
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
