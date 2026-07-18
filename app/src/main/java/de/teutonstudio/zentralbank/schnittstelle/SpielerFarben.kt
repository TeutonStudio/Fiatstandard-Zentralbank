package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.datenbank.Spieler

private val spielerFarben = listOf(
    Color(0xFFA85D00), // Orange
    Color(0xFF827000), // Ocker
    Color(0xFF367A3D), // Grün
    Color(0xFF007B83), // Türkis
    Color(0xFF3F6FAF), // Blau
    Color(0xFF7659AD), // Violett
    Color(0xFFA94F85), // Magenta
)

val auslandFarbe = Color(0xFF607681)

fun erhalteSpielerFarbe(spielerIndex: Int): Color =
    spielerFarben.getOrElse(spielerIndex) { Color(0xFF707070) }

fun erhalteSpielerFarben(spielerListe: List<Spieler>): Map<Spieler, Color> =
    spielerListe
        .mapIndexed { index, spieler ->
            spieler to erhalteSpielerFarbe(index)
        }
        .toMap()
