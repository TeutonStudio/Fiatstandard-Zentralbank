package de.teutonstudio.zentralbank.datenbank

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.DIESEL
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.EISEN
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.HOLZ
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.KOHLE
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.LEHM
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.NAHRUNG
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.ROHÖL
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.SCHWERÖL
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.STAHL
import de.teutonstudio.zentralbank.datenbank.Rohstoffe.ZIEGEL
import de.teutonstudio.zentralbank.datenbank.entries
import java.util.EnumMap
import kotlin.collections.associateWith
import kotlin.collections.forEach

@JvmName("pairOfAAndPairBC_toTriple")
fun <A, B, C> Pair<A, Pair<B, C>>.toTriple(): Triple<A, B, C> = Triple(first, second.first, second.second)
@JvmName("pairOfPairABAndC_toTriple")
fun <A, B, C> Pair<Pair<A, B>, C>.toTriple(): Triple<A, B, C> = Triple(first.first, first.second, second)

operator fun EnumMap<Rohstoffe,Int>.times(other:EnumMap<Rohstoffe, Zahlungsmittel>) = map { (other[it.key]?:Zahlungsmittel()) * it.value }.summeGeld { it }
operator fun EnumMap<Rohstoffe, Int>.plus(other: EnumMap<Rohstoffe, Int>): EnumMap<Rohstoffe, Int> = Rohstoffe.associateWith { this.getOrDefault(it, 0) + other.getOrDefault(it, 0) }
operator fun EnumMap<Rohstoffe, Int>.times(factor: Int): EnumMap<Rohstoffe, Int> = map { it.key to it.value * factor }.toEnumMap()
operator fun Int.times(map: EnumMap<Rohstoffe, Int>): EnumMap<Rohstoffe, Int> = map * this

fun EnumMap<Rohstoffe,Int>.zuPreis(marktpreis: EnumMap<Rohstoffe, Zahlungsmittel>): Zahlungsmittel = this * marktpreis

fun List<Pair<Rohstoffe,Int>>.toEnumMap(): EnumMap<Rohstoffe,Int> = Rohstoffe.associateWith { r -> this.find { it.first == r }?.second ?: 0 }

fun List<EnumMap<Rohstoffe,Int>>.sum(): EnumMap<Rohstoffe,Int> = Rohstoffe.associateWith { r -> this.sumOf { it[r]!! } }
fun List<EnumMap<Rohstoffe, Zahlungsmittel>>.toBauteilPreis(): List<Map<Bauteil, Zahlungsmittel>> = this.map { Bauteil.entries.associateWith { b -> b.zuPreis(it) } }

fun  List<Map<out JuristischePerson, Zahlungsmittel>>.toY(spieler: String): List<Int> = this.map { it.toList().find { it.first.name == spieler }?.second?.toIntOderNull() ?: 0 }

@JvmName("listOfMapBauteilAndInt_toSum")
fun List<Map<Bauteil,Int>>.sum(): Map<Bauteil,Int> = Bauteil.entries.associateWith { key -> this.sumOf { it[key] ?: 0 } }
fun Map<out Bauteil,Int>.zuKosten(): EnumMap<Rohstoffe,Int> = this.map { it.key.kosten * it.value }.sum()
fun Map<out Bauteil,Int>.zuPreis(marktpreis: EnumMap<Rohstoffe, Zahlungsmittel>): Zahlungsmittel = this.zuKosten().zuPreis(marktpreis)

fun List<Map<Anleihe,Map<Int,Anleihenhandel>>>.zuDarstellung(runde: Int): Set<AnleiheAnzeige> = this.flatMapIndexed { idx, zuordnung -> zuordnung.entries.map { AnleiheAnzeige(idx,runde,it.key,it.value) } }.toSet()

fun Map<Int, Anleihenhandel>.gläubiger(runde: Int): JuristischePerson? = (runde downTo this.keys.min()).firstNotNullOfOrNull { this[it]?.erwerber }

