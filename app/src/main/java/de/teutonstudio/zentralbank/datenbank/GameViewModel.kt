package de.teutonstudio.zentralbank.datenbank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.engine.GameEngine
import de.teutonstudio.zentralbank.domain.events.GameEvent
import de.teutonstudio.zentralbank.domain.zug.Phase
import de.teutonstudio.zentralbank.domain.zug.SchrittZustand
import de.teutonstudio.zentralbank.domain.zug.ZugAutomat
import de.teutonstudio.zentralbank.schnittstelle.domain.GameUiState
import de.teutonstudio.zentralbank.schnittstelle.domain.zuGameUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



private const val AUSLAND = "-ausland-"

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

    private lateinit var DAO: ZentralbankSpeicher
    private val datenbankBereit = CompletableDeferred<Unit>()

    private val _spielDatenListe = MutableStateFlow<Map<SpielDaten,List<SpeicherDaten>>>(emptyMap())
    private val _spielSpeicher = MutableStateFlow<Map<SpielDaten,Pair<Int,List<String>>>>(emptyMap())
    private val _domainState = MutableStateFlow<GameState?>(null)
    private val _domainUiState = MutableStateFlow<GameUiState?>(null)
    private val _domainFehler = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var gameEngine: GameEngine? = null

    val spielDatenListe: StateFlow<Map<SpielDaten,List<SpeicherDaten>>> = _spielDatenListe.asStateFlow()
    val spielSpeicher: StateFlow<Map<SpielDaten,Pair<Int,List<String>>>> = _spielSpeicher.asStateFlow()
    val domainState: StateFlow<GameState?> = _domainState.asStateFlow()
    val domainUiState: StateFlow<GameUiState?> = _domainUiState.asStateFlow()
    val domainFehler: SharedFlow<String> = _domainFehler.asSharedFlow()

    lateinit var aktuelleDaten: Pair<SpielDaten,List<SpeicherDaten>>
    lateinit var aktuellesSpiel: Spiel

    private fun setzeAktuellesSpiel(spiel: Spiel, daten: Pair<SpielDaten,List<SpeicherDaten>>) {
        aktuellesSpiel = spiel
        aktuelleDaten = daten
        val startzustand = spiel.zuDomainGameState()
        gameEngine = GameEngine(startzustand)
        aktualisiereDomainState(startzustand)
    }

    fun onEvent(event: GameEvent) {
        val engine = gameEngine
        if (engine == null) {
            _domainFehler.tryEmit("Kein Spiel geladen.")
            return
        }

        val vorher = engine.state
        engine.apply(event)
            .onSuccess { state -> uebernehmeEventErgebnis(vorher, state) }
            .onFailure { throwable ->
                _domainFehler.tryEmit(throwable.message ?: "Domain-Event wurde abgelehnt.")
            }
    }

    fun naechsterZugabschnitt() {
        val state = gameEngine?.state
        val zug = state?.zugStatus
        if (state == null || zug == null) {
            _domainFehler.tryEmit("Kein Zug aktiv.")
            return
        }

        when (zug.phase) {
            Phase.Einnahmen,
            Phase.Ausgaben -> {
                val schrittEvents = ZugAutomat.schritte(state)
                    .filter { schritt ->
                        schritt.typ.pflicht && schritt.zustand == SchrittZustand.VERFUEGBAR
                    }
                    .map { schritt -> GameEvent.SchrittAbgeschlossen(schritt.typ) }
                wendeEventsAn(schrittEvents + GameEvent.PhaseAbgeschlossen(zug.phase))
            }
            Phase.Aktionen -> beendeZug()
        }
    }

    fun beendeZug() {
        wendeEventsAn(listOf(GameEvent.ZugBeendet))
    }

    private fun wendeEventsAn(events: List<GameEvent>) {
        val engine = gameEngine
        if (engine == null) {
            _domainFehler.tryEmit("Kein Spiel geladen.")
            return
        }

        val vorher = engine.state
        for (event in events) {
            val ergebnis = engine.apply(event)
            if (ergebnis.isFailure) {
                aktualisiereDomainState(engine.state)
                val fehler = ergebnis.exceptionOrNull()
                _domainFehler.tryEmit(fehler?.message ?: "Zug konnte nicht fortgesetzt werden.")
                return
            }
        }
        uebernehmeEventErgebnis(vorher, engine.state)
    }

    private fun uebernehmeEventErgebnis(vorher: GameState, nachher: GameState) {
        if (nachher.rundenzähler > vorher.rundenzähler) {
            beginneNaechsteRunde(nachher)
        } else {
            aktualisiereDomainState(nachher)
        }
    }

    private fun beginneNaechsteRunde(nachZugende: GameState) {
        try {
            aktuellesSpiel.beginneNaechsteRunde()
        } catch (throwable: Throwable) {
            _domainFehler.tryEmit(throwable.message ?: "Neue Runde konnte nicht begonnen werden.")
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

        val aktuelleWirtschaftsdaten = aktuellesSpiel.zuDomainGameState()
        val synchronisiert = nachZugende.copy(
            marktpreise = aktuelleWirtschaftsdaten.marktpreise,
            leitzins = aktuelleWirtschaftsdaten.leitzins,
            rundenzähler = aktuelleWirtschaftsdaten.rundenzähler,
        )
        gameEngine = GameEngine(synchronisiert)
        aktualisiereDomainState(synchronisiert)

        _spielSpeicher.update { spiele ->
            spiele + (spielDaten to (rundenIndex to aktuellesSpiel.spielerStringListe))
        }

        if (spielDaten.spielID == (-1).toLong()) return

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                DAO.insertRunde(rundenDaten)
            } catch (throwable: Throwable) {
                _domainFehler.tryEmit(
                    throwable.message ?: "Neue Runde konnte nicht gespeichert werden."
                )
            }
        }
    }

    fun aktualisiereWarenkorb(neuerWarenkorb: Map<Rohstoffe, Int>) {
        if (neuerWarenkorb.values.any { menge -> menge < 0 }) {
            _domainFehler.tryEmit("Warenkorbmengen dürfen nicht negativ sein.")
            return
        }

        val warenkorb = neuerWarenkorb.filterValues { menge -> menge > 0 }.toMap()
        val engine = gameEngine
        if (engine == null) {
            _domainFehler.tryEmit("Kein Spiel geladen.")
            return
        }

        val ergebnis = engine.apply(
            GameEvent.WarenkorbGeaendert(
                warenkorb = warenkorb.mapKeys { (rohstoff, _) -> rohstoff.zuDomainRohstoff() }
            )
        )
        if (ergebnis.isFailure) {
            _domainFehler.tryEmit(
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

        aktualisiereDomainState(ergebnis.getOrThrow())

        _spielSpeicher.update { spiele ->
            val übersicht = spiele[bisherigeDaten.first]
                ?: ((aktuellesSpiel.aktuelleRunde - 1) to aktuellesSpiel.spielerStringListe)
            (spiele - bisherigeDaten.first) + (neueSpielDaten to übersicht)
        }

        if (neueSpielDaten.spielID == (-1).toLong()) return

        _spielDatenListe.update { spiele ->
            (spiele - bisherigeDaten.first) + (neueSpielDaten to bisherigeDaten.second)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                DAO.updateSpiel(neueSpielDaten)
            } catch (throwable: Throwable) {
                _domainFehler.tryEmit(
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
            speichern = DAO::insertHandel,
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
            speichern = DAO::insertAnleihe,
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
            _domainFehler.tryEmit(
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
        synchronisiereDomainNachLegacyAenderung()

        if (spielDaten.spielID == (-1).toLong()) return true

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                DAO.updateAnleiheHandel(aktualisiertesDatum)
            } catch (throwable: Throwable) {
                _domainFehler.tryEmit(
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
            _domainFehler.tryEmit(throwable.message ?: fehlermeldung)
            return false
        }

        val spielDaten = aktuelleDaten.first
        val neueDatenListe = aktuelleDaten.second + datum
        aktuelleDaten = spielDaten to neueDatenListe

        synchronisiereDomainNachLegacyAenderung()

        if (spielDaten.spielID == (-1).toLong()) return true

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                speichern(datum)
            } catch (throwable: Throwable) {
                _domainFehler.tryEmit(throwable.message ?: fehlermeldung)
            }
        }
        return true
    }

    private fun synchronisiereDomainNachLegacyAenderung() {
        val bisherigerDomainZustand = gameEngine?.state
        val abgebildeterDomainZustand = aktuellesSpiel.zuDomainGameState()
        val domainZustand = if (bisherigerDomainZustand == null) {
            abgebildeterDomainZustand
        } else {
            abgebildeterDomainZustand.copy(
                rundenzähler = bisherigerDomainZustand.rundenzähler,
                aktiverSpieler = bisherigerDomainZustand.aktiverSpieler,
                zugStatus = bisherigerDomainZustand.zugStatus,
                schuldenstriche = bisherigerDomainZustand.schuldenstriche,
                ueberschuldungen = bisherigerDomainZustand.ueberschuldungen,
            )
        }
        gameEngine = GameEngine(domainZustand)
        aktualisiereDomainState(domainZustand)
    }

    private fun aktualisiereDomainState(state: GameState) {
        _domainState.value = state
        _domainUiState.value = state.zuGameUiState()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val datenbank = AppDatabase.erhalteDatenbank(application.applicationContext).first()
                DAO = ZentralbankSpeicher(datenbank)
                datenbankBereit.complete(Unit)

                _spielDatenListe.value = DAO.observeAlleNachSpiel().first()
                _spielSpeicher.value = erhalteSpielSpeicher()
                _spielSpeicher.update { // Testdaten
                    it + (TestSpiel.zuSpeicherDaten().first to
                        ((TestSpiel.aktuelleRunde - 1) to TestSpiel.spielerStringListe))
                }

            } catch (throwable: Throwable) {
                if (!datenbankBereit.isCompleted) { datenbankBereit.completeExceptionally(throwable) }
                throw throwable
            }
        }
    }
    suspend fun erhalteSpielSpeicher(): Map<SpielDaten,Pair<Int,List<String>>> {
        fun List<SpeicherDaten>.erhalteRunde(): Int = this.filterIsInstance<RundeDaten>().maxByOrNull { it.index }?.index?:0
        fun List<SpeicherDaten>.erhalteSpieler(): List<String> = this.filterIsInstance<SpielerDaten>().map { it.spielerName }
        fun List<SpeicherDaten>.erhalteRelevantes(): Pair<Int,List<String>> = Pair(this.erhalteRunde(),this.erhalteSpieler())
        return _spielDatenListe.value.map { it.key to it.value.erhalteRelevantes() }.toMap()
    }

    public  val erstelleSpiel = { it: Spiel -> erstelleSpiel(it)}
    private fun erstelleSpiel(spiel: Spiel) {
        viewModelScope.launch {
            val daten = spiel.zuSpeicherDaten()
            val gameID = withContext(Dispatchers.IO) { DAO.insertSpielSatz(daten) }
            if (gameID != (-1).toLong()) {
                val gespeicherteDaten = daten.first.copy(spielID = gameID) to daten.second
                setzeAktuellesSpiel(spiel, gespeicherteDaten)

                _spielDatenListe.update {
                    it + gespeicherteDaten
                }

                _spielSpeicher.update {
                    it + (gespeicherteDaten.first to Pair(spiel.aktuelleRunde - 1,spiel.spielerStringListe))
                }
            } else { println("GameID macht Probleme") }
        }
    }

    public val vernichteSpiel = { it: SpielDaten -> vernichteSpiel(it) }
    private fun vernichteSpiel(daten: SpielDaten) {
        TODO()
    }

    public val ladeSpiel = { it: SpielDaten -> ladeSpiel(it) }
    private fun ladeSpiel(daten: SpielDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            ladeSpielDaten(daten)
        }
    }
    private suspend fun ladeAktuellesSpielNeu() { ladeSpielDaten(aktuelleDaten.first) }

    private suspend fun ladeSpielDaten(daten: SpielDaten) {
        if (daten.spielID == (-1).toLong()) {
            val testDaten = TestSpiel.zuSpeicherDaten()
            setzeAktuellesSpiel(testDaten.second.zuSpiel(testDaten.first), testDaten)
            return
        }
        val alles = _spielDatenListe.value[daten].orEmpty()
        setzeAktuellesSpiel(alles.zuSpiel(daten), daten to alles)
    }

    private fun List<SpeicherDaten>.zuSpiel(daten: SpielDaten): Spiel {
        val runden = filterIsInstance<RundeDaten>()
            .sortedBy { it.index }
            .map { Runde(it.index, it.leitzinssatz) }
            .toMutableList()

        require(runden.isNotEmpty()) {
            "Spiel ${daten.spielID} enthält keine Rundendaten."
        }

        require(runden.map { it.index } == runden.indices.toList()) {
            "Rundendaten von Spiel ${daten.spielID} sind nicht lückenlos bei 0 beginnend."
        }

        val rundenAnzahl = runden.size

        val bauDaten = filterIsInstance<BauteilDaten>()
        val kontrollDaten = filterIsInstance<KontrolleDaten>()

        val spieler = filterIsInstance<SpielerDaten>()
            .sortedBy { it.spielerID }
            .map { spielerDaten ->
                val gebaut = MutableList<Map<out Bauteil, Int>>(rundenAnzahl) { emptyMap() }
                val kontrolle = MutableList<Map<Wirtschaftsregionen, Int>>(rundenAnzahl) { emptyMap() }

                bauDaten
                    .filter { it.erbauer == spielerDaten.spielerName }
                    .groupBy { it.runde }
                    .forEach { (runde, einträge) ->
                        require(runde in gebaut.indices) {
                            "Baudaten für unbekannte Runde $runde bei ${spielerDaten.spielerName}."
                        }

                        gebaut[runde] = einträge
                            .groupBy { it.bauteil.zuBauteilOderFehler() }
                            .mapValues { (_, werte) -> werte.sumOf { it.delta } }
                    }

                kontrollDaten
                    .filter { it.besatzer == spielerDaten.spielerName }
                    .groupBy { it.runde }
                    .forEach { (runde, einträge) ->
                        require(runde in kontrolle.indices) {
                            "Kontrolldaten für unbekannte Runde $runde bei ${spielerDaten.spielerName}."
                        }

                        kontrolle[runde] = einträge
                            .groupBy { it.region.zuWirtschaftsregionOderFehler() }
                            .mapValues { (_, werte) -> werte.sumOf { it.delta } }
                    }

                Spieler(
                    name = spielerDaten.spielerName,
                    gebaut = gebaut,
                    kontrolle = kontrolle
                )
            }

        val handelsEinträge = MutableList<Set<Handel>>(rundenAnzahl) { emptySet() }

        filterIsInstance<HandelsDaten>().forEach { daten ->
            require(daten.runde in handelsEinträge.indices) {
                "Handelsdaten für unbekannte Runde ${daten.runde}."
            }

            val handel = RohstoffHandel(
                besitzer = findeJuristischePerson(daten.besitzer, spieler),
                erwerber = findeJuristischePerson(daten.erwerber, spieler),
                betrag = daten.preis.toZahlungsmittel(),
                anzahl = daten.menge,
                rohstoff = daten.rohstoff.zuRohstoffOderFehler()
            )

            handelsEinträge[daten.runde] = handelsEinträge[daten.runde] + handel
        }

        filterIsInstance<AnleiheDaten>().forEach { daten ->
            require(daten.emittiert in handelsEinträge.indices) {
                "Anleihedaten für unbekannte Emissionsrunde ${daten.emittiert}."
            }

            val anleihe = Anleihe(
                schuldiger = findeJuristischePerson(daten.emittent, spieler),
                sondervermögen = daten.sondervermogen.toZahlungsmittel(),
                unvermögen = daten.unvermogen.toZahlungsmittel(),
                laufzeit = daten.laufzeit
            )

            val neuesFormat = daten.handel
                .split("|")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { it.split("#") }

            if (neuesFormat.isNotEmpty() && neuesFormat.all { it.size == 4 }) {
                neuesFormat
                    .map { teile ->
                        GespeicherterAnleihehandel(
                            besitzer = teile[0],
                            erwerber = teile[1],
                            preis = teile[2].toZahlungsmittel(),
                            runde = teile[3].toInt(),
                        )
                    }
                    .sortedBy { it.runde }
                    .forEach { eintrag ->
                        require(eintrag.runde in handelsEinträge.indices) {
                            "Anleihehandel für unbekannte Runde ${eintrag.runde}."
                        }
                        handelsEinträge[eintrag.runde] = handelsEinträge[eintrag.runde] +
                            Anleihenhandel(
                                besitzer = findeJuristischePerson(eintrag.besitzer, spieler),
                                erwerber = findeJuristischePerson(eintrag.erwerber, spieler),
                                anleihe = anleihe,
                                preis = eintrag.preis,
                            )
                    }
            } else {
                // Kompatibilität mit alten Datensätzen: Dort fehlen der erste
                // Erwerber und jeweils der vorherige Besitzer.
                var aktuellerBesitzer: JuristischePerson = Geschäftsbank
                handelsEinträge[daten.emittiert] = handelsEinträge[daten.emittiert] +
                    Anleihenhandel(
                        besitzer = anleihe.schuldiger,
                        erwerber = aktuellerBesitzer,
                        anleihe = anleihe,
                        preis = anleihe.sondervermögen,
                    )

                daten.handel
                    .split("/")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { eintrag ->
                        val teile = eintrag.split("#")
                        require(teile.size == 3) {
                            "Ungültiger Anleihe-Handelseintrag: $eintrag"
                        }
                        GespeicherterAnleihehandel(
                            besitzer = aktuellerBesitzer.name,
                            erwerber = teile[0],
                            preis = teile[1].toIntOrNull()?.toZahlungsmittel()
                                ?: teile[1].toZahlungsmittel(),
                            runde = teile[2].toInt(),
                        )
                    }
                    .sortedBy { it.runde }
                    .forEach { eintrag ->
                        require(eintrag.runde in handelsEinträge.indices) {
                            "Anleihehandel für unbekannte Runde ${eintrag.runde}."
                        }
                        val erwerber = findeJuristischePerson(eintrag.erwerber, spieler)
                        handelsEinträge[eintrag.runde] = handelsEinträge[eintrag.runde] +
                            Anleihenhandel(
                                besitzer = aktuellerBesitzer,
                                erwerber = erwerber,
                                anleihe = anleihe,
                                preis = eintrag.preis,
                            )
                        aktuellerBesitzer = erwerber
                    }
            }
        }

        val vertragsEinträge = MutableList<Set<Vertrag>>(rundenAnzahl) { emptySet() }

        filterIsInstance<VertragsDaten>().forEach { daten ->
            require(daten.runde in vertragsEinträge.indices) {
                "Vertragsdaten für unbekannte Runde ${daten.runde}."
            }

            val vertrag = Vertrag(
                vertragsannehmer = listOf(daten.vertragsannehmer),
                vertragsanbieter = listOf(daten.vertragsanbieter),
                vertragsart = daten.vertragsart.zuVertragsartOderFehler()
            )

            vertragsEinträge[daten.runde] = vertragsEinträge[daten.runde] + vertrag
        }

        return Spiel(
            runden = runden,
            spieler = spieler,
            warenkorb = daten.warenkorb.zuWarenkorb(),
            inflationsziel = Triple(
                daten.inflationsziel,
                daten.nAbweichung,
                daten.sAbweichung
            ),
            handel = Handelsregister(handelsEinträge),
            konflikt = Kriegsregister(vertragsEinträge)
        )
    }

    private fun String.zuWarenkorb(): Map<Rohstoffe, Int> {
        if (isBlank()) return emptyMap()

        return split("/")
            .filter { it.isNotBlank() }
            .associate { eintrag ->
                val teile = eintrag.split("#")

                require(teile.size == 2) {
                    "Ungültiger Warenkorb-Eintrag: $eintrag"
                }

                teile[0].zuRohstoffOderFehler() to teile[1].toInt()
            }
    }

    private data class GespeicherterAnleihehandel(
        val besitzer: String,
        val erwerber: String,
        val preis: Zahlungsmittel,
        val runde: Int,
    )

    private fun findeJuristischePerson(
        name: String,
        spieler: List<Spieler>,
    ): JuristischePerson = spieler.firstOrNull { it.name == name } ?: when (name) {
        Ausland.name, AUSLAND -> Ausland
        Geschäftsbank.name, "Zentralbank" -> Geschäftsbank
        else -> error("Unbekannte juristische Person: $name")
    }

    private fun String.zuRohstoffOderFehler(): Rohstoffe {
        val text = trim()

        return Rohstoffe.entries.firstOrNull {
            it.name == text || it.str == text
        } ?: error("Unbekannter Rohstoff: $text")
    }

    private fun String.zuBauteilOderFehler(): Bauteil {
        val text = trim()

        return Bauteil.fromString(text)
            ?: Bauteil.entries.firstOrNull { it.toString() == text }
            ?: error("Unbekanntes Bauteil: $text")
    }

    private fun String.zuWirtschaftsregionOderFehler(): Wirtschaftsregionen {
        val text = trim()

        return Wirtschaftsregionen.entries.firstOrNull {
            it.name == text || it.str == text
        } ?: error("Unbekannte Wirtschaftsregion: $text")
    }

    private fun String.zuVertragsartOderFehler(): Vertragsart {
        val text = trim()

        return Vertragsart.entries.firstOrNull {
            it.name == text || it.str == text
        } ?: error("Unbekannte Vertragsart: $text")
    }

