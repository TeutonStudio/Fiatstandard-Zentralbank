package de.teutonstudio.zentralbank.datenbank

import java.math.BigDecimal
import kotlin.math.pow

fun String.toZahlungsmittel(): Zahlungsmittel = Zahlungsmittel(this)

operator fun Map<JuristischePerson, Zahlungsmittel>.plus(other:Map<JuristischePerson, Zahlungsmittel>): Map<JuristischePerson, Zahlungsmittel> = (this.keys + other.keys).associateWith { this.getOrDefault(it,Zahlungsmittel()) + other.getOrDefault(it,Zahlungsmittel()) }
operator fun Map<JuristischePerson, Zahlungsmittel>.minus(other:Map<JuristischePerson, Zahlungsmittel>): Map<JuristischePerson, Zahlungsmittel> = (this.keys + other.keys).associateWith { this.getOrDefault(it,Zahlungsmittel()) - other.getOrDefault(it,Zahlungsmittel()) }

fun Map<JuristischePerson, Zahlungsmittel>.handelt(handel: Handel): Map<JuristischePerson, Zahlungsmittel> = this + mapOf(handel.besitzer to handel.erhalteBetrag()) - mapOf(handel.erwerber to handel.erhalteBetrag())
fun Map<JuristischePerson, Zahlungsmittel>.emittiert(anleihe: Anleihe, an: JuristischePerson): Map<JuristischePerson, Zahlungsmittel> = this + (anleihe.schuldiger to anleihe.sondervermögen).toMap() - mapOf(an to anleihe.sondervermögen)
fun Map<JuristischePerson, Zahlungsmittel>.tilgt(anleihe: Anleihe, an: JuristischePerson): Map<JuristischePerson, Zahlungsmittel> = this - mapOf(anleihe.schuldiger to anleihe.sondervermögen) + mapOf(an to anleihe.sondervermögen)
fun Map<JuristischePerson, Zahlungsmittel>.zins(anleihe: Anleihe, an: JuristischePerson): Map<JuristischePerson, Zahlungsmittel> = this + mapOf(an to anleihe.unvermögen) - mapOf(anleihe.schuldiger to anleihe.unvermögen)
fun Map<JuristischePerson, Zahlungsmittel>.zinsVerbindlichkeit(anleihe: Anleihe, an: JuristischePerson): Map<JuristischePerson, Zahlungsmittel> = this - mapOf(an to anleihe.unvermögen) + mapOf(anleihe.schuldiger to anleihe.unvermögen)

private fun <K,V> MutableMap<K,V>.equal(neu:Map<K,V>) { this.clear(); neu.entries.forEach { this[it.key] = it.value } }

// operator fun MutableMap<JuristischePerson, Zahlungsmittel>.plus(other:Map<JuristischePerson, Zahlungsmittel>) { this.equal(this.toMap() + other) }
// operator fun MutableMap<JuristischePerson, Zahlungsmittel>.minus(other:Map<JuristischePerson, Zahlungsmittel>) { this.equal(this.toMap() - other) }

fun MutableMap<JuristischePerson, Zahlungsmittel>.handelt(handel: Handel) { this.equal(this.toMap().handelt(handel)) }
fun MutableMap<JuristischePerson, Zahlungsmittel>.emittiert(anleihe: Anleihe, an: JuristischePerson) { this.equal(this.toMap().emittiert(anleihe,an)) }
fun MutableMap<JuristischePerson, Zahlungsmittel>.tilgt(anleihe: Anleihe, an: JuristischePerson) { this.equal(this.toMap().tilgt(anleihe,an)) }
fun MutableMap<JuristischePerson, Zahlungsmittel>.zins(anleihe: Anleihe, an: JuristischePerson) { this.equal(this.toMap().zins(anleihe,an)) }

inline fun <T> Iterable<T>.summeGeld(
    transform: (T) -> Zahlungsmittel
): Zahlungsmittel {
    var summe = Zahlungsmittel()

    for (element in this) {
        summe += transform(element)
    }

    return summe
}

fun Int.toZahlungsmittel(): Zahlungsmittel {
    if (this == 0) return Zahlungsmittel()

    var rest = kotlin.math.abs(this.toLong())

    val betrag = buildList {
        while (rest > 0) {
            add((rest % BASIS).toInt())
            rest /= BASIS
        }
    }

    return Zahlungsmittel(
        negativ = this < 0,
        rohBetrag = betrag
    )
}

fun Int.times(other: Zahlungsmittel): Zahlungsmittel = other * this
fun Float.times(other: Zahlungsmittel): Zahlungsmittel = other * this

const val BASIS = 1000

