package de.teutonstudio.zentralbank.spielbrett

import kotlin.math.sqrt

internal const val GRUNDDREIECK_HOEHE = 2f
internal const val AUFLAGEN_HOEHE = 1f

// Bei einem gleichseitigen Dreieck gilt h = 3r/2 fuer den Umkreisradius r.
internal const val AUFLAGEN_RADIUS = AUFLAGEN_HOEHE * 2f / 3f

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
}

internal fun berechneSpielbrettGeometrie(
    zeilen: Int,
    spalten: Int,
): SpielbrettGeometrie {
    require(zeilen > 0)
    require(spalten > 0)

    val seitenlaenge = 2f * GRUNDDREIECK_HOEHE / sqrt(3f)
    val roheDreiecke = buildList {
        repeat(zeilen) { zeile ->
            val zOben = zeile * GRUNDDREIECK_HOEHE
            val zUnten = zOben + GRUNDDREIECK_HOEHE
            val zeilenVersatz = zeile * seitenlaenge / 2f

            repeat(spalten) { spalte ->
                val xLinks = zeilenVersatz + spalte * seitenlaenge
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

