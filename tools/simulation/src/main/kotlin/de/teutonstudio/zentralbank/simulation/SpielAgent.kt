package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.Zufallsquelle
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt

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
        aktionen.firstOrNull { it is SpielAktion.ZahlungsunfaehigkeitFeststellen }?.let { return it }
        aktionen.firstOrNull { it is SpielAktion.ProzugAbschliessen }?.let { return it }
        aktionen.firstOrNull { it is SpielAktion.ZugBeenden }?.let { return it }
        // Erst den Aktionstyp, dann einen konkreten legalen Kandidaten ziehen. Dadurch wird eine
        // Phase nicht allein deshalb praktisch unendlich, weil ein Typ viele Kartenparameter hat.
        val gruppen = aktionen.groupBy { it::class.qualifiedName.orEmpty() }
            .toSortedMap().values.toList()
        val gruppe = gruppen[zufall.naechsteGanzzahl(gruppen.size)]
        return gruppe[zufall.naechsteGanzzahl(gruppe.size)]
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
        if (entscheidungspunkt.beobachtung.zug?.phase == ZugPhase.Epizug) {
            return erste { it is SpielAktion.WaffenstillstandAnnehmen }
                ?: erste { it is SpielAktion.VerwaltungsruineReparieren }
                ?: erste { it is SpielAktion.ZugBeenden }
                ?: aktionen.first()
        }
        if (entscheidungspunkt.zahlungsfaehigkeitsPlan?.kapitulationNoetig == true) {
            erste { it is SpielAktion.KriegKapitulieren }?.let { return it }
        }
        val bestaende = entscheidungspunkt.beobachtung.eigeneWirtschaft.rohstoffe
            .associate { it.rohstoff to it.menge }
        val import = aktionen.filterIsInstance<SpielAktion.MitAuslandHandeln>()
            .filter { it.art == AussenhandelsArt.IMPORT }
            .minWithOrNull(compareBy({ bestaende.getOrDefault(it.rohstoff, 0) }, { it.rohstoff.name }))
        val export = aktionen.filterIsInstance<SpielAktion.MitAuslandHandeln>()
            .filter { it.art == AussenhandelsArt.EXPORT }
            .maxWithOrNull(compareBy({ bestaende.getOrDefault(it.rohstoff, 0) }, { it.rohstoff.name }))
        return erste { it is SpielAktion.VerbindlichkeitBegleichen }
            ?: erste { it is SpielAktion.VerwaltungsstandortVersorgen }
            ?: erste { it is SpielAktion.VerarbeitungAusfuehren }
            ?: erste { it is SpielAktion.ProzugAbschliessen }
            ?: erste { it is SpielAktion.HandelsangebotAnnehmen }
            ?: import
            ?: export
            ?: erste { it is SpielAktion.AnleiheAufstocken }
            ?: erste { it is SpielAktion.AnleiheEmittieren }
            ?: erste { it is SpielAktion.KriegKapitulieren }
            ?: erste { it is SpielAktion.SchuldenstrichDurchfuehren }
            ?: erste { it is SpielAktion.ZahlungsunfaehigkeitFeststellen }
            ?: erste { it is SpielAktion.ZugBeenden }
            ?: aktionen.first()
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
        is SpielAktion.ZahlungsunfaehigkeitFeststellen -> 840
        is SpielAktion.MitAuslandHandeln -> if (punkt.beobachtung.zug?.phase == ZugPhase.Prozug) 750 else 50
        is SpielAktion.AnleiheAufstocken -> if (punkt.beobachtung.zug?.phase == ZugPhase.Prozug) 740 else 40
        is SpielAktion.AnleiheEmittieren -> if (punkt.beobachtung.zug?.phase == ZugPhase.Prozug) 730 else 30
        is SpielAktion.SchuldenstrichDurchfuehren -> 720
        is SpielAktion.KriegKapitulieren -> 710
        is SpielAktion.HandelsangebotAnnehmen -> 700
        is SpielAktion.AnlageErrichten -> 600
        is SpielAktion.EckGebaeudeBauen -> 550
        is SpielAktion.EckGebaeudeAufwerten -> 525
        is SpielAktion.SchieneBauen -> 500
        is SpielAktion.HandelsangebotErstellen ->
            if (punkt.beobachtung.angebote.isEmpty()) 350 else 50
        SpielAktion.ZugBeenden -> 500
        else -> 0
    }

    private fun prozugIstNichtMehrErfuellbar(punkt: Entscheidungspunkt): Boolean {
        if (punkt.beobachtung.zug?.phase != ZugPhase.Prozug) return false
        return punkt.aktionsRaum.aktionen.none { aktion ->
            aktion is SpielAktion.VerbindlichkeitBegleichen ||
                aktion is SpielAktion.VerwaltungsstandortVersorgen ||
                aktion is SpielAktion.VerarbeitungAusfuehren ||
                aktion is SpielAktion.ProzugAbschliessen
        }
    }
}

class AggressiverHeuristikAgent : SpielAgent {
    override val name: String = "aggressiv"
    private val sicherheit = SicherheitsAgent()

    override fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion {
        if (entscheidungspunkt.beobachtung.zug?.phase != ZugPhase.Epizug) {
            return sicherheit.waehleAktion(entscheidungspunkt, zufall)
        }
        return entscheidungspunkt.aktionsRaum.aktionen.maxBy { aktion ->
        when (aktion) {
            is SpielAktion.KriegsEinheitenBewegen -> 1_000
            is SpielAktion.KriegsEinheitBewegen -> 950
            is SpielAktion.KriegsEinheitBauen -> 900
            is SpielAktion.KriegErklaeren -> 850
            is SpielAktion.VerwaltungsruineReparieren -> 800
            is SpielAktion.VerbindlichkeitBegleichen -> 780
            is SpielAktion.VerwaltungsstandortVersorgen -> 770
            is SpielAktion.ProzugAbschliessen -> 760
            is SpielAktion.ZahlungsunfaehigkeitFeststellen -> 750
            // Nach den tatsächlich militärischen Optionen muss der Heuristikagent die Phase
            // beenden; dauerhaft verfügbare Diplomatie-/Transferkandidaten dürfen ihn nicht
            // in einem technisch legalen, aber endlosen Epizug festhalten.
            SpielAktion.ZugBeenden -> 700
            else -> 200
        }
    }
    }
}

class DefensiverHeuristikAgent : SpielAgent {
    override val name: String = "defensiv"
    private val sicherheit = SicherheitsAgent()

    override fun waehleAktion(
        entscheidungspunkt: Entscheidungspunkt,
        zufall: Zufallsquelle,
    ): SpielAktion {
        val defensiv = entscheidungspunkt.aktionsRaum.aktionen.firstOrNull {
            it is SpielAktion.WaffenstillstandAnnehmen ||
                it is SpielAktion.WaffenstillstandAnbieten ||
                it is SpielAktion.VerwaltungsruineReparieren
        }
        return defensiv ?: sicherheit.waehleAktion(entscheidungspunkt, zufall)
    }
}