/*
    suspend fun erstelleSpiel(daten: Pair<Map<String, Zahlungsmittel>,Map<Rohstoffe,Int>>): Int {
        return viewModelScope.async(Dispatchers.IO) {
            datenbankBereit.await()

            val spielerString = spielSiedler(daten.first.map { it.key })
            val gameID = gameDao.insert(Spiel(spielerString)).toInt()

            if (gameID != -1) {
                withContext(Dispatchers.Main) {
                    aktuellesSpiel = Spiel(gameID, spielerString)
                    _spielListe.update { vorhandene -> vorhandene + aktuellesSpiel }
                    _spielRundenListe.update { vorhandene -> vorhandene + (gameID to 0) }
                    _siedlerListe.value = emptyList()
                    _rundenListe.value = emptyList()
                    _creditList.value = emptyList()
                    _handelsList.value = emptyList()
                    _bauwerkListe.value = emptyList()
                    vernichteZwischenspeicher()
                }
            } else {
                println("GameID macht Probleme")
            }

            gameID
        }.await()
    }
*/


/*    fun fuegeSiedlerHinzu(spielerDaten: SpielerDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()

            val spielID = aktuellesSpielID()
            val playerID = playerDao.insert(spielerDaten.copy(SpielID = spielID)).toInt()
            if (playerID == -1) {
                println("PlayerID macht Probleme: $spielerDaten")
            }

            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun neueAnleihe(anleiheDaten: AnleiheDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()

            val spielID = aktuellesSpielID()
            val creditID = creditDao.insert(anleiheDaten.copy(SpielID = spielID)).toInt()
            if (creditID == -1) {
                println("CreditID macht Probleme: ${anleiheDaten.emittent}")
            }

            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun aktualisiereAnleihe(anleiheDaten: AnleiheDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            aktuellesSpielID()

            creditDao.update(anleiheDaten)
            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun loescheAnleihe(anleiheDaten: AnleiheDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            aktuellesSpielID()

            creditDao.delete(anleiheDaten)
            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun neuerHandel(handelsDaten: HandelsDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()

            val spielID = aktuellesSpielID()
            val tradeID = tradeDao.insert(handelsDaten.copy(SpielID = spielID)).toInt()
            if (tradeID == -1) {
                println("TradeID macht Probleme")
            }

            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun aktualisiereHandel(handelsDaten: HandelsDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            aktuellesSpielID()

            tradeDao.update(handelsDaten)
            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun loescheHandel(handelsDaten: HandelsDaten) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            aktuellesSpielID()

            tradeDao.delete(handelsDaten)
            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun neuesBauwerk(bauwerk: Bauwerk) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()

            val spielID = aktuellesSpielID()
            val buildID = buildDao.insert(bauwerk.copy(SpielID = spielID)).toInt()
            if (buildID == -1) {
                println("BuildID macht Probleme")
            }

            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun aktualisiereBauwerk(bauwerk: Bauwerk) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            aktuellesSpielID()

            buildDao.update(bauwerk)
            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun loescheBauwerk(bauwerk: Bauwerk) {
        viewModelScope.launch(Dispatchers.IO) {
            datenbankBereit.await()
            aktuellesSpielID()

            buildDao.delete(bauwerk)
            ladeAktuellesSpielNeu()
        }
    }*/

