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
): SpeicherDaten
