package de.teutonstudio.zentralbank.datenbank

import java.util.EnumMap
import kotlin.collections.associateWith
import kotlin.collections.count
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign



private fun Spiel.baueCache(idx:Int): Zahlungsmittel {
    if (idx == 0) return Zahlungsmittel()
    return warenkorb.entries.summeGeld { (key, value) -> marktpreise[idx][key]!! * value }
}
private fun Spieler.baueCache(idx:Int,cache:List<Map<out Bauteil,Int>>): Map<Bauteil,Int> {
    if (idx == 0) return Bauteil.entries.associateWith { (gebaut.first()[it]?:0) + (kontrolle.first()[it]?:0) }
    val vorherigerStand = cache.getOrNull(idx-1) ?: emptyMap()
    val aktuellerBau = gebaut.getOrNull(idx) ?: emptyMap()
    val aktuelleKontrolle = kontrolle.getOrNull(idx) ?: emptyMap()
    return Bauteil.entries.associateWith {
        (vorherigerStand[it]?:0) + (aktuellerBau[it]?:0) + (aktuelleKontrolle.getOrDefault(it,0))
    }
}
private fun Handelsregister.baueCache(idx:Int,cache:List<EnumMap<Rohstoffe,Zahlungsmittel>>): EnumMap<Rohstoffe,Zahlungsmittel> {
    if (idx == 0) return Rohstoffe.associateWith { Zahlungsmittel() }
    val vorherigeMarktpreise = cache.getOrNull(idx-1) ?: emptyMap()
    val relevante = erhalteMarktpreisRelevante().getOrNull(idx) ?: emptySet()
    val sortiert = relevante.groupBy { it.rohstoff }
    return Rohstoffe.associateWith {
        val menge = sortiert[it]
        if (menge == null || menge.count() == 0) vorherigeMarktpreise[it]!! else menge.summeGeld { h -> h.einzelpreis() } / menge.count()
    }
}
private fun Handelsregister.baueLiquidität(idx:Int,liquidität:List<Map<JuristischePerson, Zahlungsmittel>>): Map<JuristischePerson, Zahlungsmittel> {
    val vorherigeLiquidität = liquidität.getOrNull(idx - 1) ?: emptyMap()
    val neu = bekannteSpieler().associateWith { spieler -> vorherigeLiquidität[spieler] ?: Zahlungsmittel() }.toMutableMap()
    jeHandelZurRunde(idx,{ neu.handelt(it) })
    // Zinsen und Rückkauf am Ende der Runde buchen
    erhalteRelevanteAnleihen(idx).forEachTriple { (emittiert,anleihe,handelsZuordnung) ->
        val rückkauf = emittiert+anleihe.laufzeit+1
        val indexListe = (emittiert..rückkauf)
        if (idx in indexListe) {
            indexListe.forEach { runde ->
                val handel = handelsZuordnung[runde]
                val besitzer = handelsZuordnung.gläubiger(runde)!!
                if (idx == runde) {
                    if (emittiert == idx && handel != null) {
                        // teil von rundenHandel
                    } else if (runde == rückkauf) { neu.emittiert(anleihe,besitzer) } else {
                        if (handel != null) {/* teil von rundenHandel */}
                        neu.zins(anleihe,besitzer)
                    }

                }
            }
        }
    }
    return neu
}
private fun Handelsregister.baueVerbindlichkeiten(idx:Int): Map<JuristischePerson, Zahlungsmittel> {
    val neu = bekannteSpieler().associateWith { Zahlungsmittel() }.toMutableMap()
    // Zinsen und Rückkauf am Ende der Runde buchen
    erhalteRelevanteAnleihen(idx).forEachTriple { (emittiert,anleihe,handelsZuordnung) ->
        val rückkauf = emittiert+anleihe.laufzeit+1
        val indexListe = (emittiert..rückkauf)
        if (idx in indexListe) {
            (emittiert..rückkauf).forEach { runde ->
                val handel = handelsZuordnung[runde]
                val besitzer = handelsZuordnung.gläubiger(runde)!!
                if (runde < idx) { // alles was in Zukunft noch aussteht
                    if (emittiert == idx && handel != null) {
                        // keine Verbindlichkeit
                    } else if (runde == rückkauf) { neu.emittiert(anleihe,besitzer) } else {
                        if (handel != null) {/* keine Verbindlichkeit */}
                        neu.zinsVerbindlichkeit(anleihe,besitzer)
                    }
                }
            }
        }
    }
    return neu
}
private fun Kriegsregister.baueCache(idx: Int ,cache: List<Map<String, KonfliktStatus>>): Map<String, KonfliktStatus> {
    val status = cache.getOrNull(idx - 1).orEmpty().mapValues { (spieler, alterStatus) ->
        VeränderbarerKonfliktStatus(
            krieg = alterStatus.krieg.toMutableSet(),
            waffenstillstand = alterStatus.waffenstillstand.toMutableSet(),
            frieden = alterStatus.frieden.toMutableSet()
        )
    }.toMutableMap()

    fun statusVon(spieler: String): VeränderbarerKonfliktStatus {
        return status.getOrPut(spieler) { VeränderbarerKonfliktStatus() }
    }

    fun setzeBeziehung(
        spielerA: String,
        spielerB: String,
        vertragsart: Vertragsart
    ) {
        if (spielerA == spielerB) return

        val statusA = statusVon(spielerA)
        val statusB = statusVon(spielerB)

        when (vertragsart) {
            Vertragsart.KRIEGSERKLÄRUNG -> {
                statusA.krieg.add(spielerB)
                statusA.waffenstillstand.remove(spielerB)
                statusA.frieden.remove(spielerB)

                statusB.krieg.add(spielerA)
                statusB.waffenstillstand.remove(spielerA)
                statusB.frieden.remove(spielerA)
            }

            Vertragsart.WAFFENSTILLSTAND -> {
                statusA.krieg.remove(spielerB)
                statusA.waffenstillstand.add(spielerB)
                statusA.frieden.remove(spielerB)

                statusB.krieg.remove(spielerA)
                statusB.waffenstillstand.add(spielerA)
                statusB.frieden.remove(spielerA)
            }

            Vertragsart.FRIEDENSERKLÄRUNG -> {
                statusA.krieg.remove(spielerB)
                statusA.waffenstillstand.remove(spielerB)
                statusA.frieden.add(spielerB)

                statusB.krieg.remove(spielerA)
                statusB.waffenstillstand.remove(spielerA)
                statusB.frieden.add(spielerA)
            }
        }
    }

    jeKonfliktZurRunde(idx) { vertrag ->
        val anbieter = vertrag.vertragsanbieter
        val annehmer = vertrag.vertragsannehmer

        // Alle Beteiligten als "bekannte Spieler" registrieren
        (anbieter + annehmer).forEach { statusVon(it) }

        // Beziehungen nur zwischen den beiden Vertragsseiten setzen
        for (a in anbieter) {
            for (b in annehmer) {
                setzeBeziehung(a, b, vertrag.vertragsart)
            }
        }
    }

    return status.mapValues { (spieler, wert) -> wert.zuKonfliktStatus() }
}

