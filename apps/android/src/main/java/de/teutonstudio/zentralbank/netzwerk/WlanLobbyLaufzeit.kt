package de.teutonstudio.zentralbank.netzwerk

import android.content.Context
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.daten.RoomSpielPersistenz
import de.teutonstudio.zentralbank.protokoll.LobbyDto
import de.teutonstudio.zentralbank.protokoll.LobbyErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.LobbyKonfigurationDto
import de.teutonstudio.zentralbank.protokoll.LobbySitzungDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerAendernDto
import de.teutonstudio.zentralbank.protokoll.LobbySpielerRegistrierenDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class WlanLobbyHostStatus(
    val lobbyId: String,
    val adresse: String,
    val port: Int,
    val hostToken: String,
)

data class WlanLobbyZustand(
    val host: WlanLobbyHostStatus? = null,
    val gefundeneLobbys: List<WlanLobbyEndpunkt> = emptyList(),
    val sucheAktiv: Boolean = false,
    val verbundenMit: WlanLobbyEndpunkt? = null,
    val sitzung: LobbySitzungDto? = null,
    val lobby: LobbyDto? = null,
    val spielEndpunkt: WlanEndpunkt? = null,
    val meldung: String? = null,
    val fehler: String? = null,
)

/**
 * Prozessweite Android-Laufzeit für die Vor-Spiel-Lobby. Ein Host bleibt damit erreichbar,
 * während Compose neu aufgebaut wird; der eigentliche Spielzustand liegt weiterhin in Room.
 */
object WlanLobbyLaufzeit {
    private val _zustand = MutableStateFlow(WlanLobbyZustand())
    val zustand: StateFlow<WlanLobbyZustand> = _zustand.asStateFlow()

    private var appContext: Context? = null
    private var ablage: SpielAblage? = null
    private var lobbyDienst: MehrspielerLobbyDienst? = null
    private var spielDienst: SpielNetzwerkDienst? = null
    private var host: WlanSpielHost? = null
    private var hostEntdeckung: WlanSpielEntdeckung? = null
    private var suchEntdeckung: WlanSpielEntdeckung? = null
    private var client: WlanLobbyClient? = null
    private var eigenerName: String = ""
    private var eigenesPasswort: String = ""

    @Synchronized
    fun initialisieren(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        ablage = RoomSpielPersistenz.oeffnen(app).spielAblage
        WlanMehrspielerLaufzeit.initialisieren(app)
    }

    suspend fun neueLobbyHosten(
        konfiguration: LobbyKonfigurationDto,
        spielerName: String,
        passwort: String,
        farbe: String,
    ) {
        val app = requireNotNull(appContext) { "WLAN-Lobby wurde nicht initialisiert." }
        val speicher = requireNotNull(ablage)
        runCatching {
            require(spielerName.isNotBlank()) { "Bitte einen Spielernamen angeben." }
            require(passwort.isNotBlank()) { "Für Wiederverbindungen ist ein Passwort erforderlich." }
            stoppeHost()
            val neuerLobbyDienst = MehrspielerLobbyDienst(speicher)
            val neuerSpielDienst = SpielNetzwerkDienst(speicher)
            val erstellt = neuerLobbyDienst.erstellen(LobbyErstellenAnfrageDto(konfiguration = konfiguration))
            val sitzung = neuerLobbyDienst.spielerRegistrieren(
                erstellt.lobbyId,
                LobbySpielerRegistrierenDto(
                    name = spielerName.trim(),
                    passwort = passwort,
                    farbe = farbe,
                ),
            )
            val neuerHost = WlanSpielHost(
                lobbyDienst = neuerLobbyDienst,
                spielDienst = neuerSpielDienst,
                freigegebeneLobbyId = erstellt.lobbyId,
            )
            withContext(Dispatchers.IO) { neuerHost.starten() }
            lobbyDienst = neuerLobbyDienst
            spielDienst = neuerSpielDienst
            host = neuerHost
            eigenerName = spielerName.trim()
            eigenesPasswort = passwort
            val adresse = WlanSpielHost.lokaleIpv4Adresse() ?: "127.0.0.1"
            val status = WlanLobbyHostStatus(
                lobbyId = erstellt.lobbyId,
                adresse = adresse,
                port = neuerHost.port,
                hostToken = erstellt.hostToken,
            )
            val endpunkt = WlanLobbyEndpunkt(
                host = adresse,
                port = neuerHost.port,
                lobbyId = erstellt.lobbyId,
                name = konfiguration.name,
            )
            client = WlanLobbyClient(endpunkt)
            _zustand.value = WlanLobbyZustand(
                host = status,
                verbundenMit = endpunkt,
                sitzung = sitzung,
                lobby = sitzung.lobby,
                meldung = "Lobby '${konfiguration.name}' ist im WLAN geöffnet.",
            )
            hostEntdeckung = WlanSpielEntdeckung(app).also { entdeckung ->
                entdeckung.lobbyVeroeffentlichen(
                    lobbyId = erstellt.lobbyId,
                    port = neuerHost.port,
                    name = konfiguration.name,
                    beiFehler = ::meldeFehler,
                )
            }
        }.onFailure { meldeFehler(it.message ?: "WLAN-Lobby konnte nicht gestartet werden.") }
    }

