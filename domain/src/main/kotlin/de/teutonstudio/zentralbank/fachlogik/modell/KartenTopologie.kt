package de.teutonstudio.zentralbank.fachlogik.modell

import java.util.ArrayDeque

/** Ganzzahlige Topologie des Dreiecksgitters; unabhängig von der 3D-Darstellung. */
fun KartenFeld.ecken(): List<KartenEcke> {
    val links = Math.addExact(zeile, Math.multiplyExact(spalte, 2))
    val oben = Math.multiplyExact(zeile, 2)
    val a = KartenEcke(links, oben)
    val b = KartenEcke(Math.addExact(links, 2), oben)
    val c = KartenEcke(Math.addExact(links, 1), Math.addExact(oben, 2))
    return when (haelfte) {
        DreieckHaelfte.UNTEN -> listOf(a, b, c)
        DreieckHaelfte.OBEN -> listOf(
            b,
            KartenEcke(Math.addExact(links, 3), Math.addExact(oben, 2)),
            c,
        )
    }
}

fun KartenFeld.kanten(): List<KartenKante> {
    val ecken = ecken()
    return listOf(
        KartenKante.zwischen(ecken[0], ecken[1]),
        KartenKante.zwischen(ecken[1], ecken[2]),
        KartenKante.zwischen(ecken[2], ecken[0]),
    )
}

fun Spielkarte.enthaeltFeld(position: KartenFeld): Boolean =
    position.zeile.toLong() in startZeile.toLong() until endeZeileExklusiv &&
        position.spalte.toLong() in startSpalte.toLong() until endeSpalteExklusiv

fun KartenVorlage.enthaeltFeld(position: KartenFeld): Boolean =
    position.zeile.toLong() in startZeile.toLong() until endeZeileExklusiv &&
        position.spalte.toLong() in startSpalte.toLong() until endeSpalteExklusiv

/** Liefert die geometrisch möglichen Nachbarfelder; Positionen außerhalb sind Wasser. */
fun angrenzendeFelder(ecke: KartenEcke): List<KartenFeld> {
    val ungefaehreZeile = Math.floorDiv(ecke.y, 2)
    return buildList {
        for (zeile in (ungefaehreZeile - 1)..(ungefaehreZeile + 1)) {
            val ungefaehreSpalte = Math.floorDiv(ecke.x - zeile, 2)
            for (spalte in (ungefaehreSpalte - 2)..(ungefaehreSpalte + 2)) {
                DreieckHaelfte.entries.forEach { haelfte ->
                    val feld = KartenFeld(zeile, spalte, haelfte)
                    if (ecke in feld.ecken()) add(feld)
                }
            }
        }
    }.distinct().sortedMitKartenPosition()
}

fun angrenzendeFelder(kante: KartenKante): List<KartenFeld> =
    angrenzendeFelder(kante.anfang)
        .asSequence()
        .filter { feld -> kante in feld.kanten() }
        .distinct()
        .toList()
        .sortedMitKartenPosition()

fun benachbarteEcken(ecke: KartenEcke): Set<KartenEcke> =
    angrenzendeFelder(ecke)
        .flatMap(KartenFeld::ecken)
        .filterNot { kandidat -> kandidat == ecke }
        .filter { kandidat ->
            angrenzendeFelder(ecke).any { feld ->
                KartenKante.zwischen(ecke, kandidat) in feld.kanten()
            }
        }
        .toSet()

fun kantenAbstand(
    start: KartenEcke,
    ziel: KartenEcke,
    maximal: Int = Int.MAX_VALUE,
): Int? {
    if (start == ziel) return 0
    require(maximal >= 0) { "Der maximale Abstand darf nicht negativ sein." }
    val besucht = mutableSetOf(start)
    val offen = ArrayDeque<Pair<KartenEcke, Int>>()
    offen.add(start to 0)
    while (offen.isNotEmpty()) {
        val (aktuell, abstand) = offen.removeFirst()
        if (abstand >= maximal) continue
        benachbarteEcken(aktuell).forEach { nachbar ->
            if (nachbar == ziel) return abstand + 1
            if (besucht.add(nachbar)) offen.add(nachbar to abstand + 1)
        }
    }
    return null
}

private fun List<KartenFeld>.sortedMitKartenPosition(): List<KartenFeld> =
    sortedWith(
        compareBy<KartenFeld>(KartenFeld::zeile)
            .thenBy(KartenFeld::spalte)
            .thenBy { feld -> feld.haelfte.ordinal },
    )
