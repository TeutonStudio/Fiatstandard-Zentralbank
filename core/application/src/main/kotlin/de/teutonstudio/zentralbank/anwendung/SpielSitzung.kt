package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.ablauf.EreignisVerlauf
import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.SpielSchrittErgebnis
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Verwaltet genau eine geladene, UI-unabhängige Spielsitzung. */
class SpielSitzung(
    startzustand: SpielZustand,
    ereignisse: List<SpielEreignis> = emptyList(),
    private val engine: SpielEngine = StandardSpielEngine(),
) {
    private val ablauf = SpielAblauf(startzustand, ereignisse)
    private val _zustand = MutableStateFlow(ablauf.zustand)
    private val _anwendungsEreignisse = MutableSharedFlow<SpielAnwendungsEreignis>(
        extraBufferCapacity = 16,
    )

    val zustandsFluss: StateFlow<SpielZustand> = _zustand.asStateFlow()
    val anwendungsEreignisse: SharedFlow<SpielAnwendungsEreignis> =
        _anwendungsEreignisse.asSharedFlow()

    val startzustand: SpielZustand
        get() = ablauf.startzustand

    val zustand: SpielZustand
        get() = ablauf.zustand

    val ereignisVerlauf: EreignisVerlauf
        get() = ablauf.ereignisVerlauf

    val fachEreignisse: List<SpielEreignis>
        get() = ablauf.ereignisVerlauf.angewandteEreignisse

    @Synchronized
    fun aktionAnwenden(aktion: SpielAktion): Result<SpielSchrittErgebnis> =
        aktionenAtomarAnwenden(listOf(aktion))

    @Synchronized
    fun aktionenAtomarAnwenden(
        aktionen: List<SpielAktion>,
    ): Result<SpielSchrittErgebnis> = runCatching {
        require(aktionen.isNotEmpty()) { "Mindestens eine Aktion ist erforderlich." }
        var pruefzustand = ablauf.zustand
        val ereignisse = buildList {
            aktionen.forEach { aktion ->
                val schritt = engine.anwenden(pruefzustand, aktion).getOrThrow()
                pruefzustand = schritt.zustand
                addAll(schritt.ereignisse)
            }
        }
        ereignisse.forEach { ereignis -> ablauf.ereignisAnwenden(ereignis).getOrThrow() }
        check(ablauf.zustand == pruefzustand) {
            "Engine-Ergebnis und Ereignisrekonstruktion stimmen nicht überein."
        }
        _zustand.value = ablauf.zustand
        val ergebnis = SpielSchrittErgebnis(ablauf.zustand, ereignisse)
        _anwendungsEreignisse.tryEmit(SpielAnwendungsEreignis.AktionenAkzeptiert(ergebnis))
        ergebnis
    }.onFailure { fehler ->
        _anwendungsEreignisse.tryEmit(
            SpielAnwendungsEreignis.AktionAbgelehnt(
                fehler.message ?: "Die Spielaktion wurde abgelehnt.",
            ),
        )
    }

    /** Übergangsbrücke für Android-Abläufe, für die noch keine [SpielAktion] existiert. */
    @Synchronized
    fun legacyEreignisseAtomarAnwenden(
        ereignisse: List<SpielEreignis>,
    ): Result<SpielSchrittErgebnis> = runCatching {
        require(ereignisse.isNotEmpty()) { "Mindestens ein Ereignis ist erforderlich." }
        val pruefablauf = SpielAblauf(ablauf.zustand)
        ereignisse.forEach { ereignis -> pruefablauf.ereignisAnwenden(ereignis).getOrThrow() }
        ereignisse.forEach { ereignis -> ablauf.ereignisAnwenden(ereignis).getOrThrow() }
        _zustand.value = ablauf.zustand
        SpielSchrittErgebnis(ablauf.zustand, ereignisse)
    }

    /** Einzelereignis-Brücke für noch nicht auf [SpielAktion] umgestellte Android-Aufrufer. */
    @Synchronized
    fun ereignisAnwenden(ereignis: SpielEreignis): Result<SpielZustand> =
        legacyEreignisseAtomarAnwenden(listOf(ereignis)).map { it.zustand }

    @Synchronized
    fun rueckgaengig(): SpielZustand = ablauf.rueckgaengig().also { _zustand.value = it }

    @Synchronized
    fun wiederholen(): Result<SpielZustand> = ablauf.wiederholen().onSuccess {
        _zustand.value = it
    }

    fun erlaubteAktionen(spieler: SpielerId): List<SpielAktion> =
        engine.erlaubteAktionen(ablauf.zustand, spieler)

    fun alsGespeichertesSpiel(id: Long, seed: Long? = null): GespeichertesSpiel =
        GespeichertesSpiel(
            id = id,
            startzustand = ablauf.startzustand,
            ereignisse = ablauf.ereignisVerlauf.angewandteEreignisse,
            seed = seed,
        )
}

sealed interface SpielAnwendungsEreignis {
    data class AktionenAkzeptiert(val ergebnis: SpielSchrittErgebnis) : SpielAnwendungsEreignis
    data class AktionAbgelehnt(val meldung: String) : SpielAnwendungsEreignis
}
