package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "PlayerData")
data class SpielerDaten(
    @PrimaryKey(autoGenerate = true)
    val spielerID: Int = 0, // -1 bedeutet Ausland oder Geschäftsbank, abhängig des Kontext

    override var spielID: Long,

    val spielerName: String,
): SpeicherDaten {
    constructor(name: String):this(
        spielID = -1,
        spielerName = name,
    )
}
