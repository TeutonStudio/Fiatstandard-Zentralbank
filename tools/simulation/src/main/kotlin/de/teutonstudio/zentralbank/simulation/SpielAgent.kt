package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.Zufallsquelle

interface SpielAgent {
    val name: String

    fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion
}

class ZufallsAgent : SpielAgent {
    override val name: String = "zufall"

    override fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion {
        val aktionen = entscheidungspunkt.aktionsRaum.aktionen
        require(aktionen.isNotEmpty()) { "Es gibt keine auswählbare Aktion." }
        return aktionen[zufall.naechsteGanzzahl(aktionen.size)]
    }
}

class SicherheitsAgent : SpielAgent {
    override val name: String = "sicherheit"

    override fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion {
        val aktionen = entscheidungspunkt.aktionsRaum.aktionen
        fun erste(predicate: (SpielAktion) -> Boolean) = aktionen.firstOrNull(predicate)
        return erste { it is SpielAktion.VerbindlichkeitBegleichen }
            ?: erste { it is SpielAktion.VerwaltungsstandortVersorgen }
            ?: erste { it is SpielAktion.VerarbeitungAusfuehren }
            ?: erste { it is SpielAktion.ProzugAbschliessen }
            ?: erste { it is SpielAktion.HandelsangebotAnnehmen }
            ?: strategischeAufgabe(entscheidungspunkt, aktionen)
            ?: erste { it is SpielAktion.ZugBeenden }
            ?: aktionen.first()
    }

    private fun strategischeAufgabe(
        punkt: Entscheidungspunkt,
        aktionen: List<SpielAktion>,
    ): SpielAktion? {
        if (punkt.beobachtung.runde < 12) return null
        val kleinsteId = (punkt.beobachtung.gegner.map { it.id } + punkt.spieler)
            .minBy { it.wert }
        return if (punkt.spieler != kleinsteId) {
            aktionen.firstOrNull { it is SpielAktion.Aufgeben }
        } else null
    }
}

class WirtschaftsAgent : SpielAgent {
    override val name: String = "wirtschaft"

    override fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion {
        val bewertet = entscheidungspunkt.aktionsRaum.aktionen.map { aktion ->
            aktion to prioritaet(aktion, entscheidungspunkt)
        }
        return bewertet.maxWithOrNull(
            compareBy<Pair<SpielAktion, Int>> { it.second }
                .thenByDescending { it.first::class.simpleName.orEmpty() },
        )?.first ?: error("Es gibt keine auswählbare Aktion.")
    }

    private fun prioritaet(aktion: SpielAktion, punkt: Entscheidungspunkt): Int = when (aktion) {
        is SpielAktion.VerbindlichkeitBegleichen -> 1_000
        is SpielAktion.VerwaltungsstandortVersorgen -> 950
        is SpielAktion.VerarbeitungAusfuehren -> 900
        is SpielAktion.ProzugAbschliessen -> 850
        is SpielAktion.HandelsangebotAnnehmen -> 700
        is SpielAktion.AnlageErrichten -> 600
        is SpielAktion.EckGebaeudeBauen -> 550
        is SpielAktion.EckGebaeudeAufwerten -> 525
        is SpielAktion.SchieneBauen -> 500
        is SpielAktion.HandelsangebotErstellen -> 350
        SpielAktion.ZugBeenden -> 100
        is SpielAktion.Aufgeben -> if (
            punkt.beobachtung.runde >= 20 &&
            punkt.spieler != (punkt.beobachtung.gegner.map { it.id } + punkt.spieler)
                .minBy { it.wert }
        ) 200 else -1_000
        else -> 0
    }
}
