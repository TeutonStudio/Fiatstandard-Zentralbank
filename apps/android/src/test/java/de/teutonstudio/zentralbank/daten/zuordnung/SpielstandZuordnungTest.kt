package de.teutonstudio.zentralbank.daten.zuordnung

import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpielstandZuordnungTest {
    private val anna = SpielerId("Anna")
    private val spiel = GespeichertesSpiel(
        id = 42,
        startzustand = SpielZustand(
            spieler = listOf(Spieler(id = anna, name = "Anna")),
            karte = Spielkarte(
                id = "spiel-42",
                name = "Insel 42",
                hexagon = KartenHexagon(radius = 7),
            ),
        ),
        ereignisse = listOf(
            SpielEreignis.ProzugBegonnen(1L),
        ),
    )

    @Test
    fun fachlicherSpielstandDurchlaeuftDasRoomFormatOhneInformationsverlust() {
        val geladen = spiel.zuEntitaet().zuGespeichertemSpiel()

        assertEquals(spiel, geladen)
        assertEquals("spiel-42", geladen.startzustand.karte?.id)
        assertEquals(true, geladen.aktuellerZustand().zugStatus?.prozug?.begonnen)
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
    fun altesZugformatWirdMitVerstaendlicherMeldungAbgelehnt() {
        val entitaet = spiel.zuEntitaet().copy(formatVersion = 1)

        val fehler = assertThrows(IllegalArgumentException::class.java) {
            entitaet.zuGespeichertemSpiel()
        }

        assertEquals(
            "Spielstand 42 verwendet das alte Zugformat 1. " +
                "Es enthält keine nachweisbaren Prozug-Buchungen und kann deshalb nicht sicher geladen werden.",
            fehler.message,
        )
    }

    @Test
    fun tabellendatenWerdenAlsSpielRekonstruiert() {
        val (spielDaten, fachDaten) = TestSpiel.zuSpeicherDaten()

        val rekonstruiert = fachDaten.zuSpiel(
            daten = spielDaten,
            karte = TestSpiel.karte,
        )

        assertEquals(TestSpiel.spielerStringListe, rekonstruiert.spielerStringListe)
        assertEquals(TestSpiel.aktuelleRunde, rekonstruiert.aktuelleRunde)
        assertEquals(TestSpiel.warenkorb, rekonstruiert.warenkorb)
        assertEquals(TestSpiel.karte, rekonstruiert.karte)
    }
}
