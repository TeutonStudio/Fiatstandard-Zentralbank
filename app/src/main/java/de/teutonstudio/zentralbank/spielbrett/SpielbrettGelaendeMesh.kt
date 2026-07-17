package de.teutonstudio.zentralbank.spielbrett

import kotlin.math.abs
import kotlin.math.sqrt

internal const val GELAENDE_BEVEL_BREITE = 0.12f
internal const val GELAENDE_BEVEL_HOEHE = 0.08f

internal data class GelaendeMeshVektor(
    val x: Float,
    val y: Float,
    val z: Float,
)

internal data class GelaendeMeshEcke(
    val position: GelaendeMeshVektor,
    val normale: GelaendeMeshVektor,
)

internal data class GelaendeMeshDaten(
    val ecken: List<GelaendeMeshEcke>,
    val indizes: List<Int>,
) {
    init {
        require(ecken.isNotEmpty())
        require(indizes.isNotEmpty() && indizes.size % 3 == 0)
        require(indizes.all { it in ecken.indices })
    }
}

/**
 * Erzeugt je Gelaendetyp ein gemeinsames Mesh. Nur Landauflagen werden aufgenommen; Wasser ist
 * keine Auflage und Spezialauflagen behalten ihre bisherige, nicht abgeschraegte Darstellung.
 */
internal fun erstelleAbgeschraegteGelaendeMeshes(
    geometrie: SpielbrettGeometrie,
    auflagen: List<DreieckAuflage>,
): Map<DreieckTyp, GelaendeMeshDaten> = auflagen
    .asSequence()
    .filter { it.ebene == AuflagenEbene.LAND }
    .groupBy(DreieckAuflage::typ)
    .mapValues { (_, landauflagen) ->
        GelaendeMeshErsteller().apply {
            landauflagen.forEach { auflage ->
                fuegeDreieckHinzu(geometrie.dreieck(auflage.position))
            }
        }.baue()
    }

private class GelaendeMeshErsteller {
    private val ecken = mutableListOf<GelaendeMeshEcke>()
    private val indizes = mutableListOf<Int>()

    fun fuegeDreieckHinzu(dreieck: GrundDreieck) {
        val aussen = dreieck.ecken
        val mittelpunkt = dreieck.mittelpunkt
        val innenRadius = abstandZuGerade(mittelpunkt, aussen[0], aussen[1])
        val innenFaktor = (1f - GELAENDE_BEVEL_BREITE / innenRadius).coerceIn(0.5f, 0.95f)
        val innen = aussen.map { ecke ->
            BrettPunkt(
                x = mittelpunkt.x + (ecke.x - mittelpunkt.x) * innenFaktor,
                z = mittelpunkt.z + (ecke.z - mittelpunkt.z) * innenFaktor,
            )
        }
        val basisY = OBERFLAECHEN_ABSTAND
        val obenY = basisY + AUFLAGEN_HOEHE
        val bevelBeginnY = obenY - GELAENDE_BEVEL_HOEHE

        fuegeDreiecksflaecheHinzu(
            punkte = innen.map { punkt -> GelaendeMeshVektor(punkt.x, obenY, punkt.z) },
            normale = GelaendeMeshVektor(0f, 1f, 0f),
        )

        aussen.indices.forEach { index ->
            val naechsterIndex = (index + 1) % aussen.size
            val a = aussen[index]
            val b = aussen[naechsterIndex]
            val innenA = innen[index]
            val innenB = innen[naechsterIndex]
            val deltaX = b.x - a.x
            val deltaZ = b.z - a.z
            val aussenNormale = normalisiere(deltaZ, 0f, -deltaX)

            fuegeViereckHinzu(
                punkte = listOf(
                    GelaendeMeshVektor(a.x, basisY, a.z),
                    GelaendeMeshVektor(b.x, basisY, b.z),
                    GelaendeMeshVektor(b.x, bevelBeginnY, b.z),
                    GelaendeMeshVektor(a.x, bevelBeginnY, a.z),
                ),
                normale = aussenNormale,
            )

            fuegeViereckHinzu(
                punkte = listOf(
                    GelaendeMeshVektor(a.x, bevelBeginnY, a.z),
                    GelaendeMeshVektor(b.x, bevelBeginnY, b.z),
                    GelaendeMeshVektor(innenB.x, obenY, innenB.z),
                    GelaendeMeshVektor(innenA.x, obenY, innenA.z),
                ),
                normale = normalisiere(
                    x = aussenNormale.x * GELAENDE_BEVEL_HOEHE,
                    y = GELAENDE_BEVEL_BREITE,
                    z = aussenNormale.z * GELAENDE_BEVEL_HOEHE,
                ),
            )
        }
    }

    fun baue(): GelaendeMeshDaten = GelaendeMeshDaten(
        ecken = ecken.toList(),
        indizes = indizes.toList(),
    )

    private fun fuegeDreiecksflaecheHinzu(
        punkte: List<GelaendeMeshVektor>,
        normale: GelaendeMeshVektor,
    ) {
        require(punkte.size == 3)
        val ersterIndex = fuegeEckenHinzu(punkte, normale)
        // Die Grunddreiecke laufen in der XZ-Ebene im Uhrzeigersinn; umgekehrt zeigt die
        // Deckflaeche nach oben.
        indizes += listOf(ersterIndex, ersterIndex + 2, ersterIndex + 1)
    }

    private fun fuegeViereckHinzu(
        punkte: List<GelaendeMeshVektor>,
        normale: GelaendeMeshVektor,
    ) {
        require(punkte.size == 4)
        val ersterIndex = fuegeEckenHinzu(punkte, normale)
        indizes += listOf(
            ersterIndex,
            ersterIndex + 2,
            ersterIndex + 1,
            ersterIndex,
            ersterIndex + 3,
            ersterIndex + 2,
        )
    }

    private fun fuegeEckenHinzu(
        punkte: List<GelaendeMeshVektor>,
        normale: GelaendeMeshVektor,
    ): Int = ecken.size.also {
        punkte.forEach { punkt -> ecken += GelaendeMeshEcke(punkt, normale) }
    }
}

private fun abstandZuGerade(
    punkt: BrettPunkt,
    a: BrettPunkt,
    b: BrettPunkt,
): Float {
    val deltaX = b.x - a.x
    val deltaZ = b.z - a.z
    val laenge = sqrt(deltaX * deltaX + deltaZ * deltaZ)
    return abs(deltaZ * punkt.x - deltaX * punkt.z + b.x * a.z - b.z * a.x) / laenge
}

private fun normalisiere(
    x: Float,
    y: Float,
    z: Float,
): GelaendeMeshVektor {
    val laenge = sqrt(x * x + y * y + z * z)
    require(laenge > 0f)
    return GelaendeMeshVektor(x / laenge, y / laenge, z / laenge)
}
