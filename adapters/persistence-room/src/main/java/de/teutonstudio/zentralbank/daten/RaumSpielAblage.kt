package de.teutonstudio.zentralbank.daten

import androidx.room.withTransaction
import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import de.teutonstudio.zentralbank.daten.raumdatenbank.entitaet.SpielstandEntitaet
import de.teutonstudio.zentralbank.daten.zuordnung.zuEntitaet
import de.teutonstudio.zentralbank.daten.zuordnung.zuGespeichertemSpiel
import de.teutonstudio.zentralbank.datenbank.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RaumSpielAblage(
    private val datenbank: AppDatabase,
) : SpielAblage {
    private val spielstandDao = datenbank.spielstandDao()

    override fun spielstaendeBeobachten(): Flow<List<SpielstandUebersicht>> =
        spielstandDao.spielstaendeBeobachten().map { fachSpielstaende ->
            fachSpielstaende
                .map(SpielstandEntitaet::zuSichereUebersicht)
                .sortedBy { uebersicht -> uebersicht.id }
        }

    override suspend fun spielLaden(id: Long): GespeichertesSpiel? =
        spielstandDao.spielstandLaden(id)?.zuGespeichertemSpiel()

    override suspend fun spielSpeichern(spiel: GespeichertesSpiel) {
        require(spiel.id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        spielstandDao.spielstandSpeichern(spiel.zuEntitaet())
    }

    override suspend fun spielLoeschen(id: Long) {
        require(id >= 0) { "Spielstand-ID darf nicht negativ sein." }
        datenbank.withTransaction {
            spielstandDao.spielstandLoeschen(id)
            id.alsTabellenIdOderNull()?.let { tabellenId ->
                datenbank.contractDao().deleteBySpiel(tabellenId)
                datenbank.creditDao().deleteBySpiel(tabellenId)
                datenbank.tradeDao().deleteBySpiel(tabellenId)
                datenbank.roundDao().deleteBySpiel(tabellenId)
                datenbank.controlDao().deleteBySpiel(tabellenId)
                datenbank.buildDAO().deleteBySpiel(tabellenId)
                datenbank.playerDao().deleteBySpiel(tabellenId)
                datenbank.gameDao().deleteById(tabellenId)
            }
        }
    }
}

private fun SpielstandEntitaet.zuSichereUebersicht(): SpielstandUebersicht {
    val gespeichert = runCatching { zuGespeichertemSpiel() }
        .getOrElse { fehler ->
            return SpielstandUebersicht(
                id = spielId,
                spielerNamen = emptyList(),
                runde = 0,
                ladeFehler = fehler.kompakteLadeFehlerMeldung(),
            )
        }
    return runCatching { gespeichert.zuUebersicht() }
        .getOrElse { fehler ->
            SpielstandUebersicht(
                id = gespeichert.id,
                spielerNamen = gespeichert.startzustand.spieler.map { it.name },
                runde = gespeichert.startzustand.rundenzähler,
                ladeFehler = fehler.kompakteLadeFehlerMeldung(),
            )
        }
}

private fun Throwable.kompakteLadeFehlerMeldung(): String =
    message?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: "Spielstand kann mit dem aktuellen Regelwerk nicht rekonstruiert werden."

private fun Long.alsTabellenIdOderNull(): Int? =
    takeIf { id -> id in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
