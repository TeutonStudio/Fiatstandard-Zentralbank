package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import kotlin.math.floor
import kotlin.math.sqrt

internal const val GRUNDDREIECK_HOEHE = 2f
internal const val AUFLAGEN_HOEHE = 0.32f
internal const val SPEZIAL_AUFLAGEN_HOEHE = 0.14f

// Bei einem gleichseitigen Dreieck gilt h = 3r/2 fuer den Umkreisradius r.
internal const val AUFLAGEN_RADIUS = GRUNDDREIECK_HOEHE * 2f / 3f
private val GRUNDDREIECK_SEITENLAENGE = 2f * GRUNDDREIECK_HOEHE / sqrt(3f)

internal data class BrettPunkt(
    val x: Float,
    val z: Float,
)

internal data class GrundDreieck(
    val position: DreieckPosition,
    val ecken: List<BrettPunkt>,
) {
    val mittelpunkt = BrettPunkt(
        x = ecken.sumOf { punkt -> punkt.x.toDouble() }.toFloat() / ecken.size,
        z = ecken.sumOf { punkt -> punkt.z.toDouble() }.toFloat() / ecken.size,
    )
}

internal data class SpielbrettGeometrie(
    val dreiecke: List<GrundDreieck>,
    val breite: Float,
    val tiefe: Float,
    val ursprung: BrettPunkt = BrettPunkt(0f, 0f),
) {
    private val nachPosition = dreiecke.associateBy(GrundDreieck::position)

    fun dreieck(position: DreieckPosition): GrundDreieck =
        requireNotNull(nachPosition[position]) { "Unbekannte Dreieckposition: $position" }

    fun treffer(punkt: BrettPunkt): DreieckTreffer? {
        val dreieck = dreiecke.firstOrNull { kandidat -> kandidat.enthaelt(punkt) } ?: return null
        return dreieck.treffer(punkt)
    }

    fun unbegrenzterTreffer(punkt: BrettPunkt): DreieckTreffer? {
        val absolutesX = punkt.x + ursprung.x
        val absolutesZ = punkt.z + ursprung.z
        val ungefaehreZeile = floor(absolutesZ / GRUNDDREIECK_HOEHE).toInt()
        val ungefaehreSpalte = floor(
            (absolutesX - ungefaehreZeile * GRUNDDREIECK_SEITENLAENGE / 2f) /
                GRUNDDREIECK_SEITENLAENGE,
        ).toInt()
        val kandidaten = buildList {
            for (zeile in (ungefaehreZeile - 1)..(ungefaehreZeile + 1)) {
                for (spalte in (ungefaehreSpalte - 1)..(ungefaehreSpalte + 1)) {
                    DreieckAusrichtung.entries.forEach { ausrichtung ->
                        add(grundDreieck(DreieckPosition(zeile, spalte, ausrichtung), ursprung))
                    }
                }
            }
        }
        return kandidaten.firstOrNull { it.enthaelt(punkt) }?.treffer(punkt)
    }

    private fun GrundDreieck.treffer(punkt: BrettPunkt): DreieckTreffer {
        val naechsteEcke = ecken.indices.minBy { index ->
            val ecke = ecken[index]
            val deltaX = ecke.x - punkt.x
            val deltaZ = ecke.z - punkt.z
            deltaX * deltaX + deltaZ * deltaZ
        }
        val naechsteKante = ecken.indices.minBy { index ->
            val a = ecken[index]
            val b = ecken[(index + 1) % ecken.size]
            quadratischerAbstandZuStrecke(punkt, a, b)
        }
        val ecke = ecken[naechsteEcke]
        val eckenAbstand = sqrt(
            (ecke.x - punkt.x) * (ecke.x - punkt.x) +
                (ecke.z - punkt.z) * (ecke.z - punkt.z),
        )
        val kantenAbstand = sqrt(
            quadratischerAbstandZuStrecke(
                punkt,
                ecken[naechsteKante],
                ecken[(naechsteKante + 1) % ecken.size],
            ),
        )
        return DreieckTreffer(
            position = position,
            naechsteEcke = naechsteEcke,
            naechsteKante = naechsteKante,
            abstandZurNaechstenEcke = eckenAbstand,
            abstandZurNaechstenKante = kantenAbstand,
        )
    }

    fun hexagonUm(treffer: DreieckTreffer): List<DreieckPosition> {
        val mittelpunkt = dreieck(treffer.position).ecken[treffer.naechsteEcke]
        return dreiecke
            .filter { kandidat -> kandidat.ecken.any { ecke -> ecke.fastGleich(mittelpunkt) } }
            .map(GrundDreieck::position)
            .sortedWith(
                compareBy<DreieckPosition>(DreieckPosition::zeile)
                    .thenBy(DreieckPosition::spalte)
                    .thenBy { position -> position.ausrichtung.ordinal },
            )
    }

    fun punkt(ecke: KartenEcke): BrettPunkt? {
        return BrettPunkt(
            x = ecke.x * GRUNDDREIECK_SEITENLAENGE / 2f - ursprung.x,
            z = ecke.y * GRUNDDREIECK_HOEHE / 2f - ursprung.z,
        )
    }
}

