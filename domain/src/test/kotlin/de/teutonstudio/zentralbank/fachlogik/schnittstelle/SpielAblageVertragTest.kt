package de.teutonstudio.zentralbank.fachlogik.schnittstelle

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpielAblageVertragTest {
    private val anna = SpielerId("Anna")
    private val spiel = GespeichertesSpiel(
        id = 7,
        startzustand = SpielZustand(
            spieler = listOf(Spieler(id = anna, name = "Anna")),
        ),
        ereignisse = listOf(
            SpielEreignis.RohstoffEinnahme(
                spieler = anna,
                mengen = mapOf(Rohstoff.HOLZ to 2),
            ),
        ),
    )

    @Test
    fun speichernLadenBeobachtenUndLoeschenFolgenDemAblagevertrag() = runBlocking {
        val ablage: SpielAblage = ArbeitsspeicherSpielAblage()

        ablage.spielSpeichern(spiel)

        assertEquals(spiel, ablage.spielLaden(spiel.id))
        assertEquals(
            listOf(
                SpielstandUebersicht(
                    id = 7,
                    spielerNamen = listOf("Anna"),
                    runde = 0,
                    ausLegacyDatenImportiert = false,
                ),
            ),
            ablage.spielstaendeBeobachten().first(),
        )
        assertEquals(
            2,
            ablage.spielLaden(spiel.id)
                ?.aktuellerZustand()
                ?.spieler
                ?.single()
                ?.rohstoffe
                ?.get(Rohstoff.HOLZ),
        )

        ablage.spielLoeschen(spiel.id)

        assertNull(ablage.spielLaden(spiel.id))
        assertEquals(emptyList<SpielstandUebersicht>(), ablage.spielstaendeBeobachten().first())
    }

    private class ArbeitsspeicherSpielAblage : SpielAblage {
        private val spiele = MutableStateFlow<Map<Long, GespeichertesSpiel>>(emptyMap())

        override fun spielstaendeBeobachten() = spiele.map { gespeicherteSpiele ->
            gespeicherteSpiele.values
                .sortedBy { spiel -> spiel.id }
                .map { spiel -> spiel.zuUebersicht() }
        }

        override suspend fun spielLaden(id: Long): GespeichertesSpiel? = spiele.value[id]

        override suspend fun spielSpeichern(spiel: GespeichertesSpiel) {
            spiele.value = spiele.value + (spiel.id to spiel)
        }

        override suspend fun spielLoeschen(id: Long) {
            spiele.value = spiele.value - id
        }
    }
}
