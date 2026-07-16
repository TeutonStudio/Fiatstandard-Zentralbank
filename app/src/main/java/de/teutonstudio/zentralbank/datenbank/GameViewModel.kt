package de.teutonstudio.zentralbank.datenbank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.SchrittZustand
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZugAuswertung
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
    private val _spielZustand = MutableStateFlow<SpielZustand?>(null)
    private val _spielUebersicht = MutableStateFlow<SpielUebersichtZustand?>(null)
    private val _spielFehler = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var spielAblauf: SpielAblauf? = null

    val spielDatenListe: StateFlow<Map<SpielDaten,List<SpeicherDaten>>> = _spielDatenListe.asStateFlow()
    val spielSpeicher: StateFlow<Map<SpielDaten,Pair<Int,List<String>>>> = _spielSpeicher.asStateFlow()
    val spielZustand: StateFlow<SpielZustand?> = _spielZustand.asStateFlow()
    val spielUebersicht: StateFlow<SpielUebersichtZustand?> = _spielUebersicht.asStateFlow()
    val spielFehler: SharedFlow<String> = _spielFehler.asSharedFlow()

    lateinit var aktuelleDaten: Pair<SpielDaten,List<SpeicherDaten>>
    lateinit var aktuellesSpiel: Spiel
    val aktuellesSpielOderNull: Spiel?
        get() = if (::aktuellesSpiel.isInitialized) aktuellesSpiel else null

    private fun setzeAktuellesSpiel(spiel: Spiel, daten: Pair<SpielDaten,List<SpeicherDaten>>) {
        aktuellesSpiel = spiel
        aktuelleDaten = daten
        val startzustand = spiel.zuSpielZustand()
        spielAblauf = SpielAblauf(startzustand)
        aktualisiereSpielZustand(startzustand)
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
                val schrittEreignisse = ZugAuswertung.schritte(zustand)
                    .filter { schritt ->
                        schritt.typ.pflicht && schritt.zustand == SchrittZustand.VERFUEGBAR
                    }
                    .map { schritt -> SpielEreignis.SchrittAbgeschlossen(schritt.typ) }
                wendeEreignisseAn(
                    schrittEreignisse + SpielEreignis.PhaseAbgeschlossen(zug.phase),
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
        aktualisiereSpielZustand(synchronisiert)

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
                DAO.updateAnleiheHandel(aktualisiertesDatum)
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
                rundenzähler = bisherigerSpielZustand.rundenzähler,
                aktiverSpieler = bisherigerSpielZustand.aktiverSpieler,
                zugStatus = bisherigerSpielZustand.zugStatus,
                schuldenstriche = bisherigerSpielZustand.schuldenstriche,
                ueberschuldungen = bisherigerSpielZustand.ueberschuldungen,
            )
        }
        spielAblauf = SpielAblauf(spielZustand)
        aktualisiereSpielZustand(spielZustand)
    }

    private fun aktualisiereSpielZustand(zustand: SpielZustand) {
        aktuellesSpielOderNull?.aktualisiereAktivenSpieler(zustand.zugStatus?.spieler?.wert)
        _spielZustand.value = zustand
        _spielUebersicht.value = zustand.zuSpielUebersichtZustand()
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

    public val ladeSpiel = { daten: SpielDaten, nachLaden: () -> Unit ->
        ladeSpiel(daten, nachLaden)
    }
    private fun ladeSpiel(daten: SpielDaten, nachLaden: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                ladeSpielDaten(daten)
                withContext(Dispatchers.Main) { nachLaden() }
            } catch (throwable: Throwable) {
                _spielFehler.emit(
                    throwable.message?.let { "Spielstand konnte nicht geladen werden: $it" }
                        ?: "Spielstand konnte nicht geladen werden."
                )
            }
        }
    }
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

    fun declareWar(aggressor: String, verteidiger: String) {}
    fun declareMilitary(first: String, second: Int) {}
    fun declarePeace(aggressor: String, verteidiger: String) {}
}
