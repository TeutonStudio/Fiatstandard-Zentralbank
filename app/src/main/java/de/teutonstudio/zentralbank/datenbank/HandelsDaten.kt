package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "TradeData")
data class HandelsDaten(
    @PrimaryKey(autoGenerate = true)
    val handelID: Int = 0,
    override val spielID: Long,

    val runde: Int,
    val besitzer: String,
    val erwerber: String,
    val menge: Int,
    val rohstoff: String,
    val preis: String,
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,handel: RohstoffHandel):this(
        spielID=-1, // rundeDaten.spielID,
        runde=rundeDaten.index,
        besitzer=handel.besitzer.name,
        erwerber=handel.erwerber.name,
        menge=handel.anzahl,
        rohstoff=handel.rohstoff.str,
        preis=handel.betrag.speichereString(),
    )
}
