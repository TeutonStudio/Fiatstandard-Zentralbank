package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ControlData")
data class KontrolleDaten(
    @PrimaryKey(autoGenerate = true)
    val kontrolleID: Int = 0,
    override val spielID: Long,

    val besatzer: String,
    val runde: Int,
    val region: String,
    val delta: Int,
): SpeicherDaten {
    constructor(spieler: SpielerDaten, runde: RundeDaten, region: Wirtschaftsregionen, änderung: Int):this(
        spielID = -1, // runde.spielID,
        besatzer = spieler.spielerName,
        runde = runde.index,
        region = region.str,
        delta = änderung,
    )
}
