package de.teutonstudio.zentralbank.fachlogik.technik

fun addiereExakt(a: Int, b: Int): Int {
    val wert = a.toLong() + b.toLong()
    require(wert in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Ganzzahlüberlauf bei Addition." }
    return wert.toInt()
}

fun multipliziereExakt(a: Int, b: Int): Int {
    val wert = a.toLong() * b.toLong()
    require(wert in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Ganzzahlüberlauf bei Multiplikation." }
    return wert.toInt()
}

fun subtrahiereExakt(a: Int, b: Int): Int {
    val wert = a.toLong() - b.toLong()
    require(wert in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Ganzzahlüberlauf bei Subtraktion." }
    return wert.toInt()
}

fun multipliziereExakt(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    require(!(a == Long.MIN_VALUE && b == -1L) && !(b == Long.MIN_VALUE && a == -1L)) {
        "Langzahlüberlauf bei Multiplikation."
    }
    val wert = a * b
    require(wert / b == a) { "Langzahlüberlauf bei Multiplikation." }
    return wert
}

fun bodenDivision(a: Int, b: Int): Int {
    require(b != 0) { "Division durch null." }
    require(!(a == Int.MIN_VALUE && b == -1)) { "Ganzzahlüberlauf bei Division." }
    val q = a / b
    val r = a % b
    return if (r != 0 && (r < 0) != (b < 0)) q - 1 else q
}

fun bodenDivision(a: Long, b: Long): Long {
    require(b != 0L) { "Division durch null." }
    require(!(a == Long.MIN_VALUE && b == -1L)) { "Langzahlüberlauf bei Division." }
    val q = a / b
    val r = a % b
    return if (r != 0L && (r < 0L) != (b < 0L)) q - 1L else q
}

fun bodenModulo(a: Long, b: Int): Int {
    require(b > 0) { "Modulo-Basis muss positiv sein." }
    val rest = a % b.toLong()
    return if (rest < 0L) (rest + b).toInt() else rest.toInt()
}