private fun quadratischerAbstandZuStrecke(
    punkt: BrettPunkt,
    a: BrettPunkt,
    b: BrettPunkt,
): Float {
    val dx = b.x - a.x
    val dz = b.z - a.z
    val laengeQuadrat = dx * dx + dz * dz
    if (laengeQuadrat <= 0.000001f) {
        val px = punkt.x - a.x
        val pz = punkt.z - a.z
        return px * px + pz * pz
    }
    val anteil = (((punkt.x - a.x) * dx + (punkt.z - a.z) * dz) / laengeQuadrat)
        .coerceIn(0f, 1f)
    val naechsterX = a.x + anteil * dx
    val naechsterZ = a.z + anteil * dz
    val px = punkt.x - naechsterX
    val pz = punkt.z - naechsterZ
    return px * px + pz * pz
}

private fun GrundDreieck.enthaelt(punkt: BrettPunkt): Boolean {
    fun vorzeichen(a: BrettPunkt, b: BrettPunkt, c: BrettPunkt): Float =
        (a.x - c.x) * (b.z - c.z) - (b.x - c.x) * (a.z - c.z)

    val d1 = vorzeichen(punkt, ecken[0], ecken[1])
    val d2 = vorzeichen(punkt, ecken[1], ecken[2])
    val d3 = vorzeichen(punkt, ecken[2], ecken[0])
    val hatNegativ = d1 < -0.0001f || d2 < -0.0001f || d3 < -0.0001f
    val hatPositiv = d1 > 0.0001f || d2 > 0.0001f || d3 > 0.0001f
    return !(hatNegativ && hatPositiv)
}

private fun BrettPunkt.fastGleich(anderer: BrettPunkt): Boolean =
    kotlin.math.abs(x - anderer.x) < 0.0001f &&
        kotlin.math.abs(z - anderer.z) < 0.0001f

internal fun berechneSpielbrettGeometrie(hexagon: KartenHexagon): SpielbrettGeometrie {
    val ursprung = BrettPunkt(
        x = hexagon.zentrum.x * GRUNDDREIECK_SEITENLAENGE / 2f,
        z = hexagon.zentrum.y * GRUNDDREIECK_HOEHE / 2f,
    )
    return geometrieAusPositionen(
        positionen = hexagon.felder().map(KartenFeld::zu3DPosition),
        ursprung = ursprung,
    )
}

internal fun SpielbrettGeometrie.rasterAusschnittUm(
    mitte: DreieckPosition,
    radius: Int = 12,
): SpielbrettGeometrie {
    require(radius > 0)
    val positionen = buildList {
        for (zeile in (mitte.zeile - radius)..(mitte.zeile + radius)) {
            for (spalte in (mitte.spalte - radius)..(mitte.spalte + radius)) {
                DreieckAusrichtung.entries.forEach { ausrichtung ->
                    add(DreieckPosition(zeile, spalte, ausrichtung))
                }
            }
        }
    }
    return geometrieAusPositionen(positionen, ursprung)
}

private fun geometrieAusPositionen(
    positionen: List<DreieckPosition>,
    ursprung: BrettPunkt,
): SpielbrettGeometrie {
    val dreiecke = positionen.map { grundDreieck(it, ursprung) }
    val minX = dreiecke.minOf { it.ecken.minOf(BrettPunkt::x) }
    val maxX = dreiecke.maxOf { it.ecken.maxOf(BrettPunkt::x) }
    val minZ = dreiecke.minOf { it.ecken.minOf(BrettPunkt::z) }
    val maxZ = dreiecke.maxOf { it.ecken.maxOf(BrettPunkt::z) }
    return SpielbrettGeometrie(
        dreiecke = dreiecke,
        breite = maxX - minX,
        tiefe = maxZ - minZ,
        ursprung = ursprung,
    )
}

private fun grundDreieck(
    position: DreieckPosition,
    ursprung: BrettPunkt,
): GrundDreieck {
    val zOben = position.zeile * GRUNDDREIECK_HOEHE - ursprung.z
    val zUnten = zOben + GRUNDDREIECK_HOEHE
    val xLinks = position.zeile * GRUNDDREIECK_SEITENLAENGE / 2f +
        position.spalte * GRUNDDREIECK_SEITENLAENGE - ursprung.x
    val a = BrettPunkt(xLinks, zOben)
    val b = BrettPunkt(xLinks + GRUNDDREIECK_SEITENLAENGE, zOben)
    val c = BrettPunkt(xLinks + GRUNDDREIECK_SEITENLAENGE / 2f, zUnten)
    val d = BrettPunkt(xLinks + GRUNDDREIECK_SEITENLAENGE * 1.5f, zUnten)
    return GrundDreieck(
        position = position,
        ecken = when (position.ausrichtung) {
            DreieckAusrichtung.UNTEN -> listOf(a, b, c)
            DreieckAusrichtung.OBEN -> listOf(b, d, c)
        },
    )
}
