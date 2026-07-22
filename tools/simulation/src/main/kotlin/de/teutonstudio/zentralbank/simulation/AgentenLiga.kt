package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.measureTimedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class AgentenLigaEintrag(
    val agent: String,
    val spiele: Int,
    val siege: Int,
    val siegquote: Double,
    val ausscheidungen: Int,
    val ausscheidensGruende: Map<String, Int>,
    val durchschnittlicheRunden: Double,
    val durchschnittlicheEntscheidungen: Double,
    val marktwertverlaufCent: List<Long>,
    val durchschnittlicheSchuldenCent: Double,
    val durchschnittlicheZinslastCent: Double,
    val schuldenstriche: Int,
    val zentralbankgeldschoepfungCent: Long,
    val kriegserklaerungen: Int,
    val kapitulationen: Int,
    val gewonneneKaempfe: Int,
    val verloreneKaempfe: Int,
    val erfolgreicheBelagerungen: Int,
    val unterbrocheneBelagerungen: Int,
    val zerstoerteVerwaltungsstandorte: Int,
    val blockaderunden: Int,
    val produktionsueberschuss: Long,
    val startpositionsSiege: Map<Int, Int>,
    val aktionenJeTyp: Map<String, Int>,
)

@Serializable
data class AgentenLigaBericht(
    val schemaVersion: Int = 1,
    val seed: Long,
    val spiele: Int,
    val dauerMillis: Long,
    val schritteProSekunde: Double,
    val fehler: List<String>,
    val agenten: List<AgentenLigaEintrag>,
)

object AgentenLiga {
    private val standardAgenten = listOf("zufall", "sicherheit", "wirtschaft", "aggressiv", "defensiv", "onnx")