const val prozentpunkt = 100
open class Spiel(
    private val runden: MutableList<Runde>,
    private val spieler: List<Spieler>, // Zu beginn des Spiels definiert
    public val warenkorb: Map<Rohstoffe, Int> = emptyMap(), // Zu beginn des Spiels definiert
    private val inflationsziel: Triple<Float,Float,Float>, // Zielwert, ein schritt, zwei schritte
    private val handel: Handelsregister, // Handelsdaten während des Spiels
    private val konflikt: Kriegsregister,
) {
    constructor(
        leitzinssatz: Float,
        spieler: Map<Spieler,Zahlungsmittel>,
        warenkorb: Map<Rohstoffe, Int>,
        inflationsziel: Float,
        normaleAbweichung: Float,
        starkeAbweichung: Float,
    ): this(
        mutableListOf(Runde(0,leitzinssatz)),
        spieler.keys.toList(), warenkorb, (inflationsziel to normaleAbweichung to starkeAbweichung).toTriple(),
        Handelsregister(spieler.map { (spieler,guthaben) ->
            Anleihenhandel(spieler, Geschäftsbank,Anleihe(spieler, Zahlungsmittel(),-guthaben,1),Zahlungsmittel())
        }.toSet()), Kriegsregister()
    )

/*    constructor(daten:SpielDaten): this(
        TODO()
    )*/

    public val spielerStringListe = spieler.map { it.name }
    public val spielerListe = spieler.map { it }
    public val spielerMarktwert: List<Map<Spieler, Zahlungsmittel>> get() = List(aktuelleRunde) { idx -> spielerListe.associateWith { it.erhalteBauSaldoZurRunde(idx).zuKosten() * marktpreise[idx] }}
    public val spielerSaldo: List<Map<Spieler, Zahlungsmittel>> get() = List(aktuelleRunde) { idx -> spielerListe.associateWith { handel.erhalteSpielerSaldoZurRunde(idx,it) } }
    public val spielerSchulden: List<Map<Spieler, Zahlungsmittel>> get() = List(aktuelleRunde) { idx -> spielerListe.associateWith { handel.erhalteSpielerSchuldenZurRunde(idx,it) } }
    public val nächsterZinssatz: Float get() = runden.last().leitzinssatz + erhalteZinssatzSchritte()
    public val emittierteAnleihen: List<Set<Anleihe>> get() = List(aktuelleRunde) { handel.erhalteEmittierteAnleihen()[it].filter { a -> a.sondervermögen != Zahlungsmittel() }.toSet() }
    public val anleihen: Set<AnleiheAnzeige> get() = emittierteAnleihen.map { anleihen -> anleihen.associateWith { handel.erhalteRelevanteAnleihenhandel(it) } }.zuDarstellung(aktuelleRunde)
    public val aktuelleMarktpreise: EnumMap<Rohstoffe, Zahlungsmittel> get() = handel.erhalteMarktpreisZurRunde(aktuelleRunde-1)
    public val marktpreise: List<EnumMap<Rohstoffe, Zahlungsmittel>> get() = List(aktuelleRunde) { handel.erhalteMarktpreisZurRunde(it) }
    public val aktuelleRunde: Int get() = runden.size
    private val cache: MutableList<Zahlungsmittel> = mutableListOf()
    init { List(aktuelleRunde) { cache.add(baueCache(it)) } }

    private fun erhaltePreisWarenkorbZurRunde(runde: Int): Zahlungsmittel {
        if (runde == 0) return Zahlungsmittel()
        val marktpreise = handel.erhalteMarktpreisZurRunde(runde - 1)
        return warenkorb.entries.summeGeld { (key, value) -> marktpreise[key]!! * value }
    }

    private fun erhaltePreisinflationZurRunde(runde: Int): Float {
        if (runde == 0 || runde == 1) return 0f
        val vorher = erhaltePreisWarenkorbZurRunde(runde - 1)
        if (vorher == Zahlungsmittel()) return 0f
        return prozentpunkt*erhaltePreisWarenkorbZurRunde(runde).erhaltePreisinflation(vorher)
    }

    private fun erhalteZinssatzSchritte(runde: Int = runden.size): Float {
        val infaltionsAbweichung = inflationsziel.first - erhaltePreisinflationZurRunde(runde)
        if (abs(infaltionsAbweichung) < inflationsziel.second) return 0f
        if (abs(infaltionsAbweichung) < inflationsziel.third) return sign(infaltionsAbweichung)
        return sign(infaltionsAbweichung) * 2
    }

    public fun leitzinssatz(runde: Int): Float? = runden.find { it.index == runde }?.leitzinssatz

    public fun neueRundenDatenDefinieren(
        spielerDaten: Map<Spieler,Pair<Map<Bauteil,Int>,Map<Wirtschaftsregionen,Int>>>,
        handelDaten: Set<Handel>,
        konfliktDaten: Set<Vertrag>
    ) {
        runden.add(Runde(runden.size,nächsterZinssatz))
        spieler.forEach {
            val daten = spielerDaten[it]?: (emptyMap<Bauteil,Int>() to emptyMap<Wirtschaftsregionen,Int>())
            it.neueRundenDatenDefinieren(daten.first,daten.second)
        }
        handel.neueRundenDatenDefinieren(handelDaten)
        konflikt.neueRundenDatenDefinieren(konfliktDaten)
        cache.add(baueCache(cache.size))
    }

    fun zuSpeicherDaten(): Pair<SpielDaten,List<SpeicherDaten>> {
        val spielerDaten = spieler.map { it.zuSpeicherDaten() }
        val daten = SpielDaten(warenkorb,spieler,inflationsziel)
        val rundenDaten = runden.map { it.zuSpeicherDaten() }
        val bauDaten = spieler.flatMap { it.zuSpeicherBauDaten(rundenDaten,spielerDaten.find { d -> it.name == d.spielerName }!!) }
        val kontrollDaten = spieler.flatMap { it.zuSpeicherKontrollDaten(rundenDaten,spielerDaten.find { d -> it.name == d.spielerName }!!) }
        val handelsDaten = handel.zuSpeicherHandelDaten(rundenDaten)
        val anleiheDaten = handel.zuSpeicherAnleihenDaten(rundenDaten)
        val vertragsDaten = konflikt.zuSpeicherDaten(rundenDaten)
        val speicherListe = emptySequence<SpeicherDaten>().plus(rundenDaten).plus(spielerDaten).plus(bauDaten).plus(kontrollDaten).plus(handelsDaten).plus(anleiheDaten).plus(vertragsDaten).toList()
        return daten to speicherListe
    }
}