data class Zahlungsmittel(
    val negativ: Boolean = false,
    val rohBetrag: List<Int> = emptyList()
) : Comparable<Zahlungsmittel> {
    init {
        require(rohBetrag == rohBetrag.dropLastWhile { it == 0 }) {
            "rohBetrag darf keine trailing Nullen enthalten"
        }
    }

    constructor(): this(false,emptyList())

    constructor(str:String): this(str.split("/").last().toBoolean(),str.split("/").dropLast(1).map { it.toInt() })

    override fun compareTo(other: Zahlungsmittel): Int {
        if (this.istNull() && other.istNull()) return 0

        if (this.istNegativ() && other.istPositiv()) return -1
        if (this.istPositiv() && other.istNegativ()) return 1

        val betragVergleich = vergleicheBetrag(this.rohBetrag, other.rohBetrag)

        return if (this.istNegativ()) {
            -betragVergleich
        } else {
            betragVergleich
        }
    }

    operator fun unaryMinus(): Zahlungsmittel {
        if (this.istNull()) return this
        return Zahlungsmittel(!negativ,rohBetrag)
    }

    operator fun plus(other: Zahlungsmittel): Zahlungsmittel {
        if (this.istNull()) return other
        if (other.istNull()) return this

        if (this.negativ == other.negativ) {
            return baueZahlungsmittel(
                negativ = this.negativ,
                betrag = addiereBetrag(this.rohBetrag, other.rohBetrag)
            )
        }

        val vergleich = vergleicheBetrag(this.rohBetrag, other.rohBetrag)

        return when {
            vergleich == 0 -> Zahlungsmittel()

            vergleich > 0 -> baueZahlungsmittel(
                negativ = this.negativ,
                betrag = subtrahiereBetrag(
                    gross = this.rohBetrag,
                    klein = other.rohBetrag
                )
            )

            else -> baueZahlungsmittel(
                negativ = other.negativ,
                betrag = subtrahiereBetrag(
                    gross = other.rohBetrag,
                    klein = this.rohBetrag
                )
            )
        }
    }

    operator fun minus(other: Zahlungsmittel): Zahlungsmittel {
        return this + (-other)
    }

    operator fun times(other: Zahlungsmittel): Zahlungsmittel {
        return throw IllegalArgumentException("produkt von zwei Währungsbeträgen nicht erwertet")
    }

    operator fun times(other: Int): Zahlungsmittel {
        return this * other.toLong()
    }

    operator fun times(other: Long): Zahlungsmittel {
        if (this.istNull() || other == 0L) return Zahlungsmittel()

        val faktorNegativ = other < 0
        val faktorAbs = absLong(other)

        return baueZahlungsmittel(
            negativ = this.istNegativ() xor faktorNegativ,
            betrag = multipliziereBetragMitLong(this.rohBetrag, faktorAbs)
        )
    }

    operator fun times(other: Float): Zahlungsmittel {
        require(other.isFinite()) {
            "Float-Faktor muss endlich sein"
        }

        if (this.istNull() || other == 0f) return Zahlungsmittel()

        val bruch = floatAlsDezimalbruch(other)

        return (this * bruch.zaehler) / bruch.nenner
    }

    operator fun div(other: Zahlungsmittel): Float {
        require(!other.istNull()) {
            "Division durch 0"
        }

        if (this.istNull()) return 0f

        val betragQuotient = teileBetragAlsFloat(
            links = this.rohBetrag,
            rechts = other.rohBetrag
        )

        return if (this.istNegativ() xor other.istNegativ()) {
            -betragQuotient
        } else {
            betragQuotient
        }
    }

    operator fun div(other: Int): Zahlungsmittel {
        return this / other.toLong()
    }

    operator fun div(other: Long): Zahlungsmittel {
        require(other != 0L) {
            "Division durch 0"
        }

        if (this.istNull()) return Zahlungsmittel()

        val divisorNegativ = other < 0
        val divisorAbs = absLong(other)

        return baueZahlungsmittel(
            negativ = this.istNegativ() xor divisorNegativ,
            betrag = dividiereBetragDurchLong(this.rohBetrag, divisorAbs)
        )
    }

    operator fun div(other: Float): Zahlungsmittel {
        require(other.isFinite()) {
            "Float-Divisor muss endlich sein"
        }

        require(other != 0f) {
            "Division durch 0"
        }

        val bruch = floatAlsDezimalbruch(other)

        return (this * bruch.nenner) / bruch.zaehler
    }

    fun istNull(): Boolean = rohBetrag.isEmpty()
    fun istNegativ(): Boolean = negativ && !istNull()
    fun istPositiv(): Boolean = !istNegativ()

    fun speichereString(): String = (rohBetrag.map { it.toString() } + negativ.toString()).joinToString("/")

    fun erhaltePreisinflation(vorher: Zahlungsmittel): Float {
        require(!vorher.istNull()) {
            "Vorheriger Preis darf nicht 0 sein"
        }
        return this / vorher - 1
    }

    fun erhalteZins(zinssatz: Float): Zahlungsmittel {
        return this * (1 + zinssatz)
    }

    fun erhalteZinssatz(zins: Zahlungsmittel): Float {
        require(!this.istNull()) {
            "Ausgangsbetrag darf nicht 0 sein"
        }
        return zins / this
    }

    fun erhalteVerzinst(laufzeit: Int, zinssatz: Float): Zahlungsmittel {
        return this.erhalteZins(zinssatz) * laufzeit + this
    }

    fun vorzeichen(): Int = if (negativ) -1 else 1

    fun toIntOderNull(): Int {
        return toDoubleOderNull().toInt()
    }

    fun toDoubleOderNull(): Double {
        return rohBetrag.mapIndexed { idx, wert -> wert * BASIS.toDouble().pow(idx.toDouble()) }.sum() * vorzeichen()
    }
}

