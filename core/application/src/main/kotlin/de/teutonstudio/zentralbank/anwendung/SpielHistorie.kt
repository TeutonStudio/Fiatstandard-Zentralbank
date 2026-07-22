package de.teutonstudio.zentralbank.anwendung

import de.teutonstudio.zentralbank.fachlogik.ablauf.SpielAblauf
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.ereignis.ProzugBegonnen
import de.teutonstudio.zentralbank.fachlogik.ereignis.ProzugErfolgreichAbgeschlossen
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase

class SpielHistorie(
    private val gespeichertesSpiel: GespeichertesSpiel,
) {
    fun zeitleiste(): List<SpielStandPosition> {
        val ereignisse = gespeichertesSpiel.ereignisse
        val positionen = mutableListOf<SpielStandPosition>()

        var ereignisIndex = 0
        var zugId = 0L
        var runde = 0
        var spielerId: SpielerId? = null
        var positionIndex = 0
        var anzahlAktuelleZuege = 0

        while (ereignisIndex <= ereignisse.size) {
            val istAktuell = ereignisIndex == ereignisse.size

            if (ereignisIndex < ereignisse.size) {
                val ereignis = ereignisse[ereignisIndex]

                if (ereignis is ProzugBegonnen) {
                    zugId = ereignis.zugId
                    runde = ereignis.zugId
                    positionIndex++

                    val spielerListe = gespeichertesSpiel.startzustand.zug.spieler
                    val spielerAnzahl = spielerListe.size
                    val spielerPosition = ((ereignis.zugId - 1L) % spielerAnzahl).toInt()
                    spielerId = spielerListe.getOrNull(spielerPosition)?.id

                    if (ereignis is ProzugErfolgreichAbgeschlossen) {
                        if (zugId > 0) {
                            anzahlAktuelleZuege = zugId.toInt()
                        }
                    }

                    ereignisIndex++
                }

                val phase = when {
                    spielerId == null -> ZugPhase.EPIZUG
                    else -> ZugPhase.PROZUG
                }

                positionen.add(
                    SpielStandPosition(
                        positionIndex = positionIndex,
                        ereignisIndexExklusiv = ereignisIndex,
                        zugId = zugId.takeIf { it > 0 } ?: null,
                        runde = runde,
                        spielerId = spielerId,
                        spielerName = spielerId?.wert,
                        phase = phase,
                        istAktuell = istAktuell,
                        anzahlZuegeBisAktuell = anzahlAktuelleZuege,
                    )
                )
            }
        }

        return positionen
    }

    fun zustandBeiPosition(position: Int): HistorischerSpielstand {
        val positionen = zeitleiste()
        require(position >= 0 && position < positionen.size) {
            "Position $position liegt außerhalb der Grenzen (0-${positionen.size - 1})."
        }

        val historienPosition = positionen[position]
        val ereignisse = gespeichertesSpiel.ereignisse.take(historienPosition.ereignisIndexExklusiv)
        val zustand = SpielAblauf(gespeichertesSpiel.startzustand, ereignisse).zustand

        return HistorischerSpielstand(
            spielId = gespeichertesSpiel.id,
            position = historienPosition,
            zustand = zustand,
        )
    }

    fun zustandBeiEreignisIndex(indexExklusiv: Int): SpielZustand {
        require(indexExklusiv >= 0 && indexExklusiv <= gespeichertesSpiel.ereignisse.size) {
            "Ereignisindex $indexExklusiv liegt außerhalb der Grenzen (0-${gespeichertesSpiel.ereignisse.size})."
        }

        val ereignisse = gespeichertesSpiel.ereignisse.take(indexExklusiv)
        return SpielAblauf(gespeichertesSpiel.startzustand, ereignisse).zustand
    }

    fun anzahlZuege(): Int {
        return gespeichertesSpiel.ereignisse.count { it is ProzugBegonnen }
    }
}
