package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.AKTUELLE_AKTIONS_SCHEMA_VERSION
import de.teutonstudio.zentralbank.fachlogik.auswertung.AktionsAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.MarktAuswertung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.AKTUELLE_BEOBACHTUNGS_VERSION
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun trainingsWorkerMain() {
    val worker = TrainingsWorker()
    generateSequence(::readlnOrNull).forEach { zeile ->
        if (zeile.isBlank()) return@forEach
        val (antwort, schliessen) = runCatching {
            val anfrage = TrainingsWorker.json.parseToJsonElement(zeile).jsonObject
            worker.verarbeite(anfrage)
        }.getOrElse { fehler ->
            buildJsonObject {
                put("ok", false)
                put("error", buildJsonObject {
                    put("code", "UNGUELTIGE_ANFRAGE")
                    put("message", fehler.message ?: fehler::class.simpleName.orEmpty())
                })
            } to false
        }
        println(TrainingsWorker.json.encodeToString(JsonObject.serializer(), antwort))
        System.out.flush()
        if (schliessen) return
    }
}

private class WorkerSitzung(
    val environmentId: String,
    val szenario: TrainingsSzenario,
    val seed: Long,
    val umgebung: StandardTrainingsUmgebung,
    var punkt: Entscheidungspunkt,
) {
    val entscheidungen = mutableListOf<EntscheidungsDatensatz>()
    var letzterUebergang: TrainingsUebergang? = null

    fun step(aktion: SpielAktion): TrainingsUebergang {
        val aktuellerPunkt = punkt
        val vorher = umgebung.zustand
        val marktVorher = vorher.spieler.associate { spieler ->
            spieler.id to MarktAuswertung.spielerMarktwert(vorher, spieler.id)
        }
        val uebergang = umgebung.step(aktion)
        val nachher = umgebung.zustand
        val marktNachher = nachher.spieler.associate { spieler ->
            spieler.id to MarktAuswertung.spielerMarktwert(nachher, spieler.id)
        }
        entscheidungen += EntscheidungsDatensatz(
            spielId = environmentId,
            entscheidungsNummer = entscheidungen.size.toLong(),
            spieler = aktuellerPunkt.spieler,
            spielstil = aktuellerPunkt.beobachtung.eigeneWirtschaft.spielstil,
            beobachtung = aktuellerPunkt.beobachtung,
            erlaubteAktionen = aktuellerPunkt.aktionsRaum,
            gewaehlteAktion = aktion,
            belohnungen = uebergang.belohnungen,
            terminated = uebergang.terminated,
            truncated = uebergang.truncated,
            ausscheidensGruende = uebergang.ereignisse
                .filterIsInstance<SpielEreignis.SpielerAusgeschieden>()
                .associate { it.spieler to it.grund },
            naechsterAktiverSpieler = uebergang.naechsterPunkt?.spieler,
            ereignisse = uebergang.ereignisse,
            marktwerteVorher = marktVorher,
            marktwerteNachher = marktNachher,
            ergebnis = uebergang.ergebnis,
        )
        uebergang.naechsterPunkt?.let { punkt = it }
        letzterUebergang = uebergang
        return uebergang
    }

    fun episode(): SpielEpisode {
        val truncated = letzterUebergang?.truncated == true
        return SpielEpisode(
            spielId = environmentId,
            seed = seed,
            szenarioId = szenario.id,
            startzustand = umgebung.startzustand.ohnePasswoerter(),
            entscheidungen = entscheidungen,
            spielerUebergaenge = SpielEpisode.spielerUebergaenge(entscheidungen),
            ereignisse = umgebung.ereignisse,
            ergebnis = letzterUebergang?.ergebnis,
            truncated = truncated,
            abbruchDiagnose = if (truncated) TechnischeAbbruchDiagnose(
                "MARKTWERT_UEBER_WATCHDOG_FENSTER_UNVERAENDERT",
                umgebung.entscheidungenOhneMarktwertAenderung,
                umgebung.letzteAktionen,
                umgebung.zustand.ohnePasswoerter(),
            ) else null,
        )
    }
}

class TrainingsWorker {
    private val sitzungen = linkedMapOf<String, WorkerSitzung>()

    fun verarbeite(anfrage: JsonObject): Pair<JsonObject, Boolean> {
        val command = anfrage.pflichtText("command")
        val id = anfrage["id"]?.jsonPrimitive?.contentOrNull
        return try {
            val daten = when (command) {
                "reset" -> reset(anfrage)
                "observe" -> punktAntwort(sitzung(anfrage))
                "legal_actions" -> legalActions(sitzung(anfrage))
                "step" -> step(anfrage)
                "batch_reset" -> batchReset(anfrage)
                "batch_step" -> batchStep(anfrage)
                "export_episode" -> exportEpisode(anfrage)
                "close" -> close(anfrage)
                else -> error("Unbekannter Worker-Befehl: $command")
            }
            buildJsonObject {
                put("ok", true)
                id?.let { put("id", it) }
                put("command", command)
                daten.forEach { (name, wert) -> put(name, wert) }
            } to (command == "close" && anfrage["environmentId"] == null)
        } catch (fehler: Throwable) {
            buildJsonObject {
                put("ok", false)
                id?.let { put("id", it) }
                put("command", command)
                put("error", buildJsonObject {
                    put("code", fehler::class.simpleName ?: "WORKER_FEHLER")
                    put("message", fehler.message ?: "Unbekannter Worker-Fehler")
                    anfrage["environmentId"]?.let { put("environmentId", it) }
                })
            } to false
        }
    }

