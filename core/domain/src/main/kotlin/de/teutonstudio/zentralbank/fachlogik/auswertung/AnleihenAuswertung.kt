package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.FaelligeVerbindlichkeit
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitArt
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId

object AnleihenAuswertung {
    fun freieGeschaeftsbankPlaetze(zustand: SpielZustand, spieler: SpielerId): Int {
        val kontrollierteBanken = zustand.karte?.belegung?.felder.orEmpty().count { feld ->
            KartenAuswertung.kontrolliertGeschaeftsbank(
                requireNotNull(zustand.karte),
                feld.position,
                spieler,
                zustand.konflikte,
            )
        }
        val kapazitaet = maxOf(1, kontrollierteBanken)
        val belegt = zustand.anleihen.values.count { anleihe ->
            anleihe.emittent == spieler && besitzer(zustand, anleihe.id) != KontoId.Spieler(spieler)
        }
        return (kapazitaet - belegt).coerceAtLeast(0)
    }

    fun besitzer(
        zustand: SpielZustand,
        anleihe: AnleiheId,
    ): KontoId? {
        if (anleihe in zustand.bankAnleihen) return KontoId.Bank
        return zustand.spieler
            .firstOrNull { spieler -> anleihe in spieler.anleihen }
            ?.let { spieler -> KontoId.Spieler(spieler.id) }
    }

    fun zinszahlung(anleihe: Anleihe): Geld = anleihe.zinsbetrag
        ?: Geld.cent(anleihe.nennwert.cent * anleihe.zinsBasispunkte / 10_000L)

    fun gesamtschuld(anleihe: Anleihe): Geld =
        anleihe.nennwert + zinszahlung(anleihe) * anleihe.laufzeitRunden

    fun bankgehalteneSchuldensumme(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): Geld = zustand.anleihen.values
        .filter { anleihe ->
            anleihe.emittent == spieler && anleihe.id in zustand.bankAnleihen
        }
        .fold(Geld.NULL) { summe, anleihe -> summe + gesamtschuld(anleihe) }

    fun faelligeVerbindlichkeiten(
        zustand: SpielZustand,
        schuldner: SpielerId,
        zugId: Long,
    ): List<FaelligeVerbindlichkeit> = zustand.anleihen.values
        .asSequence()
        .filter { anleihe -> anleihe.emittent == schuldner }
        .mapNotNull { anleihe ->
            val empfaenger = besitzer(zustand, anleihe.id) ?: return@mapNotNull null
            if (empfaenger == KontoId.Spieler(schuldner)) return@mapNotNull null
            val art = when {
                zustand.rundenzähler == anleihe.faelligkeitsRunde ->
                    VerbindlichkeitArt.RUECKKAUF
                zustand.rundenzähler in (anleihe.emissionsRunde + 1) until
                    anleihe.faelligkeitsRunde -> VerbindlichkeitArt.UNVERMOEGEN
                else -> return@mapNotNull null
            }
            FaelligeVerbindlichkeit(
                id = VerbindlichkeitId(anleihe.id, zugId, art),
                schuldner = schuldner,
                empfaenger = empfaenger,
                betrag = if (art == VerbindlichkeitArt.RUECKKAUF) {
                    anleihe.nennwert
                } else {
                    zinszahlung(anleihe)
                },
            )
        }
        .filter { verbindlichkeit -> verbindlichkeit.betrag > Geld.NULL }
        .sortedWith(
            compareBy<FaelligeVerbindlichkeit> { it.id.art }
                .thenBy { it.id.anleihe.wert },
        )
        .toList()
}
