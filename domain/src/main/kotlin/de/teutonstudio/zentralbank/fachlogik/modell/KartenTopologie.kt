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
    hexagon.enthaelt(position)

fun KartenVorlage.enthaeltFeld(position: KartenFeld): Boolean =
    hexagon.enthaelt(position)

fun KartenHexagon.enthaelt(position: KartenFeld): Boolean =
    benoetigterRadius(position) <= radius

fun KartenHexagon.benoetigterRadius(position: KartenFeld): Int {
    val (x, y) = position.skalierterMittelpunkt()
    val relativX = x - 3L * zentrum.x
    val relativY = y - 3L * zentrum.y
    return maxOf(
        Math.floorDiv(kotlin.math.abs(relativY), 6L) + 1L,
        Math.floorDiv(kotlin.math.abs(2L * relativX + relativY), 12L) + 1L,
        Math.floorDiv(kotlin.math.abs(2L * relativX - relativY), 12L) + 1L,
    ).also { radius ->
        require(radius <= Int.MAX_VALUE) { "Das Dreieck liegt außerhalb des Radius-Zahlenraums." }
    }.toInt()
}

/** Liefert die genau 6 * radius² Dreiecksfelder des Hexagons in stabiler Reihenfolge. */
fun KartenHexagon.felder(): List<KartenFeld> {
    val mittelZeile = Math.floorDiv(zentrum.y, 2)
    val ungefaehreMittelSpalte = Math.floorDiv(zentrum.x - mittelZeile, 2)
    val zeilen = (mittelZeile - radius)..(mittelZeile + radius)
    val spalten = (ungefaehreMittelSpalte - radius * 2)..
        (ungefaehreMittelSpalte + radius * 2)
    return buildList {
        zeilen.forEach { zeile ->
            spalten.forEach { spalte ->
                DreieckHaelfte.entries.forEach { haelfte ->
                    val feld = KartenFeld(zeile, spalte, haelfte)
                    if (enthaelt(feld)) add(feld)
                }
            }
        }
    }.sortedMitKartenPosition().also { felder ->
        check(felder.size.toLong() == anzahlFelder) {
            "Hexagonradius $radius ergab ${felder.size} statt $anzahlFelder Dreiecke."
        }
    }
}

/** Umschließt einen alten rechteckigen Rasterausschnitt verlustfrei mit einem Hexagon. */
fun rechteckAlsHexagon(
    startZeile: Int,
    startSpalte: Int,
    zeilen: Int,
    spalten: Int,
): KartenHexagon {
    require(zeilen > 0 && spalten > 0) { "Der alte Kartenausschnitt muss positiv sein." }
    val endZeile = Math.addExact(startZeile, zeilen - 1)
    val endSpalte = Math.addExact(startSpalte, spalten - 1)
    val mittelZeile = startZeile + (zeilen - 1) / 2
    val mittelSpalte = startSpalte + (spalten - 1) / 2
    val zentrum = KartenFeld(mittelZeile, mittelSpalte, DreieckHaelfte.UNTEN).ecken().first()
    val eckFelder = buildList {
        listOf(startZeile, endZeile).forEach { zeile ->
            listOf(startSpalte, endSpalte).forEach { spalte ->
                DreieckHaelfte.entries.forEach { haelfte ->
                    add(KartenFeld(zeile, spalte, haelfte))
                }
            }
        }
    }
    return KartenHexagon(zentrum).mitMindestradiusFuer(eckFelder)
}

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

private fun KartenFeld.skalierterMittelpunkt(): Pair<Long, Long> = when (haelfte) {
    DreieckHaelfte.UNTEN -> 3L * zeile + 6L * spalte + 3L to 6L * zeile + 2L
    DreieckHaelfte.OBEN -> 3L * zeile + 6L * spalte + 6L to 6L * zeile + 4L
}
