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

@Composable
fun SpielErstellen(
    nachAbbruch: () -> Unit,
    erstelleSpiel: (daten: Spiel, nachErstellen: () -> Unit) -> Unit,
    nachAbschluß: () -> Unit,
    seite: MutableIntState = remember { mutableIntStateOf(1) },
) {
    val spieler = remember { mutableStateMapOf(
        "Spieler 1" to 100.toZahlungsmittel(),
        "Spieler 2" to 100.toZahlungsmittel(),
        "Spieler 3" to 100.toZahlungsmittel(),
    ) }
    val spielerGültig = remember { mutableStateOf(true) }
    val warenkorb = remember { mutableStateMapOf<Rohstoffe, Int>() }
    val zentralbankZiele = remember { mutableListOf(15f,2f,.5f,2f) }
    val bauteileProSpieler = remember {
        List(MAXIMALE_SPIELER_ANZAHL) {
            Bauteil.entries.associateWith { 0 }.toMutableMap()
        }
    }
    val spielerNamen = spieler.keys.toList()
    val abschlussSeite = 4 + spielerNamen.size
    Titel(
        beiZurück = { seite.intValue -= 1 },
        beiWeiter = if (seite.intValue < abschlussSeite) {
            { if (spielerGültig.value) seite.intValue += 1 }
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
            in 4 until abschlussSeite -> {
                val idx = seite.intValue - 4
                val fürWenn = "für ${spielerNamen[idx]}"

                definiereBauteile(fürWenn, bauteileProSpieler[idx])
            }
            abschlussSeite -> {
                val ausgabe = spielerNamen.mapIndexed { idx, name ->
                    val bauteile = bauteileProSpieler[idx].toMutableMap()
                    Spieler(name, bauteile) to (spieler[name] ?: Zahlungsmittel())
                }.toMap()
                val daten = Spiel(
                    zentralbankZiele[0],
                    ausgabe,
                    warenkorb.toMap(),
                    zentralbankZiele[1],
                    zentralbankZiele[2],
                    zentralbankZiele[3]
                )
                LaunchedEffect(Unit) {
                    erstelleSpiel(daten, nachAbschluß)
                }
                Text("Spiel wird erstellt …")
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
