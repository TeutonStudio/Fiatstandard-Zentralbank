package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "RoundData")
data class RundeDaten(
    @PrimaryKey(autoGenerate = true)
    val rundeID: Int = 0,
    override var spielID: Long,

    val index: Int,
    val leitzinssatz: Float,
): SpeicherDaten