fun <K,V,T> Map<out Pair<K, V>,T>.forEachTriple(action: (Triple<K,V,T>) -> Unit) = this.forEach { (pair, t) -> action((pair.first to pair.second to t).toTriple()) }

fun String.zuBauteil(): Bauteil? = Bauteil.entries.firstOrNull { it.str == this }
fun Bauteil.Companion.fromString(str: String): Bauteil? = str.zuBauteil()
fun Bauteil.zuPreis(marktpreis: EnumMap<Rohstoffe, Zahlungsmittel>) = kosten * marktpreis
val Bauteil.farbe: Color get() = when (this) {
    Handelslinie.LAND -> Color(0xFF527681)
    Handelslinie.SEE -> Color(0xFF8F6D56)
    Verwaltungsstandort.HAUPTBAHNHOF -> Color(0xFF536D55)
    Verwaltungsstandort.BAHNHOF -> Color(0xFF687B55)
    Verwaltungsstandort.GROSSBAHNHOF -> Color(0xFF946570)
    Verwaltungsstandort.HAFEN -> Color(0xFF5F7392)
    Verwaltungsstandort.GROSSHAFEN -> Color(0xFF88764F)
    Wirtschaftsregionen.GESCHÄFTSBANK -> Color(0xFF6B6288)
    Wirtschaftsregionen.VIEHHOF -> Color(0xFF47756C)
    Wirtschaftsregionen.ANGLER -> Color(0xFF3F728F)
    Wirtschaftsregionen.ZIEGELBRENNER -> Color(0xFF8A604D)
    Wirtschaftsregionen.LEHMINE -> Color(0xFF5F784F)
    Wirtschaftsregionen.FÖRSTER -> Color(0xFF786183)
    Wirtschaftsregionen.BOHRTURM -> Color(0xFF466D87)
    Wirtschaftsregionen.RAFFINERIE -> Color(0xFF8B7943)
    Wirtschaftsregionen.SRAFINNERIE -> Color(0xFF4D7164)
    Wirtschaftsregionen.KOHLEMINE -> Color(0xFF865B63)
    Wirtschaftsregionen.STAHLFABRIK -> Color(0xFF666A7B)
    Wirtschaftsregionen.EISENMINE -> Color(0xFF7B634B)
}
fun Int.zuZinssatz(): String = "$this %"
fun Zahlungsmittel.zuMark(): String = "${toIntOderNull()} ℳ"
fun <V> Bauteil.Companion.associateWith(valueSelector: (Bauteil) -> V): Map<Bauteil,V> = entries.associateWith(valueSelector)

val Bauteil.Companion.entries: Iterable<Bauteil>
    get() = listOf(Handelslinie.entries, Verwaltungsstandort.entries, Wirtschaftsregionen.entries).flatten().toSet()

fun <V> Rohstoffe.Companion.associateWith(valueSelector: (Rohstoffe) -> V): EnumMap<Rohstoffe, V> = EnumMap<Rohstoffe, V>(Rohstoffe::class.java).apply { Rohstoffe.entries.forEach { rohstoff -> this[rohstoff] = valueSelector(rohstoff) } }
fun Rohstoffe.Companion.anzahl(
    nahrung: Int = 0,
    lehm: Int = 0,
    ziegel: Int = 0,
    holz: Int = 0,
    rohöl: Int = 0,
    schweröl: Int = 0,
    diesel: Int = 0,
    kohle: Int = 0,
    stahl: Int = 0,
    eisen: Int = 0,
): EnumMap<Rohstoffe,Int> = EnumMap<Rohstoffe, Int>(Rohstoffe::class.java).apply {
    this[NAHRUNG] = nahrung
    this[LEHM] = lehm
    this[ZIEGEL] = ziegel
    this[HOLZ] = holz
    this[ROHÖL] = rohöl
    this[SCHWERÖL] = schweröl
    this[DIESEL] = diesel
    this[KOHLE] = kohle
    this[STAHL] = stahl
    this[EISEN] = eisen
}