/*    fun spielSiedlerListe(players: String): List<String> {
        return players
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }*/

/*    fun spielSiedler(players: List<String>): String {
        return players
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("/")
    }*/

/*    fun siedlerSaldoZurRunde(runde: Int): Map<String, Zahlungsmittel> {
        return _spielerDatenListe.value.associate { siedler ->
            val name = siedler.siedlerName
            val saldo = siedlerHandelsSaldoBisZurRunde(name, runde) +
                    siedlerAnleiheSaldoBisZurRunde(name, runde)

            name to saldo
        }
    }*/

/*    fun siedlerSollZurRunde(runde: Int): Map<String, Zahlungsmittel> {
        return _spielerDatenListe.value.associate { siedler ->
            siedler.siedlerName to siedlerOffeneSchuldBisZurRunde(siedler.siedlerName, runde)
        }
    }*/

/*    fun marktpreisZurRunde(runde: Int): Map<Rohstoffe, Zahlungsmittel> {
        return Rohstoffe.entries.associateWith { rohstoff ->
            val aktuellePreise = _handelsList.value.filter {
                it.runde == runde && it.erhalteRohstoff() == rohstoff
            }

            val aktuellerPreis = if (aktuellePreise.isNotEmpty()) {
                aktuellePreise.summeGeld { it._erhalteGesamtPreis() } / aktuellePreise.size
            } else {
                Zahlungsmittel(0)
            }

            when {
                runde <= 1 -> Zahlungsmittel(0)
                aktuellerPreis != Zahlungsmittel(0) -> aktuellerPreis
                else -> letzterBekannterMarktpreisVorRunde(runde, rohstoff)
            }
        }
    }*/

