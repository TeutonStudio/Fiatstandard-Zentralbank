package de.teutonstudio.zentralbank.netzwerk

import android.content.Context
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import de.teutonstudio.zentralbank.daten.RoomSpielPersistenz
import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.ErlaubteAktionenDto
import de.teutonstudio.zentralbank.protokoll.SpielAktionDto
import de.teutonstudio.zentralbank.protokoll.SpielBeobachtungAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielSitzungDto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class WlanHostStatus(
    val spielId: Long,
    val adresse: String,
    val port: Int,
    val spieler: List<String>,
)

data class WlanMehrspielerZustand(
    val host: WlanHostStatus? = null,
    val gefundeneSpiele: List<WlanEndpunkt> = emptyList(),
    val sucheAktiv: Boolean = false,
    val verbundenMit: WlanEndpunkt? = null,
    val sitzung: SpielSitzungDto? = null,
    val beobachtung: SpielBeobachtungAntwortDto? = null,
    val erlaubteAktionen: ErlaubteAktionenDto? = null,
    val meldung: String? = null,
    val fehler: String? = null,
)

/** Prozessweite Laufzeit, damit ein Android-Tablet weiter hosten kann, während es zur Spielansicht zurückkehrt. */
object WlanMehrspielerLaufzeit {
    private val _zustand = MutableStateFlow(WlanMehrspielerZustand())
    val zustand: StateFlow<WlanMehrspielerZustand> = _zustand.asStateFlow()

    private var appContext: Context? = null
    private var ablage: SpielAblage? = null
    private var host: WlanSpielHost? = null
    private var hostEntdeckung: WlanSpielEntdeckung? = null
    private var suchEntdeckung: WlanSpielEntdeckung? = null
    private var client: WlanSpielClient? = null

    @Synchronized
    fun initialisieren(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        ablage = RoomSpielPersistenz.oeffnen(app).spielAblage
    }

    fun spielstaende(context: Context): Flow<List<SpielstandUebersicht>> {
        initialisieren(context)
        return requireNotNull(ablage).spielstaendeBeobachten()
    }

    suspend fun hosten(spielId: Long) {
        val app = requireNotNull(appContext) { "WLAN-Laufzeit wurde nicht initialisiert." }
        val speicher = requireNotNull(ablage)
        runCatching {
            val gespeichert = withContext(Dispatchers.IO) {
                speicher.spielLaden(spielId) ?: error("Spielstand $spielId wurde nicht gefunden.")
            }
            stoppeHost()
            val neuerHost = WlanSpielHost(SpielNetzwerkDienst(speicher), spielId)
            withContext(Dispatchers.IO) { neuerHost.starten() }
            host = neuerHost
            val adresse = WlanSpielHost.lokaleIpv4Adresse() ?: "lokale WLAN-Adresse"
            val status = WlanHostStatus(
                spielId = spielId,
                adresse = adresse,
                port = neuerHost.port,
                spieler = gespeichert.aktuellerZustand().spieler.map { it.name },
            )
            _zustand.value = _zustand.value.copy(
                host = status,
                meldung = "Spiel $spielId wird im lokalen WLAN angeboten.",
                fehler = null,
            )
            hostEntdeckung = WlanSpielEntdeckung(app).also { entdeckung ->
                entdeckung.hostVeroeffentlichen(
                    spielId = spielId,
                    port = neuerHost.port,
                    beiFehler = ::meldeFehler,
                )
            }
        }.onFailure { meldeFehler(it.message ?: "WLAN-Host konnte nicht gestartet werden.") }
    }

    fun stoppeHost() {
        hostEntdeckung?.close()
        hostEntdeckung = null
        host?.close()
        host = null
        _zustand.value = _zustand.value.copy(host = null, meldung = "WLAN-Host beendet.")
    }

    fun sucheStarten() {
        val app = requireNotNull(appContext) { "WLAN-Laufzeit wurde nicht initialisiert." }
        suchEntdeckung?.close()
        _zustand.value = _zustand.value.copy(
            gefundeneSpiele = emptyList(),
            sucheAktiv = true,
            fehler = null,
        )
        suchEntdeckung = WlanSpielEntdeckung(app).also { entdeckung ->
            entdeckung.spieleSuchen(
                beiFund = { fund ->
                    val bisher = _zustand.value.gefundeneSpiele
                        .filterNot { it.host == fund.host && it.port == fund.port && it.spielId == fund.spielId }
                    _zustand.value = _zustand.value.copy(
                        gefundeneSpiele = (bisher + fund).sortedBy { it.name },
                    )
                },
                beiEntfernt = { name ->
                    _zustand.value = _zustand.value.copy(
                        gefundeneSpiele = _zustand.value.gefundeneSpiele.filterNot { it.name == name },
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

    suspend fun beitreten(endpunkt: WlanEndpunkt, spielerName: String, passwort: String = "") {
        runCatching {
            require(spielerName.isNotBlank()) { "Bitte den eigenen Spielernamen eingeben." }
            val neuerClient = WlanSpielClient(endpunkt)
            val neueSitzung = neuerClient.beitreten(spielerName.trim(), passwort)
            client = neuerClient
            _zustand.value = _zustand.value.copy(
                verbundenMit = endpunkt,
                sitzung = neueSitzung,
                meldung = "Als ${neueSitzung.spielerId} beigetreten.",
                fehler = null,
            )
            aktualisieren()
        }.onFailure { meldeFehler(it.message ?: "Beitritt zum WLAN-Spiel fehlgeschlagen.") }
    }

    suspend fun manuellBeitreten(
        host: String,
        port: Int,
        spielId: Long,
        spielerName: String,
        passwort: String = "",
    ) = beitreten(
        WlanEndpunkt(host = host.trim(), port = port, spielId = spielId, name = "Manuell $host:$port"),
        spielerName,
        passwort,
    )

    suspend fun aktualisieren() {
        val aktuellerClient = client ?: return
        val sitzung = _zustand.value.sitzung ?: return
        runCatching {
            val (beobachtung, aktionen) = coroutineScope {
                val beobachtung = async { aktuellerClient.beobachten(sitzung.sessionToken) }
                val aktionen = async { aktuellerClient.erlaubteAktionen(sitzung.sessionToken) }
                beobachtung.await() to aktionen.await()
            }
            _zustand.value = _zustand.value.copy(
                beobachtung = beobachtung,
                erlaubteAktionen = aktionen,
                fehler = null,
            )
        }.onFailure { meldeFehler(it.message ?: "WLAN-Spiel konnte nicht aktualisiert werden.") }
    }

    suspend fun aktionAusfuehren(aktion: SpielAktionDto) {
        val aktuellerClient = client ?: return
        val sitzung = _zustand.value.sitzung ?: return
        val revision = _zustand.value.erlaubteAktionen?.revision
            ?: _zustand.value.beobachtung?.revision
            ?: sitzung.revision
        runCatching {
            aktuellerClient.aktionAusfuehren(
                sitzung.sessionToken,
                AktionAusfuehrenAnfrageDto(
                    aktion = aktion,
                    commandId = UUID.randomUUID().toString(),
                    expectedRevision = revision,
                ),
            )
            aktualisieren()
        }.onFailure { ursache ->
            meldeFehler(ursache.message ?: "WLAN-Aktion wurde abgelehnt.")
            aktualisieren()
        }
    }

    fun fehlerVerwerfen() {
        _zustand.value = _zustand.value.copy(fehler = null)
    }

    private fun meldeFehler(meldung: String) {
        _zustand.value = _zustand.value.copy(fehler = meldung, meldung = null)
    }
}
