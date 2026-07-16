package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
data class UeberschuldungsStatus(
    val spieler: SpielerId,
    val friedlicheUeberschuldeteZuege: Int,
    val letztePruefungRunde: Int,
    val schuldensumme: Geld,
    val marktwert: Geld,
    val warnungAktiv: Boolean = false,
    val schuldenstrichFaellig: Boolean = false,
)
