package de.teutonstudio.zentralbank.datenbank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import de.teutonstudio.zentralbank.daten.RaumSpielAblage
import de.teutonstudio.zentralbank.daten.zuordnung.zuLegacySpiel
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoff
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittZustand
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZugAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.GespeichertesSpiel
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.SpielAblage
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.SpielstandUebersicht
import de.teutonstudio.zentralbank.schnittstelle.domain.SpielUebersichtZustand
import de.teutonstudio.zentralbank.schnittstelle.domain.zuSpielUebersichtZustand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong



class GameViewModel(application: Application): AndroidViewModel(application) {
    class GameViewModelFactory(private val application: Application): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                return GameViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private lateinit var legacySpeicher: ZentralbankSpeicher
    private lateinit var spielAblage: SpielAblage
    private val datenbankBereit = CompletableDeferred<Unit>()
    private val ablageSperre = Mutex()
    private val naechsteAblageAenderung = AtomicLong()
    private val letzteAblageAenderung = mutableMapOf<Long, Long>()

    private val _spielDatenListe = MutableStateFlow<Map<SpielDaten,List<SpeicherDaten>>>(emptyMap())
    private val _spielstaende = MutableStateFlow<List<SpielstandUebersicht>>(emptyList())
    private val _spielZustand = MutableStateFlow<SpielZustand?>(null)
    private val _spielUebersicht = MutableStateFlow<SpielUebersichtZustand?>(null)
    private val _spielFehler = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var spielAblauf: SpielAblauf? = null
    private var ausLegacyDatenImportiert = false

    val spielstaende: StateFlow<List<SpielstandUebersicht>> = _spielstaende.asStateFlow()
    val spielZustand: StateFlow<SpielZustand?> = _spielZustand.asStateFlow()
    val spielUebersicht: StateFlow<SpielUebersichtZustand?> = _spielUebersicht.asStateFlow()
    val spielFehler: SharedFlow<String> = _spielFehler.asSharedFlow()

    lateinit var aktuelleDaten: Pair<SpielDaten,List<SpeicherDaten>>
    lateinit var aktuellesSpiel: Spiel
    val aktuellesSpielOderNull: Spiel?
        get() = if (::aktuellesSpiel.isInitialized) aktuellesSpiel else null

    private fun setzeAktuellesSpiel(
        spiel: Spiel,
        daten: Pair<SpielDaten, List<SpeicherDaten>>,
        gespeichertesSpiel: GespeichertesSpiel? = null,
    ) {
        aktuellesSpiel = spiel
        aktuelleDaten = daten
        val ablauf = gespeichertesSpiel?.let { gespeichert ->
            SpielAblauf(gespeichert.startzustand, gespeichert.ereignisse)
        } ?: SpielAblauf(spiel.zuSpielZustand())
        ausLegacyDatenImportiert = gespeichertesSpiel?.ausLegacyDatenImportiert ?: false
        spielAblauf = ablauf
        aktualisiereSpielZustand(ablauf.zustand)
    }

    fun ereignisAnwenden(ereignis: SpielEreignis) {
        val ablauf = spielAblauf
        if (ablauf == null) {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return
        }

        val vorher = ablauf.zustand
        ablauf.ereignisAnwenden(ereignis)
            .onSuccess { zustand -> uebernehmeEreignisErgebnis(vorher, zustand) }
            .onFailure { throwable ->
                _spielFehler.tryEmit(throwable.message ?: "Spielereignis wurde abgelehnt.")
            }
    }

    fun ereignisRueckgaengig() {
        val ablauf = spielAblauf
        if (ablauf == null) {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return
        }
        runCatching { ablauf.rueckgaengig() }
            .onSuccess { zustand ->
                aktualisiereSpielZustand(zustand)
                speichereAktuellenFachSpielstand()
            }
            .onFailure { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Es gibt nichts zum Rückgängigmachen.")
            }
    }

    fun ereignisWiederholen() {
        val ablauf = spielAblauf
        if (ablauf == null) {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return
        }
        ablauf.wiederholen()
            .onSuccess { zustand ->
                aktualisiereSpielZustand(zustand)
                speichereAktuellenFachSpielstand()
            }
            .onFailure { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Es gibt nichts zum Wiederholen.")
            }
    }

