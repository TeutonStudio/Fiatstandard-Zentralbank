package de.teutonstudio.zentralbank.fachlogik.generierung

import de.teutonstudio.zentralbank.fachlogik.engine.SeedZufallsquelle
import de.teutonstudio.zentralbank.fachlogik.engine.Zufallsquelle
import de.teutonstudio.zentralbank.fachlogik.modell.AKTUELLE_KARTEN_FORMAT_VERSION
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.RohstoffVorkommen
import de.teutonstudio.zentralbank.fachlogik.modell.VorkommensArt
import de.teutonstudio.zentralbank.fachlogik.modell.felder
import kotlin.math.roundToInt

const val AKTUELLE_KARTEN_GENERATOR_VERSION = 1

enum class KartenGeneratorProfil {
    AUSGEWOGEN,
    KONTINENT,
    INSELREICH,
    KARG,
    ROHSTOFFREICH,
}

enum class VorkommensDichte {
    SELTEN,
    NORMAL,
    REICH,
}

data class KartenGeneratorKonfiguration(
    val radius: Int = 10,
    val profil: KartenGeneratorProfil = KartenGeneratorProfil.AUSGEWOGEN,
    val vorkommensDichte: VorkommensDichte = VorkommensDichte.NORMAL,
    val maximaleVersuche: Int = 24,
) {
    init {
        require(radius >= 4) { "Generierte Karten brauchen mindestens Radius 4." }
        require(maximaleVersuche in 1..100) { "Es sind 1 bis 100 Generatorversuche erlaubt." }
    }
}

data class KartenGeneratorErgebnis(
    val vorlage: KartenVorlage,
    val seed: Long,
    val generatorVersion: Int,
    val qualitaet: KartenQualitaet,
)

/**
 * Erzeugt reproduzierbare Karten aus räumlich zusammenhängenden Regionen. Die Zufallsquelle
 * gehört bewusst zur Generatorversion, damit ein Seed zusammen mit [generatorVersion]
 * langfristig rekonstruierbar bleibt.
 */
class KartenGenerator {
    fun generiere(
        seed: Long,
        konfiguration: KartenGeneratorKonfiguration = KartenGeneratorKonfiguration(),
    ): KartenGeneratorErgebnis {
        var bestes: KartenGeneratorErgebnis? = null
        repeat(konfiguration.maximaleVersuche) { versuch ->
            val versuchsSeed = seedFuerVersuch(seed, versuch)
            val vorlage = generiereEinmal(
                hauptSeed = seed,
                versuchsSeed = versuchsSeed,
                konfiguration = konfiguration,
            )
            val qualitaet = KartenQualitaetsPruefung.pruefe(vorlage)
            val ergebnis = KartenGeneratorErgebnis(
                vorlage = vorlage,
                seed = seed,
                generatorVersion = AKTUELLE_KARTEN_GENERATOR_VERSION,
                qualitaet = qualitaet,
            )
            if (bestes == null || ergebnis.qualitaet.punkte > bestes!!.qualitaet.punkte) {
                bestes = ergebnis
            }
            if (qualitaet.spielbar && qualitaet.punkte >= 90) return ergebnis
        }
        return requireNotNull(bestes) { "Der Kartengenerator lieferte kein Ergebnis." }
    }

    /** Verteilt ausschließlich Vorkommen neu und lässt Land, Gelände und Spezialfelder unverändert. */
    fun verteileVorkommenNeu(
        vorlage: KartenVorlage,
        seed: Long,
        dichte: VorkommensDichte = VorkommensDichte.NORMAL,
    ): KartenVorlage {
        val basis = if (vorlage.formatVersion == AKTUELLE_KARTEN_FORMAT_VERSION) {
            vorlage.copy(vorkommen = emptyList())
        } else {
            KartenVorlage(
                formatVersion = AKTUELLE_KARTEN_FORMAT_VERSION,
                id = vorlage.id,
                name = vorlage.name,
                hexagon = vorlage.hexagon,
                gelaendefelder = vorlage.gelaendefelder,
                spezialfelder = vorlage.spezialfelder,
            )
        }
        return verteileVorkommen(
            vorlage = basis,
            zufall = SeedZufallsquelle(seed),
            dichte = dichte,
            profilFaktorProzent = 100,
        )
    }

