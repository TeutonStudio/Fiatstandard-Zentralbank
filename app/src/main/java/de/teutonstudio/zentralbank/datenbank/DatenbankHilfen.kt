package de.teutonstudio.zentralbank.datenbank

fun <A> List<A>.dropFirst(n:Int) = this.reversed().dropLast(n).reversed()
fun <A> Map<Int,A>.dropLowest(): Map<Int,A> = this.toSortedMap().toList().dropFirst(1).toMap()
fun <A> Map<Int,A>.getLowest(): Map.Entry<Int,A> = this.toSortedMap().firstEntry()
fun Map<Int, Anleihenhandel>.erhalteErste(): Anleihenhandel = this.getLowest().value
