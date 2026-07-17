package de.teutonstudio.zentralbank.spielbrett

import kotlin.math.sqrt

internal const val GRUNDDREIECK_HOEHE = 2f
internal const val AUFLAGEN_HOEHE = 0.32f
internal const val SPEZIAL_AUFLAGEN_HOEHE = 0.14f

// Bei einem gleichseitigen Dreieck gilt h = 3r/2 fuer den Umkreisradius r.
internal const val AUFLAGEN_RADIUS = GRUNDDREIECK_HOEHE * 2f / 3f

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
) {
    private val nachPosition = dreiecke.associateBy(GrundDreieck::position)

    fun dreieck(position: DreieckPosition): GrundDreieck =
        requireNotNull(nachPosition[position]) { "Unbekannte Dreieckposition: $position" }

    fun treffer(punkt: BrettPunkt): DreieckTreffer? {
        val dreieck = dreiecke.firstOrNull { kandidat -> kandidat.enthaelt(punkt) } ?: return null
        val naechsteEcke = dreieck.ecken.indices.minBy { index ->
            val ecke = dreieck.ecken[index]
            val deltaX = ecke.x - punkt.x
            val deltaZ = ecke.z - punkt.z
            deltaX * deltaX + deltaZ * deltaZ
        }
        return DreieckTreffer(dreieck.position, naechsteEcke)
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

internal fun berechneSpielbrettGeometrie(
    zeilen: Int,
    spalten: Int,
    startZeile: Int = 0,
    startSpalte: Int = 0,
): SpielbrettGeometrie {
    require(zeilen > 0)
    require(spalten > 0)

    val seitenlaenge = 2f * GRUNDDREIECK_HOEHE / sqrt(3f)
    val roheDreiecke = buildList {
        repeat(zeilen) { lokalerZeilenIndex ->
            val zeile = startZeile + lokalerZeilenIndex
            val zOben = lokalerZeilenIndex * GRUNDDREIECK_HOEHE
            val zUnten = zOben + GRUNDDREIECK_HOEHE
            val zeilenVersatz = lokalerZeilenIndex * seitenlaenge / 2f

            repeat(spalten) { lokalerSpaltenIndex ->
                val spalte = startSpalte + lokalerSpaltenIndex
                val xLinks = zeilenVersatz + lokalerSpaltenIndex * seitenlaenge
                val a = BrettPunkt(xLinks, zOben)
                val b = BrettPunkt(xLinks + seitenlaenge, zOben)
                val c = BrettPunkt(xLinks + seitenlaenge / 2f, zUnten)
                val d = BrettPunkt(xLinks + seitenlaenge * 1.5f, zUnten)

                add(
                    GrundDreieck(
                        position = DreieckPosition(
                            zeile = zeile,
                            spalte = spalte,
                            ausrichtung = DreieckAusrichtung.UNTEN,
                        ),
                        ecken = listOf(a, b, c),
                    ),
                )
                add(
                    GrundDreieck(
                        position = DreieckPosition(
                            zeile = zeile,
                            spalte = spalte,
                            ausrichtung = DreieckAusrichtung.OBEN,
                        ),
                        ecken = listOf(b, d, c),
                    ),
                )
            }
        }
    }

    val minX = roheDreiecke.minOf { dreieck -> dreieck.ecken.minOf(BrettPunkt::x) }
    val maxX = roheDreiecke.maxOf { dreieck -> dreieck.ecken.maxOf(BrettPunkt::x) }
    val minZ = roheDreiecke.minOf { dreieck -> dreieck.ecken.minOf(BrettPunkt::z) }
    val maxZ = roheDreiecke.maxOf { dreieck -> dreieck.ecken.maxOf(BrettPunkt::z) }
    val mitteX = (minX + maxX) / 2f
    val mitteZ = (minZ + maxZ) / 2f

    return SpielbrettGeometrie(
        dreiecke = roheDreiecke.map { dreieck ->
            dreieck.copy(
                ecken = dreieck.ecken.map { punkt ->
                    BrettPunkt(x = punkt.x - mitteX, z = punkt.z - mitteZ)
                },
            )
        },
        breite = maxX - minX,
        tiefe = maxZ - minZ,
    )
}
