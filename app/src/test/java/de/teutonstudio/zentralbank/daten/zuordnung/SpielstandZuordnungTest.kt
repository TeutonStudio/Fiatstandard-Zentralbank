package de.teutonstudio.zentralbank.daten.zuordnung

import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.GespeichertesSpiel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpielstandZuordnungTest {
    private val anna = SpielerId("Anna")
    private val spiel = GespeichertesSpiel(
        id = 42,
        startzustand = SpielZustand(
            spieler = listOf(Spieler(id = anna, name = "Anna")),
        ),
        ereignisse = listOf(
            SpielEreignis.RohstoffEinnahme(
                spieler = anna,
                mengen = mapOf(Rohstoff.LEHM to 3),
            ),
        ),
        ausLegacyDatenImportiert = true,
    )

    @Test
    fun fachlicherSpielstandDurchlaeuftDasRoomFormatOhneInformationsverlust() {
        val geladen = spiel.zuEntitaet().zuGespeichertemSpiel()

        assertEquals(spiel, geladen)
        assertEquals(3, geladen.aktuellerZustand().spieler.single().rohstoffe[Rohstoff.LEHM])
    }

    @Test
    fun unbekannteFormatversionWirdExplizitAbgelehnt() {
        val entitaet = spiel.zuEntitaet().copy(formatVersion = 99)

        val fehler = assertThrows(IllegalArgumentException::class.java) {
            entitaet.zuGespeichertemSpiel()
        }

        assertEquals(
            "Spielstand 42 verwendet die nicht unterstützte Formatversion 99.",
            fehler.message,
        )
    }

    @Test
    fun bestehenderRoomDatensatzBleibtAlsLegacySpielRekonstruierbar() {
        val (spielDaten, fachDaten) = TestSpiel.zuSpeicherDaten()

        val rekonstruiert = fachDaten.zuLegacySpiel(spielDaten)

        assertEquals(TestSpiel.spielerStringListe, rekonstruiert.spielerStringListe)
        assertEquals(TestSpiel.aktuelleRunde, rekonstruiert.aktuelleRunde)
        assertEquals(TestSpiel.warenkorb, rekonstruiert.warenkorb)
    }
}
