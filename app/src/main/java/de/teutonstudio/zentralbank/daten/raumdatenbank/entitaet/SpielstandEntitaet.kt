package de.teutonstudio.zentralbank.daten.raumdatenbank.entitaet

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "FachSpielstand")
data class SpielstandEntitaet(
    @PrimaryKey
    val spielId: Long,
    val formatVersion: Int,
    val startzustandJson: String,
    val ereignisseJson: String,
    val ausLegacyDatenImportiert: Boolean,
)