    suspend fun beitreten(
        endpunkt: WlanLobbyEndpunkt,
        spielerName: String,
        passwort: String,
        farbe: String,
    ) {
        runCatching {
            require(spielerName.isNotBlank()) { "Bitte einen Spielernamen angeben." }
            require(passwort.isNotBlank()) { "Für Wiederverbindungen ist ein Passwort erforderlich." }
            val neuerClient = WlanLobbyClient(endpunkt)
            val sitzung = neuerClient.registrieren(spielerName.trim(), passwort, farbe)
            client = neuerClient
            eigenerName = spielerName.trim()
            eigenesPasswort = passwort
            _zustand.value = _zustand.value.copy(
                verbundenMit = endpunkt,
                sitzung = sitzung,
                lobby = sitzung.lobby,
                spielEndpunkt = null,
                meldung = "Lobby '${sitzung.lobby.konfiguration.name}' beigetreten.",
                fehler = null,
            )
        }.onFailure { meldeFehler(it.message ?: "Beitritt zur WLAN-Lobby fehlgeschlagen.") }
    }

    suspend fun bereitSetzen(bereit: Boolean) {
        val sitzung = _zustand.value.sitzung ?: return
        runCatching {
            val lobby = if (_zustand.value.host != null) {
                requireNotNull(lobbyDienst).spielerAendern(
                    sitzung.lobbyId,
                    sitzung.sessionToken,
                    LobbySpielerAendernDto(bereit = bereit),
                )
            } else {
                requireNotNull(client).spielerAendern(
                    sitzung.sessionToken,
                    LobbySpielerAendernDto(bereit = bereit),
                )
            }
            _zustand.value = _zustand.value.copy(lobby = lobby, fehler = null)
        }.onFailure { meldeFehler(it.message ?: "Bereit-Status konnte nicht geändert werden.") }
    }

    suspend fun konfigurationAendern(konfiguration: LobbyKonfigurationDto) {
        val hostStatus = _zustand.value.host ?: return
        runCatching {
            val lobby = requireNotNull(lobbyDienst).konfigurationAendern(
                hostStatus.lobbyId,
                hostStatus.hostToken,
                konfiguration,
            )
            _zustand.value = _zustand.value.copy(
                lobby = lobby,
                meldung = "Spielregeln aktualisiert; Bereitschaften wurden zurückgesetzt.",
                fehler = null,
            )
        }.onFailure { meldeFehler(it.message ?: "Lobby-Konfiguration konnte nicht geändert werden.") }
    }

