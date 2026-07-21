package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "BuildData")
data class BauteilDaten(
    @PrimaryKey(autoGenerate = true)
    val bauwerkID: Int = 0,
    override val spielID: Long,

    val erbauer: String,
    val runde: Int,
    val bauteil: String,
    val delta: Int,
): SpeicherDaten
