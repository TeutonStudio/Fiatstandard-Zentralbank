package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ContractData")
data class VertragsDaten(
    @PrimaryKey(autoGenerate = true)
    val vertragID: Int = 0,
    override val spielID: Long,

    val runde: Int,
    val vertragsannehmer: String,
    val vertragsanbieter: String,
    val vertragsart: String,
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,annehmer: String,anbieter: String,vertragsart: Vertragsart):this(
        spielID=-1, // rundeDaten.spielID,
        runde=rundeDaten.index,
        vertragsannehmer=annehmer,
        vertragsanbieter=anbieter,
        vertragsart=vertragsart.str,
    )
}