data class Runde(
    val index: Int,
    val leitzinssatz: Float,
) {
    fun zuSpeicherDaten(): RundeDaten = RundeDaten(index,leitzinssatz)
}

sealed interface JuristischePerson{
    val name: String
}

data object Geschäftsbank: JuristischePerson {
    override val name: String = "Geschäftsbank"
}

data object Ausland: JuristischePerson {
    override val name: String = "Ausland"
}

class Spieler(
    override val name: String,
    public val gebaut: MutableList<Map<out Bauteil,Int>>, // index=runde, zuordnung=was gebaut/was zerstört
    public val kontrolle: MutableList<Map<Wirtschaftsregionen,Int>> // index=runde, zuordnung kontrollverlust/kontrollgewinn
): JuristischePerson {
    constructor(name: String, runde0gebaut: Map<out Bauteil,Int>, runde0kontrolle: Map<Wirtschaftsregionen,Int>): this(
        name,mutableListOf(runde0gebaut),mutableListOf(runde0kontrolle)
    )
    constructor(name: String, runde0gebaut: Map<out Bauteil,Int>): this(
        name,runde0gebaut,emptyMap<Wirtschaftsregionen,Int>()
    )

    private val cache: MutableList<Map<out Bauteil,Int>> = mutableListOf()
    init { List(max(gebaut.size, kontrolle.size)) { cache.add(baueCache(it,cache)) } }

    public fun erhalteBauteilSaldoZurRunde(runde:Int=cache.lastIndex, bauteil: Bauteil): Int = cache[runde][bauteil]!!
    public fun erhalteBauSaldoZurRunde(runde:Int=cache.lastIndex): Map<out Bauteil,Int> = cache[runde]

    public fun neueRundenDatenDefinieren(
        neuGebaut:Map<Bauteil,Int>,
        neuKontrolle:Map<Wirtschaftsregionen,Int>
    ) {
        gebaut.addLast(neuGebaut)
        kontrolle.addLast(neuKontrolle)
        cache.add(baueCache(cache.size,cache))
    }

    fun zuSpeicherDaten(): SpielerDaten = SpielerDaten(name)
    private fun <A,T> Iterable<Map<out A,Int>>.speicherMap(rundenDaten: List<RundeDaten>,transform: (RundeDaten,A,Int) -> T): List<T> {
        return this.flatMapIndexed { index, map -> map.map { (etwas,anzahl) -> transform(rundenDaten.find { it.index == index }!!,etwas,anzahl) } }
    }
    fun zuSpeicherBauDaten(rundenDaten: List<RundeDaten>,spielerDaten: SpielerDaten): List<BauteilDaten> {
        return gebaut.speicherMap(rundenDaten) { runde,bauteil,anzahl -> BauteilDaten(spielerDaten, runde, bauteil, anzahl) }
    }

    fun zuSpeicherKontrollDaten(rundenDaten: List<RundeDaten>,spielerDaten: SpielerDaten): List<KontrolleDaten> {
        return kontrolle.speicherMap(rundenDaten) {runde,region,anzahl -> KontrolleDaten(spielerDaten, runde, region, anzahl) }
    }
}

