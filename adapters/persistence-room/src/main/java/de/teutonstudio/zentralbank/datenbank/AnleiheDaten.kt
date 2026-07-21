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
): SpeicherDaten
