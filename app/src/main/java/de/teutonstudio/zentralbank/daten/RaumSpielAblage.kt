package de.teutonstudio.zentralbank.daten

import androidx.room.withTransaction
import de.teutonstudio.zentralbank.daten.zuordnung.zuEntitaet
import de.teutonstudio.zentralbank.daten.zuordnung.zuGespeichertemSpiel
import de.teutonstudio.zentralbank.daten.zuordnung.zuLegacySpiel
import de.teutonstudio.zentralbank.daten.zuordnung.zuSpielZustand
import de.teutonstudio.zentralbank.datenbank.AppDatabase
import de.teutonstudio.zentralbank.datenbank.RundeDaten
import de.teutonstudio.zentralbank.datenbank.SpielDaten
import de.teutonstudio.zentralbank.datenbank.SpielerDaten
import de.teutonstudio.zentralbank.datenbank.SpeicherDaten
import de.teutonstudio.zentralbank.datenbank.ZentralbankSpeicher
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.GespeichertesSpiel
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.SpielAblage
import de.teutonstudio.zentralbank.fachlogik.schnittstelle.SpielstandUebersicht
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class RaumSpielAblage(
    private val datenbank: AppDatabase,
) : SpielAblage {
    private val spielstandDao = datenbank.spielstandDao()
    private val legacySpeicher = ZentralbankSpeicher(datenbank)

    override fun spielstaendeBeobachten(): Flow<List<SpielstandUebersicht>> = combine(
        spielstandDao.spielstaendeBeobachten(),
        legacySpeicher.observeAlleNachSpiel(),
    ) { fachSpielstaende, legacySpielstaende ->
        val fachUebersichten = fachSpielstaende
            .map { entitaet -> entitaet.zuGespeichertemSpiel().zuUebersicht() }
            .associateBy { uebersicht -> uebersicht.id }
        val legacyUebersichten = legacySpielstaende
            .map { (spiel, daten) -> daten.zuLegacyUebersicht(spiel) }
            .associateBy { uebersicht -> uebersicht.id }

        (legacyUebersichten + fachUebersichten)
            .values
            .sortedBy { uebersicht -> uebersicht.id }
    }

    override suspend fun spielLaden(id: Long): GespeichertesSpiel? {
        spielstandDao.spielstandLaden(id)?.let { return it.zuGespeichertemSpiel() }

        val legacyId = id.alsLegacyIdOderNull() ?: return null
        val spielDaten = datenbank.gameDao().getById(legacyId) ?: return null
        val daten = legacySpeicher.observeDatenZuSpiel(id).first()
        return GespeichertesSpiel(
            id = id,
            startzustand = daten.zuLegacySpiel(spielDaten).zuSpielZustand(),
            ausLegacyDatenImportiert = true,
        )
    }

    override suspend fun spielSpeichern(spiel: GespeichertesSpiel) {
        require(spiel.id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        spielstandDao.spielstandSpeichern(spiel.zuEntitaet())
    }

    override suspend fun spielLoeschen(id: Long) {
        require(id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        datenbank.withTransaction {
            spielstandDao.spielstandLoeschen(id)
            id.alsLegacyIdOderNull()?.let { legacyId ->
                datenbank.contractDao().deleteBySpiel(legacyId)
                datenbank.creditDao().deleteBySpiel(legacyId)
                datenbank.tradeDao().deleteBySpiel(legacyId)
                datenbank.roundDao().deleteBySpiel(legacyId)
                datenbank.controlDao().deleteBySpiel(legacyId)
                datenbank.buildDAO().deleteBySpiel(legacyId)
                datenbank.playerDao().deleteBySpiel(legacyId)
                datenbank.gameDao().deleteById(legacyId)
            }
        }
    }
}

private fun List<SpeicherDaten>.zuLegacyUebersicht(spiel: SpielDaten): SpielstandUebersicht =
    SpielstandUebersicht(
        id = spiel.spielID,
        spielerNamen = filterIsInstance<SpielerDaten>()
            .sortedBy { spieler -> spieler.spielerID }
            .map { spieler -> spieler.spielerName },
        runde = filterIsInstance<RundeDaten>().maxOfOrNull { runde -> runde.index } ?: 0,
        ausLegacyDatenImportiert = true,
    )

private fun Long.alsLegacyIdOderNull(): Int? =
    takeIf { id -> id in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
