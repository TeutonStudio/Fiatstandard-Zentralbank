package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.BeobachtungsAuswertung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielErgebnis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.auswertung.MarktAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZahlungsfaehigkeitsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.ZahlungsfaehigkeitsPlan

interface TrainingsUmgebung {
    fun reset(szenario: TrainingsSzenario, seed: Long): Entscheidungspunkt

    fun step(aktion: SpielAktion): TrainingsUebergang
}

data class Entscheidungspunkt(
    val spieler: SpielerId,
    val beobachtung: SpielBeobachtung,
    val aktionsRaum: de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsRaum,
    val zahlungsfaehigkeitsPlan: ZahlungsfaehigkeitsPlan? = null,
)

data class TrainingsUebergang(
    val naechsterPunkt: Entscheidungspunkt?,
    val belohnungen: Map<SpielerId, Float>,
    val terminated: Boolean,
    val truncated: Boolean,
    val ergebnis: SpielErgebnis?,
    val ereignisse: List<SpielEreignis>,
)

class StandardTrainingsUmgebung(
    private val engine: SpielEngine = StandardSpielEngine(),
    private val belohnungsModell: BelohnungsModell = PotentialBelohnungsModell(),
    private val maximaleEntscheidungen: Int = 10_000,
) : TrainingsUmgebung {
    init {
        require(maximaleEntscheidungen > 0) { "Das Entscheidungslimit muss positiv sein." }
    }

    private var ablauf: SpielAblauf? = null
    private var entscheidungen: Int = 0
    private var technischBeendet: Boolean = false
    private var szenarioId: String? = null
    private var startSeed: Long? = null
    private var aktuellerPunkt: Entscheidungspunkt? = null
    private var stagnierendeEntscheidungen: Int = 0
    private val letzteAktionenPuffer = ArrayDeque<SpielAktion>()

    val startzustand: SpielZustand
        get() = requireNotNull(ablauf).startzustand
    val zustand: SpielZustand
        get() = requireNotNull(ablauf).zustand
    val ereignisse: List<SpielEreignis>
        get() = requireNotNull(ablauf).ereignisVerlauf.angewandteEreignisse
    val aktuellesSzenarioId: String
        get() = requireNotNull(szenarioId)
    val seed: Long
        get() = requireNotNull(startSeed)
    val entscheidungenOhneMarktwertAenderung: Int get() = stagnierendeEntscheidungen
    val letzteAktionen: List<SpielAktion> get() = letzteAktionenPuffer.toList()

    override fun reset(szenario: TrainingsSzenario, seed: Long): Entscheidungspunkt {
        val neuerAblauf = SpielAblauf(szenario.startzustand(seed))
        ablauf = neuerAblauf
        entscheidungen = 0
        technischBeendet = false
        szenarioId = szenario.id
        startSeed = seed
        val zug = neuerAblauf.zustand.zugStatus
        if (
            neuerAblauf.zustand.spielabschnitt !=
                de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt.RUNDE_NULL &&
            zug != null && !zug.prozug.begonnen
        ) {
            val automatisch = engine.anwenden(
                neuerAblauf.zustand,
                SpielAktion.ProzugBeginnen(zug.zugId),
            ).getOrThrow()
            automatisch.ereignisse.forEach { neuerAblauf.ereignisAnwenden(it).getOrThrow() }
        }
        val punkt = requireNotNull(entscheidungspunkt(neuerAblauf.zustand)) {
            "Das Szenario besitzt beim Reset keinen Entscheidungspunkt."
        }
        aktuellerPunkt = punkt
        stagnierendeEntscheidungen = 0
        letzteAktionenPuffer.clear()
        return punkt
    }

    override fun step(aktion: SpielAktion): TrainingsUebergang {
        val spielAblauf = requireNotNull(ablauf) { "Die Umgebung wurde noch nicht zurückgesetzt." }
        check(!technischBeendet && spielAblauf.zustand.ergebnis == null) {
            "Nach Ende oder Truncation ist kein weiterer Schritt zulässig."
        }
        val vorher = spielAblauf.zustand
        val punkt = requireNotNull(aktuellerPunkt) {
            "Der nichtterminale Zustand besitzt keinen Entscheidungspunkt."
        }
        require(aktion in punkt.aktionsRaum.aktionen) {
            "Die Aktion gehört nicht zum aktuellen Aktionsraum."
        }
        val schritt = engine.anwenden(vorher, aktion).getOrThrow()
        schritt.ereignisse.forEach { spielAblauf.ereignisAnwenden(it).getOrThrow() }
        check(spielAblauf.zustand == schritt.zustand) {
            "Engine-Schritt und Ereignis-Replay sind auseinander gelaufen."
        }
        entscheidungen += 1
        val marktwerteVorher = vorher.spieler.associate { spieler ->
            spieler.id to MarktAuswertung.spielerMarktwert(vorher, spieler.id)
        }
        val marktwerteNachher = spielAblauf.zustand.spieler.associate { spieler ->
            spieler.id to MarktAuswertung.spielerMarktwert(spielAblauf.zustand, spieler.id)
        }
        stagnierendeEntscheidungen = if (marktwerteVorher == marktwerteNachher) {
            stagnierendeEntscheidungen + 1
        } else {
            0
        }
        letzteAktionenPuffer.addLast(aktion)
        while (letzteAktionenPuffer.size > 100) letzteAktionenPuffer.removeFirst()
        val terminated = spielAblauf.zustand.ergebnis != null
        val truncated = !terminated && stagnierendeEntscheidungen >= maximaleEntscheidungen
        technischBeendet = truncated
        val naechsterPunkt = if (terminated || truncated) {
            null
        } else {
            requireNotNull(entscheidungspunkt(spielAblauf.zustand)) {
                "Nichtterminaler Zustand besitzt keinen erlaubten Folgepunkt."
            }
        }
        aktuellerPunkt = naechsterPunkt
        return TrainingsUebergang(
            naechsterPunkt = naechsterPunkt,
            belohnungen = belohnungsModell.berechne(
                vorher,
                spielAblauf.zustand,
                aktion,
                spielAblauf.zustand.ergebnis,
            ),
            terminated = terminated,
            truncated = truncated,
            ergebnis = spielAblauf.zustand.ergebnis,
            ereignisse = schritt.ereignisse,
        )
    }

    private fun entscheidungspunkt(zustand: SpielZustand): Entscheidungspunkt? {
        if (zustand.ergebnis != null) return null
        val spieler = zustand.aktiverSpieler ?: return null
        val raum = AktionsAuswertung.erlaubteAktionen(zustand, spieler)
        check(raum.aktionen.isNotEmpty()) {
            "Nichtterminaler Zustand hat einen leeren Aktionsraum für ${spieler.wert}."
        }
        return Entscheidungspunkt(
            spieler = spieler,
            beobachtung = BeobachtungsAuswertung.fuerSpieler(zustand, spieler),
            aktionsRaum = raum,
            zahlungsfaehigkeitsPlan = zustand.zugStatus
                ?.takeIf { it.phase == de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase.Prozug }
                ?.let { ZahlungsfaehigkeitsAuswertung.plan(zustand, spieler) },
        )
    }
}
