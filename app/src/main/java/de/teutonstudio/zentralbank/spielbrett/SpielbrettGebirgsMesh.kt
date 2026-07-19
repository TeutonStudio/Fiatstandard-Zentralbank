package de.teutonstudio.zentralbank.spielbrett

import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val GEBIRGS_PYRAMIDEN_HOEHE = 0.46f

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

/** Liefert die Rasterkanten, an die auf beiden Seiten ein Gebirgsdreieck grenzt. */
internal fun ermittleGebirgsBinnenkanten(
    gebirgsPositionen: Iterable<DreieckPosition>,
): Set<KartenKante> = gebirgsPositionen
    .flatMap { position -> position.zuKartenFeld().kanten() }
    .groupingBy { kante -> kante }
    .eachCount()
    .filterValues { anzahl -> anzahl == 2 }
    .keys

/**
 * Erzeugt über jedem Gebirgsdreieck eine Pyramide mit der Spitze über dem Feldmittelpunkt.
 * Benachbarte Gebirgsfelder besitzen an ihrer gemeinsamen Kante keine inneren Trennwände:
 * Stattdessen verbinden zwei gefüllte Dreiecke beide Pyramidenspitzen mit den Kantenendpunkten.
 */
internal fun erstelleGebirgsPyramidenMesh(
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
    val basisY = OBERFLAECHEN_ABSTAND + AUFLAGEN_HOEHE
    val spitzen = gebirgsDreiecke.mapValues { (_, dreieck) ->
        GelaendeMeshVektor(
            x = dreieck.mittelpunkt.x,
            y = basisY + GEBIRGS_PYRAMIDEN_HOEHE,
            z = dreieck.mittelpunkt.z,
        )
    }
    val kanten = mutableMapOf<KantenSchluessel, GebirgsKante>()
    gebirgsDreiecke.forEach { (position, dreieck) ->
        dreieck.ecken.indices.forEach { index ->
            val anfang = dreieck.ecken[index]
            val ende = dreieck.ecken[(index + 1) % dreieck.ecken.size]
            val schluessel = kantenSchluessel(anfang, ende)
            kanten.getOrPut(schluessel) { GebirgsKante(anfang, ende) }.felder += position
        }
    }

    return GebirgsPyramidenErsteller().apply {
        kanten.values.forEach { kante ->
            val anfang = kante.anfang.alsMeshVektor(basisY)
            val ende = kante.ende.alsMeshVektor(basisY)
            val felder = kante.felder.toList()
            if (felder.size == 1) {
                fuegeSichtbaresDreieckHinzu(
                    anfang,
                    ende,
                    spitzen.getValue(felder.single()),
                )
            } else {
                val ersteSpitze = spitzen.getValue(felder[0])
                val zweiteSpitze = spitzen.getValue(felder[1])
                fuegeSichtbaresDreieckHinzu(anfang, ersteSpitze, zweiteSpitze)
                fuegeSichtbaresDreieckHinzu(ende, zweiteSpitze, ersteSpitze)
            }
        }
    }.baueOderNull()
}

private class GebirgsPyramidenErsteller {
    private val ecken = mutableListOf<GelaendeMeshEcke>()
    private val indizes = mutableListOf<Int>()

    fun fuegeSichtbaresDreieckHinzu(
        a: GelaendeMeshVektor,
        b: GelaendeMeshVektor,
        c: GelaendeMeshVektor,
    ) {
        var punkte = listOf(a, b, c)
        var normale = punkte.flaechenNormale()
        if (normale.y < 0f) {
            punkte = listOf(a, c, b)
            normale = punkte.flaechenNormale()
        }
        val ersterIndex = ecken.size
        punkte.forEach { punkt -> ecken += GelaendeMeshEcke(punkt, normale) }
        indizes += listOf(ersterIndex, ersterIndex + 1, ersterIndex + 2)
    }

    fun baueOderNull(): GelaendeMeshDaten? = if (ecken.isEmpty()) {
        null
    } else {
        GelaendeMeshDaten(ecken.toList(), indizes.toList())
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

private fun BrettPunkt.alsMeshVektor(y: Float) = GelaendeMeshVektor(x, y, z)

private fun List<GelaendeMeshVektor>.flaechenNormale(): GelaendeMeshVektor {
    val kreuzprodukt = (this[1] - this[0]).kreuz(this[2] - this[0])
    val laenge = sqrt(
        kreuzprodukt.x * kreuzprodukt.x +
            kreuzprodukt.y * kreuzprodukt.y +
            kreuzprodukt.z * kreuzprodukt.z,
    )
    require(laenge > 0.0001f) { "Eine Gebirgsfläche darf nicht entartet sein." }
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