/*    fun auslandsSaldoZurRunde(runde: Int): Map<Rohstoffe, Int> {
        return Rohstoffe.entries.associateWith { rohstoff ->
            _handelsList.value
                .filter { it.runde <= runde && it.erhalteRohstoff() == rohstoff }
                .sumOf { handel ->
                    when {
                        handel.besitzer == AUSLAND -> 1
                        handel.erwerber == AUSLAND -> -1
                        else -> 0
                    }
                }
        }
    }*/

/*    fun siedlerSchuldStatistikBisZurRunde(
        runde: Int
    ): List<Map<String, Pair<Zahlungsmittel, Map<Int, Int>>>> {
        return _spielerDatenListe.value.map { siedler ->
            val offeneAnleihen = _creditList.value.filter { anleihe ->
                anleihe.emittent == siedler.siedlerName &&
                        anleihe.emittiert <= runde &&
                        anleihe.emittiert + anleihe.laufzeit >= runde &&
                        anleihe.emittiert != 0
            }

            mapOf(
                siedler.siedlerName to (
                        siedlerOffeneSchuldBisZurRunde(siedler.siedlerName, runde) to
                                offeneAnleihen.groupingBy { it.laufzeit }.eachCount()
                        )
            )
        }
    }*/

/*    fun siedlerBauteileZurRunde(runde: Int): Map<String, Map<Bauteil, Int>> {
        return _spielerDatenListe.value.associate { siedler ->
            val bestand = Bauteil.entries.associateWith { bauteil ->
                _bauwerkListe.value.count { siedlerBauwerk ->
                    siedlerBauwerk.siedlerName == siedler.siedlerName &&
                            siedlerBauwerk.erhalteBauwerk() == bauteil &&
                            siedlerBauwerk.runde <= runde
                }
            }

            siedler.siedlerName to bestand
        }
    }*/

