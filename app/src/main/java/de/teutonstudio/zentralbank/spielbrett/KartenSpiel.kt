package de.teutonstudio.zentralbank.spielbrett

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.fachlogik.ereignis.KartenAenderungsGrund
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.bewegungsKosten
import de.teutonstudio.zentralbank.fachlogik.modell.kuerzesterWasserweg
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.bauteilIconPfadOderNull

private enum class SpielKartenWerkzeug(
    val beschriftung: String,
    val ziel: KartenZielModus,
    val startBauteil: BauteilTyp? = null,
    val startKriegsEinheit: KriegsEinheitTyp? = null,
    val nurRundeNull: Boolean = false,
) {
    HAUPTBAHNHOF("Hauptbahnhof", KartenZielModus.ECKE, BauteilTyp.HAUPTBAHNHOF),
    BAHNHOF("Bahnhof", KartenZielModus.ECKE, BauteilTyp.BAHNHOF),
    HAFEN("Hafen", KartenZielModus.ECKE, BauteilTyp.HAFEN),
    FRACHTSCHIFF("Frachtschiff", KartenZielModus.ECKE, BauteilTyp.FRACHTSCHIFF),
    AUFWERTEN("Aufwerten", KartenZielModus.ECKE),
    ECKE_BELAGERN("Belagern", KartenZielModus.ECKE),
    ECKE_ZERSTOEREN("Zerstören", KartenZielModus.ECKE),
    ECKE_REPARIEREN("Reparieren", KartenZielModus.ECKE),
    ECKE_ENTFERNEN("Entfernen", KartenZielModus.ECKE),
    SCHIENE("Handelslinie", KartenZielModus.KANTE, BauteilTyp.EISENBAHNLINIE),
    PANZER_BAUEN(
        "Panzer bauen",
        KartenZielModus.KANTE,
        startKriegsEinheit = KriegsEinheitTyp.PANZER,
    ),
    KRIEGSSCHIFF_BAUEN(
        "Kriegsschiff bauen",
        KartenZielModus.KANTE,
        startKriegsEinheit = KriegsEinheitTyp.KRIEGSSCHIFF,
    ),
    TRUPPE_BEWEGEN("Truppe bewegen", KartenZielModus.KANTE),
    TRUPPE_ENTFERNEN("Truppe entfernen", KartenZielModus.KANTE),
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

private val planbareWerkzeuge: List<SpielKartenWerkzeug> =
    SpielKartenWerkzeug.entries.filter { eintrag ->
        (eintrag.startBauteil != null ||
            eintrag.startKriegsEinheit != null ||
            eintrag == SpielKartenWerkzeug.TRUPPE_BEWEGEN) &&
            eintrag != SpielKartenWerkzeug.HAUPTBAHNHOF &&
            !eintrag.nurRundeNull
    }

private val SpielKartenWerkzeug.planungsKosten: Map<Rohstoff, Int>?
    get() = when {
        startBauteil != null -> startBauteil.kosten
        startKriegsEinheit != null -> emptyMap()
        else -> null
    }

private val KompakteKopfleistenHoehe = 146.dp

private data class GeplanterBauauftrag(
    val werkzeug: SpielKartenWerkzeug,
    val kosten: Map<Rohstoff, Int>,
    val ziel: KartenOrt,
    val ereignis: SpielEreignis,
)

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
    beiBauplanAusLager: ((List<SpielEreignis>) -> Boolean)? = null,
    beiBauplanAusFinanzmitteln: ((List<SpielEreignis>, Map<Rohstoff, Int>) -> Boolean)? = null,
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
    var seewegBearbeitungId by remember { mutableStateOf<String?>(null) }
    var aktiverSeewegId by remember { mutableStateOf<String?>(null) }
    var truppenStart by remember { mutableStateOf<KartenOrt.Kante?>(null) }
    var werkzeugVorHoverAufwertung by remember { mutableStateOf<SpielKartenWerkzeug?>(null) }
    var planungsmodus by remember(zustand.spielabschnitt, aktiverSpieler) {
        mutableStateOf(false)
    }
    var geplanteBauten by remember(zustand.spielabschnitt, aktiverSpieler) {
        mutableStateOf(emptyList<GeplanterBauauftrag>())
    }
    var planungsFehler by remember(zustand.spielabschnitt, aktiverSpieler) {
        mutableStateOf<String?>(null)
    }

    val planungsErgebnis = remember(zustand, geplanteBauten) {
        projiziereBauplan(zustand, geplanteBauten)
    }
    val planungsZustand = planungsErgebnis.getOrElse { zustand }
    val geplanteRohstoffkosten = remember(geplanteBauten) {
        summiereRohstoffKosten(geplanteBauten.map { auftrag -> auftrag.kosten })
    }
    val fehlendePlanRohstoffe = remember(zustand, geplanteRohstoffkosten) {
        fehlendeBauRohstoffe(zustand, geplanteRohstoffkosten)
    }
    val lagerPruefung = remember(zustand, geplanteBauten) {
        pruefeBauereignisse(zustand, geplanteBauten.map { auftrag -> auftrag.ereignis })
    }
    val finanziertePruefung = remember(zustand, geplanteBauten, fehlendePlanRohstoffe) {
        pruefeBauereignisse(
            zustand.mitZusaetzlichenRohstoffen(fehlendePlanRohstoffe),
            geplanteBauten.map { auftrag -> auftrag.ereignis },
        )
    }

    val setzePlanungsmodus: (Boolean) -> Unit = { aktiv ->
        planungsmodus = aktiv && zustand.spielabschnitt == Spielabschnitt.REGULAER
        ausgewaehltesZiel = null
        seewegStart = null
        seewegBearbeitungId = null
        truppenStart = null
        planungsFehler = null
        if (!planungsmodus) {
            geplanteBauten = emptyList()
        } else if (werkzeug !in planbareWerkzeuge) {
            werkzeug = SpielKartenWerkzeug.BAHNHOF
        }
    }

    val bauwerkPlanen: (KartenOrt) -> Unit = planen@ { ziel ->
        if (werkzeug !in planbareWerkzeuge) {
            planungsFehler =
                "Im Planungsmodus können Bauwerke, Einheiten und Truppenbewegungen gewählt werden."
            return@planen
        }
        val ereignis = runCatching {
            werkzeug.erstelleEreignis(
                zustand = planungsZustand,
                ziel = ziel,
                rohstoff = rohstoff,
                seewegStart = seewegStart,
                seewegBearbeitungId = seewegBearbeitungId,
                truppenStart = truppenStart,
            )
        }.getOrElse { fehler ->
            planungsFehler = fehler.message ?: "Das Vorhaben konnte nicht erstellt werden."
            return@planen
        }
        val kosten = when (ereignis) {
            is SpielEreignis.KriegsEinheitBewegt -> {
                val einheit = planungsZustand.karte?.belegung?.kriegseinheiten
                    .orEmpty()
                    .firstOrNull { belegung -> belegung.id == ereignis.id }
                    ?: run {
                        planungsFehler = "Die zu bewegende Einheit wurde nicht gefunden."
                        return@planen
                    }
                einheit.typ.bewegungsKosten(ereignis.weg.size)
            }
            else -> werkzeug.planungsKosten ?: run {
                planungsFehler = "Für dieses Vorhaben ist kein Rohstoffbedarf definiert."
                return@planen
            }
        }
        val pruefzustand = planungsZustand.mitZusaetzlichenRohstoffen(
            fehlendeBauRohstoffe(planungsZustand, kosten),
        )
        SpielRegelwerk.wendeAn(pruefzustand, ereignis).fold(
            onSuccess = {
                geplanteBauten = geplanteBauten + GeplanterBauauftrag(
                    werkzeug = werkzeug,
                    kosten = kosten,
                    ziel = ziel,
                    ereignis = ereignis,
                )
                planungsFehler = null
            },
            onFailure = { fehler ->
                planungsFehler = fehler.message ?: "Das Vorhaben kann dort nicht geplant werden."
            },
        )
        seewegStart = null
        seewegBearbeitungId = null
        truppenStart = null
    }

    LaunchedEffect(vorgewaehltesBauteil) {
        vorgewaehltesBauteil?.let { bauteil ->
            setzePlanungsmodus(false)
            SpielKartenWerkzeug.entries.firstOrNull { eintrag ->
                eintrag.startBauteil == bauteil
            }?.let { externesWerkzeug ->
                werkzeug = externesWerkzeug
                werkzeugVorHoverAufwertung = null
                ausgewaehltesZiel = null
                seewegStart = null
                seewegBearbeitungId = null
                truppenStart = null
            }
        }
    }

    val aufwertungAusHover: (KartenEcke) -> Unit = aufwerten@ { ecke ->
        val basisZustand = if (planungsmodus) planungsZustand else zustand
        val bisher = basisZustand.karte?.belegung?.eckenNachPosition?.get(ecke)
            ?: return@aufwerten
        val (zu, bauteil) = when (bisher.typ) {
            EckGebaeudeTyp.BAHNHOF ->
                EckGebaeudeTyp.GROSSBAHNHOF to BauteilTyp.GROSSBAHNHOF
            EckGebaeudeTyp.HAFEN -> EckGebaeudeTyp.GROSSHAFEN to BauteilTyp.GROSSHAFEN
            else -> return@aufwerten
        }
        val ereignis = SpielEreignis.EckGebaeudeAufgewertet(aktiverSpieler, ecke, zu)
        if (!planungsmodus) {
            werkzeugVorHoverAufwertung = werkzeug
            werkzeug = SpielKartenWerkzeug.AUFWERTEN
            ausgewaehltesZiel = KartenOrt.Ecke(ecke)
            return@aufwerten
        }
        val pruefzustand = basisZustand.mitZusaetzlichenRohstoffen(
            fehlendeBauRohstoffe(basisZustand, bauteil),
        )
        SpielRegelwerk.wendeAn(pruefzustand, ereignis).fold(
            onSuccess = {
                geplanteBauten = geplanteBauten + GeplanterBauauftrag(
                    werkzeug = SpielKartenWerkzeug.AUFWERTEN,
                    kosten = bauteil.kosten,
                    ziel = KartenOrt.Ecke(ecke),
                    ereignis = ereignis,
                )
                planungsFehler = null
            },
            onFailure = { fehler ->
                planungsFehler = fehler.message ?: "Das Gebäude kann nicht aufgewertet werden."
            },
        )
    }
    val beendeHoverAufwertung: () -> Unit = {
        werkzeugVorHoverAufwertung?.let { vorher -> werkzeug = vorher }
        werkzeugVorHoverAufwertung = null
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
                        selected = planungsmodus,
                        enabled = zustand.spielabschnitt == Spielabschnitt.REGULAER &&
                            vorgewaehltesBauteil == null,
                        onClick = { setzePlanungsmodus(true) },
                        label = { Text("Planen") },
                    )
                    FilterChip(
                        selected = !planungsmodus,
                        onClick = { setzePlanungsmodus(false) },
                        label = { Text("Spielen") },
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val breiteAnsicht = maxWidth >= 840.dp
            val verfuegbareBreite = maxWidth
            val warenkorbEingebettet = !kompakteZentrale &&
                planungsmodus && maxWidth >= 1180.dp
            val werkzeugleiste: @Composable (Modifier) -> Unit = { leistenModifier ->
                SpielWerkzeugleiste(
                    modifier = leistenModifier,
                    werkzeug = werkzeug,
                    beiWerkzeug = { neu ->
                        werkzeug = neu
                        ausgewaehltesZiel = null
                        seewegStart = null
                        seewegBearbeitungId = null
                        truppenStart = null
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
                    planungsmodus = planungsmodus,
                )
            }
            val warenkorb: @Composable (Modifier) -> Unit = { warenkorbModifier ->
                BauwerkWarenkorb(
                    modifier = warenkorbModifier,
                    werkzeug = werkzeug,
                    beiWerkzeug = { neu ->
                        werkzeug = neu
                        ausgewaehltesZiel = null
                        seewegStart = null
                        seewegBearbeitungId = null
                        truppenStart = null
                        planungsFehler = null
                    },
                    geplanteBauten = geplanteBauten,
                    marktpreise = zustand.marktpreise,
                    rohstoffkosten = geplanteRohstoffkosten,
                    fehlendeRohstoffe = fehlendePlanRohstoffe,
                    fehlermeldung = planungsFehler
                        ?: planungsErgebnis.exceptionOrNull()?.message,
                    lagerBauMoeglich = geplanteBauten.isNotEmpty() && lagerPruefung.isSuccess,
                    finanzierterBauMoeglich = geplanteBauten.isNotEmpty() &&
                        finanziertePruefung.isSuccess,
                    beiEntfernen = { index ->
                        val gekuerzt = geplanteBauten.filterIndexed { aktuellerIndex, _ ->
                            aktuellerIndex != index
                        }
                        projiziereBauplan(zustand, gekuerzt).fold(
                            onSuccess = {
                                geplanteBauten = gekuerzt
                                planungsFehler = null
                                seewegStart = null
                                seewegBearbeitungId = null
                            },
                            onFailure = { fehler ->
                                planungsFehler = fehler.message
                                    ?: "Der Bauauftrag wird von späteren Planungen benötigt."
                            },
                        )
                    },
                    beiAusLagerBauen = {
                        val gebaut = beiBauplanAusLager?.invoke(
                            geplanteBauten.map { auftrag -> auftrag.ereignis },
                        ) == true
                        if (gebaut) setzePlanungsmodus(false)
                    },
                    beiAusFinanzmittelnBauen = {
                        val gebaut = beiBauplanAusFinanzmitteln?.invoke(
                            geplanteBauten.map { auftrag -> auftrag.ereignis },
                            fehlendePlanRohstoffe,
                        ) == true
                        if (gebaut) setzePlanungsmodus(false)
                    },
                    lagerAktionVorhanden = beiBauplanAusLager != null,
                    finanzAktionVorhanden = beiBauplanAusFinanzmitteln != null,
                    beiSchliessen = { setzePlanungsmodus(false) },
                )
            }
            val brett: @Composable (Modifier) -> Unit = { brettModifier ->
                val angezeigteKarte = if (planungsmodus) {
                    planungsZustand.karte ?: karte
                } else {
                    karte
                }
                val hervorgehobeneRoute = aktiverSeewegId
                    ?.let { id ->
                        angezeigteKarte.belegung.seewege.firstOrNull { seeweg -> seeweg.id == id }
                    }
                    ?.let { seeweg ->
                        angezeigteKarte.kuerzesterWasserweg(seeweg.hafenA, seeweg.hafenB)
                    }
                    .orEmpty()
                    .toSet()
                val epizugAktiv = zustand.zugStatus?.phase == ZugPhase.Epizug
                val aufwertbareEcken = if (planungsmodus || epizugAktiv) {
                    angezeigteKarte.belegung.ecken
                        .asSequence()
                        .filter { belegung ->
                            belegung.besitzer == aktiverSpieler &&
                                belegung.zustand == BauwerkZustand.INTAKT &&
                                belegung.typ in setOf(
                                    EckGebaeudeTyp.BAHNHOF,
                                    EckGebaeudeTyp.HAFEN,
                                )
                        }
                        .map { belegung -> belegung.position }
                        .toSet()
                } else {
                    emptySet()
                }
                val aenderbareSeewege = if (!planungsmodus && epizugAktiv) {
                    angezeigteKarte.belegung.seewege
                        .filter { seeweg -> seeweg.besitzer == aktiverSpieler }
                        .mapTo(mutableSetOf()) { seeweg -> seeweg.id }
                } else {
                    emptySet()
                }
                Box(modifier = brettModifier) {
                    Spielbrett3D(
                        modell = angezeigteKarte.zu3DModell(
                            spielerReihenfolge = zustand.spieler.map { it.id },
                            hervorhebung = ausgewaehltesZiel ?: seewegStart ?: truppenStart,
                            routenHervorhebung = hervorgehobeneRoute,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        betrachtungsStatus = betrachtungsStatus,
                        kameraInteraktionsModus = kameraModus,
                        himmel = himmel,
                        bauwerkInfoFreiraum = if (kompakteZentrale) {
                            PaddingValues(
                                start = 140.dp,
                                top = KompakteKopfleistenHoehe,
                                end = if (
                                    planungsmodus && verfuegbareBreite >= 720.dp
                                ) 352.dp else 0.dp,
                                bottom = if (
                                    planungsmodus || vorgewaehltesBauteil != null
                                ) 56.dp else 0.dp,
                            )
                        } else {
                            PaddingValues(bottom = 56.dp)
                        },
                        aufwertbareEcken = aufwertbareEcken,
                        aenderbareSeewege = aenderbareSeewege,
                        beiEckgebaeudeAufwerten = aufwertungAusHover,
                        beiSeewegRouteAendern = { id ->
                            werkzeug = SpielKartenWerkzeug.FRACHTSCHIFF
                            seewegBearbeitungId = id
                            seewegStart = null
                            ausgewaehltesZiel = null
                            planungsFehler = null
                        },
                        beiAktivemSeeweg = { id -> aktiverSeewegId = id },
                        onDreieckBeruehrt = beruehrung@ { treffer ->
                            if (
                                planungsmodus || !kompakteZentrale ||
                                vorgewaehltesBauteil != null || seewegBearbeitungId != null
                            ) {
                                val ziel = treffer.zuKartenOrt(werkzeug.ziel)
                                    ?: return@beruehrung
                                if (werkzeug == SpielKartenWerkzeug.FRACHTSCHIFF) {
                                    val hafen = ziel as KartenOrt.Ecke
                                    if (seewegStart == null) {
                                        seewegStart = hafen
                                        planungsFehler = null
                                    } else if (planungsmodus && seewegBearbeitungId == null) {
                                        bauwerkPlanen(hafen)
                                    } else {
                                        ausgewaehltesZiel = hafen
                                    }
                                } else if (werkzeug == SpielKartenWerkzeug.TRUPPE_BEWEGEN) {
                                    val kante = ziel as KartenOrt.Kante
                                    if (truppenStart == null) {
                                        truppenStart = kante
                                        planungsFehler = null
                                    } else if (planungsmodus) {
                                        bauwerkPlanen(kante)
                                    } else {
                                        ausgewaehltesZiel = kante
                                    }
                                } else if (planungsmodus) {
                                    bauwerkPlanen(ziel)
                                } else {
                                    ausgewaehltesZiel = ziel
                                }
                            }
                        },
                    )
                    if (
                        planungsmodus || !kompakteZentrale || vorgewaehltesBauteil != null ||
                        seewegBearbeitungId != null
                    ) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ) {
                            if (planungsmodus) {
                                Text(
                                    if (seewegBearbeitungId != null) {
                                        "Route ändern: ${if (seewegStart == null) "ersten" else "zweiten"} Hafen wählen"
                                    } else if (werkzeug == SpielKartenWerkzeug.FRACHTSCHIFF) {
                                        "Planen: ${if (seewegStart == null) "ersten" else "zweiten"} Hafen wählen"
                                    } else if (werkzeug == SpielKartenWerkzeug.TRUPPE_BEWEGEN) {
                                        "Planen: ${if (truppenStart == null) "Truppenkante" else "benachbarte Zielkante"} wählen"
                                    } else {
                                        "Planen: ${werkzeug.beschriftung} auf der Karte wählen"
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            } else if (vorgewaehltesBauteil != null) {
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
                            } else if (seewegBearbeitungId != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "Route ändern: ${if (seewegStart == null) "ersten" else "zweiten"} Hafen wählen",
                                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    TextButton(onClick = {
                                        ausgewaehltesZiel = null
                                        seewegStart = null
                                        seewegBearbeitungId = null
                                    }) {
                                        Text("Abbrechen")
                                    }
                                }
                            } else {
                                Text(
                                    if (werkzeug == SpielKartenWerkzeug.TRUPPE_BEWEGEN) {
                                        "Truppe bewegen: ${if (truppenStart == null) "Truppenkante" else "benachbarte Zielkante"} wählen"
                                    } else {
                                        "Tippen: ${werkzeug.ziel.name.lowercase()} wählen · " +
                                        "Ziehen: ${if (kameraModus == KameraInteraktionsModus.DREHEN) "drehen" else "verschieben"} · " +
                                            "Zwei Finger: verschieben/zoomen"
                                    },
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
                    if (warenkorbEingebettet) {
                        warenkorb(Modifier.widthIn(min = 280.dp, max = 340.dp).fillMaxHeight())
                    }
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
            if (zustand.spielabschnitt == Spielabschnitt.REGULAER &&
                vorgewaehltesBauteil == null && !planungsmodus && kompakteZentrale
            ) {
                OutlinedButton(
                    onClick = { setzePlanungsmodus(true) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                ) {
                    Text("Planen")
                }
            }
            if (planungsmodus && !warenkorbEingebettet) {
                warenkorb(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = if (kompakteZentrale) KompakteKopfleistenHoehe else 8.dp,
                            end = 8.dp,
                            bottom = if (kompakteZentrale) 56.dp else 8.dp,
                        )
                        .fillMaxHeight()
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(),
                )
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
                seewegBearbeitungId = seewegBearbeitungId,
                truppenStart = truppenStart,
            )
        }
        val pruefung = ereignisErgebnis.mapCatching { ereignis ->
            SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
            ereignis
        }
        val bauteil = when (val ereignis = ereignisErgebnis.getOrNull()) {
            is SpielEreignis.SeewegRouteGeaendert -> null
            is SpielEreignis.EckGebaeudeAufgewertet -> when (ereignis.zu) {
                EckGebaeudeTyp.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
                EckGebaeudeTyp.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
                else -> null
            }
            else -> werkzeug.startBauteil
        }?.takeIf { zustand.spielabschnitt == Spielabschnitt.REGULAER }
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
                seewegBearbeitungId = null
                truppenStart = null
                beendeHoverAufwertung()
            },
            title = { Text(werkzeug.beschriftung) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ziel.anzeigeText())
                    if (pruefung.isSuccess) {
                        Text(
                            when (val ereignis = pruefung.getOrNull()) {
                                is SpielEreignis.KriegsEinheitGebaut ->
                                    "Die Truppe wird ohne zusätzliche Herstellungskosten gebaut."
                                is SpielEreignis.KriegsEinheitBewegt -> {
                                    val typ = zustand.karte?.belegung?.kriegseinheiten
                                        .orEmpty()
                                        .first { einheit -> einheit.id == ereignis.id }
                                        .typ
                                    "Treibstoff: ${ereignis.weg.size} × " +
                                        typ.bewegungsRohstoff.anzeigeName()
                                }
                                is SpielEreignis.KriegsEinheitEntfernt ->
                                    "Die Truppe wird von der Karte entfernt."
                                else -> if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
                                    "Das Startbauwerk wird ohne zusätzliche Kosten platziert."
                                } else {
                                    "Die Aktion ist regelkonform. Hinterlegte Baukosten und " +
                                        "Bestand werden gemeinsam mit der Kartenbelegung geändert."
                                }
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
                    seewegBearbeitungId = null
                    truppenStart = null
                    beendeHoverAufwertung()
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
                            seewegBearbeitungId = null
                            truppenStart = null
                            beendeHoverAufwertung()
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
                                seewegBearbeitungId = null
                                truppenStart = null
                                beendeHoverAufwertung()
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
                                    seewegBearbeitungId = null
                                    truppenStart = null
                                    beendeHoverAufwertung()
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
): Map<Rohstoff, Int> = fehlendeBauRohstoffe(zustand, bauteil.kosten)

internal fun fehlendeBauRohstoffe(
    zustand: SpielZustand,
    kosten: Map<Rohstoff, Int>,
): Map<Rohstoff, Int> {
    val aktiverSpieler = zustand.spieler.firstOrNull { spieler ->
        spieler.id == zustand.aktiverSpieler
    } ?: return kosten
    return kosten.mapNotNull { (rohstoff, menge) ->
        val fehlen = menge - aktiverSpieler.rohstoffe.getOrDefault(rohstoff, 0)
        if (fehlen > 0) rohstoff to fehlen else null
    }.toMap()
}

internal fun bauRohstoffKosten(bauteile: Iterable<BauteilTyp>): Map<Rohstoff, Int> =
    summiereRohstoffKosten(bauteile.map(BauteilTyp::kosten))

private fun summiereRohstoffKosten(
    kosten: Iterable<Map<Rohstoff, Int>>,
): Map<Rohstoff, Int> =
    kosten
        .flatMap(Map<Rohstoff, Int>::entries)
        .groupingBy { eintrag -> eintrag.key }
        .fold(0) { summe, eintrag -> summe + eintrag.value }
        .toSortedMap(compareBy(Rohstoff::ordinal))

internal fun marktpreisSumme(
    kosten: Map<Rohstoff, Int>,
    marktpreise: Map<Rohstoff, Geld>,
): Geld? {
    if (kosten.any { (rohstoff, menge) -> menge > 0 && rohstoff !in marktpreise }) return null
    return kosten.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
        summe + (marktpreise[rohstoff] ?: Geld.NULL) * menge
    }
}

private fun pruefeBauereignisse(
    ausgangszustand: SpielZustand,
    ereignisse: List<SpielEreignis>,
): Result<SpielZustand> = runCatching {
    ereignisse.fold(ausgangszustand) { aktuellerZustand, ereignis ->
        SpielRegelwerk.wendeAn(aktuellerZustand, ereignis).getOrThrow()
    }
}

private fun projiziereBauplan(
    ausgangszustand: SpielZustand,
    auftraege: List<GeplanterBauauftrag>,
): Result<SpielZustand> = runCatching {
    auftraege.fold(ausgangszustand) { aktuellerZustand, auftrag ->
        val pruefzustand = aktuellerZustand.mitZusaetzlichenRohstoffen(
            fehlendeBauRohstoffe(aktuellerZustand, auftrag.kosten),
        )
        SpielRegelwerk.wendeAn(pruefzustand, auftrag.ereignis).getOrThrow()
    }
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
private fun BauwerkWarenkorb(
    modifier: Modifier,
    werkzeug: SpielKartenWerkzeug,
    beiWerkzeug: (SpielKartenWerkzeug) -> Unit,
    geplanteBauten: List<GeplanterBauauftrag>,
    marktpreise: Map<Rohstoff, Geld>,
    rohstoffkosten: Map<Rohstoff, Int>,
    fehlendeRohstoffe: Map<Rohstoff, Int>,
    fehlermeldung: String?,
    lagerBauMoeglich: Boolean,
    finanzierterBauMoeglich: Boolean,
    beiEntfernen: (Int) -> Unit,
    beiAusLagerBauen: () -> Unit,
    beiAusFinanzmittelnBauen: () -> Unit,
    lagerAktionVorhanden: Boolean,
    finanzAktionVorhanden: Boolean,
    beiSchliessen: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Bau- und Einsatzplan", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (geplanteBauten.size == 1) {
                            "1 Vorhaben geplant"
                        } else {
                            "${geplanteBauten.size} Vorhaben geplant"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = beiSchliessen) { Text("Verwerfen") }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Vorhaben wählen", style = MaterialTheme.typography.labelLarge)
                        WerkzeugChips(
                            werkzeuge = planbareWerkzeuge,
                            ausgewaehlt = werkzeug,
                            beiWerkzeug = beiWerkzeug,
                        )
                        Text(
                            "Danach Bauort oder Start- und Zielkante auf der Karte antippen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (geplanteBauten.isEmpty()) {
                    item {
                        Text(
                            "Noch keine Vorhaben im Plan.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                itemsIndexed(geplanteBauten) { index, auftrag ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}. ${auftrag.werkzeug.beschriftung}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(onClick = { beiEntfernen(index) }) {
                                    Text("Entfernen")
                                }
                            }
                            Text(
                                auftrag.ziel.anzeigeText(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Rohstoffbedarf: ${auftrag.kosten.alsKostenText()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Marktpreis: " + marktpreisSumme(
                                    auftrag.kosten,
                                    marktpreise,
                                ).alsPreisText(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            auftrag.werkzeug.startKriegsEinheit?.let { einheit ->
                                Text(
                                    "Bewegung: 1 × ${einheit.bewegungsRohstoff.anzeigeName()} je Kante",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider()
                        Text("Rohstoffbedarf gesamt", style = MaterialTheme.typography.titleSmall)
                        if (rohstoffkosten.isEmpty()) {
                            Text("Keine", style = MaterialTheme.typography.bodySmall)
                        } else {
                            rohstoffkosten.forEach { (rohstoff, menge) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "$menge × ${rohstoff.anzeigeName()}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        marktpreisSumme(
                                            mapOf(rohstoff to menge),
                                            marktpreise,
                                        ).alsPreisText(),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Marktpreis (Summe)", fontWeight = FontWeight.Bold)
                            Text(
                                marktpreisSumme(rohstoffkosten, marktpreise).alsPreisText(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (fehlendeRohstoffe.isNotEmpty()) {
                            Text(
                                "Fehlt im Lager: ${fehlendeRohstoffe.alsKostenText()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        fehlermeldung?.let { meldung ->
                            Text(
                                meldung,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = beiAusLagerBauen,
                enabled = lagerBauMoeglich && lagerAktionVorhanden,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Plan aus Lager ausführen")
            }
            Button(
                onClick = beiAusFinanzmittelnBauen,
                enabled = finanzierterBauMoeglich && finanzAktionVorhanden,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Plan mit Finanzmitteln ausführen")
            }
        }
    }
}

private fun Map<Rohstoff, Int>.alsKostenText(): String = if (isEmpty()) {
    "keine"
} else {
    entries.joinToString { (rohstoff, menge) -> "$menge × ${rohstoff.anzeigeName()}" }
}

private fun Geld?.alsPreisText(): String = this?.zuMarkString() ?: "nicht verfügbar"

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
    planungsmodus: Boolean,
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
        } else if (planungsmodus) {
            Text(
                "Planbare Bauwerke, Einheiten und Bewegungen",
                style = MaterialTheme.typography.titleSmall,
            )
            WerkzeugChips(
                werkzeuge = planbareWerkzeuge,
                ausgewaehlt = werkzeug,
                beiWerkzeug = beiWerkzeug,
            )
        } else {
            Text("Ecke", style = MaterialTheme.typography.titleSmall)
            WerkzeugChips(
                werkzeuge = SpielKartenWerkzeug.entries.filter { it.ziel == KartenZielModus.ECKE }
                    .filterNot {
                        it == SpielKartenWerkzeug.HAUPTBAHNHOF ||
                            it == SpielKartenWerkzeug.AUFWERTEN || it.nurRundeNull
                    },
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
                leadingIcon = eintrag.startBauteil?.bauteilIconPfadOderNull()?.let { iconPfad ->
                    {
                        Image(
                            painter = painterResource(iconPfad),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                },
                label = {
                    val menge = eintrag.startBauteil?.let { mengen[it] }
                    val beschriftung = if (menge == null) {
                        eintrag.beschriftung
                    } else {
                        "${eintrag.beschriftung} · $menge"
                    }
                    Text(beschriftung)
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
    seewegBearbeitungId: String?,
    truppenStart: KartenOrt.Kante?,
): SpielEreignis {
    val spieler = requireNotNull(zustand.aktiverSpieler) { "Es ist kein Spieler aktiv." }
    return when (this) {
        SpielKartenWerkzeug.HAUPTBAHNHOF -> SpielEreignis.HauptbahnhofPlatziert(
            spieler,
            (ziel as KartenOrt.Ecke).position,
        )
        SpielKartenWerkzeug.BAHNHOF -> eckGebaeude(spieler, ziel, EckGebaeudeTyp.BAHNHOF)
        SpielKartenWerkzeug.HAFEN -> eckGebaeude(spieler, ziel, EckGebaeudeTyp.HAFEN)
        SpielKartenWerkzeug.FRACHTSCHIFF -> {
            val hafenA = requireNotNull(seewegStart) { "Bitte zuerst den ersten Hafen wählen." }
            val hafenB = ziel as KartenOrt.Ecke
            if (seewegBearbeitungId != null) {
                return SpielEreignis.SeewegRouteGeaendert(
                    spieler = spieler,
                    id = seewegBearbeitungId,
                    hafenA = hafenA.position,
                    hafenB = hafenB.position,
                )
            }
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
        SpielKartenWerkzeug.PANZER_BAUEN,
        SpielKartenWerkzeug.KRIEGSSCHIFF_BAUEN -> {
            val typ = if (this == SpielKartenWerkzeug.PANZER_BAUEN) {
                KriegsEinheitTyp.PANZER
            } else {
                KriegsEinheitTyp.KRIEGSSCHIFF
            }
            val vorhandeneIds = zustand.karte?.belegung?.kriegseinheiten.orEmpty()
                .mapTo(mutableSetOf()) { einheit -> einheit.id }
            val namensTeil = typ.name.lowercase()
            val id = generateSequence(1) { nummer -> nummer + 1 }
                .map { nummer -> "$namensTeil-${spieler.wert}-$nummer" }
                .first { kandidat -> kandidat !in vorhandeneIds }
            SpielEreignis.KriegsEinheitGebaut(
                id = id,
                spieler = spieler,
                typ = typ,
                kante = (ziel as KartenOrt.Kante).position,
            )
        }
        SpielKartenWerkzeug.TRUPPE_BEWEGEN -> {
            val start = requireNotNull(truppenStart) { "Bitte zuerst eine Truppenkante wählen." }
            val einheit = zustand.karte?.belegung?.kriegseinheiten
                .orEmpty()
                .singleOrNull { belegung -> belegung.position == start.position }
                ?: error("Auf der gewählten Startkante steht nicht genau eine Truppe.")
            require(einheit.besitzer == spieler) { "Nur die eigene Truppe darf bewegt werden." }
            SpielEreignis.KriegsEinheitBewegt(
                spieler = spieler,
                id = einheit.id,
                weg = listOf((ziel as KartenOrt.Kante).position),
            )
        }
        SpielKartenWerkzeug.TRUPPE_ENTFERNEN -> {
            val kante = (ziel as KartenOrt.Kante).position
            val einheit = zustand.karte?.belegung?.kriegseinheiten
                .orEmpty()
                .singleOrNull { belegung -> belegung.position == kante }
                ?: error("Auf der gewählten Kante steht nicht genau eine Truppe.")
            SpielEreignis.KriegsEinheitEntfernt(spieler = spieler, id = einheit.id)
        }
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