    private fun generiereEinmal(
        hauptSeed: Long,
        versuchsSeed: Long,
        konfiguration: KartenGeneratorKonfiguration,
    ): KartenVorlage {
        val zufall = SeedZufallsquelle(versuchsSeed)
        val parameter = konfiguration.profil.parameter()
        val hexagon = KartenHexagon(radius = konfiguration.radius)
        val alleFelder = hexagon.felder()
        val landZiel = (alleFelder.size * parameter.landProzent / 100.0)
            .roundToInt()
            .coerceIn(1, alleFelder.size)
        val land = wachseMenge(
            erlaubt = alleFelder.toSet(),
            startAnzahl = parameter.landKeime.coerceAtMost(landZiel),
            zielAnzahl = landZiel,
            zufall = zufall,
        )

        val gelaende = erzeugeGelaende(land, parameter, zufall)
        val basis = KartenVorlage(
            formatVersion = AKTUELLE_KARTEN_FORMAT_VERSION,
            id = "generiert-${seedKennung(hauptSeed)}",
            name = "Generierte Karte $hauptSeed",
            hexagon = hexagon,
            gelaendefelder = gelaende,
        )
        return verteileVorkommen(
            vorlage = basis,
            zufall = zufall,
            dichte = konfiguration.vorkommensDichte,
            profilFaktorProzent = parameter.rohstoffFaktorProzent,
        )
    }

    private fun erzeugeGelaende(
        land: Set<KartenFeld>,
        parameter: ProfilParameter,
        zufall: Zufallsquelle,
    ): List<GelaendeFeld> {
        val frei = land.toMutableSet()
        val zuordnung = mutableMapOf<KartenFeld, GelaendeTyp>()

        fun verteile(typ: GelaendeTyp, prozent: Int, clusterDivisor: Int) {
            val ziel = (land.size * prozent / 100.0).roundToInt().coerceAtMost(frei.size)
            if (ziel <= 0) return
            val cluster = maxOf(1, ziel / clusterDivisor)
            val region = wachseMenge(
                erlaubt = frei,
                startAnzahl = cluster.coerceAtMost(ziel),
                zielAnzahl = ziel,
                zufall = zufall,
            )
            region.forEach { feld -> zuordnung[feld] = typ }
            frei.removeAll(region)
        }

        verteile(GelaendeTyp.GEBIRGE, parameter.gebirgeProzent, 7)
        verteile(GelaendeTyp.WALD, parameter.waldProzent, 8)
        verteile(GelaendeTyp.WUESTE, parameter.wuesteProzent, 10)
        verteile(GelaendeTyp.SUMPF, parameter.sumpfProzent, 10)
        frei.forEach { feld -> zuordnung[feld] = GelaendeTyp.EBENE }

        return zuordnung.entries
            .sortedWith(kartenFeldEintragVergleich())
            .map { (position, typ) -> GelaendeFeld(position, typ) }
    }

    private fun verteileVorkommen(
        vorlage: KartenVorlage,
        zufall: Zufallsquelle,
        dichte: VorkommensDichte,
        profilFaktorProzent: Int,
    ): KartenVorlage {
        val gesperrt = vorlage.spezialfelder.flatMap { it.positionen }.toSet()
        val gebirge = vorlage.gelaendefelder
            .filter { feld -> feld.gelaende == GelaendeTyp.GEBIRGE && feld.position !in gesperrt }
            .map(GelaendeFeld::position)
            .toSet()
        val land = vorlage.gelaendefelder
            .map(GelaendeFeld::position)
            .filterNot { feld -> feld in gesperrt }
            .toSet()
        val belegt = mutableSetOf<KartenFeld>()
        val ergebnis = mutableListOf<RohstoffVorkommen>()
        val dichteFaktor = when (dichte) {
            VorkommensDichte.SELTEN -> 65
            VorkommensDichte.NORMAL -> 100
            VorkommensDichte.REICH -> 150
        }
        val gesamtFaktor = dichteFaktor * profilFaktorProzent / 100

        fun verteileArt(
            art: VorkommensArt,
            erlaubt: Set<KartenFeld>,
            basisProzent: Int,
        ) {
            val frei = erlaubt - belegt
            if (frei.isEmpty()) return
            val ziel = maxOf(
                1,
                (erlaubt.size * basisProzent * gesamtFaktor / 10_000.0).roundToInt(),
            ).coerceAtMost(frei.size)
            val cluster = maxOf(1, ziel / 3)
            val positionen = wachseMenge(
                erlaubt = frei,
                startAnzahl = cluster.coerceAtMost(ziel),
                zielAnzahl = ziel,
                zufall = zufall,
            )
            positionen.forEach { position ->
                belegt += position
                ergebnis += RohstoffVorkommen(position, art)
            }
        }

        verteileArt(VorkommensArt.EISENERZ, gebirge, 8)
        verteileArt(VorkommensArt.KOHLE, gebirge, 8)
        verteileArt(VorkommensArt.LEHM, gebirge, 8)
        verteileArt(VorkommensArt.ROHOEL, land, 4)

        return vorlage.copy(
            formatVersion = AKTUELLE_KARTEN_FORMAT_VERSION,
            vorkommen = ergebnis.sortedWith(
                compareBy<RohstoffVorkommen> { it.position.zeile }
                    .thenBy { it.position.spalte }
                    .thenBy { it.position.haelfte.ordinal }
                    .thenBy { it.art.ordinal },
            ),
        )
    }

