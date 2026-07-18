package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel
import de.teutonstudio.zentralbank.schnittstelle.eingabe.MAXIMALE_SPIELER_ANZAHL
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereBauteile
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereLeitzinsatzZiele
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereSpieler
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereWarenkorb
import java.util.UUID

internal const val STARTBAUWERKE_LAZY_ROW = "startbauwerke_spieler"

@Composable
fun SpielErstellen(
    nachAbbruch: () -> Unit,
    erstelleSpiel: (daten: Spiel, nachErstellen: () -> Unit) -> Unit,
    nachAbschluß: () -> Unit,
    seite: MutableIntState = remember { mutableIntStateOf(1) },
    vorbelegteKarte: KartenVorlage? = null,
) {
    val spieler = remember { mutableStateMapOf(
        "Spieler 1" to 100.toZahlungsmittel(),
        "Spieler 2" to 100.toZahlungsmittel(),
        "Spieler 3" to 100.toZahlungsmittel(),
    ) }
    val spielerGültig = remember { mutableStateOf(true) }
    val ausgewaehlteKarte = remember { mutableStateOf(vorbelegteKarte) }
    val warenkorb = remember { mutableStateMapOf<Rohstoffe, Int>() }
    val zentralbankZiele = remember { mutableListOf(15f,2f,.5f,2f) }
    val bauteileProSpieler = remember {
        List(MAXIMALE_SPIELER_ANZAHL) {
            Bauteil.entries.associateWith { bauteil ->
                if (bauteil == Verwaltungsstandort.HAUPTBAHNHOF) 1 else 0
            }.toMutableMap()
        }
    }
    val spielerNamen = spieler.keys.toList()
    val abschlussSeite = 5
    Titel(
        beiZurück = {
            if (seite.intValue > 1) {
                seite.intValue -= 1
            } else {
                nachAbbruch()
            }
        },
        beiWeiter = if (seite.intValue < abschlussSeite) {
            {
                val darfWeiter = spielerGültig.value &&
                    (seite.intValue != 3 || ausgewaehlteKarte.value != null)
                if (darfWeiter) seite.intValue += 1
            }
        } else {
            null
        },
        anleitung = remember { mutableStateOf(false) }
    ) {
        when (seite.intValue) {
            1 -> { definiereSpieler(spielerGültig,spieler) }
            2 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        definiereWarenkorb(warenkorb)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        definiereLeitzinsatzZiele(zentralbankZiele)
                    }
                }
            }
            3 -> {
                KartenAuswahl(
                    ausgewaehlteKarte = ausgewaehlteKarte.value,
                    beiAuswahl = { karte -> ausgewaehlteKarte.value = karte },
                )
            }
            4 -> {
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(STARTBAUWERKE_LAZY_ROW),
                ) {
                    itemsIndexed(spielerNamen) { idx, spielerName ->
                        definiereBauteile(
                            fürWenn = "für $spielerName",
                            inhalt = bauteileProSpieler[idx],
                        )
                    }
                }
            }
            abschlussSeite -> {
                val ausgabe = spielerNamen.mapIndexed { idx, name ->
                    val bauteile = bauteileProSpieler[idx].toMutableMap()
                    Spieler(name, bauteile) to (spieler[name] ?: Zahlungsmittel())
                }.toMap()
                val karte = ausgewaehlteKarte.value
                if (karte == null) {
                    Text("Bitte zuerst eine Spielkarte auswählen.")
                } else {
                    val daten = Spiel(
                        leitzinssatz = zentralbankZiele[0],
                        spieler = ausgabe,
                        warenkorb = warenkorb.toMap(),
                        inflationsziel = zentralbankZiele[1],
                        normaleAbweichung = zentralbankZiele[2],
                        starkeAbweichung = zentralbankZiele[3],
                        karte = karte.alsSpielkarte(spielId = "spiel-${UUID.randomUUID()}"),
                    )
                    LaunchedEffect(Unit) {
                        erstelleSpiel(daten, nachAbschluß)
                    }
                    Text("Spiel wird erstellt …")
                }
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
private fun SpielErstellenPreview() {
    val seite = remember { mutableIntStateOf(3) }
    Column() {
        SpielErstellen({}, { _, _ -> }, {}, seite)
    }
}