data class Handelsregister(
    private val einträge: MutableList<Set<Handel>> = mutableListOf(emptySet()) // index=runde handel in der Runde
) {
    constructor(runde0: Set<Handel>): this(
        mutableListOf(runde0)
    )

    private val cache: MutableList<EnumMap<Rohstoffe,Zahlungsmittel>> = mutableListOf()
    private val liquidität: MutableList<Map<JuristischePerson, Zahlungsmittel>> = mutableListOf()
    private val verbindlichkeiten: MutableList<Map<JuristischePerson, Zahlungsmittel>> = mutableListOf()
    init { List(einträge.size) {
        cache.add(baueCache(it,cache))
        liquidität.add(baueLiquidität(it,liquidität))
        verbindlichkeiten.add(baueVerbindlichkeiten(it))
    } }

    public fun bekannteSpieler(): Set<JuristischePerson> {
        return einträge.flatMap { runde -> runde.flatMap { handel -> when (handel) {
            is Anleihenhandel -> listOf(handel.besitzer,handel.erwerber,handel.anleihe.schuldiger)
            else -> listOf(handel.besitzer,handel.erwerber)
        } } }.toSet()
    }

    public fun erhalteRelevanteAnleihen(runde: Int): Map<Pair<Int, Anleihe>,Map<Int, Anleihenhandel>> = erhalteEmittierteAnleihen().flatMapIndexed { emittiert, anleihen -> anleihen.map {
        if (runde in emittiert..emittiert+it.laufzeit+1) (emittiert to it) to erhalteRelevanteAnleihenhandel(it) else null
    } }.filterNotNull().toMap()

    public fun erhalteMarktpreisRelevante(): List<Set<RohstoffHandel>> = einträge.map { it.filterIsInstance<RohstoffHandel>().toSet() }
    private fun erhalteSpielerRelevante(): List<Set<Anleihenhandel>> = einträge.map { it.filterIsInstance<Anleihenhandel>().toSet() }
    public fun erhalteRelevanteAnleihenhandel(anleihe: Anleihe): Map<Int, Anleihenhandel> = erhalteSpielerRelevante().flatMapIndexed { runde, handel -> handel.map { runde to it } }.groupBy { it.second.anleihe }[anleihe]?.toMap() ?: emptyMap()
    public fun erhalteEmittierteAnleihen(): List<Set<Anleihe>> {
        val emittierteAnleihen = MutableList(einträge.size) { mutableSetOf<Anleihe>() }
        val bereitsGesehen = mutableSetOf<Anleihe>()
        erhalteSpielerRelevante().forEachIndexed { runde, handelsMenge -> handelsMenge.forEach { handel ->
            if (bereitsGesehen.add(handel.anleihe)) { emittierteAnleihen[runde].add(handel.anleihe) }
        } }
        return emittierteAnleihen.map { it.toSet() }
    }

    public fun erhalteRohstoffMarktpreisZurRunde(runde: Int,rohstoff: Rohstoffe): Zahlungsmittel = cache[runde][rohstoff]!!
    public fun erhalteMarktpreisZurRunde(runde: Int): EnumMap<Rohstoffe, Zahlungsmittel> = cache[runde]

    public fun erhalteSpielerSaldoZurRunde(runde: Int, spieler: Spieler): Zahlungsmittel = liquidität[runde][spieler]!!
    public fun erhalteSaldoZurRunde(runde: Int): Map<JuristischePerson,Zahlungsmittel> = liquidität[runde]

    public fun erhalteSpielerSchuldenZurRunde(runde: Int, spieler: Spieler): Zahlungsmittel = verbindlichkeiten[runde][spieler]!!
    public fun erhalteSchuldenZurRunde(runde: Int): Map<JuristischePerson,Zahlungsmittel> = verbindlichkeiten[runde]

    public fun jeHandelZurRunde(runde:Int,jeHandel:(Handel) -> Unit) = einträge[runde].forEach { jeHandel(it) }

    public fun neueRundenDatenDefinieren(neuGehandelt: Set<Handel>) {
        einträge.add(neuGehandelt)
        cache.add(baueCache(cache.size,cache))
        liquidität.add(baueLiquidität(liquidität.size,liquidität))
        verbindlichkeiten.add(baueVerbindlichkeiten(verbindlichkeiten.size))
    }

    private inline fun <reified R,T> Iterable<Set<Handel>>.speicherMap(rundenDaten: List<RundeDaten>,transform:(RundeDaten,R) -> T): List<T> {
        return this.flatMapIndexed { idx, handel -> handel.filterIsInstance<R>().map { h -> transform(rundenDaten.find { it.index == idx }!!, h) } }
    }
    public fun zuSpeicherHandelDaten(rundenDaten: List<RundeDaten>): List<HandelsDaten> {
        return einträge.speicherMap<RohstoffHandel,_>(rundenDaten) { rundeDaten, handel -> HandelsDaten(rundeDaten,handel) }
    }

    public fun zuSpeicherAnleihenDaten(rundenDaten: List<RundeDaten>): List<AnleiheDaten> {
        val liste = einträge.mapIndexed { index, handel -> handel.filterIsInstance<Anleihenhandel>().map { index to it } }.flatten()
        val gruppen = liste.groupBy { it.second.anleihe }.map { it.key to it.value.toMap() }.toMap()
        return gruppen.map { (anleihe,handelsListe) -> AnleiheDaten(rundenDaten.find { handelsListe.getLowest().key == it.index }!!,handelsListe) }
    }
}

