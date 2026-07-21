package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.anwendung.AKTUELLE_ENGINE_VERSION
import de.teutonstudio.zentralbank.anwendung.SpielSitzung
import de.teutonstudio.zentralbank.fachlogik.engine.SeedZufallsquelle
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.protokoll.SimulationsschrittDto
import de.teutonstudio.zentralbank.protokoll.zuDto

data class SimulationsKonfiguration(
    val episoden: Int,
    val seed: Long,
    val maximaleSchritte: Int,
) {
    init {
        require(episoden > 0) { "Episodenzahl muss positiv sein." }
        require(maximaleSchritte > 0) { "Schrittlimit muss positiv sein." }
    }
}

class SimulationsLaeufer(
    private val strategie: AgentenStrategie = DeterministischeZufallsStrategie(),
    private val bewertung: Bewertungsfunktion = NeutraleBaselineBewertung(),
    private val startzustandFabrik: (Long) -> SpielZustand = ::standardStartzustand,
) {
    fun ausfuehren(konfiguration: SimulationsKonfiguration): Sequence<SimulationsschrittDto> =
        sequence {
            repeat(konfiguration.episoden) { episodeIndex ->
                val episodenSeed = Math.addExact(konfiguration.seed, episodeIndex.toLong())
                val episodeId = "episode-$episodeIndex"
                val zufall = SeedZufallsquelle(episodenSeed)
                val sitzung = SpielSitzung(startzustandFabrik(episodenSeed))

                for (schritt in 0 until konfiguration.maximaleSchritte) {
                    val vorher = sitzung.zustand
                    val akteur = vorher.aktiverSpieler ?: break
                    val erlaubte = sitzung.erlaubteAktionen(akteur)
                    if (erlaubte.isEmpty()) break
                    val gewaehlt = strategie.waehleAktion(vorher, erlaubte, zufall)
                    val ergebnis = sitzung.aktionAnwenden(gewaehlt).getOrThrow()
                    val nachher = ergebnis.zustand
                    val keineFolgeaktion = nachher.aktiverSpieler?.let(sitzung::erlaubteAktionen)
                        .orEmpty().isEmpty()
                    val beendet = keineFolgeaktion || schritt + 1 >= konfiguration.maximaleSchritte
                    yield(
                        SimulationsschrittDto(
                            episodeId = episodeId,
                            engineVersion = AKTUELLE_ENGINE_VERSION,
                            seed = episodenSeed,
                            schritt = schritt,
                            akteur = akteur.wert,
                            beobachtung = vorher.zuDto(),
                            erlaubteAktionen = erlaubte.map { it.zuDto() },
                            gewaehlteAktion = gewaehlt.zuDto(),
                            belohnungsKomponenten = bewertung.bewerte(
                                vorher,
                                gewaehlt,
                                nachher,
                            ).alsKomponenten(),
                            naechsteBeobachtung = nachher.zuDto(),
                            beendet = beendet,
                            gewinner = null,
                            ereignisse = ergebnis.ereignisse.map { it.zuDto() },
                        ),
                    )
                    if (beendet) break
                }
            }
        }
}

private fun standardStartzustand(seed: Long): SpielZustand = SpielZustand(
    spieler = listOf(
        Spieler(SpielerId("Agent-1"), "Agent 1", geldkonto = Geld.mark(100)),
        Spieler(SpielerId("Agent-2"), "Agent 2", geldkonto = Geld.mark(100)),
    ),
)