/*    fun siedlerVermoegenZurRunde(runde: Int): Map<String, Zahlungsmittel> {
        return _spielerDatenListe.value.associate { siedler ->
            siedler.siedlerName to siedlerBauwerkWertZurRunde(runde, siedler.siedlerName)
        }
    }*/

/*    fun siedlerFaelligeSchuldZurRunde(runde: Int): Map<String, Zahlungsmittel> {
        return _spielerDatenListe.value.associate { siedler ->
            val schuld = _creditList.value
                .filter {
                    it.emittent == siedler.siedlerName &&
                            it.emittiert != 0 &&
                            it.emittiert + it.laufzeit == runde
                }
                .summeGeld { anleihe ->
                    -anleiheSchuld(anleihe)
                }

            siedler.siedlerName to schuld
        }
    }*/

/*    fun siedlerSchuldAnzahlZurRunde(runde: Int): Map<String, Int> {
        return _spielerDatenListe.value.associate { siedler ->
            val anzahl = _creditList.value.count {
                it.emittent == siedler.siedlerName &&
                        it.emittiert <= runde &&
                        runde < it.emittiert + it.laufzeit
            }

            siedler.siedlerName to anzahl
        }
    }*/

/*    fun siedlerSchuldQuoteZurRunde(runde: Int): Map<String, Int> {
        return _spielerDatenListe.value.associate { siedler ->
            val schulden = siedlerOffeneSchuldBisZurRunde(siedler.siedlerName, runde)
            val vermoegen = siedlerBauwerkWertZurRunde(runde, siedler.siedlerName)

            val quote = if (vermoegen == Zahlungsmittel()) {
                0
            } else {
                Zahlungsmittel.finanzQuote(vermoegen,schulden)
            }

            siedler.siedlerName to quote
        }
    }*/