    private fun reset(anfrage: JsonObject): JsonObject {
        val environmentId = anfrage["environmentId"]?.jsonPrimitive?.content ?: "default"
        val seed = anfrage["seed"]?.jsonPrimitive?.long ?: 0L
        val szenarioId = anfrage["scenarioId"]?.jsonPrimitive?.content ?: "kleine-wirtschaft-v2"
        val watchdog = anfrage["watchdogDecisions"]?.jsonPrimitive?.int ?: 10_000
        val szenario = SzenarioKatalog.szenario(szenarioId)
        val umgebung = StandardTrainingsUmgebung(maximaleEntscheidungen = watchdog)
        val punkt = umgebung.reset(szenario, seed)
        val sitzung = WorkerSitzung(environmentId, szenario, seed, umgebung, punkt)
        sitzungen[environmentId] = sitzung
        return punktAntwort(sitzung)
    }

    private fun step(anfrage: JsonObject): JsonObject {
        val sitzung = sitzung(anfrage)
        val aktion = dekodiereAktion(anfrage)
        require(aktion in sitzung.punkt.aktionsRaum.aktionen) {
            "Die Aktion gehört nicht zur legalen Kandidatenliste."
        }
        val uebergang = sitzung.step(aktion)
        return buildJsonObject {
            put("environmentId", sitzung.environmentId)
            put("rewards", json.encodeToJsonElement(uebergang.belohnungen))
            put("terminated", uebergang.terminated)
            put("truncated", uebergang.truncated)
            put("events", json.encodeToJsonElement(uebergang.ereignisse))
            put("result", json.encodeToJsonElement(uebergang.ergebnis))
            if (!uebergang.terminated && !uebergang.truncated) {
                punktAntwort(sitzung).forEach { (name, wert) -> put(name, wert) }
            }
        }
    }

    private fun batchReset(anfrage: JsonObject): JsonObject = buildJsonObject {
        val elemente = anfrage["environments"]?.jsonArray ?: error("environments fehlt.")
        put("results", buildJsonArray {
            elemente.forEach { add(reset(it.jsonObject)) }
        })
    }

    private fun batchStep(anfrage: JsonObject): JsonObject = buildJsonObject {
        val elemente = anfrage["steps"]?.jsonArray ?: error("steps fehlt.")
        put("results", buildJsonArray {
            elemente.forEach { add(step(it.jsonObject)) }
        })
    }

    private fun legalActions(sitzung: WorkerSitzung): JsonObject = buildJsonObject {
        put("environmentId", sitzung.environmentId)
        put("actionSchemaVersion", AKTUELLE_AKTIONS_SCHEMA_VERSION)
        put("actions", buildJsonArray {
            sitzung.punkt.aktionsRaum.aktionen.forEach { aktion ->
                add(buildJsonObject {
                    put("canonical", AktionsAuswertung.aktionsSchluessel(aktion))
                    put("action", json.encodeToJsonElement(SpielAktion.serializer(), aktion))
                })
            }
        })
    }

    private fun punktAntwort(sitzung: WorkerSitzung): JsonObject = buildJsonObject {
        put("environmentId", sitzung.environmentId)
        put("observationSchemaVersion", AKTUELLE_BEOBACHTUNGS_VERSION)
        put("actionSchemaVersion", AKTUELLE_AKTIONS_SCHEMA_VERSION)
        put("episodeSchemaVersion", AKTUELLE_EPISODEN_FORMAT_VERSION)
        put("activePlayer", sitzung.punkt.spieler.wert)
        put("observation", json.encodeToJsonElement(sitzung.punkt.beobachtung))
        legalActions(sitzung)["actions"]?.let { put("legalActions", it) }
    }

    private fun exportEpisode(anfrage: JsonObject): JsonObject {
        val sitzung = sitzung(anfrage)
        val pfad = Path.of(anfrage.pflichtText("path"))
        val episode = sitzung.episode()
        EpisodenJsonl.exportieren(pfad, sequenceOf(episode))
        return buildJsonObject {
            put("environmentId", sitzung.environmentId)
            put("path", pfad.toAbsolutePath().toString())
            put("decisions", episode.entscheidungen.size)
        }
    }

    private fun close(anfrage: JsonObject): JsonObject {
        val environmentId = anfrage["environmentId"]?.jsonPrimitive?.contentOrNull
        if (environmentId == null) sitzungen.clear() else sitzungen.remove(environmentId)
        return buildJsonObject { put("closed", environmentId ?: "all") }
    }

    private fun sitzung(anfrage: JsonObject): WorkerSitzung {
        val id = anfrage["environmentId"]?.jsonPrimitive?.content ?: "default"
        return sitzungen[id] ?: error("Unbekannte Umgebung: $id")
    }

    private fun dekodiereAktion(anfrage: JsonObject): SpielAktion {
        anfrage["actionCanonical"]?.jsonPrimitive?.contentOrNull?.let {
            return json.decodeFromString(SpielAktion.serializer(), it)
        }
        val element = anfrage["action"] ?: error("action oder actionCanonical fehlt.")
        return json.decodeFromJsonElement(SpielAktion.serializer(), element)
    }

    private fun JsonObject.pflichtText(name: String): String =
        this[name]?.jsonPrimitive?.content ?: error("$name fehlt.")

    companion object {
        val json = Json {
            classDiscriminator = "art"
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    }
}
