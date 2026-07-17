package de.teutonstudio.zentralbank.daten.raumdatenbank.zugriff

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.teutonstudio.zentralbank.daten.raumdatenbank.entitaet.SpielstandEntitaet
import kotlinx.coroutines.flow.Flow

@Dao
interface SpielstandDao {
    @Query("SELECT * FROM FachSpielstand ORDER BY spielId ASC")
    fun spielstaendeBeobachten(): Flow<List<SpielstandEntitaet>>

    @Query("SELECT * FROM FachSpielstand WHERE spielId = :id LIMIT 1")
    suspend fun spielstandLaden(id: Long): SpielstandEntitaet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun spielstandSpeichern(spielstand: SpielstandEntitaet)

    @Query("DELETE FROM FachSpielstand WHERE spielId = :id")
    suspend fun spielstandLoeschen(id: Long)
}
