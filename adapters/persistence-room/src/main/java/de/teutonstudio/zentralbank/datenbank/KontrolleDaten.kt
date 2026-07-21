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
): SpeicherDaten
