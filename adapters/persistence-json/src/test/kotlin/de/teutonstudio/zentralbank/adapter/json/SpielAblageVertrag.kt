package de.teutonstudio.zentralbank.adapter.json

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

internal fun pruefeAblageVertrag(ablage: SpielAblage) = runBlocking {
    val anna = SpielerId("Anna")
    val gespeichert = GespeichertesSpiel(
        id = 7,
        startzustand = SpielZustand(spieler = listOf(Spieler(anna, "Anna"))),
        ereignisse = listOf(SpielEreignis.ProzugBegonnen(1L)),
        seed = 42,
    )

    ablage.spielSpeichern(gespeichert)
    assertEquals(gespeichert, ablage.spielLaden(7))
    assertEquals(listOf(7L), ablage.spielstaendeBeobachten().first().map { it.id })

    ablage.spielLoeschen(7)
    assertNull(ablage.spielLaden(7))
}
