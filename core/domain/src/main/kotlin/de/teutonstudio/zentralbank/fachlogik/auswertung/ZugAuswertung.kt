package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase

object ZugAuswertung {
    fun kannProzugAbschliessen(zustand: SpielZustand): Boolean =
        ProzugAuswertung.plan(zustand)?.kannErfolgreichAbschliessen == true

    fun kannZugBeenden(zustand: SpielZustand): Boolean =
        zustand.zugStatus?.let { zug ->
            zug.phase == ZugPhase.Epizug && zug.prozug.erfolgreichAbgeschlossen
        } == true
}
