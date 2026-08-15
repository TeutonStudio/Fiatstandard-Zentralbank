package de.teutonstudio.zentralbank.netzwerk

import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielDienst
import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Geldpolitik
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerFarbe
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import de.teutonstudio.zentralbank.fachlogik.modell.hasheSpielerPasswort
import de.teutonstudio.zentralbank.protokoll.API_VERSION
import de.teutonstudio.zentralbank.protokoll.LobbyDto
import de.teutonstudio.zentralbank.protokoll.LobbyErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.LobbyErstelltDto
import de.teutonstudio.zentralbank.protokoll.LobbyGestartetDto
import de.teutonstudio.zentralbank.protokoll.LobbyKonfigurationDto
import de.teutonstudio.zentralbank.protokoll.LobbySitzungDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerAendernDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerRegistrierenDto
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Transportneutrale Lobby vor dem eigentlichen Spielstand.
 *
 * Der Host bestimmt die Spielkonfiguration. Jeder Teilnehmer registriert dagegen seine eigene
 * dauerhafte Spieleridentität mit Name, Farbe und Passwort. Erst [starten] erzeugt atomar einen
 * persistierten [SpielZustand].
 */
class MehrspielerLobbyDienst(
    private val ablage: SpielAblage,
    ersteSpielId: Long = 1L,
) {
    private enum class Status { OFFEN, GESTARTET }

    private data class LobbySpieler(
        val id: SpielerId,
        var name: String,
        var farbe: SpielerFarbe,
        val passwortHash: String,
        var bereit: Boolean = false,
        var verbunden: Boolean = true,
    )

    private data class Lobby(
        val id: String,
        val hostToken: String,
        var konfiguration: LobbyKonfigurationDto,
        val spieler: MutableList<LobbySpieler> = mutableListOf(),
        var revision: Long = 0L,
        var status: Status = Status.OFFEN,
        var spielId: Long? = null,
        val sperre: Mutex = Mutex(),
    )

    private data class LobbySitzung(val lobbyId: String, val spielerId: SpielerId)

    private val spielDienst = SpielDienst(ablage)
    private val lobbys = ConcurrentHashMap<String, Lobby>()
    private val sitzungen = ConcurrentHashMap<String, LobbySitzung>()
    private val naechsteSpielId = AtomicLong(ersteSpielId)
    private val zufall = SecureRandom()

    fun erstellen(anfrage: LobbyErstellenAnfrageDto): LobbyErstelltDto {
        pruefeVersion(anfrage.version)
        pruefeKonfiguration(anfrage.konfiguration)
        val lobby = Lobby(
            id = UUID.randomUUID().toString(),
            hostToken = neuesToken(),
            konfiguration = anfrage.konfiguration.normalisiert(),
        )
        lobbys[lobby.id] = lobby
        return LobbyErstelltDto(
            lobbyId = lobby.id,
            hostToken = lobby.hostToken,
            lobby = lobby.zuDto(),
        )
    }

    fun lesen(lobbyId: String): LobbyDto = lobby(lobbyId).zuDto()

    suspend fun spielerRegistrieren(
        lobbyId: String,
        anfrage: LobbySpielerRegistrierenDto,
    ): LobbySitzungDto {
        pruefeVersion(anfrage.version)
        require(anfrage.passwort.isNotBlank()) {
            "Für Wiederverbindung und spätere Fortsetzung ist ein Spielerpasswort erforderlich."
        }
        val name = anfrage.name.trim()
        require(name.isNotBlank()) { "Spielername darf nicht leer sein." }
        val farbe = runCatching { SpielerFarbe.valueOf(anfrage.farbe.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unbekannte Spielerfarbe '${anfrage.farbe}'.") }
        val lobby = lobby(lobbyId)
        return lobby.sperre.withLock {
            check(lobby.status == Status.OFFEN) { "Die Lobby wurde bereits gestartet." }

            val vorhandenerSpieler = lobby.spieler.firstOrNull {
                it.name.equals(name, ignoreCase = true)
            }
            if (vorhandenerSpieler != null) {
                val eingabeHash = hasheSpielerPasswort(anfrage.passwort)
                if (!MessageDigestHelper.gleich(vorhandenerSpieler.passwortHash, eingabeHash)) {
                    throw UngueltigeSpielerAnmeldung()
                }
                vorhandenerSpieler.verbunden = true
                lobby.revision++
                return@withLock neueSitzung(lobby, vorhandenerSpieler)
            }

            require(lobby.spieler.size < lobby.konfiguration.maximaleSpieler) { "Die Lobby ist voll." }
            require(lobby.spieler.none { it.farbe == farbe }) {
                "Die Farbe ${farbe.name} ist bereits vergeben."
            }
            val spieler = LobbySpieler(
                id = SpielerId(UUID.randomUUID().toString()),
                name = name,
                farbe = farbe,
                passwortHash = hasheSpielerPasswort(anfrage.passwort),
            )
            lobby.spieler += spieler
            lobby.revision++
            neueSitzung(lobby, spieler)
        }
    }

    suspend fun spielerAendern(
        lobbyId: String,
        sessionToken: String,
        anfrage: LobbySpielerAendernDto,
    ): LobbyDto {
        pruefeVersion(anfrage.version)
        val lobby = lobby(lobbyId)
        val sitzung = pruefeSitzung(lobbyId, sessionToken)
        return lobby.sperre.withLock {
            check(lobby.status == Status.OFFEN) { "Die Lobby wurde bereits gestartet." }
            val spieler = lobby.spieler.singleOrNull { it.id == sitzung.spielerId }
                ?: throw UngueltigeSpielSitzung()
            anfrage.name?.trim()?.let { neuerName ->
                require(neuerName.isNotBlank()) { "Spielername darf nicht leer sein." }
                require(lobby.spieler.none {
                    it.id != spieler.id && it.name.equals(neuerName, ignoreCase = true)
                }) { "Der Spielername '$neuerName' ist bereits vergeben." }
                spieler.name = neuerName
                spieler.bereit = false
            }
            anfrage.farbe?.let { farbText ->
                val neueFarbe = runCatching { SpielerFarbe.valueOf(farbText.trim().uppercase()) }
                    .getOrElse { throw IllegalArgumentException("Unbekannte Spielerfarbe '$farbText'.") }
                require(lobby.spieler.none { it.id != spieler.id && it.farbe == neueFarbe }) {
                    "Die Farbe ${neueFarbe.name} ist bereits vergeben."
                }
                spieler.farbe = neueFarbe
                spieler.bereit = false
            }
            anfrage.bereit?.let { spieler.bereit = it }
            lobby.revision++
            lobby.zuDto()
        }
    }

    suspend fun konfigurationAendern(
        lobbyId: String,
        hostToken: String,
        konfiguration: LobbyKonfigurationDto,
    ): LobbyDto {
        pruefeHost(lobbyId, hostToken)
        pruefeKonfiguration(konfiguration)
        val lobby = lobby(lobbyId)
        return lobby.sperre.withLock {
            check(lobby.status == Status.OFFEN) { "Die Lobby wurde bereits gestartet." }
            require(konfiguration.maximaleSpieler >= lobby.spieler.size) {
                "Die maximale Spielerzahl liegt unter der Zahl bereits beigetretener Spieler."
            }
            lobby.konfiguration = konfiguration.normalisiert()
            lobby.spieler.forEach { it.bereit = false }
            lobby.revision++
            lobby.zuDto()
        }
    }

    suspend fun starten(lobbyId: String, hostToken: String): LobbyGestartetDto {
        pruefeHost(lobbyId, hostToken)
        val lobby = lobby(lobbyId)
        return lobby.sperre.withLock {
            check(lobby.status == Status.OFFEN) { "Die Lobby wurde bereits gestartet." }
            require(lobby.spieler.size >= MINDEST_SPIELER) {
                "Mindestens $MINDEST_SPIELER Spieler müssen der Lobby beigetreten sein."
            }
            require(lobby.spieler.all { it.bereit }) { "Alle Spieler müssen bereit sein." }

            val spielId = freieSpielId()
            val konfiguration = lobby.konfiguration
            val startBauteile = konfiguration.startBauteile.zuBauteile()
            val startzustand = SpielZustand(
                spieler = lobby.spieler.map { lobbySpieler ->
                    Spieler(
                        id = lobbySpieler.id,
                        name = lobbySpieler.name,
                        passwortHash = lobbySpieler.passwortHash,
                        farbe = lobbySpieler.farbe,
                        rohstoffe = konfiguration.startRohstoffe.zuRohstoffe(),
                        geldkonto = Geld.cent(konfiguration.startGuthabenCent),
                        bauteile = startBauteile,
                        spielstil = SpielerStil.VORSICHTIG,
                    )
                },
                karte = konfiguration.karte.alsSpielkarte("spiel-$spielId"),
                spielabschnitt = if (startBauteile.isEmpty()) Spielabschnitt.REGULAER else Spielabschnitt.RUNDE_NULL,
                rundeNullRestbestand = if (startBauteile.isEmpty()) null else lobby.spieler.associate {
                    it.id to startBauteile
                },
                warenkorb = konfiguration.warenkorb.zuRohstoffe(),
                leitzins = Basispunkte(konfiguration.leitzinsBasispunkte),
                geldpolitik = Geldpolitik(
                    inflationsziel = Basispunkte(konfiguration.inflationszielBasispunkte),
                    normaleAbweichung = Basispunkte(konfiguration.normaleAbweichungBasispunkte),
                    starkeAbweichung = Basispunkte(konfiguration.starkeAbweichungBasispunkte),
                    leitzinsSchritt = Basispunkte(konfiguration.leitzinsSchrittBasispunkte),
                ),
            )
            spielDienst.spielErstellen(spielId, startzustand, konfiguration.seed)
            lobby.status = Status.GESTARTET
            lobby.spielId = spielId
            lobby.revision++
            LobbyGestartetDto(
                lobbyId = lobby.id,
                spielId = spielId.toString(),
                lobby = lobby.zuDto(),
            )
        }
    }

    private fun neueSitzung(lobby: Lobby, spieler: LobbySpieler): LobbySitzungDto {
        val token = neuesToken()
        sitzungen[token] = LobbySitzung(lobby.id, spieler.id)
        return LobbySitzungDto(
            lobbyId = lobby.id,
            spielerId = spieler.id.wert,
            sessionToken = token,
            lobby = lobby.zuDto(),
        )
    }

    private fun pruefeKonfiguration(konfiguration: LobbyKonfigurationDto) {
        require(konfiguration.name.trim().isNotBlank()) { "Lobbyname darf nicht leer sein." }
        require(konfiguration.maximaleSpieler in MINDEST_SPIELER..MAXIMALE_SPIELER) {
            "Spielerzahl muss zwischen $MINDEST_SPIELER und $MAXIMALE_SPIELER liegen."
        }
        require(konfiguration.startGuthabenCent >= 0) { "Startguthaben darf nicht negativ sein." }
        require(konfiguration.normaleAbweichungBasispunkte >= 0) {
            "Normale Inflationsabweichung darf nicht negativ sein."
        }
        require(konfiguration.starkeAbweichungBasispunkte >= konfiguration.normaleAbweichungBasispunkte) {
            "Starke Inflationsabweichung muss mindestens so groß wie die normale sein."
        }
        require(konfiguration.leitzinsSchrittBasispunkte >= 0) { "Leitzinsschritt darf nicht negativ sein." }
        konfiguration.warenkorb.zuRohstoffe()
        konfiguration.startRohstoffe.zuRohstoffe()
        konfiguration.startBauteile.zuBauteile()
    }

    private fun LobbyKonfigurationDto.normalisiert() = copy(name = name.trim())

    private fun Map<String, Int>.zuRohstoffe(): Map<Rohstoff, Int> = entries.associate { (name, menge) ->
        require(menge >= 0) { "Rohstoffmenge für $name darf nicht negativ sein." }
        Rohstoff.valueOf(name.uppercase()) to menge
    }.filterValues { it > 0 }

    private fun Map<String, Int>.zuBauteile(): Map<BauteilTyp, Int> = entries.associate { (name, menge) ->
        require(menge >= 0) { "Bauteilmenge für $name darf nicht negativ sein." }
        BauteilTyp.valueOf(name.uppercase()) to menge
    }.filterValues { it > 0 }

    private suspend fun freieSpielId(): Long {
        while (true) {
            val kandidat = naechsteSpielId.getAndIncrement()
            if (ablage.spielLaden(kandidat) == null) return kandidat
        }
    }

    private fun lobby(id: String): Lobby = lobbys[id] ?: throw NetzwerkLobbyNichtGefunden(id)

    private fun pruefeHost(lobbyId: String, token: String) {
        val lobby = lobby(lobbyId)
        if (!MessageDigestHelper.gleich(lobby.hostToken, token.trim())) throw UngueltigerLobbyHost()
    }

    private fun pruefeSitzung(lobbyId: String, token: String): LobbySitzung {
        val sitzung = sitzungen[token.trim()] ?: throw UngueltigeSpielSitzung()
        if (sitzung.lobbyId != lobbyId) throw UngueltigeSpielSitzung()
        return sitzung
    }

    private fun Lobby.zuDto(): LobbyDto = LobbyDto(
        lobbyId = id,
        revision = revision,
        status = status.name,
        konfiguration = konfiguration,
        spieler = spieler.map {
            LobbySpielerDto(
                id = it.id.wert,
                name = it.name,
                farbe = it.farbe.name,
                bereit = it.bereit,
                verbunden = it.verbunden,
            )
        },
        spielId = spielId?.toString(),
    )

    private fun neuesToken(): String {
        val bytes = ByteArray(32)
        zufall.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun pruefeVersion(version: Int) {
        require(version == API_VERSION) {
            "API-Version $version wird nicht unterstützt; erwartet wird $API_VERSION."
        }
    }

    private object MessageDigestHelper {
        fun gleich(a: String, b: String): Boolean = java.security.MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8),
        )
    }

    companion object {
        const val MINDEST_SPIELER = 2
        const val MAXIMALE_SPIELER = 7
    }
}

class NetzwerkLobbyNichtGefunden(val lobbyId: String) : NoSuchElementException(
    "Lobby '$lobbyId' wurde nicht gefunden.",
)

class UngueltigerLobbyHost : SecurityException("Ungültiger oder abgelaufener Lobby-Hostschlüssel.")
