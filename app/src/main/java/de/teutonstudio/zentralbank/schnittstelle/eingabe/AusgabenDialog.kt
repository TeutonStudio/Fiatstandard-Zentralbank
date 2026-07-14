package de.teutonstudio.zentralbank.schnittstelle.eingabe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.teutonstudio.zentralbank.datenbank.Ausgabenplan
import de.teutonstudio.zentralbank.datenbank.zuMark

@Composable
fun AusgabenDialog(
    plan: Ausgabenplan,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text("Ausgaben von ${plan.spieler.name} · Runde ${plan.runde}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Zahlungen",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (plan.zahlungen.isEmpty()) {
                    Text("Keine Zins- oder Rückzahlungen in dieser Runde.")
                } else {
                    plan.zahlungen.forEach { zahlung ->
                        Text(
                            text = "${zahlung.art.bezeichnung} an ${zahlung.empfaenger.name}: " +
                                zahlung.betrag.zuMark(),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = "Rohstoffe an Gebäude",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                if (plan.rohstoffVerwendungen.isEmpty()) {
                    Text("Kein Rohstoffverbrauch durch Gebäude in dieser Runde.")
                } else {
                    plan.rohstoffVerwendungen.forEach { verwendung ->
                        Text(
                            text = "${verwendung.rohstoffAnzahl} × ${verwendung.rohstoff.str} → " +
                                "${verwendung.gebaeudeAnzahl} × ${verwendung.bauteil.str}",
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Ausgaben abschließen")
            }
        },
    )
}
