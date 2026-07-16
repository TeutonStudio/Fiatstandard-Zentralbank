package de.teutonstudio.zentralbank.domain

import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

@JvmInline
@Serializable
value class Geld private constructor(
    val cent: Long,
) : Comparable<Geld> {
    override fun compareTo(other: Geld): Int = cent.compareTo(other.cent)

    operator fun plus(other: Geld): Geld = Geld(cent + other.cent)

    operator fun minus(other: Geld): Geld = Geld(cent - other.cent)

    operator fun unaryMinus(): Geld = Geld(-cent)

    operator fun times(faktor: Long): Geld = Geld(cent * faktor)

    operator fun times(faktor: Int): Geld = this * faktor.toLong()

    fun istNegativ(): Boolean = cent < 0

    fun istNull(): Boolean = cent == 0L

    fun zuMarkString(): String {
        val vorzeichen = if (cent < 0) "-" else ""
        val absolut = cent.absoluteValue
        val mark = absolut / CENT_PRO_MARK
        val centRest = absolut % CENT_PRO_MARK
        return "$vorzeichen$mark,${centRest.toString().padStart(2, '0')} ℳ"
    }

    companion object {
        const val CENT_PRO_MARK: Long = 100L

        val NULL: Geld = Geld(0L)

        fun cent(cent: Long): Geld = Geld(cent)

        fun mark(mark: Long): Geld = Geld(mark * CENT_PRO_MARK)
    }
}

operator fun Long.times(geld: Geld): Geld = geld * this

operator fun Int.times(geld: Geld): Geld = geld * this
