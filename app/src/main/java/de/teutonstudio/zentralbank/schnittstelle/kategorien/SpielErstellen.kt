package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.entries
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel
import de.teutonstudio.zentralbank.schnittstelle.eingabe.MAXIMALE_SPIELER_ANZAHL
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereBauteile
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereLeitzinsatzZiele
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereSpieler
import de.teutonstudio.zentralbank.schnittstelle.eingabe.definiereWarenkorb
import java.util.UUID

@Composable
fun SpielErstellen(
    nachAbbruch: () -> Unit,
    erstelleSpiel: (daten: Spiel, nachErstellen: () -> Unit) -> Unit,
    nachAbschluß: () -> Unit,
    seite: MutableIntState = remember { mutableIntStateOf(1) },
    vorbelegteKarte: Spielkarte? = null,
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
            Bauteil.entries.associateWith { 0 }.toMutableMap()
        }
    }
    val spielerNamen = spieler.keys.toList()
    val abschlussSeite = 5 + spielerNamen.size
    Titel(
        beiZurück = { seite.intValue -= 1 },
        beiWeiter = if (seite.intValue < abschlussSeite) {
            {
                val darfWeiter = spielerGültig.value &&
                    (seite.intValue != 4 || ausgewaehlteKarte.value != null)
                if (darfWeiter) seite.intValue += 1
            }
        } else {
            null
        },
        anleitung = remember { mutableStateOf(false) }
    ) {
        when (seite.intValue) {
            0 -> { nachAbbruch() }
            1 -> { definiereSpieler(spielerGültig,spieler) }
            2 -> { definiereWarenkorb(warenkorb) }
            3 -> { definiereLeitzinsatzZiele(zentralbankZiele) }
            4 -> {
                KartenAuswahl(
                    ausgewaehlteKarte = ausgewaehlteKarte.value,
                    beiAuswahl = { karte -> ausgewaehlteKarte.value = karte },
                )
            }
            in 5 until abschlussSeite -> {
                val idx = seite.intValue - 5
                val fürWenn = "für ${spielerNamen[idx]}"

                definiereBauteile(fürWenn, bauteileProSpieler[idx])
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
                        karte = karte.copy(id = "spiel-${UUID.randomUUID()}"),
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
    val seite = remember { mutableIntStateOf(4) }
    Column() {
        SpielErstellen({}, { _, _ -> }, {}, seite)
    }
}
