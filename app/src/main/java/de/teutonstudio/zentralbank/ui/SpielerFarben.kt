package de.teutonstudio.zentralbank.ui

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.datenbank.Spieler

private val spielerPastellfarben = listOf(
    Color(0xFFFFD6A5), // Pfirsich / Orange
    Color(0xFFFDFFB6), // Gelb
    Color(0xFFCAFFBF), // Grün
    Color(0xFF9BF6FF), // Türkis
    Color(0xFFA0C4FF), // Blau
    Color(0xFFBDB2FF), // Violett
    Color(0xFFFFC6FF), // Rosa
)

fun erhalteSpielerFarben(spielerListe: List<Spieler>): Map<Spieler, Color> =
    spielerListe
        .mapIndexed { index, spieler ->
            spieler to spielerPastellfarben.getOrElse(index) {
                Color.LightGray
            }
        }
        .toMap()
