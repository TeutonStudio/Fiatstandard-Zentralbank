package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand

internal object KonfliktRegelwerk {
    fun kriegErklaeren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegErklaert,
    ): SpielZustand {
        require(ereignis.aggressor != ereignis.verteidiger) {
            "Ein Spieler kann sich nicht selbst Krieg erklaeren."
        }
        require(zustand.spieler.any { spieler -> spieler.id == ereignis.aggressor }) {
            "Unbekannter Spieler: ${ereignis.aggressor.wert}"
        }
        require(zustand.spieler.any { spieler -> spieler.id == ereignis.verteidiger }) {
            "Unbekannter Spieler: ${ereignis.verteidiger.wert}"
        }
        require(zustand.konflikte.none { konflikt ->
            konflikt.betrifft(ereignis.aggressor, ereignis.verteidiger)
        }) {
            "Zwischen diesen Spielern besteht bereits Krieg."
        }
        return zustand.copy(
            konflikte = zustand.konflikte + Konflikt(ereignis.aggressor, ereignis.verteidiger),
        )
    }

    fun kriegBeenden(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegBeendet,
    ): SpielZustand {
        val konflikt = zustand.konflikte.firstOrNull { bestehend ->
            bestehend.betrifft(ereignis.spielerA, ereignis.spielerB)
        } ?: error("Zwischen diesen Spielern besteht kein Krieg.")
        val nachKampf = KriegsEinheitTyp.entries.fold(zustand) { aktuellerZustand, typ ->
            kampfAuswerten(aktuellerZustand, konflikt, typ)
        }
        return nachKampf.copy(
            konflikte = zustand.konflikte - konflikt,
        )
    }

    private fun kampfAuswerten(
        zustand: SpielZustand,
        konflikt: Konflikt,
        typ: KriegsEinheitTyp,
    ): SpielZustand {
        val karte = zustand.karte ?: return zustand
        val einheitenA = karte.belegung.kriegseinheiten
            .filter { einheit -> einheit.besitzer == konflikt.spielerA && einheit.typ == typ }
            .sortedBy { einheit -> einheit.id }
        val einheitenB = karte.belegung.kriegseinheiten
            .filter { einheit -> einheit.besitzer == konflikt.spielerB && einheit.typ == typ }
            .sortedBy { einheit -> einheit.id }
        val (ueberlebendeA, ueberlebendeB) = ueberlebendeTruppen(einheitenA.size, einheitenB.size)
        val ueberlebendeIds = einheitenA.take(ueberlebendeA).mapTo(mutableSetOf()) { it.id }
        einheitenB.take(ueberlebendeB).mapTo(ueberlebendeIds) { it.id }
        val beteiligteIds = (einheitenA + einheitenB).mapTo(mutableSetOf()) { it.id }
        return zustand.copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    kriegseinheiten = karte.belegung.kriegseinheiten.filter { einheit ->
                        einheit.id !in beteiligteIds || einheit.id in ueberlebendeIds
                    },
                ),
            ),
        )
    }

    internal fun ueberlebendeTruppen(anzahlA: Int, anzahlB: Int): Pair<Int, Int> {
        require(anzahlA >= 0 && anzahlB >= 0) { "Truppenzahlen dürfen nicht negativ sein." }
        if (anzahlA == anzahlB) return 0 to 0
        val aIstStaerker = anzahlA > anzahlB
        val staerker = maxOf(anzahlA, anzahlB)
        val unterschied = kotlin.math.abs(anzahlA - anzahlB)
        val ueberlebendeStaerkere = when (unterschied) {
            1 -> 1
            2 -> minOf(3, staerker)
            else -> staerker
        }
        return if (aIstStaerker) ueberlebendeStaerkere to 0 else 0 to ueberlebendeStaerkere
    }
}
