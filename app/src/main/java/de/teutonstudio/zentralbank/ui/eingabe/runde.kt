package de.teutonstudio.zentralbank.ui.eingabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Runde
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel


@Composable
fun definiereRunde(angezeigteRunde: MutableIntState, breite_fraction: Float = 1f) {
    definiereGanzzahl("aktuelle Runde",angezeigteRunde,breite_fraction)
}

@Composable
fun definiereLeitzinssatz(runde: Int, angezeigterLeitzinssatz: MutableFloatState, breite_fraction: Float = 1f) {
    ZinsEingabe("Leitzinssatz für Runde $runde",angezeigterLeitzinssatz,breite_fraction)
}

@Composable
fun bearbeiteRunde(gameID: Long, spiel: Spiel, onInput: (Runde) -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Card {
            val eingabeRunde = remember { mutableIntStateOf(spiel.aktuelleRunde) }
            val eingabeLeitzinssatz = remember { mutableFloatStateOf(spiel.nächsterZinssatz) }
            val eingabeSiedler = remember { mutableStateOf("Spieler wählen") }
            Row {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.dice),
                        contentDescription = null,
                        modifier = Modifier.clickable { onInput( Runde(
                            eingabeRunde.intValue,
                            eingabeLeitzinssatz.floatValue,
                        ) ) }
                    )
                    Text(text = "SpielID: $gameID", fontSize = 25.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        definiereRunde(eingabeRunde,.5f)
                        definiereLeitzinssatz(eingabeRunde.intValue,eingabeLeitzinssatz)
                    }
                    wähleSiedler(spiel.spielerStringListe,eingabeSiedler)
                }
            }
        }
    }
}

@Preview
@Composable
private fun RundePreview() {
    Column() {
        bearbeiteRunde(-1,spiel,) { }
    }
}

private val spieler = mapOf(
    "Spieler 1" to 100.toZahlungsmittel(),
    "Spieler 2" to 100.toZahlungsmittel(),
    "Spieler 3" to 100.toZahlungsmittel(),
).map{ Spieler(it.key,emptyMap()) to it.value }.toMap()
private val spiel = Spiel(
    15f,spieler,Rohstoffe.entries.associateWith { -1 },2f,.5f,1.8f
)