    suspend fun spielStarten() {
        val hostStatus = _zustand.value.host ?: return
        runCatching {
            val gestartet = requireNotNull(lobbyDienst).starten(hostStatus.lobbyId, hostStatus.hostToken)
            val spielId = gestartet.spielId.toLong()
            val spielEndpunkt = WlanEndpunkt(
                host = hostStatus.adresse,
                port = hostStatus.port,
                spielId = spielId,
                name = gestartet.lobby.konfiguration.name,
            )
            hostEntdeckung?.hostVeroeffentlichen(
                spielId = spielId,
                port = hostStatus.port,
                name = gestartet.lobby.konfiguration.name,
                beiFehler = ::meldeFehler,
            )
            _zustand.value = _zustand.value.copy(
                lobby = gestartet.lobby,
                spielEndpunkt = spielEndpunkt,
                meldung = "Spiel $spielId wurde erstellt.",
                fehler = null,
            )
            WlanMehrspielerLaufzeit.beitreten(spielEndpunkt, eigenerName, eigenesPasswort)
        }.onFailure { meldeFehler(it.message ?: "Spiel konnte nicht gestartet werden.") }
    }

    suspend fun aktualisieren() {
        val lobbyEndpunkt = _zustand.value.verbundenMit ?: return
        runCatching {
            val lobby = if (_zustand.value.host != null) {
                requireNotNull(lobbyDienst).lesen(lobbyEndpunkt.lobbyId)
            } else {
                requireNotNull(client).lesen()
            }
            _zustand.value = _zustand.value.copy(lobby = lobby, fehler = null)
            if (lobby.spielId != null && _zustand.value.spielEndpunkt == null) {
                val spielEndpunkt = WlanEndpunkt(
                    host = lobbyEndpunkt.host,
                    port = lobbyEndpunkt.port,
                    spielId = lobby.spielId.toLong(),
                    name = lobby.konfiguration.name,
                )
                _zustand.value = _zustand.value.copy(spielEndpunkt = spielEndpunkt)
                WlanMehrspielerLaufzeit.beitreten(spielEndpunkt, eigenerName, eigenesPasswort)
            }
        }.onFailure { meldeFehler(it.message ?: "Lobby konnte nicht aktualisiert werden.") }
    }

    fun sucheStarten() {
        val app = requireNotNull(appContext) { "WLAN-Lobby wurde nicht initialisiert." }
        suchEntdeckung?.close()
        _zustand.value = _zustand.value.copy(
            gefundeneLobbys = emptyList(),
            sucheAktiv = true,
            fehler = null,
        )
        suchEntdeckung = WlanSpielEntdeckung(app).also { entdeckung ->
            entdeckung.lobbysSuchen(
                beiFund = { fund ->
                    val bisher = _zustand.value.gefundeneLobbys.filterNot {
                        it.host == fund.host && it.port == fund.port && it.lobbyId == fund.lobbyId
                    }
                    _zustand.value = _zustand.value.copy(
                        gefundeneLobbys = (bisher + fund).sortedBy(WlanLobbyEndpunkt::name),
                    )
                },
                beiEntfernt = { name ->
                    _zustand.value = _zustand.value.copy(
                        gefundeneLobbys = _zustand.value.gefundeneLobbys.filterNot { it.name == name },
                    )
                },
                beiFehler = ::meldeFehler,
            )
        }
    }

    fun sucheBeenden() {
        suchEntdeckung?.close()
        suchEntdeckung = null
        _zustand.value = _zustand.value.copy(sucheAktiv = false)
    }

    fun stoppeHost() {
        hostEntdeckung?.close()
        hostEntdeckung = null
        host?.close()
        host = null
        lobbyDienst = null
        spielDienst = null
        if (_zustand.value.host != null) {
            _zustand.value = WlanLobbyZustand(meldung = "WLAN-Lobby beendet.")
        }
    }

    fun fehlerVerwerfen() {
        _zustand.value = _zustand.value.copy(fehler = null)
    }

    private fun meldeFehler(meldung: String) {
        _zustand.value = _zustand.value.copy(fehler = meldung, meldung = null)
    }
}