    private fun wachseMenge(
        erlaubt: Set<KartenFeld>,
        startAnzahl: Int,
        zielAnzahl: Int,
        zufall: Zufallsquelle,
    ): Set<KartenFeld> {
        if (zielAnzahl <= 0 || erlaubt.isEmpty()) return emptySet()
        val ziel = zielAnzahl.coerceAtMost(erlaubt.size)
        val start = startAnzahl.coerceIn(1, ziel)
        val reihenfolge = erlaubt.toList().zufaelligeReihenfolge(zufall)
        val ergebnis = reihenfolge.take(start).toMutableSet()

        while (ergebnis.size < ziel) {
            val front = ergebnis
                .asSequence()
                .flatMap { feld -> feldNachbarn(feld).asSequence() }
                .filter { kandidat -> kandidat in erlaubt && kandidat !in ergebnis }
                .distinct()
                .toList()
            val kandidat = if (front.isNotEmpty()) {
                front[zufall.naechsteGanzzahl(front.size)]
            } else {
                val rest = erlaubt.filterNot { feld -> feld in ergebnis }
                rest[zufall.naechsteGanzzahl(rest.size)]
            }
            ergebnis += kandidat
        }
        return ergebnis
    }
}

/** Stabiler Seed für Migrationen oder reproduzierbare Ableitungen aus Kartendaten. */
fun stabilerKartenSeed(text: String): Long {
    var hash = 1_125_899_906_842_597L
    text.forEach { zeichen -> hash = 31L * hash + zeichen.code.toLong() }
    return hash
}

private data class ProfilParameter(
    val landProzent: Int,
    val landKeime: Int,
    val waldProzent: Int,
    val gebirgeProzent: Int,
    val wuesteProzent: Int,
    val sumpfProzent: Int,
    val rohstoffFaktorProzent: Int,
)

private fun KartenGeneratorProfil.parameter(): ProfilParameter = when (this) {
    KartenGeneratorProfil.AUSGEWOGEN -> ProfilParameter(76, 3, 25, 22, 12, 10, 100)
    KartenGeneratorProfil.KONTINENT -> ProfilParameter(84, 1, 24, 20, 12, 8, 100)
    KartenGeneratorProfil.INSELREICH -> ProfilParameter(62, 7, 24, 18, 10, 12, 100)
    KartenGeneratorProfil.KARG -> ProfilParameter(74, 3, 14, 30, 26, 8, 95)
    KartenGeneratorProfil.ROHSTOFFREICH -> ProfilParameter(78, 3, 22, 30, 12, 8, 140)
}

private fun seedFuerVersuch(seed: Long, versuch: Int): Long =
    seed xor (-7_046_029_254_386_353_131L * (versuch + 1L))

private fun seedKennung(seed: Long): String = if (seed < 0) "n${seed.toString().removePrefix("-")}" else seed.toString()

private fun <T> List<T>.zufaelligeReihenfolge(zufall: Zufallsquelle): List<T> {
    val werte = toMutableList()
    for (index in werte.lastIndex downTo 1) {
        val tausch = zufall.naechsteGanzzahl(index + 1)
        val zwischen = werte[index]
        werte[index] = werte[tausch]
        werte[tausch] = zwischen
    }
    return werte
}

private fun kartenFeldEintragVergleich(): Comparator<Map.Entry<KartenFeld, GelaendeTyp>> =
    compareBy<Map.Entry<KartenFeld, GelaendeTyp>> { it.key.zeile }
        .thenBy { it.key.spalte }
        .thenBy { it.key.haelfte.ordinal }
