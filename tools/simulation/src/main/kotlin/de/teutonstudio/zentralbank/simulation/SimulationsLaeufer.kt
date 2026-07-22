package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.engine.SeedZufallsquelle
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.auswertung.MarktAuswertung
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import java.nio.file.Path

data class SimulationsKonfiguration(
    val spiele: Int,
    val seed: Long,
    val maximaleEntscheidungen: Int,
    val agenten: List<String> = listOf("zufall"),
    val szenarioId: String = "kleine-wirtschaft-v1",
    val parallelitaet: Int = 1,
) {
    init {
        require(spiele > 0) { "Die Spielanzahl muss positiv sein." }
        require(maximaleEntscheidungen > 0) { "Das Entscheidungslimit muss positiv sein." }
        require(agenten.isNotEmpty()) { "Mindestens ein Agent ist erforderlich." }
        require(parallelitaet > 0) { "Die Parallelität muss positiv sein." }
    }
}

@Serializable
data class SimulationsStatistik(
    val spiele: Int,
    val beendet: Int,
    val truncations: Int,
    val siegeJeAgent: Map<String, Int>,
    val durchschnittlicheRunden: Double,
    val durchschnittlicheEntscheidungen: Double,
    val fehler: List<String>,
)

data class SimulationsErgebnis(
    val episoden: List<SpielEpisode>,
    val statistik: SimulationsStatistik,
)