/*    private suspend fun aktualisiereSpielUebersicht() {
        val spiele = gameDao.getAll()
        val alleRunden = roundDao.getAll()

        withContext(Dispatchers.Main) {
            _spielDatenListe.value = spiele
            _spielRundenListe.value = spiele.associate { spiel ->
                val vergangeneSpielDauer = (alleRunden.count { runde ->
                    runde.SpielID == spiel.SpielID
                } - 1).coerceAtLeast(0)

                spiel.SpielID to vergangeneSpielDauer
            }
        }
    }*/


/*    private fun initialisiereZwischenspeicher() {
        val vergangeneRunden = 1 until aktuelleRunde

        siedlerSaldoZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerSaldoZurRunde(runde)
        }
        siedlerSollZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerSollZurRunde(runde)
        }
        marktpreisZwischenspeicher = vergangeneRunden.map { runde ->
            marktpreisZurRunde(runde)
        }
        auslandsSaldoZwischenspeicher = vergangeneRunden.map { runde ->
            auslandsSaldoZurRunde(runde)
        }
        siedlerVermoegenZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerVermoegenZurRunde(runde)
        }
        siedlerBauteilZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerBauteileZurRunde(runde)
        }
        siedlerSchuldFaelligZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerFaelligeSchuldZurRunde(runde)
        }
        siedlerSchuldAnzahlZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerSchuldAnzahlZurRunde(runde)
        }
        siedlerSchuldQuoteZwischenspeicher = vergangeneRunden.map { runde ->
            siedlerSchuldQuoteZurRunde(runde)
        }
    }*/