    fun ausfuehren(spiele: Int, seed: Long, agenten: List<String> = standardAgenten): AgentenLigaBericht {
        require(spiele > 0 && agenten.isNotEmpty())
        val gemessen = measureTimedValue {
            (0 until spiele).map { index ->
                val vergleichsGruppe = index / agenten.size
                val spielerzahl = 3 + vergleichsGruppe % 5
                val sitzung = List(spielerzahl) { sitz -> agenten[(sitz + index) % agenten.size] }
                val kategorie = if (vergleichsGruppe % 2 == 0) "wirtschaft" else "landkrieg"
                val paarSeed = seed + vergleichsGruppe
                val szenario = if (vergleichsGruppe % 3 == 2) {
                    val karte = SzenarioKatalog.echteKarten[
                        vergleichsGruppe % SzenarioKatalog.echteKarten.size
                    ].substringBeforeLast('.')
                    "vorlage-$karte-$spielerzahl"
                } else {
                    "generiert-$kategorie-$spielerzahl"
                }
                sitzung to SimulationsLaeufer().ausfuehren(
                    SimulationsKonfiguration(
                        spiele = 1,
                        seed = paarSeed,
                        maximaleEntscheidungen = 10_000,
                        agenten = sitzung,
                        szenarioId = szenario,
                    ),
                )
            }
        }
        val laeufe = gemessen.value
        val fehler = laeufe.flatMap { it.second.statistik.fehler }
        val totalSchritte = laeufe.sumOf { it.second.episoden.sumOf { episode -> episode.entscheidungen.size } }
        val eintraege = agenten.distinct().sorted().map { agent ->
            val beteiligungen = laeufe.flatMap { (sitzung, ergebnis) ->
                ergebnis.episoden.map { episode -> sitzung to episode }
            }.filter { (sitzung, _) -> agent in sitzung }
            val siege = beteiligungen.count { (sitzung, episode) ->
                val gewinner = episode.ergebnis?.gewinner ?: return@count false
                val sitz = episode.startzustand.spieler.indexOfFirst { it.id == gewinner }
                sitzung[sitz] == agent
            }
            val spielerIds = beteiligungen.flatMap { (sitzung, episode) ->
                episode.startzustand.spieler.mapIndexedNotNull { index, spieler ->
                    spieler.id.takeIf { sitzung[index] == agent }
                }.map { episode to it }
            }
            val aktionen = spielerIds.flatMap { (episode, id) ->
                episode.entscheidungen.filter { it.spieler == id }.map { it.gewaehlteAktion }
            }
            val ausscheiden = spielerIds.flatMap { (episode, id) ->
                episode.entscheidungen.flatMap { it.ausscheidensGruende.entries }
                    .filter { it.key == id }.map { it.value.name }
            }
            val ereignisse = spielerIds.flatMap { (episode, id) ->
                episode.ereignisse.map { id to it }
            }
            val marktwerte = spielerIds.flatMap { (episode, id) ->
                episode.entscheidungen.mapNotNull { it.marktwerteNachher[id]?.cent }
            }
            val schulden = spielerIds.map { (episode, id) ->
                episode.replay().anleihen.values.filter { it.emittent == id }.sumOf { it.nennwert.cent }
            }
            val zinsen = spielerIds.map { (episode, id) ->
                episode.replay().anleihen.values.filter { it.emittent == id }.sumOf { anleihe ->
                    de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
                        .zinszahlung(anleihe).cent * anleihe.geleisteteZinszahlungen
                }
            }
            val kaempfe = ereignisse.mapNotNull { (id, event) ->
                (event as? SpielEreignis.KampfAufgeloest)?.let { id to it }
            }
            val konfliktStatistiken = spielerIds.map { (episode, id) ->
                konfliktStatistik(episode).getOrDefault(id, KonfliktStatistik())
            }
            AgentenLigaEintrag(
                agent = agent,
                spiele = beteiligungen.size,
                siege = siege,
                siegquote = siege.toDouble() / maxOf(1, beteiligungen.size),
                ausscheidungen = ausscheiden.size,
                ausscheidensGruende = ausscheiden.groupingBy { it }.eachCount().toSortedMap(),
                durchschnittlicheRunden = beteiligungen.map { it.second.replay().rundenzähler }
                    .average().takeUnless(Double::isNaN) ?: 0.0,
                durchschnittlicheEntscheidungen = beteiligungen.map { it.second.entscheidungen.size }
                    .average().takeUnless(Double::isNaN) ?: 0.0,
                marktwertverlaufCent = marktwerte,
                durchschnittlicheSchuldenCent = schulden.average().takeUnless(Double::isNaN) ?: 0.0,
                durchschnittlicheZinslastCent = zinsen.average().takeUnless(Double::isNaN) ?: 0.0,
                schuldenstriche = aktionen.count { it is SpielAktion.SchuldenstrichDurchfuehren },
                zentralbankgeldschoepfungCent = ereignisse.sumOf { (_, event) ->
                    (event as? SpielEreignis.ZentralbankgeldGeschoepft)?.betrag?.cent ?: 0L
                },
                kriegserklaerungen = aktionen.count { it is SpielAktion.KriegErklaeren },
                kapitulationen = aktionen.count { it is SpielAktion.KriegKapitulieren },
                gewonneneKaempfe = kaempfe.count { (id, event) ->
                    event.angreifer == id && event.angreiferNachher > 0 ||
                        event.verteidiger == id && event.verteidigerNachher > 0
                },
                verloreneKaempfe = kaempfe.count { (id, event) ->
                    event.angreifer == id && event.angreiferNachher == 0 ||
                        event.verteidiger == id && event.verteidigerNachher == 0
                },
                erfolgreicheBelagerungen = konfliktStatistiken.sumOf { it.erfolgreicheBelagerungen },
                unterbrocheneBelagerungen = konfliktStatistiken.sumOf { it.unterbrocheneBelagerungen },
                zerstoerteVerwaltungsstandorte = konfliktStatistiken.sumOf {
                    it.zerstoerteVerwaltungsstandorte
                },
                blockaderunden = konfliktStatistiken.sumOf { it.blockaderunden },
                produktionsueberschuss = spielerIds.sumOf { (episode, id) ->
                    episode.entscheidungen.sumOf { entscheidung ->
                        entscheidung.beobachtung.spieler.single { it.id == id }
                            .produktionsmengenJeRohstoff.sumOf { it.menge }.toLong()
                    }
                },
                startpositionsSiege = beteiligungen.mapNotNull { (sitzung, episode) ->
                    val gewinner = episode.ergebnis?.gewinner ?: return@mapNotNull null
                    val index = episode.startzustand.spieler.indexOfFirst { it.id == gewinner }
                    index.takeIf { sitzung[index] == agent }
                }.groupingBy { it }.eachCount().toSortedMap(),
                aktionenJeTyp = aktionen.map { it::class.simpleName ?: "Unbekannt" }
                    .groupingBy { it }.eachCount().toSortedMap(),
            )
        }
        val sekunden = gemessen.duration.inWholeNanoseconds / 1_000_000_000.0
        return AgentenLigaBericht(
            seed = seed,
            spiele = spiele,
            dauerMillis = gemessen.duration.inWholeMilliseconds,
            schritteProSekunde = totalSchritte / maxOf(0.001, sekunden),
            fehler = fehler,
            agenten = eintraege,
        )
    }