private fun addiereBetrag(
    links: List<Int>,
    rechts: List<Int>
): List<Int> {
    val maxSize = maxOf(links.size, rechts.size)
    val ergebnis = MutableList(maxSize) { 0 }
    var carry = 0L

    for (k in 0 until maxSize) {
        val summe =
            (links.getOrNull(k) ?: 0).toLong() +
                    (rechts.getOrNull(k) ?: 0).toLong() +
                    carry

        ergebnis[k] = (summe % BASIS).toInt()
        carry = summe / BASIS
    }

    while (carry > 0) {
        ergebnis += (carry % BASIS).toInt()
        carry /= BASIS
    }

    return ergebnis.dropLastWhile { it == 0 }
}
private fun subtrahiereBetrag(
    gross: List<Int>,
    klein: List<Int>
): List<Int> {
    require(vergleicheBetrag(gross, klein) >= 0) {
        "gross muss größer oder gleich klein sein"
    }

    val ergebnis = MutableList(gross.size) { 0 }
    var borrow = 0

    for (k in gross.indices) {
        var differenz =
            gross[k] -
                    (klein.getOrNull(k) ?: 0) -
                    borrow

        if (differenz < 0) {
            differenz += BASIS
            borrow = 1
        } else {
            borrow = 0
        }

        ergebnis[k] = differenz
    }

    require(borrow == 0) {
        "Interner Fehler: negativer Betrag bei subtrahiereBetrag"
    }

    return ergebnis.dropLastWhile { it == 0 }
}
private fun multipliziereBetrag(
    links: List<Int>,
    rechts: List<Int>
): List<Int> {
    if (links.isEmpty() || rechts.isEmpty()) {
        return emptyList()
    }

    val letzterIndex = links.lastIndex + rechts.lastIndex
    val ergebnis = MutableList(letzterIndex + 1) { 0 }
    var carry = 0L

    for (k in 0..letzterIndex) {
        var summe = carry

        val iMin = maxOf(0, k - rechts.lastIndex)
        val iMax = minOf(k, links.lastIndex)

        for (i in iMin..iMax) {
            val j = k - i
            summe += links[i].toLong() * rechts[j].toLong()
        }

        ergebnis[k] = (summe % BASIS).toInt()
        carry = summe / BASIS
    }

    while (carry > 0) {
        ergebnis += (carry % BASIS).toInt()
        carry /= BASIS
    }

    return ergebnis.dropLastWhile { it == 0 }
}
private fun multipliziereBetragMitLong(
    betrag: List<Int>,
    faktor: Long
): List<Int> {
    if (betrag.isEmpty() || faktor == 0L) {
        return emptyList()
    }

    require(faktor > 0) {
        "faktor muss positiv sein"
    }

    require(faktor <= Long.MAX_VALUE / BASIS) {
        "faktor ist zu groß"
    }

    val ergebnis = MutableList(betrag.size) { 0 }
    var carry = 0L

    for (k in betrag.indices) {
        val produkt = betrag[k].toLong() * faktor + carry

        ergebnis[k] = (produkt % BASIS).toInt()
        carry = produkt / BASIS
    }

    while (carry > 0) {
        ergebnis += (carry % BASIS).toInt()
        carry /= BASIS
    }

    return ergebnis.dropLastWhile { it == 0 }
}
private fun dividiereBetragDurchLong(
    betrag: List<Int>,
    divisor: Long
): List<Int> {
    require(divisor > 0) {
        "divisor muss positiv sein"
    }

    require(divisor <= Long.MAX_VALUE / BASIS) {
        "divisor ist zu groß"
    }

    if (betrag.isEmpty()) {
        return emptyList()
    }

    val ergebnis = MutableList(betrag.size) { 0 }
    var rest = 0L

    for (k in betrag.lastIndex downTo 0) {
        val wert = rest * BASIS + betrag[k]

        ergebnis[k] = (wert / divisor).toInt()
        rest = wert % divisor
    }

    return ergebnis.dropLastWhile { it == 0 }
}
private fun teileBetragAlsFloat(
    links: List<Int>,
    rechts: List<Int>
): Float {
    require(rechts.isNotEmpty()) {
        "Division durch 0"
    }

    if (links.isEmpty()) return 0f

    val linksMantisse = fuehrendeMantisse(links)
    val rechtsMantisse = fuehrendeMantisse(rechts)

    val quotient =
        linksMantisse.mantisse /
                rechtsMantisse.mantisse *
                Math.pow(
                    BASIS.toDouble(),
                    (linksMantisse.exponent - rechtsMantisse.exponent).toDouble()
                )

    return quotient.toFloat()
}

private data class Mantisse(
    val mantisse: Double,
    val exponent: Int
)

private fun fuehrendeMantisse(
    betrag: List<Int>
): Mantisse {
    val letzter = betrag.lastIndex

    /*
     * 5 Basis-1000-Blöcke entsprechen bis zu 15 Dezimalstellen.
     * Für Float ist das mehr als genug, weil Float selbst nur ungefähr
     * 7 Dezimalstellen sinnvoll trägt.
     */
    val ersterBenutzter = maxOf(0, letzter - 4)

    var mantisse = 0.0

    for (k in letzter downTo ersterBenutzter) {
        mantisse = mantisse * BASIS + betrag[k]
    }

    return Mantisse(
        mantisse = mantisse,
        exponent = ersterBenutzter
    )
}
private fun vergleicheBetrag(
    links: List<Int>,
    rechts: List<Int>
): Int {
    if (links.size != rechts.size) {
        return links.size.compareTo(rechts.size)
    }

    for (k in links.lastIndex downTo 0) {
        if (links[k] != rechts[k]) {
            return links[k].compareTo(rechts[k])
        }
    }

    return 0
}
private data class Dezimalbruch(
    val zaehler: Long,
    val nenner: Long
)

private fun floatAlsDezimalbruch(
    wert: Float
): Dezimalbruch {
    require(wert.isFinite()) {
        "Float muss endlich sein"
    }

    val decimal = BigDecimal(wert.toString()).stripTrailingZeros()
    val scale = maxOf(0, decimal.scale())

    val nenner = zehnHoch(scale)
    val zaehler = decimal.movePointRight(scale).longValueExact()

    val teiler = ggT(absLong(zaehler), nenner)

    return Dezimalbruch(
        zaehler = zaehler / teiler,
        nenner = nenner / teiler
    )
}
private fun zehnHoch(exponent: Int): Long {
    require(exponent >= 0) {
        "Exponent darf nicht negativ sein"
    }

    var ergebnis = 1L

    repeat(exponent) {
        require(ergebnis <= Long.MAX_VALUE / 10L) {
            "10^$exponent passt nicht in Long"
        }

        ergebnis *= 10L
    }

    return ergebnis
}

