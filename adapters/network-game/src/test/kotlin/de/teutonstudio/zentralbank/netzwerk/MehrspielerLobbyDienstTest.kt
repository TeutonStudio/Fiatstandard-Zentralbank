package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.adapter.json.ArbeitsspeicherSpielAblage
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerFarbe
import de.teutonstudio.zentralbank.protokoll.LobbyErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.LobbyKonfigurationDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerAendernDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerRegistrierenDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MehrspielerLobbyDienstTest {
    @Test
    fun spielstandEntstehtErstNachBereitemLobbyStart() = runBlocking {
        val ablage = ArbeitsspeicherSpielAblage()
        val dienst = MehrspielerLobbyDienst(ablage)
        val erstellt = dienst.erstellen(LobbyErstellenAnfrageDto(konfiguration = konfiguration()))
        val sitzungen = listOf(
            dienst.spielerRegistrieren(
                erstellt.lobbyId,
                LobbySpielerRegistrierenDto(name = "Anna", passwort = "anna-pass", farbe = "ORANGE"),
            ),
            dienst.spielerRegistrieren(
                erstellt.lobbyId,
                LobbySpielerRegistrierenDto(name = "Bernd", passwort = "bernd-pass", farbe = "BLAU"),
            ),
            dienst.spielerRegistrieren(
                erstellt.lobbyId,
                LobbySpielerRegistrierenDto(name = "Claudia", passwort = "claudia-pass", farbe = "VIOLETT"),
            ),
        )

        assertNull("Eine offene Lobby darf noch keinen Spielstand erzeugen.", ablage.spielLaden(1L))

        sitzungen.forEach { sitzung ->
            dienst.spielerAendern(
                erstellt.lobbyId,
                sitzung.sessionToken,
                LobbySpielerAendernDto(bereit = true),
            )
        }
        val gestartet = dienst.starten(erstellt.lobbyId, erstellt.hostToken)
        val gespeichert = ablage.spielLaden(gestartet.spielId.toLong())

        assertNotNull(gespeichert)
        val spieler = requireNotNull(gespeichert).startzustand.spieler
        assertEquals(listOf("Anna", "Bernd", "Claudia"), spieler.map { it.name })
        assertEquals(
            listOf(SpielerFarbe.ORANGE, SpielerFarbe.BLAU, SpielerFarbe.VIOLETT),
            spieler.map { it.farbe },
        )
        assertTrue(spieler.all { it.passwortHash.isNotBlank() })
        assertTrue(spieler.all { it.id.wert != it.name })
        assertEquals(3, spieler.map { it.id }.toSet().size)
    }

    @Test
    fun wiederverbindungMitPasswortBehaeltSpielerIdentitaet() = runBlocking {
        val dienst = MehrspielerLobbyDienst(ArbeitsspeicherSpielAblage())
        val erstellt = dienst.erstellen(LobbyErstellenAnfrageDto(konfiguration = konfiguration()))
        val ersteSitzung = dienst.spielerRegistrieren(
            erstellt.lobbyId,
            LobbySpielerRegistrierenDto(name = "Anna", passwort = "geheim", farbe = "ORANGE"),
        )
        val zweiteSitzung = dienst.spielerRegistrieren(
            erstellt.lobbyId,
            LobbySpielerRegistrierenDto(name = "Anna", passwort = "geheim", farbe = "BLAU"),
        )

        assertEquals(ersteSitzung.spielerId, zweiteSitzung.spielerId)
        assertNotEquals(ersteSitzung.sessionToken, zweiteSitzung.sessionToken)
        assertEquals("ORANGE", zweiteSitzung.lobby.spieler.single().farbe)
    }

    @Test
    fun falschesPasswortKannBestehendenLobbySpielerNichtUebernehmen() = runBlocking {
        val dienst = MehrspielerLobbyDienst(ArbeitsspeicherSpielAblage())
        val erstellt = dienst.erstellen(LobbyErstellenAnfrageDto(konfiguration = konfiguration()))
        dienst.spielerRegistrieren(
            erstellt.lobbyId,
            LobbySpielerRegistrierenDto(name = "Anna", passwort = "richtig", farbe = "ORANGE"),
        )

        assertThrows(UngueltigeSpielerAnmeldung::class.java) {
            runBlocking {
                dienst.spielerRegistrieren(
                    erstellt.lobbyId,
                    LobbySpielerRegistrierenDto(name = "Anna", passwort = "falsch", farbe = "ORANGE"),
                )
            }
        }
    }

    @Test
    fun regelAenderungSetztBereitschaftAllerSpielerZurueck() = runBlocking {
        val dienst = MehrspielerLobbyDienst(ArbeitsspeicherSpielAblage())
        val erstellt = dienst.erstellen(LobbyErstellenAnfrageDto(konfiguration = konfiguration()))
        val anna = dienst.spielerRegistrieren(
            erstellt.lobbyId,
            LobbySpielerRegistrierenDto(name = "Anna", passwort = "geheim", farbe = "ORANGE"),
        )
        dienst.spielerAendern(
            erstellt.lobbyId,
            anna.sessionToken,
            LobbySpielerAendernDto(bereit = true),
        )
        assertTrue(dienst.lesen(erstellt.lobbyId).spieler.single().bereit)

        val geaendert = dienst.konfigurationAendern(
            erstellt.lobbyId,
            erstellt.hostToken,
            konfiguration().copy(leitzinsBasispunkte = 1_200),
        )

        assertFalse(geaendert.spieler.single().bereit)
        assertEquals(1_200, geaendert.konfiguration.leitzinsBasispunkte)
    }

    @Test
    fun spielerfarbenSindInnerhalbEinerLobbyEindeutig() = runBlocking {
        val dienst = MehrspielerLobbyDienst(ArbeitsspeicherSpielAblage())
        val erstellt = dienst.erstellen(LobbyErstellenAnfrageDto(konfiguration = konfiguration()))
        dienst.spielerRegistrieren(
            erstellt.lobbyId,
            LobbySpielerRegistrierenDto(name = "Anna", passwort = "anna-pass", farbe = "ORANGE"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                dienst.spielerRegistrieren(
                    erstellt.lobbyId,
                    LobbySpielerRegistrierenDto(name = "Bernd", passwort = "bernd-pass", farbe = "ORANGE"),
                )
            }
        }
    }

    private fun konfiguration() = LobbyKonfigurationDto(
        name = "Testlobby",
        maximaleSpieler = 5,
        karte = KartenVorlage(id = "testkarte", name = "Testkarte"),
        startBauteile = emptyMap(),
    )
}