    fun exportieren(bericht: AgentenLigaBericht, ordner: Path) {
        Files.createDirectories(ordner)
        Files.writeString(ordner.resolve("liga.json"), EpisodenJsonl.json.encodeToString(bericht))
        val text = buildString {
            appendLine("Agentenliga: ${bericht.spiele} Spiele, ${"%.2f".format(bericht.schritteProSekunde)} Schritte/s")
            bericht.agenten.forEach {
                appendLine("${it.agent}: ${it.siege}/${it.spiele} Siege (${"%.1f".format(it.siegquote * 100)} %), ${it.ausscheidungen} Ausscheidungen")
            }
            if (bericht.fehler.isNotEmpty()) appendLine("Fehler: ${bericht.fehler.joinToString()}")
        }
        Files.writeString(ordner.resolve("liga.txt"), text)
    }
}

private data class KonfliktStatistik(
    var erfolgreicheBelagerungen: Int = 0,
    var unterbrocheneBelagerungen: Int = 0,
    var zerstoerteVerwaltungsstandorte: Int = 0,
    var blockaderunden: Int = 0,
)

private fun konfliktStatistik(episode: SpielEpisode): Map<SpielerId, KonfliktStatistik> {
    val statistik = mutableMapOf<SpielerId, KonfliktStatistik>()
    var zustand = episode.startzustand
    episode.ereignisse.forEach { ereignis ->
        val vorher = (ereignis as? SpielEreignis.BelagerungAktualisiert)?.let { aktualisierung ->
            zustand.belagerungen.firstOrNull { it.standort == aktualisierung.standort }
        }
        val folge = SpielRegelwerk.wendeAn(zustand, ereignis).getOrThrow()
        if (ereignis is SpielEreignis.BelagerungAktualisiert) {
            val nachher = folge.belagerungen.firstOrNull { it.standort == ereignis.standort }
            if (ereignis.rundeFortschreiben) {
                (nachher ?: vorher)?.beteiligteBelagerer?.keys.orEmpty().forEach { spieler ->
                    statistik.getOrPut(spieler, ::KonfliktStatistik).blockaderunden += 1
                }
            }
            if (vorher != null && nachher == null) {
                val zerstoert = folge.karte?.belegung?.eckenNachPosition?.get(ereignis.standort)
                    ?.zustand == BauwerkZustand.ZERSTOERT
                if (zerstoert) {
                    statistik.getOrPut(vorher.fuehrenderBelagerer, ::KonfliktStatistik).apply {
                        erfolgreicheBelagerungen += 1
                        zerstoerteVerwaltungsstandorte += 1
                    }
                } else {
                    vorher.beteiligteBelagerer.keys.forEach { spieler ->
                        statistik.getOrPut(spieler, ::KonfliktStatistik)
                            .unterbrocheneBelagerungen += 1
                    }
                }
            }
        }
        zustand = folge
    }
    return statistik
}
