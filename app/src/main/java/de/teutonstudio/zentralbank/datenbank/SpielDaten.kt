package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "GameData")
data class SpielDaten(
    @PrimaryKey(autoGenerate = true)
    override val spielID: Long = 0,

    val spieler: String,

    val warenkorb: String,
    val inflationsziel: Float,
    val nAbweichung: Float,
    val sAbweichung: Float,
): SpeicherDaten {
    constructor(warenkorb: Map<Rohstoffe,Int>, spieler:List<Spieler>, inflationswerte: Triple<Float, Float, Float>) : this(
        spielID = 0,
        spieler=spieler.joinToString("/") { it.name },
        warenkorb=warenkorb.zuSpeicherWarenkorb(),
        inflationsziel=inflationswerte.first,
        nAbweichung=inflationswerte.second,
        sAbweichung=inflationswerte.third,
    )
}

fun Map<Rohstoffe, Int>.zuSpeicherWarenkorb(): String {
    require(values.all { menge -> menge >= 0 }) {
        "Warenkorbmengen dürfen nicht negativ sein."
    }
    return entries
        .filter { (_, menge) -> menge > 0 }
        .sortedBy { (rohstoff, _) -> rohstoff.ordinal }
        .joinToString("/") { (rohstoff, menge) -> "${rohstoff.name}#$menge" }
}