/*    private fun vernichteZwischenspeicher() {
        siedlerSaldoZwischenspeicher = emptyList()
        siedlerSollZwischenspeicher = emptyList()
        marktpreisZwischenspeicher = emptyList()
        auslandsSaldoZwischenspeicher = emptyList()
        siedlerVermoegenZwischenspeicher = emptyList()
        siedlerBauteilZwischenspeicher = emptyList()
        siedlerSchuldFaelligZwischenspeicher = emptyList()
        siedlerSchuldAnzahlZwischenspeicher = emptyList()
        siedlerSchuldQuoteZwischenspeicher = emptyList()
    }*/

/*    private fun aktuellesSpielID(): Int {
        check(::aktuellesSpielDaten.isInitialized) {
            "Es ist kein Spiel geladen oder erstellt."
        }

        return aktuellesSpielDaten.SpielID
    }*/

/*    private fun siedlerHandelsSaldoBisZurRunde(
        siedlerName: String,
        runde: Int
    ): Zahlungsmittel {
        return _handelsList.value
            .filter { it.runde <= runde }
            .summeGeld { handel ->
                when {
                    handel.besitzer == siedlerName -> handel._erhalteGesamtPreis()
                    handel.erwerber == siedlerName -> handel._erhalteGesamtPreis(true)
                    else -> Zahlungsmittel(0)
                }
            }
    }*/

/*    private fun siedlerOffeneSchuldBisZurRunde(
        siedlerName: String,
        runde: Int
    ): Zahlungsmittel {
        return _creditList.value
            .filter {
                it.emittent == siedlerName &&
                        it.emittiert <= runde &&
                        it.emittiert != 0
            }
            .summeGeld { anleihe ->
                val faelligkeitsRunde = anleihe.emittiert + anleihe.laufzeit

                if (faelligkeitsRunde < runde) {
                    Zahlungsmittel(0)
                } else {
                    val restlaufzeit = (faelligkeitsRunde - runde).coerceAtLeast(0)

                    val preis = anleihe.erhalteSondervermögen()
                    val zins = zinsZurRunde(anleihe.emittiert)

                    preis + preis * restlaufzeit * zins / 100
                }
            }
    }*/

/*    private fun siedlerAnleiheSaldoBisZurRunde(
        siedlerName: String,
        runde: Int
    ): Zahlungsmittel {
        val anleihen = _creditList.value.filter {
            it.emittent == siedlerName
        }

        val auszahlungen = anleihen
            .filter { it.emittiert <= runde }
            .summeGeld { it.erhalteSondervermögen() }

        val laufendeZinsen = anleihen
            .filter { it.emittiert < runde && runde < it.emittiert + it.laufzeit }
            .summeGeld { anleihe ->
                val vergangeneRunden = runde - anleihe.emittiert
                val preis = anleihe.erhalteSondervermögen()
                val zins = zinsZurRunde(anleihe.emittiert)

                -(preis * zins * vergangeneRunden / 100)
            }

        val faelligeSchulden = anleihen
            .filter { it.emittiert + it.laufzeit <= runde }
            .summeGeld { anleiheSchuld(it) }

        return auszahlungen +
                laufendeZinsen +
                faelligeSchulden +
                siedlerGlaeubigerSaldoBisZurRunde(siedlerName, runde)
    }*/

/*    private fun anleiheSchuld(anleiheDaten: AnleiheDaten): Zahlungsmittel {
        val preis = anleiheDaten.erhalteSondervermögen()
        val laufzeit = anleiheDaten.laufzeit
        val zins = zinsZurRunde(anleiheDaten.emittiert)

        return -(preis * (1 + laufzeit * zins / 100))
    }*/

