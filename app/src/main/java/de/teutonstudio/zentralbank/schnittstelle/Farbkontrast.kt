package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val GRENZE_FUER_DUNKLE_SCHRIFT = 0.179f

/** Liefert für eine deckende Fachfarbe eine kontrastreiche Schwarz-/Weiß-Schriftfarbe. */
fun Color.lesbareSchriftfarbe(): Color =
    if (luminance() > GRENZE_FUER_DUNKLE_SCHRIFT) Color.Black else Color.White
