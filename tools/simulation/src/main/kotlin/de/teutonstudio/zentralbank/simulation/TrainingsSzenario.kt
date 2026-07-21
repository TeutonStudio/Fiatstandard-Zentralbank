package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

interface TrainingsSzenario {
    val id: String
    fun startzustand(seed: Long): SpielZustand
}

/**
 * Kleine Android-freie Wirtschaftsbaseline. Die Karten-ID wird protokolliert;
 * bis Kartenressourcen als eigenes JVM-Modul vorliegen, startet diese Baseline
 * bewusst ohne Belegung und prüft den vollständigen Zug-/Angebots-/Partieablauf.
 */
class KleineWirtschaftsBaseline(
    override val id: String = "kleine-wirtschaft-v1",
    private val spielerNamen: List<String> = listOf("Agent-1", "Agent-2", "Agent-3"),
) : TrainingsSzenario {
    init {
        require(spielerNamen.size >= 2) { "Eine Trainingspartie benötigt mindestens zwei Spieler." }
        require(spielerNamen.distinct().size == spielerNamen.size) {
            "Spielernamen eines Szenarios müssen eindeutig sein."
        }
    }

    override fun startzustand(seed: Long): SpielZustand = SpielZustand(
        spieler = spielerNamen.mapIndexed { index, name ->
            Spieler(
                id = SpielerId("spieler-${index + 1}"),
                name = name,
                rohstoffe = Rohstoff.entries.associateWith { 3 },
                geldkonto = Geld.mark(100),
            )
        },
        bankkonto = Geld.mark(10_000),
        auslandskonto = Geld.mark(10_000),
        warenkorb = Rohstoff.entries.associateWith { 1 },
        marktpreise = Rohstoff.entries.associateWith { Geld.mark(5) },
        leitzins = Basispunkte.prozent(2),
    )
}
