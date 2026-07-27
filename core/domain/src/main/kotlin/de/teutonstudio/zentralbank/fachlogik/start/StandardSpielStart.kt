package de.teutonstudio.zentralbank.fachlogik.start

import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerStil
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import de.teutonstudio.zentralbank.fachlogik.modell.kantenAbstand
import de.teutonstudio.zentralbank.fachlogik.technik.bodenModulo

/**
 * Plattformneutraler Startzustand für lokale Clients und reproduzierbare Tests.
 * Android und Browser können denselben Zustand an dieselbe [de.teutonstudio.zentralbank.fachlogik.engine.SpielEngine]
 * übergeben; Darstellung und Persistenz bleiben plattformspezifisch.
 */
fun erstelleStandardSpiel(
    spielerNamen: List<String>,
    seed: Long = 0L,
): SpielZustand {
    val namen = spielerNamen.map(String::trim).filter(String::isNotBlank)
    require(namen.size in 3..7) { "Ein Spiel benötigt drei bis sieben Spieler." }
    require(namen.distinct().size == namen.size) { "Spielernamen müssen eindeutig sein." }

    val ids = namen.indices.map { SpielerId("spieler-${it + 1}") }
    val hexagon = KartenHexagon(radius = maxOf(5, namen.size + 1))
    val felder = hexagon.felder()
    val feldMenge = felder.toSet()
    val startEcken = waehleStartEcken(
        felder.flatMap { it.ecken() }
            .distinct()
            .filter { ecke -> angrenzendeFelder(ecke).all(feldMenge::contains) }
            .sorted(),
        namen.size,
    )
    val karte = Spielkarte(
        id = "standard-$seed",
        name = "Fiatreich Standardkarte",
        hexagon = hexagon,
        gelaendefelder = felder.mapIndexed { index, feld ->
            GelaendeFeld(
                position = feld,
                gelaende = GelaendeTyp.entries[bodenModulo(seed + index, GelaendeTyp.entries.size)],
            )
        },
        belegung = KartenBelegung(
            ecken = startEcken.mapIndexed { index, ecke ->
                EckBelegung(
                    position = ecke,
                    typ = EckGebaeudeTyp.HAUPTBAHNHOF,
                    besitzer = ids[index],
                    gebautInRunde = 0,
                )
            },
        ),
    )

    return SpielZustand(
        spieler = namen.mapIndexed { index, name ->
            Spieler(
                id = ids[index],
                name = name,
                rohstoffe = Rohstoff.entries.associateWith { 8 },
                geldkonto = Geld.mark(100),
                spielstil = SpielerStil.entries[index % SpielerStil.entries.size],
            )
        },
        karte = karte,
        bankkonto = Geld.mark(1_000_000),
        auslandskonto = Geld.mark(1_000_000),
        warenkorb = Rohstoff.entries.associateWith { 1 },
        marktpreise = Rohstoff.entries.associateWith { Geld.mark(5) },
        leitzins = Basispunkte.prozent(2),
    )
}

private fun waehleStartEcken(kandidaten: List<KartenEcke>, anzahl: Int): List<KartenEcke> {
    for (mindestAbstand in 8 downTo 0) {
        val ausgewaehlt = mutableListOf<KartenEcke>()
        kandidaten.forEach { kandidat ->
            if (ausgewaehlt.all { bisher ->
                    kantenAbstand(kandidat, bisher, maximal = mindestAbstand) == null
                }
            ) {
                ausgewaehlt += kandidat
            }
        }
        if (ausgewaehlt.size >= anzahl) return ausgewaehlt.take(anzahl)
    }
    error("Die Standardkarte bietet nicht genug getrennte Startstandorte.")
}
