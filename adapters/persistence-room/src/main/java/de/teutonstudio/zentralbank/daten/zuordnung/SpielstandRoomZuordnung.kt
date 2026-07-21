package de.teutonstudio.zentralbank.daten.zuordnung

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.daten.raumdatenbank.entitaet.SpielstandEntitaet
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val AKTUELLE_ROOM_FORMAT_VERSION = 2

private val spielstandJson = Json {
    classDiscriminator = "art"
    encodeDefaults = true
    ignoreUnknownKeys = true
}
private val ereignisListe = ListSerializer(SpielEreignis.serializer())

fun GespeichertesSpiel.zuEntitaet(): SpielstandEntitaet = SpielstandEntitaet(
    spielId = id,
    formatVersion = AKTUELLE_ROOM_FORMAT_VERSION,
    startzustandJson = spielstandJson.encodeToString(SpielZustand.serializer(), startzustand),
    ereignisseJson = spielstandJson.encodeToString(ereignisListe, ereignisse),
)

fun SpielstandEntitaet.zuGespeichertemSpiel(): GespeichertesSpiel {
    require(formatVersion == AKTUELLE_ROOM_FORMAT_VERSION) {
        if (formatVersion == 1) {
            "Spielstand $spielId verwendet das alte Zugformat 1. " +
                "Es enthält keine nachweisbaren Prozug-Buchungen und kann deshalb nicht sicher geladen werden."
        } else {
            "Spielstand $spielId verwendet die nicht unterstützte Formatversion $formatVersion."
        }
    }
    return GespeichertesSpiel(
        id = spielId,
        startzustand = spielstandJson.decodeFromString(
            SpielZustand.serializer(),
            startzustandJson,
        ),
        ereignisse = spielstandJson.decodeFromString(ereignisListe, ereignisseJson),
    )
}
