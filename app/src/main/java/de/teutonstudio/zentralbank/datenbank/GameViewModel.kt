package de.teutonstudio.zentralbank.datenbank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import de.teutonstudio.zentralbank.daten.RaumSpielAblage
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpiel
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoff
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoffe
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpielZustand
import de.teutonstudio.zentralbank.daten.zuordnung.zuGeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk
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

private const val STANDARD_AUSLANDS_IMPORTFAKTOR = 4f / 3f


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

    private lateinit var tabellenSpeicher: ZentralbankSpeicher
    private lateinit var spielAblage: SpielAblage
    private val datenbankBereit = CompletableDeferred<Unit>()
    private val ablageSperre = Mutex()
    private val naechsteAblageAenderung = AtomicLong()
    private val letzteAblageAenderung = mutableMapOf<Long, Long>()

    private val _spielDatenListe = MutableStateFlow<Map<SpielDaten,List<SpeicherDaten>>>(emptyMap())
    private val testSpielstandUebersicht = SpielstandUebersicht(
        id = -1,
        spielerNamen = TestSpiel.spielerStringListe,
        runde = TestSpiel.aktuelleRunde - 1,
    )
    private val _spielstaende = MutableStateFlow(listOf(testSpielstandUebersicht))
    private val _spielZustand = MutableStateFlow<SpielZustand?>(null)
    private val _spielUebersicht = MutableStateFlow<SpielUebersichtZustand?>(null)
    private val _rundenwechselAnzeige = MutableStateFlow<SpielZustand?>(null)
    private val _spielFehler = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var spielAblauf: SpielAblauf? = null

    val spielstaende: StateFlow<List<SpielstandUebersicht>> = _spielstaende.asStateFlow()
    val spielZustand: StateFlow<SpielZustand?> = _spielZustand.asStateFlow()
    val spielUebersicht: StateFlow<SpielUebersichtZustand?> = _spielUebersicht.asStateFlow()
    val rundenwechselAnzeige: StateFlow<SpielZustand?> = _rundenwechselAnzeige.asStateFlow()
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
        spielAblauf = ablauf
        _rundenwechselAnzeige.value = null
        aktualisiereSpielZustand(ablauf.zustand)
        starteProzugFallsNoetig()
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

    fun meldeSpielFehler(meldung: String) {
        _spielFehler.tryEmit(meldung)
    }

    fun spielstandBeenden(nachBeenden: () -> Unit) {
        val speicherauftrag = aktuellerSpielstandSpeicherauftrag()
        if (speicherauftrag == null) {
            nachBeenden()
            return
        }
        viewModelScope.launch {
            try {
                speichereSpielstand(speicherauftrag)
                nachBeenden()
            } catch (throwable: Throwable) {
                _spielFehler.emit(
                    throwable.message ?: "Spielstand konnte nicht gespeichert werden.",
                )
            }
        }
    }

    fun baueMitAuslandseinkauf(
        bauEreignis: SpielEreignis,
        fehlendeRohstoffe: Map<Rohstoff, Int>,
    ): Boolean = baueBauplanMitAuslandseinkauf(listOf(bauEreignis), fehlendeRohstoffe)

    fun baueBauplanMitAuslandseinkauf(
        bauEreignisse: List<SpielEreignis>,
        fehlendeRohstoffe: Map<Rohstoff, Int>,
    ): Boolean {
        if (bauEreignisse.isEmpty()) {
            _spielFehler.tryEmit("Der Bauplan enthält keine Bauwerke.")
            return false
        }
        val ausgangszustand = spielAblauf?.zustand ?: run {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return false
        }
        val aktiverSpielerId = ausgangszustand.aktiverSpieler ?: run {
            _spielFehler.tryEmit("Es ist kein Spieler aktiv.")
            return false
        }
        val aktiverSpieler = aktuellesSpiel.spielerListe.firstOrNull { spieler ->
            spieler.name == aktiverSpielerId.wert
        } ?: run {
            _spielFehler.tryEmit("Der aktive Spieler konnte nicht zugeordnet werden.")
            return false
        }
        val handelsvorgaenge = fehlendeRohstoffe
            .filterValues { menge -> menge > 0 }
            .map { (rohstoff, menge) ->
                val legacyRohstoff = rohstoff.zuRohstoffe()
                val marktpreis = aktuellesSpiel.aktuelleMarktpreise[legacyRohstoff]
                    ?: Zahlungsmittel()
                val gesamtpreis = marktpreis * STANDARD_AUSLANDS_IMPORTFAKTOR * menge
                if (gesamtpreis <= Zahlungsmittel()) {
                    _spielFehler.tryEmit(
                        "Für ${legacyRohstoff.str} ist kein positiver Auslandspreis vorhanden."
                    )
                    return false
                }
                RohstoffHandel(
                    besitzer = Ausland,
                    erwerber = aktiverSpieler,
                    betrag = gesamtpreis,
                    anzahl = menge,
                    rohstoff = legacyRohstoff,
                )
            }

        val fachEreignisse = runCatching {
            handelsvorgaenge.map { handel -> handel.zuFachEreignis() } + bauEreignisse
        }.getOrElse { fehler ->
            _spielFehler.tryEmit(fehler.message ?: "Auslandseinkauf konnte nicht vorbereitet werden.")
            return false
        }
        var pruefzustand = ausgangszustand
        fachEreignisse.forEach { ereignis ->
            pruefzustand = SpielRegelwerk.wendeAn(pruefzustand, ereignis).getOrElse { fehler ->
                _spielFehler.tryEmit(
                    fehler.message ?: "Bau und Auslandseinkauf wurden fachlich abgelehnt."
                )
                return false
            }
        }

        handelsvorgaenge.forEach { handel ->
            if (!erfasseRohstoffhandel(handel)) return false
        }
        return bauplanAnwenden(bauEreignisse)
    }

    fun bauplanAnwenden(bauEreignisse: List<SpielEreignis>): Boolean {
        if (bauEreignisse.isEmpty()) {
            _spielFehler.tryEmit("Der Bauplan enthält keine Bauwerke.")
            return false
        }
        val ablauf = spielAblauf ?: run {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return false
        }
        val vorher = ablauf.zustand
        val pruefung = bauEreignisse.fold(Result.success(vorher)) { bisher, ereignis ->
            bisher.mapCatching { zustand ->
                SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
            }
        }
        pruefung.exceptionOrNull()?.let { fehler ->
            _spielFehler.tryEmit(fehler.message ?: "Der Bauplan wurde fachlich abgelehnt.")
            return false
        }

        for (ereignis in bauEreignisse) {
            val ergebnis = ablauf.ereignisAnwenden(ereignis)
            if (ergebnis.isFailure) {
                _spielFehler.tryEmit(
                    ergebnis.exceptionOrNull()?.message ?: "Der Bauplan konnte nicht übernommen werden."
                )
                return false
            }
        }
        uebernehmeEreignisErgebnis(vorher, ablauf.zustand)
        return true
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

    fun prozugAbschliessen() {
        val zug = spielAblauf?.zustand?.zugStatus
        if (zug == null) {
            _spielFehler.tryEmit("Kein Zug aktiv.")
            return
        }
        wendeEreignisseAn(listOf(SpielEreignis.ProzugErfolgreichAbgeschlossen(zug.zugId)))
    }

    fun verarbeitungAusfuehren(feld: KartenFeld, laeufe: Int) {
        val zugId = spielAblauf?.zustand?.zugStatus?.zugId ?: return
        wendeEreignisseAn(listOf(SpielEreignis.VerarbeitungAusgefuehrt(zugId, feld, laeufe)))
    }

    fun verwaltungsstandortVersorgen(ecke: KartenEcke) {
        val zugId = spielAblauf?.zustand?.zugStatus?.zugId ?: return
        wendeEreignisseAn(listOf(SpielEreignis.VerwaltungsstandortVersorgt(zugId, ecke)))
    }

    fun verbindlichkeitBegleichen(verbindlichkeit: VerbindlichkeitId) {
        val zugId = spielAblauf?.zustand?.zugStatus?.zugId ?: return
        wendeEreignisseAn(listOf(SpielEreignis.VerbindlichkeitBeglichen(zugId, verbindlichkeit)))
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
            beginneNaechsteRunde(nachher)?.let { neuerRundenzustand ->
                _rundenwechselAnzeige.value = neuerRundenzustand
            }
        } else {
            aktualisiereSpielZustand(nachher)
            starteProzugFallsNoetig()
        }
        speichereAktuellenFachSpielstand()
    }

    private fun beginneNaechsteRunde(nachZugende: SpielZustand): SpielZustand? {
        try {
            aktuellesSpiel.beginneNaechsteRunde()
        } catch (throwable: Throwable) {
            _spielFehler.tryEmit(throwable.message ?: "Neue Runde konnte nicht begonnen werden.")
            return null
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
        val ablauf = requireNotNull(spielAblauf)
        val aktualisierung = SpielEreignis.RundenwerteAktualisiert(
            runde = nachZugende.rundenzähler,
            marktpreise = aktuelleWirtschaftsdaten.marktpreise,
            leitzins = aktuelleWirtschaftsdaten.leitzins,
        )
        val synchronisiert = ablauf.ereignisAnwenden(aktualisierung).getOrElse { fehler ->
            _spielFehler.tryEmit(fehler.message ?: "Rundenwerte konnten nicht übernommen werden.")
            return null
        }
        aktualisiereSpielZustand(synchronisiert)

        if (spielDaten.spielID == (-1).toLong()) return synchronisiert

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                tabellenSpeicher.insertRunde(rundenDaten)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Neue Runde konnte nicht gespeichert werden."
                )
            }
        }
        return synchronisiert
    }

    private fun starteProzugFallsNoetig() {
        if (_rundenwechselAnzeige.value != null) return
        val ablauf = spielAblauf ?: return
        val zug = ablauf.zustand.zugStatus ?: return
        if (ablauf.zustand.spielabschnitt != de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt.REGULAER ||
            zug.phase != ZugPhase.Prozug || zug.prozug.begonnen
        ) return
        ablauf.ereignisAnwenden(SpielEreignis.ProzugBegonnen(zug.zugId))
            .onSuccess { zustand ->
                aktualisiereSpielZustand(zustand)
                speichereAktuellenFachSpielstand()
            }
            .onFailure { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Prozug konnte nicht begonnen werden.")
            }
    }

    fun rundenwechselAngezeigt() {
        val animationWarAktiv = _rundenwechselAnzeige.value != null
        _rundenwechselAnzeige.value = null
        if (animationWarAktiv) starteProzugFallsNoetig()
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
                tabellenSpeicher.updateSpiel(neueSpielDaten)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Warenkorb konnte nicht gespeichert werden."
                )
            }
        }
    }

    fun erfasseRohstoffhandel(handel: RohstoffHandel): Boolean {
        val fachEreignis = runCatching { handel.zuFachEreignis() }.getOrElse { fehler ->
            _spielFehler.tryEmit(fehler.message ?: "Handel kann nicht zugeordnet werden.")
            return false
        }
        return erfasseHandel(
            handel = handel,
            fachEreignis = fachEreignis,
            speicherdatum = {
                HandelsDaten(
                    aktuelleRundenDaten(),
                    handel,
                ).copy(spielID = aktuelleDaten.first.spielID)
            },
            speichern = tabellenSpeicher::insertHandel,
            fehlermeldung = "Handel konnte nicht gespeichert werden.",
        )
    }

    fun emittiereAnleihe(handel: Anleihenhandel): Boolean {
        val fachEreignis = runCatching { handel.zuEmissionsEreignis() }.getOrElse { fehler ->
            _spielFehler.tryEmit(fehler.message ?: "Anleihe kann nicht zugeordnet werden.")
            return false
        }
        return erfasseHandel(
            handel = handel,
            fachEreignis = fachEreignis,
            speicherdatum = {
                AnleiheDaten(
                    aktuelleRundenDaten(),
                    mapOf((aktuellesSpiel.aktuelleRunde - 1) to handel),
                ).copy(spielID = aktuelleDaten.first.spielID)
            },
            speichern = tabellenSpeicher::insertAnleihe,
            fehlermeldung = "Anleihe konnte nicht gespeichert werden.",
        )
    }

    fun erfasseAnleihenhandel(handel: Anleihenhandel): Boolean {
        val bestehendeAnleihe = aktuellesSpiel.anleihen
            .firstOrNull { anzeige -> anzeige.anleihe === handel.anleihe }
            ?: return emittiereAnleihe(handel)
        val bisherigesDatum = aktuelleDaten.second
            .filterIsInstance<AnleiheDaten>()
            .firstOrNull { daten -> daten.passtZu(bestehendeAnleihe) }

        val fachEreignis = runCatching {
            handel.zuAnleihenHandelsEreignis(bestehendeAnleihe)
        }.getOrElse { fehler ->
            _spielFehler.tryEmit(fehler.message ?: "Anleihehandel kann nicht zugeordnet werden.")
            return false
        }
        if (!pruefeFachEreignis(fachEreignis)) return false

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
        if (!uebernehmeFachEreignis(fachEreignis)) return false

        if (spielDaten.spielID == (-1).toLong()) return true

        _spielDatenListe.update { spiele ->
            spiele + (spielDaten to neueDatenListe)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                datenbankBereit.await()
                tabellenSpeicher.updateAnleiheHandel(aktualisiertesDatum)
            } catch (throwable: Throwable) {
                _spielFehler.tryEmit(
                    throwable.message ?: "Anleihehandel konnte nicht gespeichert werden."
                )
            }
        }
        return true
    }

    private fun RohstoffHandel.zuFachEreignis(): SpielEreignis {
        val verkaeufer = besitzer
        val kaeufer = erwerber
        return when {
            verkaeufer == Ausland -> SpielEreignis.AuslandsHandel(
                spieler = kaeufer.zuSpielerId(),
                rohstoff = rohstoff.zuRohstoff(),
                menge = anzahl,
                preis = betrag.zuGeld(),
                art = AussenhandelsArt.IMPORT,
            )
            kaeufer == Ausland -> SpielEreignis.AuslandsHandel(
                spieler = verkaeufer.zuSpielerId(),
                rohstoff = rohstoff.zuRohstoff(),
                menge = anzahl,
                preis = betrag.zuGeld(),
                art = AussenhandelsArt.EXPORT,
            )
            else -> SpielEreignis.RohstoffHandel(
                kaeufer = kaeufer.zuSpielerId(),
                verkaeufer = verkaeufer.zuSpielerId(),
                rohstoff = rohstoff.zuRohstoff(),
                menge = anzahl,
                preis = betrag.zuGeld(),
            )
        }
    }

    private fun Anleihenhandel.zuEmissionsEreignis(): SpielEreignis.AnleiheEmittiert {
        val runde = aktuelleRundenDaten().index
        val emittent = anleihe.schuldiger.zuSpielerId()
        val id = AnleiheId(
            listOf(
                runde,
                anleihe.schuldiger.name,
                anleihe.sondervermögen.speichereString(),
                anleihe.unvermögen.speichereString(),
                anleihe.laufzeit,
            ).joinToString("#"),
        )
        return SpielEreignis.AnleiheEmittiert(
            anleihe = de.teutonstudio.zentralbank.fachlogik.modell.Anleihe(
                id = id,
                emittent = emittent,
                nennwert = anleihe.sondervermögen.zuGeld(),
                zinsBasispunkte = anleihe.erhalteZinssatz() * 100,
                laufzeitRunden = anleihe.laufzeit,
                zinsbetrag = anleihe.unvermögen.zuGeld(),
                emissionsRunde = runde,
                faelligkeitsRunde = anleihe.faelligkeitsrunde(runde),
            ),
            erwerber = erwerber.zuKontoId(),
            erloes = preis.zuGeld(),
        )
    }

    private fun Anleihenhandel.zuAnleihenHandelsEreignis(
        anzeige: AnleiheAnzeige,
    ): SpielEreignis {
        val id = AnleiheId(
            listOf(
                anzeige.emittiert,
                anzeige.schuldiger.name,
                anzeige.sondervermoegen.speichereString(),
                anzeige.unvermoegen.speichereString(),
                anzeige.laufzeit,
            ).joinToString("#"),
        )
        return if (erwerber.name == anzeige.schuldiger.name) {
            SpielEreignis.AnleiheFreiwilligZurueckgekauft(
                anleihe = id,
                emittent = anzeige.schuldiger.zuSpielerId(),
                preis = preis.zuGeld(),
            )
        } else {
            when (val kaeufer = erwerber.zuKontoId()) {
                is KontoId.Spieler -> SpielEreignis.AnleiheGekauft(
                    anleihe = id,
                    kaeufer = kaeufer.id,
                    verkaeufer = besitzer.zuKontoId(),
                    preis = preis.zuGeld(),
                )
                KontoId.Bank -> SpielEreignis.AnleiheVerkauft(
                    anleihe = id,
                    verkaeufer = besitzer.zuSpielerId(),
                    kaeufer = KontoId.Bank,
                    preis = preis.zuGeld(),
                )
                KontoId.Ausland -> error("Das Ausland handelt keine Anleihen.")
            }
        }
    }

    private fun JuristischePerson.zuSpielerId(): SpielerId {
        val zustand = requireNotNull(spielAblauf?.zustand) { "Kein Spiel geladen." }
        return zustand.spieler.firstOrNull { spieler -> spieler.name == name }?.id
            ?: error("Unbekannter Spieler: $name")
    }

    private fun JuristischePerson.zuKontoId(): KontoId = when (this) {
        Geschäftsbank -> KontoId.Bank
        Ausland -> KontoId.Ausland
        else -> KontoId.Spieler(zuSpielerId())
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
        fachEreignis: SpielEreignis,
        speicherdatum: () -> T,
        speichern: suspend (T) -> Long,
        fehlermeldung: String,
    ): Boolean {
        if (!pruefeFachEreignis(fachEreignis)) return false
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

        if (!uebernehmeFachEreignis(fachEreignis)) return false

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

    private fun pruefeFachEreignis(ereignis: SpielEreignis): Boolean {
        val zustand = spielAblauf?.zustand ?: return false
        return SpielRegelwerk.wendeAn(zustand, ereignis).fold(
            onSuccess = { true },
            onFailure = { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Handel wurde fachlich abgelehnt.")
                false
            },
        )
    }

    private fun uebernehmeFachEreignis(ereignis: SpielEreignis): Boolean {
        val ablauf = spielAblauf ?: return false
        return ablauf.ereignisAnwenden(ereignis).fold(
            onSuccess = { zustand ->
                aktualisiereSpielZustand(zustand)
                speichereAktuellenFachSpielstand()
                true
            },
            onFailure = { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Handel wurde fachlich abgelehnt.")
                false
            },
        )
    }

    private fun aktualisiereSpielZustand(zustand: SpielZustand) {
        aktuellesSpielOderNull?.aktualisiereAktivenSpieler(zustand.zugStatus?.spieler?.wert)
        _spielZustand.value = zustand
        _spielUebersicht.value = zustand.zuSpielUebersichtZustand()
    }

    private data class SpielstandSpeicherauftrag(
        val spielId: Long,
        val aenderungsNummer: Long,
        val spielstand: GespeichertesSpiel,
    )

    private fun aktuellerSpielstandSpeicherauftrag(): SpielstandSpeicherauftrag? {
        val ablauf = spielAblauf ?: return null
        val spielId = aktuelleDaten.first.spielID
        if (spielId < 0) return null
        return SpielstandSpeicherauftrag(
            spielId = spielId,
            aenderungsNummer = naechsteAblageAenderung.incrementAndGet(),
            spielstand = GespeichertesSpiel(
                id = spielId,
                startzustand = ablauf.startzustand,
                ereignisse = ablauf.ereignisVerlauf.angewandteEreignisse,
            ),
        )
    }

    private suspend fun speichereSpielstand(auftrag: SpielstandSpeicherauftrag) {
        datenbankBereit.await()
        val gespeichert = withContext(Dispatchers.IO) {
            ablageAendern(auftrag.spielId, auftrag.aenderungsNummer) {
                spielSpeichern(auftrag.spielstand)
            }
        }
        if (gespeichert) {
            val uebersicht = auftrag.spielstand.zuUebersicht()
            _spielstaende.update { spielstaende ->
                listOf(testSpielstandUebersicht) +
                    (spielstaende.filter { spielstand ->
                        spielstand.id >= 0 && spielstand.id != auftrag.spielId
                    } + uebersicht).sortedBy(SpielstandUebersicht::id)
            }
        }
    }

    private fun speichereAktuellenFachSpielstand() {
        val speicherauftrag = aktuellerSpielstandSpeicherauftrag() ?: return
        viewModelScope.launch {
            try {
                speichereSpielstand(speicherauftrag)
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
    ): Boolean = ablageSperre.withLock {
        val letzteNummer = letzteAblageAenderung[spielId] ?: Long.MIN_VALUE
        if (aenderungsNummer < letzteNummer) return@withLock false
        spielAblage.aenderung()
        letzteAblageAenderung[spielId] = aenderungsNummer
        true
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val datenbank = AppDatabase.erhalteDatenbank(application.applicationContext).first()
                tabellenSpeicher = ZentralbankSpeicher(datenbank)
                spielAblage = RaumSpielAblage(datenbank)
                datenbankBereit.complete(Unit)

                launch {
                    tabellenSpeicher.observeAlleNachSpiel().collect { spiele ->
                        _spielDatenListe.value = spiele
                    }
                }
                spielAblage.spielstaendeBeobachten().collect { spielstaende ->
                    _spielstaende.value = listOf(testSpielstandUebersicht) +
                        spielstaende.filterNot { spielstand -> spielstand.id == -1L }
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
                    tabellenSpeicher.insertSpielSatz(daten)
                }
                if (gameID <= 0) {
                    _spielFehler.emit("Spielstand konnte nicht angelegt werden.")
                    return@launch
                }

                val gespeicherteDaten = daten.first.copy(spielID = gameID) to daten.second
                setzeAktuellesSpiel(spiel, gespeicherteDaten)
                aktuellerSpielstandSpeicherauftrag()?.let { speicherauftrag ->
                    speichereSpielstand(speicherauftrag)
                }
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
            setzeAktuellesSpiel(
                spiel = testDaten.second.zuSpiel(
                    daten = testDaten.first,
                    karte = TestSpiel.karte,
                ),
                daten = testDaten,
            )
            return
        }
        val gespeichertesSpiel = spielAblage.spielLaden(id)
            ?: error("Spielstand $id wurde nicht gefunden.")
        val wirtschaftsdaten = _spielDatenListe.value.entries
            .firstOrNull { (spiel, _) -> spiel.spielID == id }
            ?.let { (spiel, daten) -> spiel to daten }
            ?: if (::aktuelleDaten.isInitialized && aktuelleDaten.first.spielID == id) {
                aktuelleDaten
            } else {
                null
            }
            ?: error(
                "Spielstand $id besitzt keine Wirtschaftsdaten für die Oberfläche.",
            )
        val (spielDaten, tabellenDaten) = wirtschaftsdaten
        setzeAktuellesSpiel(
            spiel = tabellenDaten.zuSpiel(
                daten = spielDaten,
                karte = gespeichertesSpiel.startzustand.karte,
            ),
            daten = spielDaten to tabellenDaten,
            gespeichertesSpiel = gespeichertesSpiel,
        )
    }

    fun kriegErklaeren(aggressor: String, verteidiger: String) {
        val zustand = spielAblauf?.zustand
        val aggressorId = zustand?.spieler?.firstOrNull { it.name == aggressor }?.id
        val verteidigerId = zustand?.spieler?.firstOrNull { it.name == verteidiger }?.id
        if (aggressorId == null || verteidigerId == null) {
            _spielFehler.tryEmit("Die Kriegsparteien konnten nicht zugeordnet werden.")
            return
        }
        ereignisAnwenden(SpielEreignis.KriegErklaert(aggressorId, verteidigerId))
    }

    fun friedenSchliessen(spielerA: String, spielerB: String) {
        val zustand = spielAblauf?.zustand
        val spielerAId = zustand?.spieler?.firstOrNull { it.name == spielerA }?.id
        val spielerBId = zustand?.spieler?.firstOrNull { it.name == spielerB }?.id
        if (spielerAId == null || spielerBId == null) {
            _spielFehler.tryEmit("Die Friedensparteien konnten nicht zugeordnet werden.")
            return
        }
        ereignisAnwenden(SpielEreignis.KriegBeendet(spielerAId, spielerBId))
    }
}
