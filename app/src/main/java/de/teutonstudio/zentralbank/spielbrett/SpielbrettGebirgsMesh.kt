package de.teutonstudio.zentralbank.spielbrett

import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val GEBIRGS_PRISMA_HOEHE = 0.34f
internal const val GEBIRGS_PRISMA_HALBBREITE = 0.19f

private data class PunktSchluessel(val x: Int, val z: Int) : Comparable<PunktSchluessel> {
    override fun compareTo(other: PunktSchluessel): Int =
        compareValuesBy(this, other, PunktSchluessel::x, PunktSchluessel::z)
}

private data class KantenSchluessel(
    val anfang: PunktSchluessel,
    val ende: PunktSchluessel,
)

private data class GebirgsKante(
    val anfang: BrettPunkt,
    val ende: BrettPunkt,
    val felder: MutableSet<DreieckPosition> = mutableSetOf(),
)

/**
 * Erzeugt niedrige Dreiecksprismen als Gebirgsgrate. Benachbarte Gebirgsfelder teilen sich ein
 * einziges Prisma auf ihrer Verbindungskante; nur isolierte Felder erhalten einen eigenen Grat.
 */
internal fun erstelleGebirgsPrismaMesh(
    geometrie: SpielbrettGeometrie,
    auflagen: List<DreieckAuflage>,
): GelaendeMeshDaten? {
    val gebirgsPositionen = auflagen
        .asSequence()
        .filter { auflage ->
            auflage.ebene == AuflagenEbene.LAND &&
                auflage.typ.relief == DreieckRelief.GEBIRGE
        }
        .map(DreieckAuflage::position)
        .toSet()
    if (gebirgsPositionen.isEmpty()) return null

    val gebirgsDreiecke = gebirgsPositionen.associateWith(geometrie::dreieck)
    val kanten = mutableMapOf<KantenSchluessel, GebirgsKante>()
    gebirgsDreiecke.forEach { (position, dreieck) ->
        dreieck.ecken.indices.forEach { index ->
            val anfang = dreieck.ecken[index]
            val ende = dreieck.ecken[(index + 1) % dreieck.ecken.size]
            val schluessel = kantenSchluessel(anfang, ende)
            val kante = kanten.getOrPut(schluessel) { GebirgsKante(anfang, ende) }
            kante.felder += position
        }
    }

    val gemeinsameKanten = kanten.values.filter { kante -> kante.felder.size >= 2 }
    val verbundeneFelder = gemeinsameKanten.flatMapTo(mutableSetOf()) { kante -> kante.felder }
    return GebirgsPrismaErsteller().apply {
        gemeinsameKanten.forEach { kante ->
            val (anfang, ende) = kante.anfang.und(kante.ende, randAnteil = 0.08f)
            fuegePrismaHinzu(anfang, ende)
        }
        gebirgsDreiecke
            .filterKeys { position -> position !in verbundeneFelder }
            .values
            .forEach { dreieck ->
                val kantenMitte = dreieck.ecken[0].mittelpunkt(dreieck.ecken[1])
                val anfang = dreieck.mittelpunkt.inRichtung(kantenMitte, anteil = 0.62f)
                val ende = dreieck.mittelpunkt.inRichtung(dreieck.ecken[2], anteil = 0.62f)
                fuegePrismaHinzu(anfang, ende)
            }
    }.baueOderNull()
}

private class GebirgsPrismaErsteller {
    private val ecken = mutableListOf<GelaendeMeshEcke>()
    private val indizes = mutableListOf<Int>()

    fun fuegePrismaHinzu(anfang: BrettPunkt, ende: BrettPunkt) {
        val deltaX = ende.x - anfang.x
        val deltaZ = ende.z - anfang.z
        val laenge = sqrt(deltaX * deltaX + deltaZ * deltaZ)
        require(laenge > 0.001f) { "Ein Gebirgsprisma benötigt eine erkennbare Länge." }
        val seiteX = -deltaZ / laenge * GEBIRGS_PRISMA_HALBBREITE
        val seiteZ = deltaX / laenge * GEBIRGS_PRISMA_HALBBREITE
        val basisY = OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE

        val aLinks = GelaendeMeshVektor(anfang.x - seiteX, basisY, anfang.z - seiteZ)
        val aRechts = GelaendeMeshVektor(anfang.x + seiteX, basisY, anfang.z + seiteZ)
        val aGrat = GelaendeMeshVektor(anfang.x, basisY + GEBIRGS_PRISMA_HOEHE, anfang.z)
        val bLinks = GelaendeMeshVektor(ende.x - seiteX, basisY, ende.z - seiteZ)
        val bRechts = GelaendeMeshVektor(ende.x + seiteX, basisY, ende.z + seiteZ)
        val bGrat = GelaendeMeshVektor(ende.x, basisY + GEBIRGS_PRISMA_HOEHE, ende.z)
        val prismaMitte = listOf(aLinks, aRechts, aGrat, bLinks, bRechts, bGrat).mittelpunkt()

        fuegeKonvexeFlaecheHinzu(listOf(aLinks, aRechts, aGrat), prismaMitte)
        fuegeKonvexeFlaecheHinzu(listOf(bLinks, bGrat, bRechts), prismaMitte)
        fuegeKonvexeFlaecheHinzu(listOf(aLinks, aGrat, bGrat, bLinks), prismaMitte)
        fuegeKonvexeFlaecheHinzu(listOf(aGrat, aRechts, bRechts, bGrat), prismaMitte)
    }

