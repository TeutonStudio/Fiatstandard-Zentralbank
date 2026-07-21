package de.teutonstudio.zentralbank.server

import de.teutonstudio.zentralbank.anwendung.AKTUELLE_ENGINE_VERSION
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielDienst
import de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
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

class SpielServerDienst(
    private val ablage: SpielAblage,
    engine: SpielEngine = StandardSpielEngine(),
    ersteSpielId: Long = 1,
) {
    private val spielDienst = SpielDienst(ablage, engine)
    private val naechsteId = AtomicLong(ersteSpielId)

    suspend fun erstellen(anfrage: SpielErstellenAnfrageDto): SpielErstelltDto {
        pruefeVersion(anfrage.version)
        val namen = anfrage.spielerNamen.map(String::trim)
        require(namen.isNotEmpty()) { "Mindestens ein Spieler ist erforderlich." }
        require(namen.all(String::isNotBlank)) { "Spielernamen dürfen nicht leer sein." }
        require(namen.distinct().size == namen.size) { "Spielernamen müssen eindeutig sein." }
        val id = freieId()
        val startzustand = SpielZustand(
            spieler = namen.map { name -> Spieler(id = SpielerId(name), name = name) },
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
