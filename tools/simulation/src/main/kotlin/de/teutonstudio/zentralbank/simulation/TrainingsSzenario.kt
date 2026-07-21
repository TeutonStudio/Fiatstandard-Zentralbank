package de.teutonstudio.zentralbank.simulation

import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import de.teutonstudio.zentralbank.fachlogik.modell.kantenAbstand

interface TrainingsSzenario {
    val id: String
    fun startzustand(seed: Long): SpielZustand
}

/**
 * Kleine Android-freie Wirtschaftsbaseline mit vollständig im JVM-Code erzeugter Karte.
 * Sie ist kein Ersatz für die Android-Kartendateien, deckt aber Karten-, Versorgungs-,
 * Zug-, Angebots- und Partieablauf ohne Android-Ressourcen ab.
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
                rohstoffe = Rohstoff.entries.associateWith { 30 },
                geldkonto = Geld.mark(100),
                bauteile = mapOf(BauteilTyp.HAUPTBAHNHOF to 1),
            )
        },
        karte = kompakteKarte(seed),
        bankkonto = Geld.mark(10_000),
        auslandskonto = Geld.mark(10_000),
        warenkorb = Rohstoff.entries.associateWith { 1 },
        marktpreise = Rohstoff.entries.associateWith { Geld.mark(5) },
        leitzins = Basispunkte.prozent(2),
    )

    private fun kompakteKarte(seed: Long): Spielkarte {
        val hexagon = KartenHexagon(radius = 6)
        val allePositionen = hexagon.felder()
        val allePositionsMenge = allePositionen.toSet()
        val innereEcken = allePositionen
            .flatMap { feld -> feld.ecken() }
            .distinct()
            .filter { ecke -> angrenzendeFelder(ecke).all(allePositionsMenge::contains) }
            .sorted()
        val startEcken = waehleStartEcken(innereEcken, spielerNamen.size)
        val inselPositionen = startEcken.flatMap(::angrenzendeFelder).toSet()
        val positionen = allePositionen.filter(inselPositionen::contains)
        val gelaende = positionen.mapIndexed { index, feld ->
            val typIndex = Math.floorMod(seed + index.toLong(), GelaendeTyp.entries.size.toLong())
            GelaendeFeld(feld, GelaendeTyp.entries[typIndex.toInt()])
        }
        return Spielkarte(
            id = id,
            name = "Kompakte Trainingskarte",
            hexagon = hexagon,
            gelaendefelder = gelaende,
            belegung = KartenBelegung(
                ecken = startEcken.mapIndexed { index, ecke ->
                    EckBelegung(
                        position = ecke,
                        typ = EckGebaeudeTyp.HAUPTBAHNHOF,
                        besitzer = SpielerId("spieler-${index + 1}"),
                        gebautInRunde = 0,
                    )
                },
            ),
        )
    }

    private fun waehleStartEcken(kandidaten: List<KartenEcke>, anzahl: Int): List<KartenEcke> {
        val ausgewaehlt = mutableListOf<KartenEcke>()
        kandidaten.forEach { kandidat ->
            if (ausgewaehlt.all { bisher ->
                kantenAbstand(kandidat, bisher, maximal = 6) == null
            }) {
                ausgewaehlt += kandidat
            }
        }
        require(ausgewaehlt.size >= anzahl) {
            "Die kompakte Trainingskarte bietet nicht genug getrennte Startstandorte."
        }
        return ausgewaehlt.take(anzahl)
    }
}