    fun naechsterZugabschnitt() {
        val zustand = spielAblauf?.zustand
        val zug = zustand?.zugStatus
        if (zustand == null || zug == null) {
            _spielFehler.tryEmit("Kein Zug aktiv.")
            return
        }

        when (zug.phase) {
            Phase.Einnahmen,
            Phase.Ausgaben -> {
                val kartenEinnahme = if (zug.phase == Phase.Einnahmen) {
                    zustand.karte
                        ?.let { karte -> KartenAuswertung.rohstoffErtrag(karte, zug.spieler) }
                        ?.takeIf { mengen -> mengen.isNotEmpty() }
                        ?.let { mengen -> SpielEreignis.RohstoffEinnahme(zug.spieler, mengen) }
                } else {
                    null
                }
                val schrittEreignisse = ZugAuswertung.schritte(zustand)
                    .filter { schritt ->
                        schritt.typ.pflicht && schritt.zustand == SchrittZustand.VERFUEGBAR
                    }
                    .map { schritt -> SpielEreignis.SchrittAbgeschlossen(schritt.typ) }
                wendeEreignisseAn(
                    listOfNotNull(kartenEinnahme) + schrittEreignisse +
                        SpielEreignis.PhaseAbgeschlossen(zug.phase),
                )
            }
            Phase.Aktionen -> beendeZug()
        }
    }

    fun beendeZug() {
        wendeEreignisseAn(listOf(SpielEreignis.ZugBeendet))
    }

    private fun wendeEreignisseAn(ereignisse: List<SpielEreignis>) {
        val ablauf = spielAblauf
        if (ablauf == null) {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return
        }

        val vorher = ablauf.zustand
        for (ereignis in ereignisse) {
            val ergebnis = ablauf.ereignisAnwenden(ereignis)
            if (ergebnis.isFailure) {
                aktualisiereSpielZustand(ablauf.zustand)
                val fehler = ergebnis.exceptionOrNull()
                _spielFehler.tryEmit(fehler?.message ?: "Zug konnte nicht fortgesetzt werden.")
                return
            }
        }
        uebernehmeEreignisErgebnis(vorher, ablauf.zustand)
    }

    private fun uebernehmeEreignisErgebnis(vorher: SpielZustand, nachher: SpielZustand) {
        if (nachher.rundenzähler > vorher.rundenzähler) {
            beginneNaechsteRunde(nachher)
        } else {
            aktualisiereSpielZustand(nachher)
        }
        speichereAktuellenFachSpielstand()
    }

