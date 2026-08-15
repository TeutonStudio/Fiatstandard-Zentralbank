package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.modell.KriegId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsSeite
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsStatus
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

data class LaufenderKriegAnzeige(
    val id: KriegId,
    val begonnenInRunde: Int,
    val eigeneSeite: KriegsSeite,
    val gegner: Set<SpielerId>,
    val gegnerNamen: List<String>,
    val status: KriegsStatus,
) {
    val geschaeftspartnerText: String
        get() = gegnerNamen.joinToString(", ")

    val vorgangText: String
        get() = buildString {
            append("Krieg · ")
            append(
                when (eigeneSeite) {
                    KriegsSeite.AGGRESSOREN -> "Aggressor"
                    KriegsSeite.VERTEIDIGER -> "Verteidiger"
                },
            )
            if (status == KriegsStatus.FRIEDEN_ANGEBOTEN) {
                append(" · Frieden angeboten")
            }
        }

    fun betrifftGeschaeftspartner(name: String): Boolean = name in gegnerNamen
}

fun SpielZustand.laufendeKriegeFuer(spielerName: String): List<LaufenderKriegAnzeige> {
    val spielerId = spieler.singleOrNull { it.name == spielerName }?.id ?: return emptyList()
    val namenNachId = spieler.associate { it.id to it.name }
    return konflikte
        .asSequence()
        .filter { konflikt ->
            konflikt.status != KriegsStatus.BEENDET && spielerId in konflikt.teilnehmer
        }
        .mapNotNull { konflikt ->
            val seite = konflikt.seiteVon(spielerId) ?: return@mapNotNull null
            val gegner = when (seite) {
                KriegsSeite.AGGRESSOREN -> konflikt.verteidiger
                KriegsSeite.VERTEIDIGER -> konflikt.aggressoren
            }
            LaufenderKriegAnzeige(
                id = konflikt.id,
                begonnenInRunde = konflikt.begonnenInRunde,
                eigeneSeite = seite,
                gegner = gegner,
                gegnerNamen = gegner
                    .map { id -> namenNachId[id] ?: id.wert }
                    .sorted(),
                status = konflikt.status,
            )
        }
        .sortedWith(compareBy<LaufenderKriegAnzeige> { it.begonnenInRunde }.thenBy { it.id.wert })
        .toList()
}