abstract class Handel(
    open val besitzer: JuristischePerson,
    open val erwerber: JuristischePerson,
) {
    abstract fun erhalteBetrag(): Zahlungsmittel
}

data class Anleihenhandel(
    override val besitzer: JuristischePerson,
    override val erwerber: JuristischePerson,
    val anleihe: Anleihe,
    val preis: Zahlungsmittel
): Handel(besitzer, erwerber) {
    override fun erhalteBetrag(): Zahlungsmittel {
        return preis
    }
}

class Anleihe(
    val schuldiger: JuristischePerson,
    val sondervermögen: Zahlungsmittel,
    val unvermögen: Zahlungsmittel,
    val laufzeit: Int,
) {
    fun erhalteZinssatz(): Int { // unvermögen/sondervermögen*100
        return (sondervermögen.erhalteZinssatz(unvermögen) * 100).toInt()
    }
}

data class RohstoffHandel(
    override val besitzer: JuristischePerson,
    override val erwerber: JuristischePerson,
    val betrag: Zahlungsmittel,
    val anzahl: Int,
    val rohstoff: Rohstoffe,
) : Handel(besitzer, erwerber) {
    override fun erhalteBetrag(): Zahlungsmittel {
        return betrag
    }

    fun einzelpreis(): Zahlungsmittel {
        return betrag / anzahl
    }
}

