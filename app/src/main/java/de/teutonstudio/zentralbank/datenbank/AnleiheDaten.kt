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
    var handel: String,
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,anleihe: Map<Int,Anleihenhandel>):this(
        spielID=-1,//rundeDaten.spielID,
        emittiert=rundeDaten.index,
        emittent=anleihe.erhalteErste().anleihe.schuldiger.name,
        sondervermogen=anleihe.erhalteErste().anleihe.sondervermögen.speichereString(),
        unvermogen=anleihe.erhalteErste().anleihe.unvermögen.speichereString(),
        laufzeit=anleihe.erhalteErste().anleihe.laufzeit,
        // Das neue Format enthält auch den Emissionshandel und beide Parteien:
        // Besitzer#Erwerber#gespeicherterPreis#Runde|...
        // "|" kollidiert nicht mit dem Zahlungsmittel-Format, das "/" verwendet.
        handel=anleihe.toSortedMap().map { (runde, handel) ->
            "${handel.besitzer.name}#${handel.erwerber.name}#${handel.preis.speichereString()}#$runde"
        }.joinToString("|")
    )
}
