package de.teutonstudio.zentralbank.fachlogik.engine

interface Zufallsquelle {
    fun naechsteGanzzahl(exklusivBis: Int): Int
}

/** Seed-basierte Quelle ohne globalen Zufallszustand. Ihre Folge gehört zur Engine-Version. */
class SeedZufallsquelle(seed: Long) : Zufallsquelle {
    private var zustand: Long = if (seed != 0L) seed else -7046029254386353131L

    override fun naechsteGanzzahl(exklusivBis: Int): Int {
        require(exklusivBis > 0) { "Die Obergrenze muss positiv sein." }
        var wert = zustand
        wert = wert xor (wert shl 13)
        wert = wert xor (wert ushr 7)
        wert = wert xor (wert shl 17)
        zustand = wert
        return ((wert ushr 1) % exklusivBis.toLong()).toInt()
    }
}
