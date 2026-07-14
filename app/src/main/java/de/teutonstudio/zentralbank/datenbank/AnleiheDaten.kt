package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "CreditData")
data class AnleiheDaten(
    @PrimaryKey(autoGenerate = true)
    val anleiheID: Int = 0,
    override val spielID: Long = 0,

    val emittiert: Int,
    val emittent: String,
    val sondervermogen: String,
    val unvermogen: String,
    val laufzeit: Int,
    var handel: String, // spekulant1,preis1,Zeitpunkt1/spekulant2,preis2,Zeitpunkt2
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,anleihe: Map<Int,Anleihenhandel>):this(
        spielID=-1,//rundeDaten.spielID,
        emittiert=rundeDaten.index,
        emittent=anleihe.erhalteErste().besitzer.name,
        sondervermogen=anleihe.erhalteErste().anleihe.sondervermögen.speichereString(),
        unvermogen=anleihe.erhalteErste().anleihe.unvermögen.speichereString(),
        laufzeit=anleihe.erhalteErste().anleihe.laufzeit,
        handel=anleihe.dropLowest().map { "${it.value.erwerber}#${it.value.preis.toIntOderNull()}#${it.key}" }.joinToString("/")
    )
}