    fun baueOderNull(): GelaendeMeshDaten? = if (ecken.isEmpty()) {
        null
    } else {
        GelaendeMeshDaten(ecken.toList(), indizes.toList())
    }

    private fun fuegeKonvexeFlaecheHinzu(
        punkte: List<GelaendeMeshVektor>,
        prismaMitte: GelaendeMeshVektor,
    ) {
        require(punkte.size >= 3)
        val flaechenMitte = punkte.mittelpunkt()
        var sortiertePunkte = punkte
        var normale = sortiertePunkte.flaechenNormale()
        if (normale.skalarprodukt(flaechenMitte - prismaMitte) < 0f) {
            sortiertePunkte = listOf(punkte.first()) + punkte.drop(1).reversed()
            normale = sortiertePunkte.flaechenNormale()
        }
        val ersterIndex = ecken.size
        sortiertePunkte.forEach { punkt -> ecken += GelaendeMeshEcke(punkt, normale) }
        for (index in 1 until sortiertePunkte.lastIndex) {
            indizes += listOf(ersterIndex, ersterIndex + index, ersterIndex + index + 1)
        }
    }
}

private fun kantenSchluessel(a: BrettPunkt, b: BrettPunkt): KantenSchluessel {
    val aSchluessel = a.schluessel()
    val bSchluessel = b.schluessel()
    return if (aSchluessel <= bSchluessel) {
        KantenSchluessel(aSchluessel, bSchluessel)
    } else {
        KantenSchluessel(bSchluessel, aSchluessel)
    }
}

private fun BrettPunkt.schluessel() = PunktSchluessel(
    x = (x * 10_000f).roundToInt(),
    z = (z * 10_000f).roundToInt(),
)

private fun BrettPunkt.und(anderer: BrettPunkt, randAnteil: Float): Pair<BrettPunkt, BrettPunkt> =
    inRichtung(anderer, randAnteil) to anderer.inRichtung(this, randAnteil)

private fun BrettPunkt.inRichtung(ziel: BrettPunkt, anteil: Float) = BrettPunkt(
    x = x + (ziel.x - x) * anteil,
    z = z + (ziel.z - z) * anteil,
)

private fun BrettPunkt.mittelpunkt(anderer: BrettPunkt) = inRichtung(anderer, 0.5f)

private fun List<GelaendeMeshVektor>.mittelpunkt() = GelaendeMeshVektor(
    x = sumOf { punkt -> punkt.x.toDouble() }.toFloat() / size,
    y = sumOf { punkt -> punkt.y.toDouble() }.toFloat() / size,
    z = sumOf { punkt -> punkt.z.toDouble() }.toFloat() / size,
)

private fun List<GelaendeMeshVektor>.flaechenNormale(): GelaendeMeshVektor {
    val kreuzprodukt = (this[1] - this[0]).kreuz(this[2] - this[0])
    val laenge = sqrt(
        kreuzprodukt.x * kreuzprodukt.x +
            kreuzprodukt.y * kreuzprodukt.y +
            kreuzprodukt.z * kreuzprodukt.z,
    )
    require(laenge > 0.0001f)
    return GelaendeMeshVektor(
        kreuzprodukt.x / laenge,
        kreuzprodukt.y / laenge,
        kreuzprodukt.z / laenge,
    )
}

private operator fun GelaendeMeshVektor.minus(anderer: GelaendeMeshVektor) =
    GelaendeMeshVektor(x - anderer.x, y - anderer.y, z - anderer.z)

private fun GelaendeMeshVektor.kreuz(anderer: GelaendeMeshVektor) = GelaendeMeshVektor(
    x = y * anderer.z - z * anderer.y,
    y = z * anderer.x - x * anderer.z,
    z = x * anderer.y - y * anderer.x,
)

private fun GelaendeMeshVektor.skalarprodukt(anderer: GelaendeMeshVektor): Float =
    x * anderer.x + y * anderer.y + z * anderer.z
