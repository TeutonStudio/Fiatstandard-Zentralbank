package de.teutonstudio.zentralbank.schnittstelle.kategorien

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoffe
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.ROHSTOFF_ICON_SKALIERUNG
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeRohstoff
import de.teutonstudio.zentralbank.schnittstelle.erhalteSpielerFarbe
import de.teutonstudio.zentralbank.schnittstelle.lesbareSchriftfarbe

enum class SpielmenueBereich(
    val beschriftung: String,
    val icon: Int,
) {
    SALDO("Saldo", R.drawable.saldo),
    SCHULDEN("Schulden", R.drawable.debt),
    MARKTPLATZ("Marktplatz", R.drawable.market),
    AUSSENHANDEL("Außenhandel", R.drawable.foreign),
}

internal val verarbeiteteRohstoffe = listOf(
    Rohstoff.ZIEGEL,
    Rohstoff.DIESEL,
    Rohstoff.SCHWEROEL,
    Rohstoff.STAHL,
)

internal val unverarbeiteteRohstoffe = listOf(
    Rohstoff.NAHRUNG,
    Rohstoff.LEHM,
    Rohstoff.HOLZ,
    Rohstoff.ROHOEL,
    Rohstoff.KOHLE,
    Rohstoff.EISEN,
)

@Composable
fun Spielmenü(
    zustand: SpielZustand,
    zugText: String,
    zugZeitText: String?,
    beiBereich: (SpielmenueBereich) -> Unit,
    beiZugBeenden: () -> Unit,
    beiSpielBeenden: () -> Unit,
    modifier: Modifier = Modifier,
    kartenInhalt: @Composable BoxScope.() -> Unit,
) {
    val aktiverSpieler = zustand.spieler.firstOrNull { spieler ->
        spieler.id == zustand.aktiverSpieler
    }
    val aktiverSpielerIndex = zustand.spieler.indexOfFirst { spieler ->
        spieler.id == zustand.aktiverSpieler
    }
    val leistenFarbe = if (aktiverSpielerIndex >= 0) {
        erhalteSpielerFarbe(aktiverSpielerIndex)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val leistenInhaltFarbe = leistenFarbe.lesbareSchriftfarbe()

    Box(modifier = modifier.fillMaxSize().padding(12.dp)) {
        kartenInhalt()

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 132.dp, end = 8.dp, top = 8.dp)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 7.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 120.dp)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 5.dp,
                shadowElevation = 4.dp,
                color = leistenFarbe,
                contentColor = leistenInhaltFarbe,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = aktiverSpieler?.name ?: "Kein Spieler",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = aktiverSpieler?.geldkonto?.zuMarkString() ?: "–",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    zugZeitText?.let { zeit ->
                        Text(
                            text = "$zeit Uhr",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(5.dp))
            RohstoffBestandsGruppe(
                rohstoffe = verarbeiteteRohstoffe,
                bestand = { rohstoff ->
                    aktiverSpieler?.rohstoffe?.getOrDefault(rohstoff, 0) ?: 0
                },
            )
            RohstoffBestandsGruppe(
                rohstoffe = unverarbeiteteRohstoffe,
                bestand = { rohstoff ->
                    aktiverSpieler?.rohstoffe?.getOrDefault(rohstoff, 0) ?: 0
                },
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp, top = 70.dp, bottom = 8.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 5.dp,
            shadowElevation = 4.dp,
            color = leistenFarbe,
            contentColor = leistenInhaltFarbe,
        ) {
            Column(
                modifier = Modifier.padding(7.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SpielmenueBereich.entries.forEach { bereich ->
                    OutlinedButton(
                        onClick = { beiBereich(bereich) },
                        modifier = Modifier.widthIn(min = 110.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = leistenInhaltFarbe,
                        ),
                    ) {
                        Image(
                            painter = painterResource(bereich.icon),
                            contentDescription = bereich.beschriftung,
                            modifier = Modifier.size(26.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(bereich.beschriftung)
                    }
                }
                Button(
                    onClick = beiZugBeenden,
                    enabled = zustand.zugStatus?.phase == ZugPhase.Epizug,
                    modifier = Modifier.widthIn(min = 110.dp),
                ) {
                    Text("Zug beenden")
                }
                OutlinedButton(
                    onClick = beiSpielBeenden,
                    modifier = Modifier.widthIn(min = 110.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = leistenInhaltFarbe,
                    ),
                ) {
                    Text("Spielstand beenden")
                }
                Text(
                    text = zugText,
                    modifier = Modifier.widthIn(max = 110.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Änderungen werden laufend gespeichert.",
                    modifier = Modifier.widthIn(max = 110.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun RohstoffBestandsGruppe(
    rohstoffe: List<Rohstoff>,
    bestand: (Rohstoff) -> Int,
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        rohstoffe.forEach { rohstoff ->
            val darstellung = rohstoff.zuRohstoffe()
            val farbe = darstellung.farbe
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = farbe,
                contentColor = farbe.lesbareSchriftfarbe(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(28.dp * ROHSTOFF_ICON_SKALIERUNG),
                        contentAlignment = Alignment.Center,
                    ) {
                        zeigeRohstoff(
                            rohstoff = darstellung,
                            iconSize = 28.dp,
                            text = false,
                        )
                    }
                    Text(
                        text = darstellung.str.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = bestand(rohstoff).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun SpielmenueDialog(
    titel: String,
    beiSchliessen: () -> Unit,
    inhalt: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = beiSchliessen,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(titel, style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = beiSchliessen) { Text("Schließen") }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp)) {
                    inhalt()
                }
            }
        }
    }
}
