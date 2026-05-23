package de.teutonstudio.zentralbank.ui.ausgabe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import kotlin.math.abs

import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.Anleihenhandel
import de.teutonstudio.zentralbank.datenbank.Geschäftsbank
import de.teutonstudio.zentralbank.datenbank.JuristischePerson
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.ui.LeftText
import de.teutonstudio.zentralbank.ui.ModiOrange
import de.teutonstudio.zentralbank.ui.ModiPad15
import de.teutonstudio.zentralbank.ui.ModiPad5
import de.teutonstudio.zentralbank.ui.RightText
import de.teutonstudio.zentralbank.ui.markBy


private val fälligFarbe = Color.Yellow
private val offenFarbe = Color.Cyan
private val abgelaufenFarbe = Color.Gray

@Composable
fun zeigeAnleihe(
    aktuelleRunde: Int,
    spielerListe: List<Spieler>,
//    siederListe: List<String>,
    anleihe: Map<Int, Anleihenhandel>,
    istAusgeklappt: Boolean = false,
    istHandeln: Boolean = false,
    onAnleiheHandel: (runde: Int, handel: Anleihenhandel) -> Unit = { _, _ -> },
) {
    val istAnleiheAusgeklappt = remember { mutableStateOf(istAusgeklappt) }
    val istAnleiheHandeln = remember { mutableStateOf(istHandeln) }

    val stammAnleihe = anleihe.erhalteAnleihe()

    if (stammAnleihe == null) {
        Card(modifier = ModiPad5.wrapContentSize()) {
            Text(
                text = "Keine Anleihedaten",
                modifier = ModiPad15,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val background: Color =
        if (istAnleiheAusgeklappt.value) Color.LightGray
        else if (anleihe.istFällig(aktuelleRunde)) fälligFarbe
        else if (anleihe.istAbgelaufen(aktuelleRunde)) abgelaufenFarbe
        else if (anleihe.istOffen(aktuelleRunde)) offenFarbe
        else Color.LightGray

    Card(
        onClick = {
            istAnleiheAusgeklappt.value = !istAnleiheAusgeklappt.value
            istAnleiheHandeln.value = false
        },
        modifier = ModiPad5.wrapContentSize(),
    ) {
        if (istAnleiheAusgeklappt.value) {
            VerticalGrid(
                columns = SimpleGridCells.Fixed(3),
                modifier = ModiPad5,
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                val distance = 10

                RightText(text = "Betrag zur Runde", fontSize = 15.sp)
                RightText(
                    text = "Begünstigter",
                    modifier = Modifier.padding(start = distance.dp, end = distance.dp),
                    fontSize = 15.sp
                )
                LeftText(
                    text = "Benachteiligter",
                    modifier = Modifier.padding(start = distance.dp, end = distance.dp),
                    fontSize = 15.sp
                )

                AnleihenAblauf(
                    aktuelleRunde = aktuelleRunde,
                    anleihe = anleihe
                )

                Card(
                    modifier = ModiPad5
                        .span(3)
                        .clickable {
                            istAnleiheHandeln.value = !istAnleiheHandeln.value
                        }
                ) {
                    Box(modifier = ModiOrange.wrapContentSize()) {
                        val wählen = "Käufer wählen"
                        val eingabeKäufer = remember { mutableStateOf(wählen) }

                        if (istAnleiheHandeln.value) {
                            VerticalGrid(
                                columns = SimpleGridCells.Fixed(2),
                                modifier = ModiPad5,
                                horizontalArrangement = Arrangement.Center,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val aktuellerBesitzer =
                                    anleihe.erhalteAktuellenBesitzer(aktuelleRunde)

                                var expanded by remember { mutableStateOf(false) }

                                Text(
                                    text = eingabeKäufer.value,
                                    modifier = ModiPad15.clickable {
                                        expanded = !expanded
                                    },
                                    textAlign = TextAlign.Center
                                )

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    (spielerListe + Geschäftsbank)
                                        .filter { it.name != aktuellerBesitzer?.name }
                                        .forEach { spieler ->
                                            DropdownMenuItem(
                                                text = { Text(text = spieler.name) },
                                                onClick = {
                                                    eingabeKäufer.value = spieler.name
                                                    expanded = false
                                                }
                                            )
                                        }
                                }

                                Text(
                                    text = "abbrechen",
                                    modifier = ModiPad15.clickable {
                                        istAnleiheHandeln.value = false
                                    },
                                    textAlign = TextAlign.Center
                                )

                                if (eingabeKäufer.value != wählen) {
                                    Text(
                                        text = "speichern",
                                        modifier = ModiPad15.clickable {
                                            onAnleiheHandel(
                                                aktuelleRunde,
                                                Anleihenhandel(
                                                    besitzer = spielerListe.find { it.name == aktuellerBesitzer?.name }!!,
                                                    erwerber = spielerListe.find { it.name == eingabeKäufer.value }!!,
                                                    anleihe = stammAnleihe,
                                                    preis = stammAnleihe.sondervermögen
                                                )
                                            )
                                            istAnleiheHandeln.value = false
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Handeln",
                                modifier = ModiPad5.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.background(background),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.anleihe),
                    contentDescription = null
                )

                Column {
                    Text(
                        text = anleihe.erhalteHerausgeber(),
                        fontSize = 25.sp
                    )

                    val restRunden =
                        anleihe.erhalteHerausgabe() + stammAnleihe.laufzeit - aktuelleRunde

                    if (restRunden > 0) {
                        Text(
                            text = "Noch $restRunden Rate" +
                                    if (restRunden != 1) "n" else "",
                            fontSize = 15.sp
                        )
                    } else {
                        Text(
                            text = "Seit ${abs(restRunden)} Runde" +
                                    if (abs(restRunden) != 1) "n" else "",
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = markBy(stammAnleihe.sondervermögen) +
                                " zu ${stammAnleihe.erhalteZinssatz()} %",
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AnleihenAblauf(
    aktuelleRunde: Int,
    anleihe: Map<Int, Anleihenhandel>,
) {
    val distance = 5

    val handel = anleihe
        .toSortedMap()
        .entries
        .toList()

    if (handel.isEmpty()) return

    val herausgabe = handel.first().key
    val emission = handel.first().value
    val herausgeber = emission.besitzer
    val stammAnleihe = emission.anleihe
    val letzteZahlungsrunde = herausgabe + stammAnleihe.laufzeit

    var aktuellerGläubiger = emission.erwerber
    var nächsteZahlungsrunde = herausgabe + 1

    fun modifierFürRunde(runde: Int): Modifier {
        return when {
            aktuelleRunde == runde -> Modifier.background(fälligFarbe)
            aktuelleRunde > runde -> Modifier.background(abgelaufenFarbe)
            else -> Modifier.background(offenFarbe)
        }
    }

    @Composable
    fun zahlungZeichnen(runde: Int, gläubiger: String) {
        val modifier = modifierFürRunde(runde)

        RightText(
            text = "$runde mit ${markBy(stammAnleihe.unvermögen)}",
            modifier = modifier
        )
        RightText(
            text = gläubiger,
            modifier = modifier.padding(start = distance.dp, end = distance.dp)
        )
        LeftText(
            text = herausgeber.name,
            modifier = modifier.padding(start = distance.dp, end = distance.dp)
        )
    }

    @Composable
    fun handelZeichnen(runde: Int, handel: Anleihenhandel) {
        val modifier = modifierFürRunde(runde)

        RightText(
            text = "Veräußert für ${markBy(handel.erhalteBetrag())}",
            modifier = modifier
        )
        RightText(
            text = handel.besitzer.name,
            modifier = modifier.padding(start = distance.dp, end = distance.dp)
        )
        LeftText(
            text = handel.erwerber.name,
            modifier = modifier.padding(start = distance.dp, end = distance.dp)
        )
    }

    handelZeichnen(herausgabe, emission)

    handel.drop(1).forEach { eintrag ->
        val handelsRunde = eintrag.key
        val handelsDaten = eintrag.value

        while (
            nächsteZahlungsrunde <= handelsRunde &&
            nächsteZahlungsrunde <= letzteZahlungsrunde
        ) {
            zahlungZeichnen(
                runde = nächsteZahlungsrunde,
                gläubiger = aktuellerGläubiger.name
            )
            nächsteZahlungsrunde++
        }

        handelZeichnen(
            runde = handelsRunde,
            handel = handelsDaten
        )

        aktuellerGläubiger = handelsDaten.erwerber

        if (nächsteZahlungsrunde <= handelsRunde) {
            nächsteZahlungsrunde = handelsRunde + 1
        }
    }

    while (nächsteZahlungsrunde <= letzteZahlungsrunde) {
        zahlungZeichnen(
            runde = nächsteZahlungsrunde,
            gläubiger = aktuellerGläubiger.name
        )
        nächsteZahlungsrunde++
    }
}

private fun Map<Int, Anleihenhandel>.erhalteHerausgabe(): Int {
    return keys.minOrNull() ?: 0
}

private fun Map<Int, Anleihenhandel>.erhalteEmission(): Anleihenhandel? {
    return minByOrNull { it.key }?.value
}

private fun Map<Int, Anleihenhandel>.erhalteAnleihe(): Anleihe? {
    return erhalteEmission()?.anleihe
}

private fun Map<Int, Anleihenhandel>.erhalteHerausgeber(): String {
    return erhalteEmission()?.besitzer?.name ?: ""
}

private fun Map<Int, Anleihenhandel>.erhalteAktuellenBesitzer(
    aktuelleRunde: Int,
): JuristischePerson? {
    return entries
        .filter { it.key <= aktuelleRunde }
        .maxByOrNull { it.key }
        ?.value
        ?.erwerber
        ?: erhalteEmission()?.erwerber
}

private fun Map<Int, Anleihenhandel>.istOffen(
    aktuelleRunde: Int,
): Boolean {
    val herausgabe = keys.minOrNull() ?: return false
    return aktuelleRunde <= herausgabe
}

private fun Map<Int, Anleihenhandel>.istFällig(
    aktuelleRunde: Int,
): Boolean {
    val herausgabe = keys.minOrNull() ?: return false
    val laufzeit = erhalteAnleihe()?.laufzeit ?: return false

    return aktuelleRunde in (herausgabe + 1)..(herausgabe + laufzeit)
}

private fun Map<Int, Anleihenhandel>.istAbgelaufen(
    aktuelleRunde: Int,
): Boolean {
    val herausgabe = keys.minOrNull() ?: return false
    val laufzeit = erhalteAnleihe()?.laufzeit ?: return false

    return aktuelleRunde > herausgabe + laufzeit
}


@Preview
@Composable
private fun PreviewAnleihe() {
    val siedlerListe = listOf("Spieler 1","Spieler 2","Spieler 3")
    // val anleiheDaten = AnleiheDaten(-1,"Spieler 1" to "Zentralbank", Zahlungsmittel(150) to Zahlungsmittel(10),3 to 7)
    // anleihe.anleihenHandel(5,"Spieler 3",160)
    // anleihe.anleihenHandel(5,"Spieler 2",165)
    // anleihe.anleihenHandel(7,"Zentralbank",155)
    Column() {
        // zeigeAnleihe(7,siedlerListe,anleiheDaten)
        // zeigeAnleihe(7,siedlerListe,anleiheDaten,true)
        // zeigeAnleihe(7,siedlerListe,anleiheDaten,true,true)
        // zeigeAnleihe(7,siedlerListe,anleiheDaten,false,true)
    }
}