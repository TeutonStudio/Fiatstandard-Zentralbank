/* LEGACY-KOORDINATOR: Nur Übergangsbrücke zur SpielSitzung; keine neuen Regeln ergänzen. */
package de.teutonstudio.zentralbank.datenbank

import android.app.Application
import android.util.Log
import de.teutonstudio.zentralbank.BuildConfig

import de.teutonstudio.zentralbank.daten.RoomSpielPersistenz
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpiel
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoff
import de.teutonstudio.zentralbank.daten.zuordnung.zuRohstoffe
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpielZustand
import de.teutonstudio.zentralbank.daten.zuordnung.zuGeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.anwendung.SpielSitzung
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsMenueAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsMenueBereich as DomainAktionsBereich
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsMenueEintrag as DomainAktionsEintrag
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsMenueAktion
import de.teutonstudio.zentralbank.fachlogik.modell.pruefePasswort
import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt
import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import de.teutonstudio.zentralbank.schnittstelle.domain.SpielUebersichtZustand
import de.teutonstudio.zentralbank.schnittstelle.domain.zuSpielUebersichtZustand
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AktionsBestaetigung
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AktionsBereich
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AktionsBereichEintrag
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AktionsMenueNavigationZiel
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AktionsMenueZustand
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AngezeigteAktion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private const val STANDARD_AUSLANDS_IMPORTFAKTOR = 4f / 3f


class LegacySpielKoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
) {

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
    private val _aktionsMenueZustand = MutableStateFlow<AktionsMenueZustand?>(null)
    private val _aktionsMenueNavigation = MutableStateFlow<AktionsMenueNavigationZiel?>(null)
    private var aktionsMenueJob: Job? = null
    private var aktionsMenueGeneration = 0L
    private var aktionsMenueEintraege: Map<String, DomainAktionsEintrag> = emptyMap()
    private var aktionsMenueBewegungsZiele = emptyMap<String, de.teutonstudio.zentralbank.fachlogik.modell.KartenKante>()
    private var ausstehendeAktionsMenueAktion: String? = null
    private var spielAblauf: SpielSitzung? = null

    val spielstaende: StateFlow<List<SpielstandUebersicht>> = _spielstaende.asStateFlow()
    val spielZustand: StateFlow<SpielZustand?> = _spielZustand.asStateFlow()
    val spielUebersicht: StateFlow<SpielUebersichtZustand?> = _spielUebersicht.asStateFlow()
    val rundenwechselAnzeige: StateFlow<SpielZustand?> = _rundenwechselAnzeige.asStateFlow()
    val spielFehler: SharedFlow<String> = _spielFehler.asSharedFlow()
    val aktionsMenueZustand: StateFlow<AktionsMenueZustand?> = _aktionsMenueZustand.asStateFlow()
    val aktionsMenueNavigation: StateFlow<AktionsMenueNavigationZiel?> = _aktionsMenueNavigation.asStateFlow()

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
            SpielSitzung(gespeichert.startzustand, gespeichert.ereignisse)
        } ?: SpielSitzung(spiel.zuSpielZustand())
        spielAblauf = ablauf
        _rundenwechselAnzeige.value = null
        aktualisiereSpielZustand(ablauf.zustand)
        starteProzugFallsNoetig()
    }

    fun aktionAnwenden(aktion: SpielAktion) = wendeAktionAn(aktion)

    /**
     * Übergang für die noch ereignisbasierten Karten-Callbacks. Das von der Darstellung
     * beschriebene Vorhaben wird vor der Zustandsänderung in eine Spieleraktion übersetzt.
     */
    fun kartenEreignisAnwenden(ereignis: SpielEreignis) {
        runCatching { ereignis.alsSpielerAktion() }
            .onSuccess(::wendeAktionAn)
            .onFailure { fehler ->
                _spielFehler.tryEmit(
                    fehler.message ?: "Die Kartenaktion konnte nicht zugeordnet werden.",
                )
            }
    }

    fun meldeSpielFehler(meldung: String) {
        _spielFehler.tryEmit(meldung)
    }

    fun passwortGeschuetzteSpieler(spielerNamen: Collection<String>): List<String> {
        val spielerNachName = spielAblauf?.zustand?.spieler.orEmpty().associateBy { it.name }
        return spielerNamen
            .distinct()
            .filter { name -> spielerNachName[name]?.passwortHash?.isNotBlank() == true }
    }

    fun pruefeSpielerPasswoerter(passwoerter: Map<String, String>): Boolean {
        val spielerNachName = spielAblauf?.zustand?.spieler.orEmpty().associateBy { it.name }
        val gueltig = passwoerter.isNotEmpty() && passwoerter.all { (name, passwort) ->
            spielerNachName[name]?.pruefePasswort(passwort) == true
        }
        if (!gueltig) {
            _spielFehler.tryEmit("Die Willenserklärung wurde durch ein falsches Passwort abgelehnt.")
        }
        return gueltig
    }

    fun spielstandBeenden(nachBeenden: () -> Unit) {
        val speicherauftrag = aktuellerSpielstandSpeicherauftrag()
        if (speicherauftrag == null) {
            nachBeenden()
            return
        }
        scope.launch {
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
            _spielFehler.tryEmit("Der Plan enthält keine Vorhaben.")
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
        val fachAktionen = runCatching {
            fachEreignisse.map { ereignis -> ereignis.alsSpielerAktion() }
        }.getOrElse { fehler ->
            _spielFehler.tryEmit(
                fehler.message ?: "Bau und Auslandseinkauf enthalten keine Spieleraktion.",
            )
            return false
        }
        SpielSitzung(ausgangszustand)
            .aktionenAtomarAnwenden(fachAktionen)
            .exceptionOrNull()
            ?.let { fehler ->
                _spielFehler.tryEmit(
                    fehler.message ?: "Bau und Auslandseinkauf wurden fachlich abgelehnt."
                )
                return false
            }

        handelsvorgaenge.forEach { handel ->
            if (!erfasseRohstoffhandel(handel)) return false
        }
        return bauplanAnwenden(bauEreignisse)
    }

    fun bauplanAnwenden(bauEreignisse: List<SpielEreignis>): Boolean {
        if (bauEreignisse.isEmpty()) {
            _spielFehler.tryEmit("Der Plan enthält keine Vorhaben.")
            return false
        }
        val ablauf = spielAblauf ?: run {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return false
        }
        val vorher = ablauf.zustand
        val aktionen = runCatching { bauEreignisse.map { it.alsSpielerAktion() } }
            .getOrElse { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Der Bauplan enthält keine Spieleraktion.")
                return false
            }
        val ergebnis = ablauf.aktionenAtomarAnwenden(aktionen)
        if (ergebnis.isFailure) {
            _spielFehler.tryEmit(
                ergebnis.exceptionOrNull()?.message ?: "Der Bauplan wurde fachlich abgelehnt."
            )
            return false
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
        wendeAktionAn(SpielAktion.ProzugAbschliessen(zug.zugId))
    }

    fun verarbeitungAusfuehren(feld: KartenFeld, laeufe: Int) {
        val zugId = spielAblauf?.zustand?.zugStatus?.zugId ?: return
        wendeAktionAn(SpielAktion.VerarbeitungAusfuehren(zugId, feld, laeufe))
    }

    fun verwaltungsstandortVersorgen(ecke: KartenEcke) {
        val zugId = spielAblauf?.zustand?.zugStatus?.zugId ?: return
        wendeAktionAn(SpielAktion.VerwaltungsstandortVersorgen(zugId, ecke))
    }

    fun verbindlichkeitBegleichen(verbindlichkeit: VerbindlichkeitId) {
        val zugId = spielAblauf?.zustand?.zugStatus?.zugId ?: return
        wendeAktionAn(SpielAktion.VerbindlichkeitBegleichen(zugId, verbindlichkeit))
    }

    fun beendeZug() {
        wendeAktionAn(SpielAktion.ZugBeenden)
    }

    private fun wendeAktionAn(aktion: SpielAktion): Boolean {
        val sitzung = spielAblauf
        if (sitzung == null) {
            _spielFehler.tryEmit("Kein Spiel geladen.")
            return false
        }
        val vorher = sitzung.zustand
        return sitzung.aktionAnwenden(aktion).fold(
            onSuccess = { ergebnis ->
                uebernehmeEreignisErgebnis(vorher, ergebnis.zustand)
                true
            },
            onFailure = { fehler ->
                _spielFehler.tryEmit(fehler.message ?: "Spielaktion wurde abgelehnt.")
                false
            },
        )
    }

    private fun uebernehmeEreignisErgebnis(vorher: SpielZustand, nachher: SpielZustand) {
        if (nachher.rundenzähler > vorher.rundenzähler) {
            aktualisiereSpielZustand(nachher)
            _rundenwechselAnzeige.value = nachher
        } else {
            aktualisiereSpielZustand(nachher)
            starteProzugFallsNoetig()
        }
        speichereAktuellenFachSpielstand()
    }

    private fun starteProzugFallsNoetig() {
        if (_rundenwechselAnzeige.value != null) return
        val ablauf = spielAblauf ?: return
        val zug = ablauf.zustand.zugStatus ?: return
        if (ablauf.zustand.spielabschnitt != de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt.REGULAER ||
            zug.phase != ZugPhase.Prozug || zug.prozug.begonnen
        ) return
        ablauf.aktionAnwenden(SpielAktion.ProzugBeginnen(zug.zugId))
            .onSuccess { ergebnis ->
                aktualisiereSpielZustand(ergebnis.zustand)
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

        val ergebnis = ablauf.aktionAnwenden(
            SpielAktion.WarenkorbAendern(
                warenkorb = warenkorb.mapKeys { (rohstoff, _) -> rohstoff.zuRohstoff() },
            ),
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

        aktualisiereSpielZustand(ergebnis.getOrThrow().zustand)
        speichereAktuellenFachSpielstand()

        if (neueSpielDaten.spielID == (-1).toLong()) return

        _spielDatenListe.update { spiele ->
            (spiele - bisherigeDaten.first) + (neueSpielDaten to bisherigeDaten.second)
        }

        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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

        scope.launch(Dispatchers.IO) {
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
        return SpielSitzung(zustand).ereignisAnwenden(ereignis).fold(
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
        if (_aktionsMenueZustand.value != null) {
            aktionsMenueOeffnen()
        }
    }

    private fun SpielEreignis.alsSpielerAktion(): SpielAktion = when (this) {
        is SpielEreignis.AuslandsHandel -> SpielAktion.MitAuslandHandeln(
            spieler,
            rohstoff,
            menge,
            preis,
            art,
        )
        is SpielEreignis.HauptbahnhofPlatziert ->
            SpielAktion.HauptbahnhofPlatzieren(spieler, ecke)
        is SpielEreignis.EckGebaeudeGebaut -> SpielAktion.EckGebaeudeBauen(spieler, ecke, typ)
        is SpielEreignis.EckGebaeudeAufgewertet ->
            SpielAktion.EckGebaeudeAufwerten(spieler, ecke, zu)
        is SpielEreignis.SchieneGebaut -> SpielAktion.SchieneBauen(spieler, kante)
        is SpielEreignis.NeutraleAnlageErrichtet ->
            SpielAktion.AnlageErrichten(errichter, feld, anlage)
        is SpielEreignis.KartenBelegungEntfernt ->
            SpielAktion.BelegungAbreissen(spieler, ort)
        is SpielEreignis.SeewegEingerichtet ->
            SpielAktion.SeewegEinrichten(spieler, hafenA, hafenB, richtung)
        is SpielEreignis.SeewegEntfernt -> SpielAktion.SeewegEntfernen(spieler, id)
        is SpielEreignis.KriegsEinheitGebaut ->
            SpielAktion.KriegsEinheitBauen(spieler, typ, kante)
        is SpielEreignis.KriegsEinheitEingesetzt ->
            SpielAktion.KriegsEinheitEinsetzen(spieler, gegner, typ, ort)
        is SpielEreignis.KriegsEinheitBewegt -> {
            require(weg.size == 1) { "Die UI übergibt Bewegungen schrittweise." }
            SpielAktion.KriegsEinheitBewegen(spieler, id, weg.single())
        }
        else -> error(
            "${this::class.simpleName} ist keine direkt auswählbare Bau- oder Handelsaktion.",
        )
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
        scope.launch {
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
        scope.launch(Dispatchers.IO) {
            try {
                val persistenz = RoomSpielPersistenz.oeffnen(application.applicationContext)
                tabellenSpeicher = persistenz.legacyTabellenSpeicher
                spielAblage = persistenz.spielAblage
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
        scope.launch {
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
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
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
        wendeAktionAn(SpielAktion.KriegErklaeren(aggressorId, verteidigerId))
    }

    fun friedenSchliessen(spielerA: String, spielerB: String) {
        val zustand = spielAblauf?.zustand
        val spielerAId = zustand?.spieler?.firstOrNull { it.name == spielerA }?.id
        val spielerBId = zustand?.spieler?.firstOrNull { it.name == spielerB }?.id
        if (spielerAId == null || spielerBId == null) {
            _spielFehler.tryEmit("Die Friedensparteien konnten nicht zugeordnet werden.")
            return
        }
        val krieg = zustand.konflikte.firstOrNull { it.betrifft(spielerAId, spielerBId) }
        if (krieg == null) {
            _spielFehler.tryEmit("Zwischen den gewählten Spielern besteht kein Krieg.")
            return
        }
        wendeAktionAn(
            SpielAktion.UnabhaengigenFriedenSchliessen(spielerAId, krieg.id, spielerBId),
        )
    }

    fun aktionsMenueOeffnen() {
        aktionsMenueJob?.cancel()
        val zustand = spielAblauf?.zustand ?: return
        val beginn = System.nanoTime()
        val uebersicht = AktionsMenueAuswertung.uebersicht(zustand) ?: return
        val spieler = zustand.spieler.firstOrNull { it.id == uebersicht.spieler } ?: return
        aktionsMenueGeneration++
        aktionsMenueEintraege = emptyMap()
        aktionsMenueBewegungsZiele = emptyMap()
        ausstehendeAktionsMenueAktion = null
        _aktionsMenueZustand.value = AktionsMenueZustand(
            aktiverSpieler = spieler.name,
            bereiche = uebersicht.bereiche.map { status ->
                AktionsBereichEintrag(
                    bereich = status.bereich.zuUiBereich(),
                    beschriftung = status.bereich.anzeigeName(),
                    verfuegbar = status.verfuegbar,
                    anzahl = status.anzahl,
                )
            },
            kiStil = spieler.spielstil,
        )
        protokolliereAktionsMenue("Übersicht", beginn, uebersicht.bereiche.size, 0)
    }

    fun aktionsMenueSchliessen() {
        aktionsMenueGeneration++
        aktionsMenueJob?.cancel()
        aktionsMenueJob = null
        aktionsMenueEintraege = emptyMap()
        aktionsMenueBewegungsZiele = emptyMap()
        ausstehendeAktionsMenueAktion = null
        _aktionsMenueZustand.value = null
    }

    fun aktionsMenueNavigationVerbrauchen() { _aktionsMenueNavigation.value = null }

    fun aktionsBereichAuswaehlen(bereich: AktionsBereich) {
        when (bereich) {
            AktionsBereich.HANDEL -> {
                aktionsMenueSchliessen()
                _aktionsMenueNavigation.value = AktionsMenueNavigationZiel.HANDEL
                return
            }
            AktionsBereich.ANLEIHEN -> {
                aktionsMenueSchliessen()
                _aktionsMenueNavigation.value = AktionsMenueNavigationZiel.ANLEIHEN
                return
            }
            AktionsBereich.KI -> {
                _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(ausgewaehlterBereich = bereich)
                return
            }
            else -> ladeAktionsBereich(bereich)
        }
    }

    fun aktionsEintragAuswaehlen(id: String) {
        val eintrag = aktionsMenueEintraege[id] ?: return
        if (eintrag.aktion == AktionsMenueAktion.TRUPPENSTAPEL_AUSWAEHLEN) {
            ladeBewegungsZiele(eintrag)
            return
        }
        ausstehendeAktionsMenueAktion = id
        _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
            bestaetigung = AktionsBestaetigung("Aktion bestätigen", eintrag.titel),
        )
    }

    fun aktionsMenueBestaetigen() {
        val id = ausstehendeAktionsMenueAktion ?: return
        val eintrag = aktionsMenueEintraege[id] ?: return
        val aktion = AktionsMenueAuswertung.konkreteAktion(
            spielAblauf?.zustand ?: return,
            eintrag,
            aktionsMenueBewegungsZiele[id],
        ) ?: return
        ausstehendeAktionsMenueAktion = null
        if (!wendeAktionAn(aktion)) {
            _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
                bestaetigung = null,
                fehler = "Die Aktion wurde vom aktuellen Spielzustand abgelehnt.",
            )
        }
    }

    fun aktionsMenueAbbrechen() {
        ausstehendeAktionsMenueAktion = null
        _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(bestaetigung = null)
    }

    fun aktionsMenueErneutVersuchen() {
        _aktionsMenueZustand.value?.ausgewaehlterBereich?.let(::ladeAktionsBereich)
    }

    fun aktionsMenueKiStilSetzen(stil: SpielerStil) {
        val spieler = spielAblauf?.zustand?.aktiverSpieler ?: return
        if (!wendeAktionAn(SpielAktion.SpielerStilSetzen(spieler, stil))) {
            _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
                fehler = "Der KI-Stil konnte nicht gespeichert werden.",
            )
        }
    }

    private fun ladeAktionsBereich(bereich: AktionsBereich) {
        val zustand = spielAblauf?.zustand ?: return
        val generation = ++aktionsMenueGeneration
        val beginn = System.nanoTime()
        aktionsMenueJob?.cancel()
        _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
            ausgewaehlterBereich = bereich,
            eintraege = emptyList(),
            wirdGeladen = true,
            fehler = null,
            bestaetigung = null,
        )
        aktionsMenueJob = scope.launch(Dispatchers.Default) {
            runCatching { AktionsMenueAuswertung.eintraege(zustand, bereich.zuDomainBereich()) }
                .onSuccess { eintraege ->
                    if (generation == aktionsMenueGeneration && spielAblauf?.zustand === zustand) {
                        aktionsMenueEintraege = eintraege.associateBy { it.id }
                        _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
                            eintraege = eintraege.map { AngezeigteAktion(it.id, it.titel) },
                            wirdGeladen = false,
                        )
                        protokolliereAktionsMenue("Bereich ${bereich.name}", beginn, eintraege.size, 0)
                    }
                }.onFailure { fehler ->
                    if (generation == aktionsMenueGeneration) _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
                        wirdGeladen = false,
                        fehler = fehler.message ?: "Der Aktionsbereich konnte nicht geladen werden.",
                    )
                }
        }
    }

    private fun ladeBewegungsZiele(stapel: DomainAktionsEintrag) {
        val zustand = spielAblauf?.zustand ?: return
        val generation = ++aktionsMenueGeneration
        val beginn = System.nanoTime()
        aktionsMenueJob?.cancel()
        _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(wirdGeladen = true, fehler = null)
        aktionsMenueJob = scope.launch(Dispatchers.Default) {
            runCatching { AktionsMenueAuswertung.bewegungsZiele(zustand, stapel.einheitenIds.toSet()) }
                .onSuccess { ziele ->
                    if (generation == aktionsMenueGeneration && spielAblauf?.zustand === zustand) {
                        val eintraege = ziele.mapIndexed { index, ziel ->
                            val id = "bewegung:${stapel.id}:$index"
                            id to stapel.copy(id = id, aktion = AktionsMenueAktion.TRUPPEN_BEWEGEN, titel = "Nach $ziel bewegen")
                        }
                        aktionsMenueEintraege = eintraege.toMap()
                        aktionsMenueBewegungsZiele = eintraege.mapIndexed { index, (id, _) -> id to ziele[index] }.toMap()
                        _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
                            eintraege = eintraege.map { (id, eintrag) -> AngezeigteAktion(id, eintrag.titel) },
                            wirdGeladen = false,
                        )
                        protokolliereAktionsMenue("Truppenziele", beginn, ziele.size, 0)
                    }
                }.onFailure { fehler ->
                    if (generation == aktionsMenueGeneration) _aktionsMenueZustand.value = _aktionsMenueZustand.value?.copy(
                        wirdGeladen = false,
                        fehler = fehler.message ?: "Die Bewegungsziele konnten nicht geladen werden.",
                    )
                }
        }
    }

    private fun DomainAktionsBereich.zuUiBereich(): AktionsBereich = AktionsBereich.valueOf(name)
    private fun AktionsBereich.zuDomainBereich(): DomainAktionsBereich = DomainAktionsBereich.valueOf(name)
    private fun DomainAktionsBereich.anzeigeName(): String = when (this) {
        DomainAktionsBereich.KONFLIKT -> "Konflikt"
        DomainAktionsBereich.DIPLOMATIE -> "Diplomatie"
        DomainAktionsBereich.TRUPPEN -> "Truppen"
        DomainAktionsBereich.HANDEL -> "Handel"
        DomainAktionsBereich.ANLEIHEN -> "Anleihen"
        DomainAktionsBereich.ZUG -> "Zug"
        DomainAktionsBereich.KI -> "KI"
    }

    private fun protokolliereAktionsMenue(
        abschnitt: String,
        beginnNanos: Long,
        kandidaten: Int,
        vollstaendigGeprueft: Int,
    ) {
        if (BuildConfig.DEBUG) {
            val dauerMs = (System.nanoTime() - beginnNanos) / 1_000_000.0
            Log.d(
                "AktionsMenue",
                "$abschnitt: ${"%.2f".format(java.util.Locale.ROOT, dauerMs)} ms, " +
                    "Kandidaten=$kandidaten, vollständigGeprüft=$vollstaendigGeprueft",
            )
        }
    }
}
