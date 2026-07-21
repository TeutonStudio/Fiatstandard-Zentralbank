package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.SpielSchrittErgebnis
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Orchestriert Laden, serverseitige Validierung und Speichern ohne Transportwissen. */
class SpielDienst(
    private val spielAblage: SpielAblage,
    private val engine: SpielEngine = StandardSpielEngine(),
) {
    private val spielSperren = ConcurrentHashMap<Long, Mutex>()

    suspend fun spielErstellen(
        id: Long,
        startzustand: SpielZustand,
        seed: Long? = null,
    ): GespeichertesSpiel = mitSpielsperre(id) {
        require(id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        require(spielAblage.spielLaden(id) == null) { "Spielstand $id existiert bereits." }
        GespeichertesSpiel(id = id, startzustand = startzustand, seed = seed).also {
            spielAblage.spielSpeichern(it)
        }
    }

    suspend fun spielLaden(id: Long): GespeichertesSpiel? = spielAblage.spielLaden(id)

    suspend fun zustandLaden(id: Long): SpielZustand? = spielAblage.spielLaden(id)?.aktuellerZustand()

    suspend fun erlaubteAktionen(id: Long, spieler: SpielerId): List<SpielAktion> =
        spielAblage.spielLaden(id)?.let { gespeichert ->
            engine.erlaubteAktionen(gespeichert.aktuellerZustand(), spieler)
        }.orEmpty()

    suspend fun aktionAusfuehren(
        id: Long,
        aktion: SpielAktion,
    ): Result<SpielSchrittErgebnis> = mitSpielsperre(id) {
        runCatching {
            val gespeichert = requireNotNull(spielAblage.spielLaden(id)) {
                "Spielstand $id wurde nicht gefunden."
            }
            val sitzung = SpielSitzung(
                startzustand = gespeichert.startzustand,
                ereignisse = gespeichert.ereignisse,
                engine = engine,
            )
            val ergebnis = sitzung.aktionAnwenden(aktion).getOrThrow()
            spielAblage.spielSpeichern(
                sitzung.alsGespeichertesSpiel(id = id, seed = gespeichert.seed),
            )
            ergebnis
        }
    }

    private suspend fun <T> mitSpielsperre(id: Long, block: suspend () -> T): T =
        spielSperren.computeIfAbsent(id) { Mutex() }.withLock { block() }
}