sealed interface Bauteil {
    val str: String
    val kosten: EnumMap<Rohstoffe,Int>

    companion object
}
enum class Handelslinie(override val str: String, override val kosten: EnumMap<Rohstoffe,Int>): Bauteil {
    LAND("eisenbahnlinie",Rohstoffe.anzahl(holz = 1, stahl = 1)),
    SEE("frachtschiff",Rohstoffe.anzahl(stahl = 2)),
}
enum class Verwaltungsstandort(override val str: String, override val kosten: EnumMap<Rohstoffe,Int>, val verbrauch: Map<Rohstoffe,Int>): Bauteil {
    HAUPTBAHNHOF("hauptbahnhof", Rohstoffe.anzahl(), Rohstoffe.anzahl(nahrung=3,kohle=3)),
    BAHNHOF("bahnhof",Rohstoffe.anzahl(holz = 1, ziegel = 2) + Handelslinie.LAND.kosten,Rohstoffe.anzahl(nahrung=1,kohle=1)),
    GROSSBAHNHOF("großbahnhof",Rohstoffe.anzahl(holz = 2, ziegel = 3) + 2*Handelslinie.LAND.kosten,Rohstoffe.anzahl(nahrung=2,kohle=2)),
    HAFEN("hafen",Rohstoffe.anzahl(holz = 1, ziegel = 2) + Handelslinie.SEE.kosten,Rohstoffe.anzahl(nahrung=1,schweröl=1)),
    GROSSHAFEN("großhafen",Rohstoffe.anzahl(holz = 2, ziegel = 3) + 2*Handelslinie.SEE.kosten,Rohstoffe.anzahl(nahrung=2,schweröl=2)),
}

private fun wirtschaftsstandortBaukosten(): EnumMap<Rohstoffe, Int> =
    Rohstoffe.anzahl(holz = 3, ziegel = 2)

enum class Wirtschaftsregionen(override val str:String, override val kosten: EnumMap<Rohstoffe,Int>, val ertrag: Map<Rohstoffe,Int>, val verbrauch: Map<Rohstoffe,Int>): Bauteil {
    GESCHÄFTSBANK("geschäftsbank",wirtschaftsstandortBaukosten(),emptyMap(),emptyMap()),
    VIEHHOF("viehhof",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(nahrung=1),emptyMap()),
    ANGLER("angler",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(nahrung=1),emptyMap()),
    ZIEGELBRENNER("ziegelbrenner",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(ziegel=1),Rohstoffe.anzahl(lehm=1)),
    LEHMINE("lehmmine",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(lehm=1),emptyMap()),
    FÖRSTER("förster",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(holz=1),emptyMap()),
    BOHRTURM("bohrturm",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(rohöl = 1),emptyMap()),
    RAFFINERIE("raffinerie",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(diesel=2,schweröl=1),Rohstoffe.anzahl(rohöl=2)),
    SRAFINNERIE("synthetik raffinerie",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(diesel=2,schweröl=1),Rohstoffe.anzahl(kohle=3)),
    KOHLEMINE("kohlemine",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(kohle=1),emptyMap()),
    STAHLFABRIK("stahlfabrik",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(stahl=2),Rohstoffe.anzahl(eisen=1,kohle=1)),
    EISENMINE("eisenmine",wirtschaftsstandortBaukosten(),Rohstoffe.anzahl(eisen=1),emptyMap()),
}

enum class Rohstoffe(val str: String, val farbe: Color) {
    NAHRUNG("nahrung", Color(0xFF8A6D8F)),
    LEHM("lehm", Color(0xFFB58B5A)),
    ZIEGEL("ziegel", Color(0xFFA75D5D)),
    HOLZ("holz", Color(0xFFC9826B)),
    ROHÖL("rohöl", Color(0xFF6F8061)),
    SCHWERÖL("schweröl", Color(0xFF795F7C)),
    DIESEL("diesel", Color(0xFF8A7A45)),
    KOHLE("kohle", Color(0xFF697078)),
    STAHL("stahl", Color(0xFF718096)),
    EISEN("eisen", Color(0xFF858585));