private fun ggT(
    aStart: Long,
    bStart: Long
): Long {
    var a = aStart
    var b = bStart

    while (b != 0L) {
        val rest = a % b
        a = b
        b = rest
    }

    return if (a == 0L) 1L else a
}

private fun absLong(value: Long): Long {
    require(value != Long.MIN_VALUE) {
        "Long.MIN_VALUE kann nicht positiv dargestellt werden"
    }

    return if (value < 0) -value else value
}
private fun baueZahlungsmittel(
    negativ: Boolean,
    betrag: List<Int>
): Zahlungsmittel {
    val normalisiert = betrag.dropLastWhile { it == 0 }

    return if (normalisiert.isEmpty()) {
        Zahlungsmittel()
    } else {
        Zahlungsmittel(negativ, normalisiert)
    }
}

/*{
    *//**
     * Vektor a aus:
     *
     * x = vorzeichen * Summe(a_k * 1000^k)
     *
     * Little Endian:
     * betrag[0] = 1000^0
     * betrag[1] = 1000^1
     * betrag[2] = 1000^2
     *
     * Jeder Koeffizient liegt immer in 0..999.
     *//*
    val betrag: List<Int>

    private constructor(parsed: Pair<Int, List<Int>>) : this(
        parsed.first,
        parsed.second
    )

    constructor() : this(0, emptyList())

    constructor(wert: Int) : this(wert.toString())

    constructor(wert: Long) : this(wert.toString())

    *//**
     * Akzeptiert:
     *
     * "1234567"          -> Dezimalzahl
     * "-1234567"         -> negative Dezimalzahl
     * "+|567/234/1"      -> Speicherformat, positiv
     * "-|567/234/1"      -> Speicherformat, negativ
     * "567/234/1"        -> altes positives Speicherformat
     *//*
    constructor(vonSpeicherOderDezimal: String) : this(
        parseText(vonSpeicherOderDezimal)
    )

    *//**
     * Erstellt ein Zahlungsmittel direkt aus dem Vektor a.
     *
     * Beispiel:
     * listOf(5, 2) = 5 Mark + 2 * 1000 Mark = 2005 Mark
     *//*
    constructor(vonTausendPotenzListe: List<Int>) : this(
        1,
        vonTausendPotenzListe
    )

    constructor(vararg vonTausendPotenz: Int) : this(
        vonTausendPotenz.toList()
    )

    *//**
     * Beispiel:
     * mapOf(
     *     0 to 500,
     *     1 to 2
     * )
     *
     * = 2500 Mark
     *//*
    constructor(einheiten: Map<Int, Int>) : this(
        1,
        koeffizientenAusMap(einheiten)
    )

    constructor(vararg einheiten: Pair<Int, Int>) : this(
        einheiten.toMap()
    )

    override fun equals(other: Any?): Boolean {
        return other is Zahlungsmittel && compareTo(other) == 0
    }

    override fun hashCode(): Int {
        return 31 * vorzeichen + betrag.hashCode()
    }

    override fun compareTo(other: Zahlungsmittel): Int {
        if (this.vorzeichen != other.vorzeichen) {
            return this.vorzeichen.compareTo(other.vorzeichen)
        }

        if (this.vorzeichen == 0) {
            return 0
        }

        val vergleich = vergleicheBetrag(this.betrag, other.betrag)

        return if (this.vorzeichen > 0) {
            vergleich
        } else {
            -vergleich
        }
    }

    val erhalteString: String
        get() {
            if (istNull) return "0 Mark"

            val text = betrag
                .mapIndexedNotNull { index, menge ->
                    if (menge == 0) {
                        null
                    } else {
                        "$menge ${waehrungsEinheit(index)}"
                    }
                }
                .reversed()
                .joinToString(" + ")
                .ifEmpty { "0 Mark" }

            return if (istNegativ) "-$text" else text
        }

    fun speichereString(): String {
        if (istNull) return "0"

        val vorzeichenText = if (istNegativ) "-" else "+"

        return "$vorzeichenText|${betrag.joinToString("/")}"
    }

    fun abs(): Zahlungsmittel {
        return Zahlungsmittel(
            if (istNull) 0 else 1,
            betrag
        )
    }

    fun negiert(): Zahlungsmittel {
        return -this
    }

    override fun toString(): String {
        return erhalteString
    }

    fun toDezimalString(): String {
        if (istNull) return "0"

        var rest = betrag
        val zeichen = StringBuilder()

        while (rest.isNotEmpty()) {
            val division = divModBetragDurchKlein(rest, 10)
            rest = division.quotient
            zeichen.append(('0'.code + division.rest).toChar())
        }

        val dezimal = zeichen.reverse().toString()

        return if (istNegativ) "-$dezimal" else dezimal
    }

    fun toIntOderNull(): Int? {
        if (istNull) return 0

        var wert = 0L

        for (index in betrag.indices.reversed()) {
            wert = wert * BASIS + betrag[index]

            val grenze = if (istNegativ) {
                Int.MAX_VALUE.toLong() + 1L
            } else {
                Int.MAX_VALUE.toLong()
            }

            if (wert > grenze) {
                return null
            }
        }

        val signed = if (istNegativ) -wert else wert

        return signed.toInt()
    }

    fun toDouble(): Double {
        return (this.toIntOderNull() ?: 0).toDouble()
    }

    fun toIntBegrenzt(): Int {
        return toIntOderNull() ?: if (istNegativ) {
            Int.MIN_VALUE
        } else {
            Int.MAX_VALUE
        }
    }

    operator fun unaryMinus(): Zahlungsmittel {
        return Zahlungsmittel(
            -vorzeichen,
            betrag
        )
    }

    operator fun plus(other: Zahlungsmittel): Zahlungsmittel {
        return Zahlungsmittel(addiereBetrag(this.betrag,other.betrag))
    }

    operator fun minus(other: Zahlungsmittel): Zahlungsmittel {
        return this + (-other)
    }

    operator fun times(other: Zahlungsmittel): Zahlungsmittel {
        if (this.istNull || other.istNull) return ZERO

        return Zahlungsmittel(
            this.vorzeichen * other.vorzeichen,
            multipliziereBetrag(this.betrag, other.betrag)
        )
    }

    operator fun times(faktor: Int): Zahlungsmittel {
        return this * Zahlungsmittel(faktor)
    }

    operator fun times(faktor: Float): Zahlungsmittel {
        return this * faktor
    }

    operator fun times(faktor: Long): Zahlungsmittel {
        return this * Zahlungsmittel(faktor)
    }

    *//*operator fun div(other: Zahlungsmittel): Zahlungsmittel {
        return divMod(other).quotient
    }*//*

    *//*operator fun div(divisor: Int): Zahlungsmittel {
        return this / Zahlungsmittel(divisor)
    }*//*

    operator fun div(divisor: Float): Zahlungsmittel {
        return this / divisor
    }

    operator fun div(divisor: Zahlungsmittel): Float {
        return (this / divisor)
    }

   *//*operator fun div(divisor: Long): Zahlungsmittel {
        return this / Zahlungsmittel(divisor)
    }*//*

    operator fun rem(other: Zahlungsmittel): Zahlungsmittel {
        return divMod(other).rest
    }

    operator fun rem(divisor: Int): Zahlungsmittel {
        return this % Zahlungsmittel(divisor)
    }

    operator fun rem(divisor: Long): Zahlungsmittel {
        return this % Zahlungsmittel(divisor)
    }

    fun divMod(other: Zahlungsmittel): Division {
        require(!other.istNull) {
            "Division durch 0 Mark"
        }

        if (this.istNull) {
            return Division(ZERO, ZERO)
        }

        val division = divModBetrag(this.betrag, other.betrag)

        val quotient = Zahlungsmittel(
            this.vorzeichen * other.vorzeichen,
            division.quotient
        )

        val rest = Zahlungsmittel(
            this.vorzeichen,
            division.rest
        )

        return Division(quotient, rest)
    }

    *//**
     * Division mit Rundung auf die nächste ganze Mark.
     *
     * Beispiel:
     * 5 / 2 = 3
     * -5 / 2 = -3
     *//*
    fun divGerundet(other: Zahlungsmittel): Zahlungsmittel {
        val division = divMod(other)

        if (division.rest.istNull) {
            return division.quotient
        }

        val doppelterRest = multipliziereBetragMitKlein(
            division.rest.betrag,
            2
        )

        val vergleich = vergleicheBetrag(
            doppelterRest,
            other.betrag
        )

        if (vergleich < 0) {
            return division.quotient
        }

        val richtung = this.vorzeichen * other.vorzeichen

        return division.quotient + Zahlungsmittel(
            richtung,
            listOf(1)
        )
    }

    *//**
     * zinsSatz wird als Ganzzahl interpretiert.
     *
     * zinsSatz = 5, zinsPotenz = 2
     * => 5 / 100 = 0.05 = 5 %
     *
     * zinsSatz = 25, zinsPotenz = 3
     * => 25 / 1000 = 0.025 = 2.5 %
     *//*
    fun erhalteVerzinst(
        zinsSatz: Int,
        zinsPotenz: Int = 2
    ): Zahlungsmittel {
        require(zinsPotenz >= 0) {
            "zinsPotenz darf nicht negativ sein."
        }

        val divisor = zehnHoch(zinsPotenz)
        val faktorZaehler = divisor + Zahlungsmittel(zinsSatz)

        return (this * faktorZaehler).divGerundet(divisor)
    }

    *//**
     * Bequeme Variante für Dezimal-Prozentsätze als String.
     *
     * "5"   => 5 %
     * "2.5" => 2.5 %
     * "-10" => -10 %
     *//*
    fun erhalteVerzinst(zinsSatz: Int): Zahlungsmittel {
        return this + erhalteZinssatz(zinsSatz)
    }

    fun verzinst(
        zinsSatz: Int,
        zinsPotenz: Int = 2
    ): Zahlungsmittel {
        return erhalteVerzinst(zinsSatz, zinsPotenz)
    }

    fun verzinst(zinsSatz: Int): Zahlungsmittel {
        return erhalteVerzinst(zinsSatz)
    }

    *//**
     * Gibt den echten Zins zurück:
     *
     * Kapital: 1000
     * Verzinst: 1050
     * Ergebnis: "5.00 %"
     * Ausgabe: 5
     *//*
    fun erhalteZins(
        verzinst: Zahlungsmittel
    ): Int {
        require(!this.istNull) {
            "Aus 0 Mark kann kein Zinssatz berechnet werden."
        }

        val differenz = verzinst - this

        return (differenz / this).toIntOderNull() ?: 0
    }


    *//**
     * Gibt den echten Zinssatz zurück:
     *
     * Kapital: 1000
     * Verzinst: 1050
     * Ergebnis: "50"
     *//*
    fun erhalteZinssatz(
        verzinst: Zahlungsmittel
    ): Zahlungsmittel {
        require(!this.istNull) {
            "Aus 0 Mark kann kein Zinssatz berechnet werden."
        }
        return verzinst - this
    }


    *//**
     * Gibt den echten Zinssatz zurück:
     *
     * Kapital: 1000
     * zins: 5
     * Ergebnis: "50"
     *//*
    fun erhalteZinssatz(
        zins: Int
    ): Zahlungsmittel {
        require(!this.istNull) {
            "Aus 0 Mark kann kein Zinssatz berechnet werden."
        }
        return this * zins / 100
    }

    fun erhaltePreisinflatoin(
        vorher: Zahlungsmittel
    ): Int {
        return ((this / vorher - 1) * 100).toInt()
    }

    *//**
     * Gibt verzinst / this * 100 zurück.
     *
     * Kapital: 100
     * Verzinst: 105
     * Ergebnis: "105.00 %"
     *//*
    fun erhalteFaktorProzent(
        verzinst: Zahlungsmittel,
        nachkommastellen: Int = 2
    ): String {
        require(!this.istNull) {
            "Aus 0 Mark kann kein Prozentfaktor berechnet werden."
        }

        return prozentString(
            zaehler = verzinst,
            nenner = this,
            nachkommastellen = nachkommastellen
        )
    }

    fun schuldenQuote(
        schulden: Zahlungsmittel,
        nachkommastellen: Int = 0
    ): String {
        return finanzQuoteAlsString(
            eigenkapital = this,
            fremdkapital = schulden,
            nachkommastellen = nachkommastellen
        )
    }

    fun vermoegenQuote(
        vermoegen: Zahlungsmittel,
        nachkommastellen: Int = 0
    ): String {
        return finanzQuoteAlsString(
            eigenkapital = vermoegen,
            fremdkapital = this,
            nachkommastellen = nachkommastellen
        )
    }

    fun vermögenQuote(
        vermögen: Zahlungsmittel,
        nachkommastellen: Int = 0
    ): String {
        return vermoegenQuote(vermögen, nachkommastellen)
    }

    data class Division(
        val quotient: Zahlungsmittel,
        val rest: Zahlungsmittel,
    )

    private data class BetragDivision(
        val quotient: List<Int>,
        val rest: List<Int>,
    )

    private data class KleineDivision(
        val quotient: List<Int>,
        val rest: Int,
    )

    private data class Skaliert(
        val zahl: Zahlungsmittel,
        val nachkommastellen: Int,
    )

    companion object {

        val ZERO = Zahlungsmittel(0, emptyList())
        val ONE = Zahlungsmittel(1, listOf(1))

        private val WÄHRUNGS_EINHEITEN = listOf(
            "Mark" to "ℳ",
            "Tausend Mark" to "Tsd ℳ",
            "Million Mark" to "Mio ℳ",
            "Milliarde Mark" to "Mia ℳ",
            "Billion Mark" to "Bio ℳ",
            "Billiarde Mark" to "Bia ℳ",
            "Trillion Mark" to "Trio ℳ",
            "Trilliarde Mark" to "Tria ℳ",
            "Quadrillion Mark" to "Qrio ℳ",
            "Quadrilliarde Mark" to "Qria ℳ"
        )

        fun finanzQuote(
            eigenkapital: Zahlungsmittel,
            fremdkapital: Zahlungsmittel
        ): Int {
            require(!fremdkapital.istNull) {
                "Fremdkapital darf nicht 0 sein."
            }

            return ((eigenkapital * 100).divGerundet(fremdkapital))
                .toIntBegrenzt()
        }

        fun finanzQuoteAlsString(
            eigenkapital: Zahlungsmittel,
            fremdkapital: Zahlungsmittel,
            nachkommastellen: Int = 0
        ): String {
            require(!fremdkapital.istNull) {
                "Fremdkapital darf nicht 0 sein."
            }

            return prozentString(
                zaehler = eigenkapital,
                nenner = fremdkapital,
                nachkommastellen = nachkommastellen
            )
        }

        fun zehnHoch(exponent: Int): Zahlungsmittel {
            require(exponent >= 0) {
                "Exponent darf nicht negativ sein."
            }

            var ergebnis = ONE

            repeat(exponent) {
                ergebnis *= 10
            }

            return ergebnis
        }

        private fun prozentString(
            zaehler: Zahlungsmittel,
            nenner: Zahlungsmittel,
            nachkommastellen: Int
        ): String {
            require(nachkommastellen >= 0) {
                "nachkommastellen darf nicht negativ sein."
            }

            require(!nenner.istNull) {
                "Nenner darf nicht 0 sein."
            }

            val skala = zehnHoch(nachkommastellen)
            val skaliertesProzent = (zaehler * 100 * skala)
                .divGerundet(nenner)

            return "${skaliertesProzent.alsSkalierterDezimalString(nachkommastellen)} %"
        }

        private fun Zahlungsmittel.alsSkalierterDezimalString(
            nachkommastellen: Int
        ): String {
            val negativ = this.istNegativ
            val absolut = this.abs().toDezimalString()

            if (nachkommastellen == 0) {
                return if (negativ) "-$absolut" else absolut
            }

            val aufgefuellt = absolut.padStart(nachkommastellen + 1, '0')
            val trennIndex = aufgefuellt.length - nachkommastellen

            val vorkomma = aufgefuellt.substring(0, trennIndex)
            val nachkomma = aufgefuellt.substring(trennIndex)

            val text = "$vorkomma.$nachkomma"

            return if (negativ) "-$text" else text
        }

        private fun parseText(text: String): Pair<Int, List<Int>> {
            val bereinigt = text
                .trim()
                .replace("_", "")
                .replace(" ", "")

            if (bereinigt.isBlank() || bereinigt == "0") {
                return 0 to emptyList()
            }

            if ("|" in bereinigt) {
                val teile = bereinigt.split("|", limit = 2)

                require(teile.size == 2) {
                    "Ungültiges Speicherformat: $text"
                }

                val vorzeichen = when (teile[0]) {
                    "+", "1" -> 1
                    "-", "-1" -> -1
                    "0" -> 0
                    else -> error("Ungültiges Vorzeichen im Speicherformat: ${teile[0]}")
                }

                val koeffizienten = if (teile[1].isBlank()) {
                    emptyList()
                } else {
                    teile[1].split("/").map { teil ->
                        parseKoeffizient(teil)
                    }
                }

                return normalisiere(vorzeichen, koeffizienten)
            }

            if ("/" in bereinigt) {
                val negativ = bereinigt.startsWith("-")
                val ohneVorzeichen = bereinigt
                    .removePrefix("-")
                    .removePrefix("+")

                val koeffizienten = ohneVorzeichen
                    .split("/")
                    .filter { it.isNotBlank() }
                    .map { teil -> parseKoeffizient(teil) }

                return normalisiere(
                    if (negativ) -1 else 1,
                    koeffizienten
                )
            }

            return parseDezimalGanzzahl(bereinigt)
        }

        private fun parseDezimalGanzzahl(text: String): Pair<Int, List<Int>> {
            val negativ = text.startsWith("-")
            val positiv = text.startsWith("+")
            val koerper = text
                .removePrefix("-")
                .removePrefix("+")

            require(koerper.isNotBlank()) {
                "Leere Zahl ist kein gültiges Zahlungsmittel."
            }

            require(koerper.all { it in '0'..'9' }) {
                "Nur ganze Dezimalzahlen sind erlaubt: $text"
            }

            var betrag = emptyList<Int>()

            for (zeichen in koerper) {
                val ziffer = zeichen - '0'
                betrag = addiereBetrag(
                    multipliziereBetragMitKlein(betrag, 10),
                    listOf(ziffer)
                )
            }

            val vorzeichen = when {
                betrag.isEmpty() -> 0
                negativ -> -1
                positiv -> 1
                else -> 1
            }

            return normalisiere(vorzeichen, betrag)
        }

        private fun parseDezimalSkaliert(text: String): Skaliert {
            val bereinigt = text
                .trim()
                .replace(",", ".")
                .replace("_", "")
                .replace(" ", "")

            require(bereinigt.isNotBlank()) {
                "Leerer Zinssatz ist ungültig."
            }

            val negativ = bereinigt.startsWith("-")
            val ohneVorzeichen = bereinigt
                .removePrefix("-")
                .removePrefix("+")

            val teile = ohneVorzeichen.split(".")

            require(teile.size <= 2) {
                "Ungültiger Dezimalwert: $text"
            }

            val vorkomma = teile.getOrNull(0).orEmpty()
            val nachkomma = teile.getOrNull(1).orEmpty()

            require(
                (vorkomma + nachkomma).isNotBlank() &&
                        (vorkomma + nachkomma).all { it in '0'..'9' }
            ) {
                "Ungültiger Dezimalwert: $text"
            }

            val ziffern = (vorkomma.ifBlank { "0" } + nachkomma)
                .trimStart('0')
                .ifBlank { "0" }

            val parsed = parseDezimalGanzzahl(
                if (negativ) "-$ziffern" else ziffern
            )

            return Skaliert(
                zahl = Zahlungsmittel(parsed),
                nachkommastellen = nachkomma.length
            )
        }

        private fun parseKoeffizient(text: String): Int {
            val wert = text.trim().toInt()

            require(wert >= 0) {
                "Koeffizienten dürfen nicht negativ sein."
            }

            return wert
        }

        private fun koeffizientenAusMap(
            einheiten: Map<Int, Int>
        ): List<Int> {
            if (einheiten.isEmpty()) return emptyList()

            require(einheiten.keys.all { it >= 0 }) {
                "Zahlungsmittel-Indizes dürfen nicht negativ sein."
            }

            require(einheiten.values.all { it >= 0 }) {
                "Zahlungsmittel-Koeffizienten dürfen nicht negativ sein."
            }

            val maxIndex = einheiten.keys.maxOrNull() ?: 0

            return List(maxIndex + 1) { index ->
                einheiten[index] ?: 0
            }
        }

        private fun normalisiere(
            vorzeichen: Int,
            rohBetrag: List<Int>
        ): Pair<Int, List<Int>> {
            if (vorzeichen == 0 || rohBetrag.isEmpty()) {
                return 0 to emptyList()
            }

            require(rohBetrag.all { it >= 0 }) {
                "Der Betragsvektor darf keine negativen Koeffizienten enthalten."
            }

            val ergebnis = mutableListOf<Int>()
            var carry = 0L

            for (wert in rohBetrag) {
                val gesamt = wert.toLong() + carry
                ergebnis += (gesamt % BASIS).toInt()
                carry = gesamt / BASIS
            }

            while (carry > 0) {
                ergebnis += (carry % BASIS).toInt()
                carry /= BASIS
            }

            val gekuerzt = kuerzeNullen(ergebnis)

            if (gekuerzt.isEmpty()) {
                return 0 to emptyList()
            }

            return vorzeichen.signum() to gekuerzt
        }

        private fun Int.signum(): Int {
            return when {
                this > 0 -> 1
                this < 0 -> -1
                else -> 0
            }
        }

        private fun vergleicheBetrag(
            links: List<Int>,
            rechts: List<Int>
        ): Int {
            val linksNormal = kuerzeNullen(links)
            val rechtsNormal = kuerzeNullen(rechts)

            if (linksNormal.size != rechtsNormal.size) {
                return linksNormal.size.compareTo(rechtsNormal.size)
            }

            for (index in linksNormal.indices.reversed()) {
                if (linksNormal[index] != rechtsNormal[index]) {
                    return linksNormal[index].compareTo(rechtsNormal[index])
                }
            }

            return 0
        }

        private fun addiereBetrag(
            links: List<Int>,
            rechts: List<Int>
        ): List<Int> {
            val maxSize = maxOf(links.size, rechts.size)
            val ergebnis = MutableList(maxSize) { 0 }
            var carry = 0

            for (index in 0 until maxSize) {
                val summe =
                    (links.getOrNull(index) ?: 0) +
                            (rechts.getOrNull(index) ?: 0) +
                            carry

                ergebnis[index] = summe % BASIS
                carry = summe / BASIS
            }

            if (carry > 0) {
                ergebnis += carry
            }

            return kuerzeNullen(ergebnis)
        }

        *//**
         * links muss >= rechts sein.
         *//*
        private fun subtrahiereBetrag(
            links: List<Int>,
            rechts: List<Int>
        ): List<Int> {
            require(vergleicheBetrag(links, rechts) >= 0) {
                "Interner Fehler: negativer Betrag in Betrags-Subtraktion."
            }

            val ergebnis = MutableList(links.size) { 0 }
            var borrow = 0

            for (index in links.indices) {
                var differenz =
                    links[index] -
                            (rechts.getOrNull(index) ?: 0) -
                            borrow

                if (differenz < 0) {
                    differenz += BASIS
                    borrow = 1
                } else {
                    borrow = 0
                }

                ergebnis[index] = differenz
            }

            return kuerzeNullen(ergebnis)
        }

        private fun multipliziereBetrag(
            links: List<Int>,
            rechts: List<Int>
        ): List<Int> {
            if (links.isEmpty() || rechts.isEmpty()) {
                return emptyList()
            }

            val roh = MutableList(links.size + rechts.size) { 0L }

            for (i in links.indices) {
                for (j in rechts.indices) {
                    roh[i + j] += links[i].toLong() * rechts[j].toLong()
                }
            }

            return normalisiereLongBetrag(roh)
        }

        private fun multipliziereBetragMitKlein(
            betrag: List<Int>,
            faktor: Int
        ): List<Int> {
            require(faktor >= 0) {
                "Kleiner Faktor darf nicht negativ sein."
            }

            if (betrag.isEmpty() || faktor == 0) {
                return emptyList()
            }

            val roh = MutableList(betrag.size + 1) { 0L }

            for (index in betrag.indices) {
                roh[index] = betrag[index].toLong() * faktor.toLong()
            }

            return normalisiereLongBetrag(roh)
        }

        private fun normalisiereLongBetrag(
            roh: List<Long>
        ): List<Int> {
            val ergebnis = mutableListOf<Int>()
            var carry = 0L

            for (wert in roh) {
                val gesamt = wert + carry
                ergebnis += (gesamt % BASIS).toInt()
                carry = gesamt / BASIS
            }

            while (carry > 0) {
                ergebnis += (carry % BASIS).toInt()
                carry /= BASIS
            }

            return kuerzeNullen(ergebnis)
        }

        private fun divModBetrag(
            dividend: List<Int>,
            divisor: List<Int>
        ): BetragDivision {
            require(divisor.isNotEmpty()) {
                "Division durch 0"
            }

            if (dividend.isEmpty()) {
                return BetragDivision(emptyList(), emptyList())
            }

            if (vergleicheBetrag(dividend, divisor) < 0) {
                return BetragDivision(emptyList(), dividend)
            }

            var rest = emptyList<Int>()
            val quotientVonOben = mutableListOf<Int>()

            for (index in dividend.indices.reversed()) {
                rest = verschiebeBasisUndAddiere(rest, dividend[index])

                val quotientZiffer = findeQuotientZiffer(rest, divisor)
                quotientVonOben += quotientZiffer

                if (quotientZiffer != 0) {
                    rest = subtrahiereBetrag(
                        rest,
                        multipliziereBetragMitKlein(divisor, quotientZiffer)
                    )
                }
            }

            val quotient = quotientVonOben
                .asReversed()
                .toList()

            return BetragDivision(
                quotient = kuerzeNullen(quotient),
                rest = kuerzeNullen(rest)
            )
        }

        private fun divModBetragDurchKlein(
            dividend: List<Int>,
            divisor: Int
        ): KleineDivision {
            require(divisor > 0) {
                "Divisor muss positiv sein."
            }

            if (dividend.isEmpty()) {
                return KleineDivision(emptyList(), 0)
            }

            val quotient = MutableList(dividend.size) { 0 }
            var rest = 0L

            for (index in dividend.indices.reversed()) {
                val aktuell = rest * BASIS + dividend[index]
                quotient[index] = (aktuell / divisor).toInt()
                rest = aktuell % divisor
            }

            return KleineDivision(
                quotient = kuerzeNullen(quotient),
                rest = rest.toInt()
            )
        }

        private fun verschiebeBasisUndAddiere(
            betrag: List<Int>,
            ziffer: Int
        ): List<Int> {
            require(ziffer in 0 until BASIS) {
                "Ziffer außerhalb der Basis 1000: $ziffer"
            }

            if (betrag.isEmpty() && ziffer == 0) {
                return emptyList()
            }

            val ergebnis = MutableList(betrag.size + 1) { 0 }
            ergebnis[0] = ziffer

            for (index in betrag.indices) {
                ergebnis[index + 1] = betrag[index]
            }

            return kuerzeNullen(ergebnis)
        }

        private fun findeQuotientZiffer(
            rest: List<Int>,
            divisor: List<Int>
        ): Int {
            var unten = 0
            var oben = BASIS - 1
            var beste = 0

            while (unten <= oben) {
                val mitte = (unten + oben) / 2
                val produkt = multipliziereBetragMitKlein(divisor, mitte)
                val vergleich = vergleicheBetrag(produkt, rest)

                if (vergleich <= 0) {
                    beste = mitte
                    unten = mitte + 1
                } else {
                    oben = mitte - 1
                }
            }

            return beste
        }

        private fun waehrungsEinheit(index: Int): String {
            return WÄHRUNGS_EINHEITEN.getOrNull(index)?.first
                ?: "10^${index * 3} Mark"
        }
    }
}*/
