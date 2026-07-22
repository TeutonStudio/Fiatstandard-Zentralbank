package de.teutonstudio.zentralbank.server

import de.teutonstudio.zentralbank.anwendung.AKTUELLE_ENGINE_VERSION
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielDienst
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.auswertung.BeobachtungsAuswertung
import de.teutonstudio.zentralbank.fachlogik.beobachtung.SpielBeobachtung
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import de.teutonstudio.zentralbank.protokoll.API_VERSION
import de.teutonstudio.zentralbank.protokoll.AktionErgebnisDto
import de.teutonstudio.zentralbank.protokoll.AktionAusfuehrenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.ErlaubteAktionenDto
import de.teutonstudio.zentralbank.protokoll.SpielErstellenAnfrageDto
import de.teutonstudio.zentralbank.protokoll.SpielErstelltDto
import de.teutonstudio.zentralbank.protokoll.SpielLadenAntwortDto
import de.teutonstudio.zentralbank.protokoll.zuDomain
import de.teutonstudio.zentralbank.protokoll.zuDto
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import de.teutonstudio.zentralbank.simulation.AgentenLiga
import de.teutonstudio.zentralbank.simulation.AgentenLigaBericht
import de.teutonstudio.zentralbank.simulation.SimulationsKonfiguration
import de.teutonstudio.zentralbank.simulation.SimulationsLaeufer
import de.teutonstudio.zentralbank.simulation.SimulationsStatistik

class SpielServerDienst(
    private val ablage: SpielAblage,
    engine: SpielEngine = StandardSpielEngine(),
    ersteSpielId: Long = 1,
) {
    private val spielDienst = SpielDienst(ablage, engine)
    private val naechsteId = AtomicLong(ersteSpielId)
    private val letzterLigaBericht = AtomicReference<AgentenLigaBericht?>(null)

    suspend fun erstellen(anfrage: SpielErstellenAnfrageDto): SpielErstelltDto {
        pruefeVersion(anfrage.version)
        val namen = anfrage.spielerNamen.map(String::trim)
        require(namen.isNotEmpty()) { "Mindestens ein Spieler ist erforderlich." }
        require(namen.all(String::isNotBlank)) { "Spielernamen dürfen nicht leer sein." }
        require(namen.distinct().size == namen.size) { "Spielernamen müssen eindeutig sein." }
        val id = freieId()
        require(anfrage.spielstile.isEmpty() || anfrage.spielstile.size == namen.size) {
            "Spielstile müssen entweder leer sein oder für jeden Spieler angegeben werden."
        }
        val stile = if (anfrage.spielstile.isEmpty()) {
            List(namen.size) { SpielerStil.VORSICHTIG }
        } else {
            anfrage.spielstile.map { SpielerStil.valueOf(it) }
        }
        val startzustand = SpielZustand(
            spieler = namen.mapIndexed { index, name ->
                Spieler(id = SpielerId(name), name = name, spielstil = stile[index])
            },
        )
        val gespeichert = spielDienst.spielErstellen(id, startzustand, anfrage.seed)
        return SpielErstelltDto(
            spielId = gespeichert.id.toString(),
            zustand = gespeichert.aktuellerZustand().zuDto(),
        )
    }

    suspend fun laden(id: Long): SpielLadenAntwortDto {
        val gespeichert = spielDienst.spielLaden(id)
            ?: throw SpielNichtGefunden(id)
        return SpielLadenAntwortDto(
            spielId = id.toString(),
            engineVersion = gespeichert.engineVersion.ifBlank { AKTUELLE_ENGINE_VERSION },
            zustand = gespeichert.aktuellerZustand().zuDto(),
        )
    }

    suspend fun erlaubteAktionen(id: Long): ErlaubteAktionenDto {
        val zustand = spielDienst.zustandLaden(id) ?: throw SpielNichtGefunden(id)
        val spieler = zustand.aktiverSpieler
            ?: return ErlaubteAktionenDto(spielId = id.toString(), spieler = "", aktionen = emptyList())
        return ErlaubteAktionenDto(
            spielId = id.toString(),
            spieler = spieler.wert,
            aktionen = spielDienst.erlaubteAktionen(id, spieler).map { it.zuDto() },
        )
    }

    suspend fun beobachten(id: Long): SpielBeobachtung {
        val zustand = spielDienst.zustandLaden(id) ?: throw SpielNichtGefunden(id)
        val spieler = zustand.aktiverSpieler ?: zustand.spieler.firstOrNull()?.id
            ?: error("Der Spielstand enthält keinen Spieler.")
        return BeobachtungsAuswertung.fuerSpieler(zustand, spieler)
    }

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

    suspend fun aktionAusfuehren(
        id: Long,
        anfrage: AktionAusfuehrenAnfrageDto,
    ): AktionErgebnisDto {
        pruefeVersion(anfrage.version)
        if (spielDienst.spielLaden(id) == null) throw SpielNichtGefunden(id)
        val ergebnis = spielDienst.aktionAusfuehren(id, anfrage.aktion.zuDomain()).getOrThrow()
        return AktionErgebnisDto(
            spielId = id.toString(),
            zustand = ergebnis.zustand.zuDto(),
            ereignisse = ergebnis.ereignisse.map { it.zuDto() },
        )
    }

    private suspend fun freieId(): Long {
        while (true) {
            val kandidat = naechsteId.getAndIncrement()
            if (ablage.spielLaden(kandidat) == null) return kandidat
        }
    }

    private fun pruefeVersion(version: Int) {
        require(version == API_VERSION) {
            "API-Version $version wird nicht unterstützt; erwartet wird $API_VERSION."
        }
    }
}

class SpielNichtGefunden(val spielId: Long) : NoSuchElementException(
    "Spielstand $spielId wurde nicht gefunden.",
)