/*    private fun anleiheGlaeubigerNamen(anleiheDaten: AnleiheDaten): List<String> {
        *//*
         * Bevorzugt: ein echtes Feld `glaeubiger` in deiner Anleihe-Entity.
         * Fallbacks sind nur drin, damit die Datei nicht schon wegen eines alten Feldnamens
         * beleidigt aus dem Compiler fällt.
         *//*
        val rohwert = leseStringFeld(
            objekt = anleiheDaten,
            "glaeubiger",
            "glaeubigerName",
            "holderName",
            "inhaber",
            "glaeubigerNamen"
        ) ?: return emptyList()

        return rohwert
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }*/

/*    private fun siedlerGlaeubigerSaldoBisZurRunde(
        siedlerName: String,
        runde: Int
    ): Zahlungsmittel {
        return _creditList.value.summeGeld { anleihe ->
            if (anleihe.emittiert > runde) {
                return@summeGeld Zahlungsmittel.ZERO
            }

            val glaeubiger = anleiheGlaeubigerNamen(anleihe)

            if (glaeubiger.isEmpty()) {
                return@summeGeld Zahlungsmittel.ZERO
            }

            val fortschritt = runde - anleihe.emittiert
            val aktiveGlaeubiger = glaeubiger.drop(
                (glaeubiger.size - fortschritt).coerceAtLeast(0)
            )

            val rueckzahlung = if (
                fortschritt == anleihe.laufzeit &&
                siedlerName == glaeubiger.last()
            ) {
                anleihe.erhalteSondervermögen()
            } else {
                Zahlungsmittel.ZERO
            }

            val schuldanteil = aktiveGlaeubiger.summeGeld { glaeubigerName ->
                if (glaeubigerName == siedlerName) {
                    anleiheSchuld(anleihe)
                } else {
                    Zahlungsmittel.ZERO
                }
            }

            schuldanteil + rueckzahlung
        }
    }*/

/*    private fun letzterBekannterMarktpreisVorRunde(
        runde: Int,
        rohstoff: Rohstoffe,
    ): Zahlungsmittel {
        for (vergangeneRunde in runde - 1 downTo 1) {
            val preise = _handelsList.value.filter {
                it.runde == vergangeneRunde && it.erhalteRohstoff() == rohstoff
            }

            if (preise.isNotEmpty()) {
                val preis = preise.summeGeld { handel ->
                    handel._erhalteGesamtPreis()
                } / preise.size

                if (preis != Zahlungsmittel(0)) return preis
            }
        }

        return Zahlungsmittel.ZERO
    }*/

/*    private fun siedlerBauwerkWertZurRunde(
        runde: Int,
        siedlerName: String
    ): Zahlungsmittel {
        return _bauwerkListe.value
            .filter { it.siedlerName == siedlerName && it.runde <= runde }
            .summeGeld { bauwerk -> bauwerkPreisZurRunde(bauwerk.erhalteBauwerk(), runde) }
    }*/

/*    private fun bauwerkPreisZurRunde(
        bauteil: Bauteil,
        runde: Int,
    ): Zahlungsmittel {
        val marktpreis = marktpreisZurRunde(runde)
        val kosten = BauKosten(bauteil)

        return Zahlungsmittel(0)
//        return kosten.summeGeld { rohstoff ->
//            marktpreis[rohstoff] ?: Zahlungsmittel.ZERO
//        } // TODO
    }*/

/*    private fun leseStringFeld(
        objekt: Any,
        vararg namen: String
    ): String? {
        val klasse = objekt.javaClass

        for (name in namen) {
            val feldWert = runCatching {
                val feld = klasse.getDeclaredField(name)
                feld.isAccessible = true
                feld.get(objekt) as? String
            }.getOrNull()

            if (!feldWert.isNullOrBlank()) return feldWert

            val getterName = "get" + name.replaceFirstChar { it.uppercase() }
            val getterWert = runCatching {
                klasse.getMethod(getterName).invoke(objekt) as? String
            }.getOrNull()

            if (!getterWert.isNullOrBlank()) return getterWert
        }

        return null
    }*/

/*    private fun zinsZurRunde(runde: Int): Int {
        val runden = _rundenListe.value.filter {
            it.SpielID == aktuellesSpielDaten.SpielID && it.runde == runde
        }

        return when {
            runden.size == 1 -> runden.first().leitzins
            runden.isEmpty() -> 0
            else -> {
                println("Zu viele Zinseinträge in Runde $runde")
                return 0
            }
//            else -> {
//                println("Zu viele Zinseinträge in Runde $runde")
//
//                runden.fold(0) { summe, eintrag ->
//                    summe + eintrag.leitzins
//                }.divide(runden.size, 8, RoundingMode.HALF_UP)
//            }
        }
    }*/

    fun declareWar(aggressor: String, verteidiger: String) {}
    fun declareMilitary(first: String, second: Int) {}
    fun declarePeace(aggressor: String, verteidiger: String) {}
}
