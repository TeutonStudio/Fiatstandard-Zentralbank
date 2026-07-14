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
        spielID=(-1).toLong(),
        spieler=spieler.joinToString("/") { it.name },
        warenkorb=warenkorb.map { "${it.key}#${it.value}" }.joinToString("/"),
        inflationsziel=inflationswerte.first,
        nAbweichung=inflationswerte.second,
        sAbweichung=inflationswerte.third,
    )
}