    companion object
}

data class AnleiheAnzeige(
    val emittiert: Int,
    val emittent: JuristischePerson,
    val aktuellerBesitzer: JuristischePerson,
    val anleihe: Anleihe,
    val handelsverlauf: Map<Int, Anleihenhandel> = emptyMap(),
) {
    constructor(emittiert: Int, runde: Int, anleihe: Anleihe,handelsverlauf: Map<Int, Anleihenhandel>): this(
        emittiert,anleihe.schuldiger,handelsverlauf.gläubiger(runde)!!,anleihe,handelsverlauf
    )

    val schuldiger: JuristischePerson get() = anleihe.schuldiger
    val faelligkeit: Int get() = anleihe.faelligkeitsrunde(emittiert)
    val laufzeit: Int get() = anleihe.laufzeit
    val sondervermoegen: Zahlungsmittel get() = anleihe.sondervermögen
    val unvermoegen: Zahlungsmittel get() = anleihe.unvermögen

    fun erhalteAblauf(): List<AnleiheAblaufEintrag> {
        val ereignisse = mutableListOf<AnleiheAblaufEintrag>()

        handelsverlauf
            .filterKeys { runde -> runde < faelligkeit }
            .toSortedMap()
            .forEach { (runde, handel) ->
            ereignisse += AnleiheAblaufEintrag(
                runde = runde,
                art = if (runde == emittiert) {
                    AnleiheAblaufArt.EMISSION
                } else {
                    AnleiheAblaufArt.HANDEL
                },
                von = handel.besitzer,
                an = handel.erwerber,
                betrag = handel.preis,
            )
        }

        ((emittiert + 1) until faelligkeit).forEach { runde ->
            val gläubiger = handelsverlauf.gläubiger(runde) ?: return@forEach
            ereignisse += AnleiheAblaufEintrag(
                runde = runde,
                art = AnleiheAblaufArt.ZINS,
                von = schuldiger,
                an = gläubiger,
                betrag = unvermoegen,
            )
        }
        handelsverlauf.gläubiger(faelligkeit)?.let { gläubiger ->
            ereignisse += AnleiheAblaufEintrag(
                runde = faelligkeit,
                art = AnleiheAblaufArt.RUECKKAUF,
                von = schuldiger,
                an = gläubiger,
                betrag = sondervermoegen,
            )
        }

        return ereignisse.sortedWith(
            compareBy<AnleiheAblaufEintrag> { it.runde }.thenBy { it.art.reihenfolge }
        )
    }
}

enum class AnleiheAblaufArt(
    val bezeichnung: String,
    internal val reihenfolge: Int,
) {
    EMISSION("Emission", 0),
    HANDEL("Anleihehandel", 0),
    ZINS("Zinszahlung", 1),
    RUECKKAUF("Rückkauf", 2),
}

data class AnleiheAblaufEintrag(
    val runde: Int,
    val art: AnleiheAblaufArt,
    val von: JuristischePerson,
    val an: JuristischePerson,
    val betrag: Zahlungsmittel,
) {
    val zahlungsempfaenger: JuristischePerson get() = when (art) {
        AnleiheAblaufArt.EMISSION,
        AnleiheAblaufArt.HANDEL -> von
        AnleiheAblaufArt.ZINS,
        AnleiheAblaufArt.RUECKKAUF -> an
    }
    val zahlungstaetiger: JuristischePerson get() = when (art) {
        AnleiheAblaufArt.EMISSION,
        AnleiheAblaufArt.HANDEL -> an
        AnleiheAblaufArt.ZINS,
        AnleiheAblaufArt.RUECKKAUF -> von
    }
    val buchungssatz: String get() = "${zahlungstaetiger.name} an ${zahlungsempfaenger.name}"
}
