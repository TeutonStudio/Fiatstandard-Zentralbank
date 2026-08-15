package de.teutonstudio.zentralbank.server

import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.netzwerk.SpielNetzwerkDienst
import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.AktionErgebnisDto
import de.teutonstudio.zentralbank.protokoll.ErlaubteAktionenDto
import de.teutonstudio.zentralbank.protokoll.SpielBeitretenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielBeobachtungAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielErstelltDto
import de.teutonstudio.zentralbank.protokoll.SpielLadenAntwortDto
import de.teutonstudio.zentralbank.protokoll.SpielSitzungDto
import de.teutonstudio.zentralbank.simulation.AgentenLiga
import de.teutonstudio.zentralbank.simulation.AgentenLigaBericht
import de.teutonstudio.zentralbank.simulation.SimulationsKonfiguration
import de.teutonstudio.zentralbank.simulation.SimulationsLaeufer
import de.teutonstudio.zentralbank.simulation.SimulationsStatistik
import java.util.concurrent.atomic.AtomicReference

/** JVM-Kompositionsdienst: Mehrspieler-Kern plus nur hier benötigte Simulationswerkzeuge. */
class SpielServerDienst(
    ablage: SpielAblage,
    engine: SpielEngine = StandardSpielEngine(),
    ersteSpielId: Long = 1,
) {
    val netzwerkDienst = SpielNetzwerkDienst(ablage, engine, ersteSpielId)
    private val letzterLigaBericht = AtomicReference<AgentenLigaBericht?>(null)

    suspend fun erstellen(anfrage: SpielErstellenAnfrageDto): SpielErstelltDto =
        netzwerkDienst.erstellen(anfrage)

    suspend fun laden(id: Long): SpielLadenAntwortDto = netzwerkDienst.laden(id)

    suspend fun beitreten(id: Long, anfrage: SpielBeitretenAnfrageDto): SpielSitzungDto =
        netzwerkDienst.beitreten(id, anfrage)

    suspend fun erlaubteAktionen(id: Long, sessionToken: String): ErlaubteAktionenDto =
        netzwerkDienst.erlaubteAktionen(id, sessionToken)

    suspend fun beobachten(id: Long, sessionToken: String): SpielBeobachtungAntwortDto =
        netzwerkDienst.beobachten(id, sessionToken)

    suspend fun aktionAusfuehren(
        id: Long,
        sessionToken: String,
        anfrage: AktionAusfuehrenAnfrageDto,
    ): AktionErgebnisDto = netzwerkDienst.aktionAusfuehren(id, sessionToken, anfrage)

    fun simulationStarten(anfrage: SimulationStartAnfrage): SimulationsStatistik =
        SimulationsLaeufer().ausfuehren(
            SimulationsKonfiguration(
                spiele = anfrage.spiele,
                seed = anfrage.seed,
                maximaleEntscheidungen = anfrage.watchdogEntscheidungen,
                agenten = anfrage.agenten,
                szenarioId = anfrage.szenarioId,
                parallelitaet = anfrage.parallelitaet,
            ),
        ).statistik

    fun ligaStarten(anfrage: LigaStartAnfrage): AgentenLigaBericht =
        AgentenLiga.ausfuehren(anfrage.spiele, anfrage.seed, anfrage.agenten).also {
            letzterLigaBericht.set(it)
        }

    fun letzterLigaBericht(): AgentenLigaBericht = letzterLigaBericht.get()
        ?: error("Es wurde noch keine Agentenliga ausgeführt.")
}