data class Kriegsregister(
    private val einträge: MutableList<Set<Vertrag>> = mutableListOf(emptySet()),
) {
    private val cache: MutableList<Map<String, KonfliktStatus>> = mutableListOf()
    init { List(einträge.size) { cache.add(baueCache(it,cache)) } }


    fun erhalteSpielerStatusZurRunde(runde: Int,spieler: String): KonfliktStatus = cache[runde][spieler]?: KonfliktStatus()
    fun erhalteStatusZurRunde(runde: Int): Map<String, KonfliktStatus> = cache[runde]

    public fun jeKonfliktZurRunde(runde:Int,jeKonflikt:(Vertrag) -> Unit) = einträge[runde].forEach { jeKonflikt(it) }

    public fun neueRundenDatenDefinieren(
        neuVerträge: Set<Vertrag>,
    ) {
        einträge.add(neuVerträge)
        cache.add(baueCache(cache.size,cache))
    }

    fun zuSpeicherDaten(rundenDaten: List<RundeDaten>): List<VertragsDaten> {
        return einträge.flatMapIndexed { index, vertragsListe ->
            val rundeDaten = rundenDaten.find { it.index == index }!!
            vertragsListe.flatMap { v -> v.vertragsannehmer.flatMap { annehmer ->
                v.vertragsanbieter.map { anbieter -> VertragsDaten(rundeDaten,annehmer,anbieter,v.vertragsart) }
            } }
        }
    }
}

data class KonfliktStatus(
    val krieg: Set<String> = emptySet(),
    val waffenstillstand: Set<String> = emptySet(),
    val frieden: Set<String> = emptySet()
)

private data class VeränderbarerKonfliktStatus(
    val krieg: MutableSet<String> = mutableSetOf(),
    val waffenstillstand: MutableSet<String> = mutableSetOf(),
    val frieden: MutableSet<String> = mutableSetOf()
) {
    fun zuKonfliktStatus(): KonfliktStatus {
        return KonfliktStatus(
            krieg = krieg.toSet(),
            waffenstillstand = waffenstillstand.toSet(),
            frieden = frieden.toSet()
        )
    }
}

enum class Vertragsart(val str: String) {
    KRIEGSERKLÄRUNG("kriegserklärung"),
    WAFFENSTILLSTAND("waffenstillstand"),
    FRIEDENSERKLÄRUNG("friedenserklärung"),
}

data class Vertrag(
    val vertragsannehmer: List<String>,
    val vertragsanbieter: List<String>,
    val vertragsart: Vertragsart,
)
