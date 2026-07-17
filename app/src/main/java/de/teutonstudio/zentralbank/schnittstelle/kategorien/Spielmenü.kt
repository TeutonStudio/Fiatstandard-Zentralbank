package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.schnittstelle.GridByOrientation
import de.teutonstudio.zentralbank.schnittstelle.eingabe.ImageCard
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarben
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe

@OptIn(ExperimentalGridApi::class)
@Composable
fun Spielmenü(
    beiVermogenSaldo: () -> Unit,
    beiSchuldenSaldo: () -> Unit,
    beiMarktSaldo: () -> Unit,
    beiAuslandSaldo: () -> Unit,
    beiHandel: () -> Unit,
    beiAnleihe: () -> Unit,
    beiKarte: () -> Unit,
    beiNaechstemZugabschnitt: () -> Unit,
    spiel: Spiel,
    aktiverSpielerName: String?,
    zugText: String = "Kein Zug aktiv",
    zugZeitText: String? = null,
) {
    Titel(anleitung = remember { mutableStateOf(true) }) {
        GridByOrientation() { idx: Int, modifier -> when(idx) {
            0 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.saldo, beiKlick = beiVermogenSaldo)
            1 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.debt, beiKlick = beiSchuldenSaldo)
            2 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.market, beiKlick = beiMarktSaldo)
            3 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.foreign, beiKlick = beiAuslandSaldo)
            4 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.handel, beiKlick = beiHandel)
            5 -> ImageCard(modifier = modifier(Modifier), bild_index = R.drawable.anleihe, beiKlick = beiAnleihe)
            6 -> ZugStatusKarte(
                spiel = spiel,
                aktiverSpielerName = aktiverSpielerName,
                zugText = zugText,
                zugZeitText = zugZeitText,
                modifier = modifier(null),
                beiKlick = beiNaechstemZugabschnitt,
                beiKarte = beiKarte,
            )
        } }
    }
}

@Composable
private fun ZugStatusKarte(
    spiel: Spiel,
    aktiverSpielerName: String?,
    zugText: String,
    zugZeitText: String?,
    modifier: Modifier,
    beiKlick: () -> Unit,
    beiKarte: () -> Unit,
) {
    val spielerFarben = erhalteSpielerFarben(spiel.spielerListe)
    val aktiverIndex = spiel.spielerListe.indexOfFirst { spieler ->
        spieler.name == aktiverSpielerName
    }
    val verbleibendeZuege = if (aktiverIndex >= 0) {
        spiel.spielerListe.size - aktiverIndex
    } else {
        spiel.spielerListe.size
    }

    Card(
        modifier = modifier,
        onClick = beiKlick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = zugText,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
            zugZeitText?.let { zeit ->
                Text(
                    text = "Spieltag $zeit Uhr",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (spiel.spielerListe.isNotEmpty()) {
                Text(
                    text = "$verbleibendeZuege Züge bis zu den neu berechneten Marktpreisen",
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 11.sp,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    spiel.spielerListe.forEachIndexed { index, spieler ->
                        val istAktiv = index == aktiverIndex
                        val hintergrund = if (istAktiv) {
                            spielerFarben.getValue(spieler)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        Card(
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = hintergrund,
                                contentColor = if (istAktiv) {
                                    hintergrund.lesbareSchriftfarbe()
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Text(
                                text = spieler.name,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                fontSize = if (istAktiv) 11.sp else 9.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = beiKarte,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("Spielkarte öffnen")
            }
        }
    }
}

@Preview(

)
@Composable
private fun SpielmenüPreview() {
    Column() {
        Spielmenü({},{},{},{},{},{},{},{}, TestSpiel, TestSpiel.spielerListe.first().name)
    }
}