    private fun beginneNaechsteRunde(nachZugende: SpielZustand) {
        try {
            aktuellesSpiel.beginneNaechsteRunde()
        } catch (throwable: Throwable) {
            _spielFehler.tryEmit(throwable.message ?: "Neue Runde konnte nicht begonnen werden.")
            return
        }

        val spielDaten = aktuelleDaten.first
        val rundenIndex = aktuellesSpiel.aktuelleRunde - 1
        val rundenDaten = RundeDaten(
            index = rundenIndex,
            zinsatz = aktuellesSpiel.aktuellerLeitzinssatz,
        ).copy(spielID = spielDaten.spielID)
        val neueDatenListe = aktuelleDaten.second + rundenDaten
        aktuelleDaten = spielDaten to neueDatenListe

        val aktuelleWirtschaftsdaten = aktuellesSpiel.zuSpielZustand()
        val synchronisiert = nachZugende.copy(
            marktpreise = aktuelleWirtschaftsdaten.marktpreise,
            leitzins = aktuelleWirtschaftsdaten.leitzins,
            rundenzähler = aktuelleWirtschaftsdaten.rundenzähler,
        )
        spielAblauf = SpielAblauf(synchronisiert)
        ausLegacyDatenImportiert = true
        aktualisiereSpielZustand(synchronisiert)

        if (spielDaten.spielID == (-1).toLong()) return

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                legacySpeicher.insertRunde(rundenDaten)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Neue Runde konnte nicht gespeichert werden."
                )
            }
        }
    }

    fun aktualisiereWarenkorb(neuerWarenkorb: Map<Rohstoffe, Int>) {
        if (neuerWarenkorb.values.any { menge -> menge < 0 }) {
            _spielFehler.tryEmit("Warenkorbmengen dürfen nicht negativ sein.")
            return
        }

        val warenkorb = neuerWarenkorb.filterValues { menge -> menge > 0 }.toMap()
        val ablauf = spielAblauf
        if (ablauf == null) {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return
        }

        val ergebnis = ablauf.ereignisAnwenden(
            SpielEreignis.WarenkorbGeaendert(
                warenkorb = warenkorb.mapKeys { (rohstoff, _) -> rohstoff.zuRohstoff() }
            )
        )
        if (ergebnis.isFailure) {
            _spielFehler.tryEmit(
                ergebnis.exceptionOrNull()?.message ?: "Warenkorb konnte nicht geändert werden."
            )
            return
        }

        aktuellesSpiel.aktualisiereWarenkorb(warenkorb)

        val bisherigeDaten = aktuelleDaten
        val neueSpielDaten = bisherigeDaten.first.copy(
            warenkorb = warenkorb.zuSpeicherWarenkorb()
        )
        aktuelleDaten = neueSpielDaten to bisherigeDaten.second

        aktualisiereSpielZustand(ergebnis.getOrThrow())
        speichereAktuellenFachSpielstand()

        if (neueSpielDaten.spielID == (-1).toLong()) return

        _spielDatenListe.update { spiele ->
            (spiele - bisherigeDaten.first) + (neueSpielDaten to bisherigeDaten.second)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                legacySpeicher.updateSpiel(neueSpielDaten)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Warenkorb konnte nicht gespeichert werden."
                )
            }
        }
    }

    fun erfasseRohstoffhandel(handel: RohstoffHandel): Boolean =
        erfasseHandel(
            handel = handel,
            speicherdatum = {
                HandelsDaten(
                    aktuelleRundenDaten(),
                    handel,
                ).copy(spielID = aktuelleDaten.first.spielID)
            },
            speichern = legacySpeicher::insertHandel,
            fehlermeldung = "Handel konnte nicht gespeichert werden.",
        )

    fun emittiereAnleihe(handel: Anleihenhandel): Boolean =
        erfasseHandel(
            handel = handel,
            speicherdatum = {
                AnleiheDaten(
                    aktuelleRundenDaten(),
                    mapOf((aktuellesSpiel.aktuelleRunde - 1) to handel),
                ).copy(spielID = aktuelleDaten.first.spielID)
            },
            speichern = legacySpeicher::insertAnleihe,
            fehlermeldung = "Anleihe konnte nicht gespeichert werden.",
        )

    fun erfasseAnleihenhandel(handel: Anleihenhandel): Boolean {
        val bestehendeAnleihe = aktuellesSpiel.anleihen
            .firstOrNull { anzeige -> anzeige.anleihe === handel.anleihe }
            ?: return emittiereAnleihe(handel)
        val bisherigesDatum = aktuelleDaten.second
            .filterIsInstance<AnleiheDaten>()
            .firstOrNull { daten -> daten.passtZu(bestehendeAnleihe) }

        val neueAnzeige = try {
            aktuellesSpiel.fuegeHandelZurAktuellenRundeHinzu(handel)
            aktuellesSpiel.anleihen.first { anzeige -> anzeige.anleihe === handel.anleihe }
        } catch (throwable: Throwable) {
            _spielFehler.tryEmit(
                throwable.message ?: "Anleihehandel konnte nicht gespeichert werden."
            )
            return false
        }

        val aktualisiertesDatum = (bisherigesDatum ?: AnleiheDaten(
            aktuelleRundenDaten().copy(index = bestehendeAnleihe.emittiert),
            bestehendeAnleihe.handelsverlauf,
        ).copy(spielID = aktuelleDaten.first.spielID)).copy(
            handel = neueAnzeige.speichereHandelsverlauf(),
        )
        val spielDaten = aktuelleDaten.first
        val neueDatenListe = if (bisherigesDatum == null) {
            aktuelleDaten.second + aktualisiertesDatum
        } else {
            aktuelleDaten.second.map { daten ->
                if (daten === bisherigesDatum) aktualisiertesDatum else daten
            }
        }
        aktuelleDaten = spielDaten to neueDatenListe
        synchronisiereSpielZustandNachLegacyAenderung()

        if (spielDaten.spielID == (-1).toLong()) return true

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                legacySpeicher.updateAnleiheHandel(aktualisiertesDatum)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Anleihehandel konnte nicht gespeichert werden."
                )
            }
        }
        return true
    }

    private fun aktuelleRundenDaten(): RundeDaten {
        val runde = (aktuellesSpiel.aktuelleRunde - 1).coerceAtLeast(0)
        return RundeDaten(
            index = runde,
            zinsatz = aktuellesSpiel.leitzinssatz(runde) ?: 0f,
        )
    }

    private fun <T : SpeicherDaten> erfasseHandel(
        handel: Handel,
        speicherdatum: () -> T,
        speichern: suspend (T) -> Long,
        fehlermeldung: String,
    ): Boolean {
        val datum = try {
            aktuellesSpiel.fuegeHandelZurAktuellenRundeHinzu(handel)
            speicherdatum()
        } catch (throwable: Throwable) {
            _spielFehler.tryEmit(throwable.message ?: fehlermeldung)
            return false
        }

        val spielDaten = aktuelleDaten.first
        val neueDatenListe = aktuelleDaten.second + datum
        aktuelleDaten = spielDaten to neueDatenListe

        synchronisiereSpielZustandNachLegacyAenderung()

        if (spielDaten.spielID == (-1).toLong()) return true

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                speichern(datum)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(throwable.message ?: fehlermeldung)
            }
        }
        return true
    }

    private fun synchronisiereSpielZustandNachLegacyAenderung() {
        val bisherigerSpielZustand = spielAblauf?.zustand
        val abgebildeterSpielZustand = aktuellesSpiel.zuSpielZustand()
        val spielZustand = if (bisherigerSpielZustand == null) {
            abgebildeterSpielZustand
        } else {
            abgebildeterSpielZustand.copy(
                karte = bisherigerSpielZustand.karte,
                spielabschnitt = bisherigerSpielZustand.spielabschnitt,
                rundenzähler = bisherigerSpielZustand.rundenzähler,
                aktiverSpieler = bisherigerSpielZustand.aktiverSpieler,
                zugStatus = bisherigerSpielZustand.zugStatus,
                schuldenstriche = bisherigerSpielZustand.schuldenstriche,
                ueberschuldungen = bisherigerSpielZustand.ueberschuldungen,
            )
        }
        spielAblauf = SpielAblauf(spielZustand)
        ausLegacyDatenImportiert = true
        aktualisiereSpielZustand(spielZustand)
        speichereAktuellenFachSpielstand()
    }

    private fun aktualisiereSpielZustand(zustand: SpielZustand) {
        aktuellesSpielOderNull?.aktualisiereAktivenSpieler(zustand.zugStatus?.spieler?.wert)
        _spielZustand.value = zustand
        _spielUebersicht.value = zustand.zuSpielUebersichtZustand()
    }

    private fun speichereAktuellenFachSpielstand() {
        val ablauf = spielAblauf ?: return
        val spielId = aktuelleDaten.first.spielID
        if (spielId < 0) return
        val zuSpeicherndesSpiel = GespeichertesSpiel(
            id = spielId,
            startzustand = ablauf.startzustand,
            ereignisse = ablauf.ereignisVerlauf.angewandteEreignisse,
            ausLegacyDatenImportiert = ausLegacyDatenImportiert,
        )
        val aenderungsNummer = naechsteAblageAenderung.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                ablageAendern(spielId, aenderungsNummer) {
                    spielSpeichern(zuSpeicherndesSpiel)
                }
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Spielstand konnte nicht gespeichert werden.",
                )
            }
        }
    }

    private suspend fun ablageAendern(
        spielId: Long,
        aenderungsNummer: Long,
        aenderung: suspend SpielAblage.() -> Unit,
    ) {
        ablageSperre.withLock {
            val letzteNummer = letzteAblageAenderung[spielId] ?: Long.MIN_VALUE
            if (aenderungsNummer < letzteNummer) return@withLock
            spielAblage.aenderung()
            letzteAblageAenderung[spielId] = aenderungsNummer
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val datenbank = AppDatabase.erhalteDatenbank(application.applicationContext).first()
                legacySpeicher = ZentralbankSpeicher(datenbank)
                spielAblage = RaumSpielAblage(datenbank)
                datenbankBereit.complete(Unit)

                launch {
                    legacySpeicher.observeAlleNachSpiel().collect { spiele ->
                        _spielDatenListe.value = spiele
                    }
                }
                spielAblage.spielstaendeBeobachten().collect { spielstaende ->
                    _spielstaende.value = spielstaende + SpielstandUebersicht(
                        id = -1,
                        spielerNamen = TestSpiel.spielerStringListe,
                        runde = TestSpiel.aktuelleRunde - 1,
                        ausLegacyDatenImportiert = true,
                    )
                }
            } catch (throwable: Throwable) {
                if (!datenbankBereit.isCompleted) { datenbankBereit.completeExceptionally(throwable) }
                _spielFehler.tryEmit(
                    throwable.message ?: "Spielstände konnten nicht geladen werden.",
                )
            }
        }
    }

    fun erstelleSpiel(spiel: Spiel, nachErstellen: () -> Unit) {
        viewModelScope.launch {
            try {
                val daten = spiel.zuSpeicherDaten()
                datenbankBereit.await()
                val gameID = withContext(Dispatchers.IO) {
                    legacySpeicher.insertSpielSatz(daten)
                }
                if (gameID <= 0) {
                    _spielFehler.emit("Spielstand konnte nicht angelegt werden.")
                    return@launch
                }

                val gespeicherteDaten = daten.first.copy(spielID = gameID) to daten.second
                setzeAktuellesSpiel(spiel, gespeicherteDaten)
                speichereAktuellenFachSpielstand()
                nachErstellen()
            } catch (throwable: Throwable) {
                _spielFehler.emit(
                    throwable.message?.let { "Spielstand konnte nicht angelegt werden: $it" }
                        ?: "Spielstand konnte nicht angelegt werden.",
                )
            }
        }
    }

    val vernichteSpiel = { id: Long -> vernichteSpiel(id) }
    private fun vernichteSpiel(id: Long) {
        val aenderungsNummer = naechsteAblageAenderung.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                ablageAendern(id, aenderungsNummer) {
                    spielLoeschen(id)
                }
            } catch (throwable: Throwable) {
                _spielFehler.emit(
                    throwable.message ?: "Spielstand $id konnte nicht gelöscht werden.",
                )
            }
        }
    }

    val ladeSpiel = { id: Long, nachLaden: () -> Unit ->
        ladeSpiel(id, nachLaden)
    }
    private fun ladeSpiel(id: Long, nachLaden: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                ladeSpielDaten(id)
                withContext(Dispatchers.Main) { nachLaden() }
            } catch (throwable: Throwable) {
                _spielFehler.emit(
                    throwable.message?.let { "Spielstand konnte nicht geladen werden: $it" }
                        ?: "Spielstand konnte nicht geladen werden."
                )
            }
        }
    }
    private suspend fun ladeSpielDaten(id: Long) {
        if (id == -1L) {
            val testDaten = TestSpiel.zuSpeicherDaten().let { (spiel, daten) ->
                spiel.copy(spielID = -1) to daten
            }
            setzeAktuellesSpiel(testDaten.second.zuLegacySpiel(testDaten.first), testDaten)
            return
        }
        val gespeichertesSpiel = spielAblage.spielLaden(id)
            ?: error("Spielstand $id wurde nicht gefunden.")
        val (spielDaten, legacyDaten) = _spielDatenListe.value.entries
            .firstOrNull { (spiel, _) -> spiel.spielID == id }
            ?.let { (spiel, daten) -> spiel to daten }
            ?: error(
                "Spielstand $id besitzt keine Legacy-Daten für die noch nicht migrierte Oberfläche.",
            )
        setzeAktuellesSpiel(
            spiel = legacyDaten.zuLegacySpiel(
                daten = spielDaten,
                karte = gespeichertesSpiel.startzustand.karte,
            ),
            daten = spielDaten to legacyDaten,
            gespeichertesSpiel = gespeichertesSpiel,
        )
        if (gespeichertesSpiel.ausLegacyDatenImportiert) {
            spielAblage.spielSpeichern(gespeichertesSpiel)
        }
    }

    fun kriegErklaeren(aggressor: String, verteidiger: String) {
        meldeAusstehendeKonfliktMigration("Kriegserklärung $aggressor gegen $verteidiger")
    }

    fun militaerergebnisErfassen(spieler: String, staerke: Int) {
        meldeAusstehendeKonfliktMigration("Militärergebnis $spieler mit Stärke $staerke")
    }

    fun friedenSchliessen(spielerA: String, spielerB: String) {
        meldeAusstehendeKonfliktMigration("Friedensschluss $spielerA mit $spielerB")
    }

    private fun meldeAusstehendeKonfliktMigration(aktion: String) {
        _spielFehler.tryEmit("$aktion ist bis zur Konfliktbereichsmigration nicht verfügbar.")
    }
}