class SimulationsLaeufer(
    private val szenarioFabrik: (String) -> TrainingsSzenario = { id ->
        SzenarioKatalog.szenario(id)
    },
    private val agentenFabrik: (String) -> SpielAgent = ::agentErzeugen,
) {
    fun ausfuehren(konfiguration: SimulationsKonfiguration): SimulationsErgebnis {
        val laeufe = spieleAusfuehren(konfiguration)
        val episoden = laeufe.mapNotNull(EpisodenLauf::episode)
        val fehler = laeufe.mapNotNull(EpisodenLauf::fehler)
        val beendet = episoden.count { it.ergebnis != null }
        val siege = episoden.mapNotNull { episode ->
            val gewinner = episode.ergebnis?.gewinner ?: return@mapNotNull null
            val spielerIndex = episode.startzustand.spieler.indexOfFirst { it.id == gewinner }
            konfiguration.agenten[spielerIndex % konfiguration.agenten.size]
        }.groupingBy { it }.eachCount().toSortedMap()
        return SimulationsErgebnis(
            episoden = episoden,
            statistik = SimulationsStatistik(
                spiele = konfiguration.spiele,
                beendet = beendet,
                truncations = episoden.count(SpielEpisode::truncated),
                siegeJeAgent = siege,
                durchschnittlicheRunden = episoden.map { episode ->
                    episode.ergebnis?.endRunde ?: episode.replay().rundenzähler
                }.average().takeUnless(Double::isNaN) ?: 0.0,
                durchschnittlicheEntscheidungen = episoden
                    .map { it.entscheidungen.size }
                    .average().takeUnless(Double::isNaN) ?: 0.0,
                fehler = fehler,
            ),
        )
    }

    private fun spieleAusfuehren(
        konfiguration: SimulationsKonfiguration,
    ): List<EpisodenLauf> {
        val auftrag: (Int) -> EpisodenLauf = { spielIndex ->
            val episodenSeed = Math.addExact(konfiguration.seed, spielIndex.toLong())
            runCatching {
                spieleEpisode(konfiguration, spielIndex, episodenSeed)
            }.fold(
                onSuccess = { episode -> EpisodenLauf(spielIndex, episode, null) },
                onFailure = { ursache ->
                    EpisodenLauf(
                        spielIndex,
                        null,
                        "spiel-$spielIndex: ${ursache.message ?: ursache::class.simpleName}",
                    )
                },
            )
        }
        if (konfiguration.parallelitaet == 1) {
            return List(konfiguration.spiele, auftrag)
        }
        val executor = Executors.newFixedThreadPool(konfiguration.parallelitaet)
        return try {
            (0 until konfiguration.spiele)
                .map { index -> executor.submit(Callable { auftrag(index) }) }
                .map { zukunft -> zukunft.get() }
                .sortedBy(EpisodenLauf::index)
        } finally {
            executor.shutdown()
        }
    }

    private fun spieleEpisode(
        konfiguration: SimulationsKonfiguration,
        spielIndex: Int,
        episodenSeed: Long,
    ): SpielEpisode {
        val szenario = szenarioFabrik(konfiguration.szenarioId)
        val umgebung = StandardTrainingsUmgebung(
            maximaleEntscheidungen = konfiguration.maximaleEntscheidungen,
        )
        var punkt: Entscheidungspunkt? = umgebung.reset(szenario, episodenSeed)
        val zufall = SeedZufallsquelle(episodenSeed)
        val agentNachSpieler = umgebung.startzustand.spieler.mapIndexed { index, spieler ->
            spieler.id to agentenFabrik(konfiguration.agenten[index % konfiguration.agenten.size])
        }.toMap()
        val entscheidungen = mutableListOf<EntscheidungsDatensatz>()
        var letzterUebergang: TrainingsUebergang? = null
        while (punkt != null) {
            val agent = requireNotNull(agentNachSpieler[punkt.spieler])
            val aktion = agent.waehleAktion(punkt, zufall)
            val vorher = umgebung.zustand
            val marktwerteVorher = vorher.spieler.associate { spieler ->
                spieler.id to MarktAuswertung.spielerMarktwert(vorher, spieler.id)
            }
            val uebergang = umgebung.step(aktion)
            val nachher = umgebung.zustand
            val marktwerteNachher = nachher.spieler.associate { spieler ->
                spieler.id to MarktAuswertung.spielerMarktwert(nachher, spieler.id)
            }
            entscheidungen += EntscheidungsDatensatz(
                spielId = "spiel-$spielIndex",
                entscheidungsNummer = entscheidungen.size.toLong(),
                spieler = punkt.spieler,
                spielstil = punkt.beobachtung.eigeneWirtschaft.spielstil,
                beobachtung = punkt.beobachtung,
                erlaubteAktionen = punkt.aktionsRaum,
                gewaehlteAktion = aktion,
                belohnungen = uebergang.belohnungen,
                terminated = uebergang.terminated,
                truncated = uebergang.truncated,
                ausscheidensGruende = uebergang.ereignisse.filterIsInstance<SpielEreignis.SpielerAusgeschieden>()
                    .associate { it.spieler to it.grund },
                naechsterAktiverSpieler = uebergang.naechsterPunkt?.spieler,
                ereignisse = uebergang.ereignisse,
                marktwerteVorher = marktwerteVorher,
                marktwerteNachher = marktwerteNachher,
                ergebnis = uebergang.ergebnis,
            )
            letzterUebergang = uebergang
            punkt = uebergang.naechsterPunkt
        }
        val episode = SpielEpisode(
            spielId = "spiel-$spielIndex",
            seed = episodenSeed,
            szenarioId = szenario.id,
            startzustand = umgebung.startzustand.ohnePasswoerter(),
            entscheidungen = entscheidungen,
            spielerUebergaenge = SpielEpisode.spielerUebergaenge(entscheidungen),
            ereignisse = umgebung.ereignisse,
            ergebnis = letzterUebergang?.ergebnis,
            truncated = letzterUebergang?.truncated == true,
            abbruchDiagnose = if (letzterUebergang?.truncated == true) {
                TechnischeAbbruchDiagnose(
                    grund = "MARKTWERT_UEBER_WATCHDOG_FENSTER_UNVERAENDERT",
                    entscheidungenOhneMarktwertAenderung =
                        umgebung.entscheidungenOhneMarktwertAenderung,
                    letzteAktionen = umgebung.letzteAktionen,
                    letzterZustand = umgebung.zustand.ohnePasswoerter(),
                )
            } else null,
        )
        check(episode.replay() == umgebung.zustand.ohnePasswoerter()) {
            "Episoden-Replay stimmt nicht mit dem Endzustand überein."
        }
        return episode
    }
}

private data class EpisodenLauf(
    val index: Int,
    val episode: SpielEpisode?,
    val fehler: String?,
)

fun agentErzeugen(name: String): SpielAgent = when (name.lowercase()) {
    "zufall" -> ZufallsAgent()
    "sicherheit" -> SicherheitsAgent()
    "wirtschaft" -> WirtschaftsAgent()
    "aggressiv" -> AggressiverHeuristikAgent()
    "defensiv" -> DefensiverHeuristikAgent()
    "onnx" -> OnnxModellAgent(
        Path.of(System.getProperty("fiat.onnx.model", "tools/ai-python/build/model/spieler-ki-v1.onnx")),
        Path.of(System.getProperty("fiat.onnx.manifest", "tools/ai-python/build/model/manifest.json")),
    )
    else -> error("Unbekannter Agent: $name")
}
