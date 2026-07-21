package de.teutonstudio.zentralbank.datenbank

import androidx.room.*
import de.teutonstudio.zentralbank.datenbank.SpielDaten
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun <A,B> Pair<A,B>.toMap(): Map<A,B> = listOf(this).toMap()
private fun <T : SpeicherDaten> Flow<List<T>>.alsSpeicherDaten(): Flow<List<SpeicherDaten>> =
    map { liste -> liste.map { it as SpeicherDaten } }

class ZentralbankSpeicher(
    private val db: AppDatabase,
) {
    private val gameDao = db.gameDao()
    private val playerDao = db.playerDao()
    private val buildDao = db.buildDAO()
    private val controlDao = db.controlDao()
    private val roundDao = db.roundDao()
    private val tradeDao = db.tradeDao()
    private val creditDao = db.creditDao()
    private val contractDao = db.contractDao()

    public suspend fun insertAll(daten: Map<SpielDaten,List<SpeicherDaten>>): List<Long> = db.withTransaction {
        return@withTransaction daten.keys.map { spiel ->
            val datenListe = daten[spiel]?: emptyList()
            val ID = gameDao.insert(spiel)
            datenListe.filterIsInstance<SpielerDaten>().let { if (it.isNotEmpty()) playerDao.insertAllBySpiel(it,ID) }
            datenListe.filterIsInstance<BauteilDaten>().let { if (it.isNotEmpty()) buildDao.insertAllBySpiel(it,ID) }
            datenListe.filterIsInstance<KontrolleDaten>().let { if (it.isNotEmpty()) controlDao.insertAllBySpiel(it,ID) }
            datenListe.filterIsInstance<RundeDaten>().let { if (it.isNotEmpty()) roundDao.insertAllBySpiel(it,ID) }
            datenListe.filterIsInstance<HandelsDaten>().let { if (it.isNotEmpty()) tradeDao.insertAllBySpiel(it,ID) }
            datenListe.filterIsInstance<AnleiheDaten>().let { if (it.isNotEmpty()) creditDao.insertAllBySpiel(it,ID) }
            datenListe.filterIsInstance<VertragsDaten>().let { if (it.isNotEmpty()) contractDao.insertAllBySpiel(it,ID) }
            ID
        }
    }

    public suspend fun insertSpielSatz(daten: Pair<SpielDaten,List<SpeicherDaten>>): Long = insertAll(daten.toMap()).first()

    public suspend fun updateSpiel(daten: SpielDaten) {
        gameDao.update(daten)
    }

    public suspend fun insertHandel(daten: HandelsDaten): Long = tradeDao.insert(daten)

    public suspend fun insertAnleihe(daten: AnleiheDaten): Long = creditDao.insert(daten)

    public suspend fun updateAnleiheHandel(daten: AnleiheDaten) {
        val gespeichert = if (daten.anleiheID != 0) {
            daten
        } else {
            creditDao.getBySpiel(daten.spielID.toInt())
                .lastOrNull { kandidat ->
                    kandidat.emittiert == daten.emittiert &&
                        kandidat.emittent == daten.emittent &&
                        kandidat.sondervermogen == daten.sondervermogen &&
                        kandidat.unvermogen == daten.unvermogen &&
                        kandidat.laufzeit == daten.laufzeit &&
                        daten.handel.startsWith(kandidat.handel)
                }
                ?.copy(handel = daten.handel)
                ?: error("Gespeicherte Anleihe konnte nicht gefunden werden.")
        }
        creditDao.update(gespeichert)
    }

    public suspend fun insertRunde(daten: RundeDaten): Long = roundDao.insert(daten)

    public fun observeDatenZuSpiel(spielID: Long): Flow<List<SpeicherDaten>> {
        val flows: List<Flow<List<SpeicherDaten>>> = listOf(
            roundDao.observeBySpiel(spielID).alsSpeicherDaten(),
            playerDao.observeBySpiel(spielID).alsSpeicherDaten(),
            buildDao.observeBySpiel(spielID).alsSpeicherDaten(),
            controlDao.observeBySpiel(spielID).alsSpeicherDaten(),
            tradeDao.observeBySpiel(spielID).alsSpeicherDaten(),
            creditDao.observeBySpiel(spielID).alsSpeicherDaten(),
            contractDao.observeBySpiel(spielID).alsSpeicherDaten(),
        )

        return combine(flows) { datenPakete ->
            datenPakete.flatMap { it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public fun observeAlleNachSpiel(): Flow<Map<SpielDaten, List<SpeicherDaten>>> {
        return gameDao.observeAll().flatMapLatest { spiele ->
            if (spiele.isEmpty()) { flowOf(emptyMap()) } else {
                val spielFlows: List<Flow<Pair<SpielDaten, List<SpeicherDaten>>>> = spiele.map { spiel ->
                    observeDatenZuSpiel(spiel.spielID).map { daten -> spiel to daten }
                }
                combine(spielFlows) { paare -> paare.toMap() }
            }
        }
    }
}

@Dao
interface GameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spiel: SpielDaten): Long

    @Update
    suspend fun update(spiel: SpielDaten)

    @Delete
    suspend fun delete(spiel: SpielDaten)

    @Query("SELECT * FROM GameData WHERE spielID = :spielID LIMIT 1")
    suspend fun getById(spielID: Int): SpielDaten?

    @Query("SELECT * FROM GameData WHERE spielID = :spielID LIMIT 1")
    fun observeById(spielID: Int): Flow<SpielDaten?>

    @Query("SELECT * FROM GameData ORDER BY spielID ASC")
    fun observeAll(): Flow<List<SpielDaten>>

    @Query("DELETE FROM GameData WHERE spielID = :spielID")
    suspend fun deleteById(spielID: Int)
}

@Dao
interface PlayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spieler: SpielerDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(spieler: List<SpielerDaten>): List<Long>

    suspend fun insertAllBySpiel(spieler: List<SpielerDaten>,spielID: Long): List<Long> = insertAll(spieler.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(spieler: SpielerDaten)

    @Delete
    suspend fun delete(spieler: SpielerDaten)

    @Query("SELECT * FROM PlayerData WHERE spielerID = :spielerID LIMIT 1")
    suspend fun getById(spielerID: Int): SpielerDaten?

    @Query("SELECT * FROM PlayerData WHERE spielID = :spielID ORDER BY spielerID ASC")
    suspend fun getBySpiel(spielID: Int): List<SpielerDaten>

    @Query("SELECT * FROM PlayerData WHERE spielID = :spielID ORDER BY spielerID ASC")
    fun observeBySpiel(spielID: Long): Flow<List<SpielerDaten>>

    @Query("DELETE FROM PlayerData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM PlayerData WHERE spielerID = :spielerID")
    suspend fun deleteById(spielerID: Int)
}

@Dao
interface BuildDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bauteil: BauteilDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bauteile: List<BauteilDaten>): List<Long>

    suspend fun insertAllBySpiel(bauteile: List<BauteilDaten>, spielID: Long): List<Long> = insertAll(bauteile.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(bauteil: BauteilDaten)

    @Delete
    suspend fun delete(bauteil: BauteilDaten)

    @Query("SELECT * FROM BuildData WHERE bauwerkID = :bauwerkID LIMIT 1")
    suspend fun getById(bauwerkID: Int): BauteilDaten?

    @Query("""
        SELECT * FROM BuildData
        WHERE spielID = :spielID
        ORDER BY runde ASC, bauwerkID ASC
    """)
    suspend fun getBySpiel(spielID: Int): List<BauteilDaten>

    @Query("""
        SELECT * FROM BuildData
        WHERE spielID = :spielID
        ORDER BY runde ASC, bauwerkID ASC
    """)
    fun observeBySpiel(spielID: Long): Flow<List<BauteilDaten>>

    @Query("""
        SELECT * FROM BuildData
        WHERE spielID = :spielID AND runde = :runde
        ORDER BY bauwerkID ASC
    """)
    suspend fun getByRunde(spielID: Int, runde: Int): List<BauteilDaten>

    @Query("""
        SELECT * FROM BuildData
        WHERE spielID = :spielID AND erbauer = :spieler
        ORDER BY runde ASC, bauwerkID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spieler: String): List<BauteilDaten>

    @Query("""
        SELECT b.* FROM BuildData AS b
        INNER JOIN PlayerData AS p
            ON p.spielerID = :spielerID
            AND p.spielID = :spielID
        WHERE b.spielID = :spielID
        AND b.erbauer = p.spielerName
        ORDER BY b.runde ASC, b.bauwerkID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spielerID: Int): List<BauteilDaten>

    @Query("DELETE FROM BuildData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM BuildData WHERE bauwerkID = :bauwerkID")
    suspend fun deleteById(bauwerkID: Int)
}

@Dao
interface ControlDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(kontrolle: KontrolleDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(kontrollen: List<KontrolleDaten>): List<Long>

    suspend fun insertAllBySpiel(kontrollen: List<KontrolleDaten>, spielID: Long): List<Long> = insertAll(kontrollen.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(kontrolle: KontrolleDaten)

    @Delete
    suspend fun delete(kontrolle: KontrolleDaten)

    @Query("SELECT * FROM ControlData WHERE kontrolleID = :kontrolleID LIMIT 1")
    suspend fun getById(kontrolleID: Int): KontrolleDaten?

    @Query("""
        SELECT * FROM ControlData
        WHERE spielID = :spielID
        ORDER BY runde ASC, kontrolleID ASC
    """)
    suspend fun getBySpiel(spielID: Int): List<KontrolleDaten>

    @Query("""
        SELECT * FROM ControlData
        WHERE spielID = :spielID
        ORDER BY runde ASC, kontrolleID ASC
    """)
    fun observeBySpiel(spielID: Long): Flow<List<KontrolleDaten>>

    @Query("""
        SELECT * FROM ControlData
        WHERE spielID = :spielID AND runde = :runde
        ORDER BY kontrolleID ASC
    """)
    suspend fun getByRunde(spielID: Int, runde: Int): List<KontrolleDaten>

    @Query("""
        SELECT * FROM ControlData
        WHERE spielID = :spielID AND besatzer = :spieler
        ORDER BY runde ASC, kontrolleID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spieler: String): List<KontrolleDaten>

    @Query("""
        SELECT k.* FROM ControlData AS k
        INNER JOIN PlayerData AS p
            ON p.spielerID = :spielerID
            AND p.spielID = :spielID
        WHERE k.spielID = :spielID
        AND k.besatzer = p.spielerName
        ORDER BY k.runde ASC, k.kontrolleID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spielerID: Int): List<KontrolleDaten>

    @Query("""
        SELECT * FROM ControlData
        WHERE spielID = :spielID AND region = :region
        ORDER BY runde ASC, kontrolleID ASC
    """)
    suspend fun getByRegion(spielID: Int, region: String): List<KontrolleDaten>

    @Query("DELETE FROM ControlData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM ControlData WHERE kontrolleID = :kontrolleID")
    suspend fun deleteById(kontrolleID: Int)
}

@Dao
interface RoundDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(runde: RundeDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(runden: List<RundeDaten>): List<Long>

    suspend fun insertAllBySpiel(runden: List<RundeDaten>, spielID: Long): List<Long> = insertAll(runden.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(runde: RundeDaten)

    @Delete
    suspend fun delete(runde: RundeDaten)

    @Query("SELECT * FROM RoundData WHERE rundeID = :rundeID LIMIT 1")
    suspend fun getById(rundeID: Int): RundeDaten?

    @Query("""
        SELECT * FROM RoundData
        WHERE spielID = :spielID
        ORDER BY `index` ASC
    """)
    suspend fun getBySpiel(spielID: Int): List<RundeDaten>

    @Query("""
        SELECT COALESCE(MAX(`index`), 0)
        FROM RoundData
        WHERE spielID = :spielID
    """)
    fun observeMaxIndexBySpiel(spielID: Int): Flow<Int>

    @Query("""
        SELECT * FROM RoundData
        WHERE spielID = :spielID
        ORDER BY `index` ASC
    """)
    fun observeBySpiel(spielID: Long): Flow<List<RundeDaten>>

    @Query("""
        SELECT * FROM RoundData
        WHERE spielID = :spielID AND `index` = :index
        LIMIT 1
    """)
    suspend fun getByIndex(spielID: Int, index: Int): RundeDaten?

    @Query("""
        SELECT * FROM RoundData
        WHERE spielID = :spielID
        ORDER BY `index` DESC
        LIMIT 1
    """)
    suspend fun getLetzteRunde(spielID: Int): RundeDaten?

    @Query("""
        SELECT COUNT(*) FROM RoundData
        WHERE spielID = :spielID
    """)
    suspend fun countBySpiel(spielID: Int): Int

    @Query("DELETE FROM RoundData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM RoundData WHERE rundeID = :rundeID")
    suspend fun deleteById(rundeID: Int)
}

@Dao
interface TradeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(handel: HandelsDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(handel: List<HandelsDaten>): List<Long>

    suspend fun insertAllBySpiel(handel: List<HandelsDaten>, spielID: Long): List<Long> = insertAll(handel.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(handel: HandelsDaten)

    @Delete
    suspend fun delete(handel: HandelsDaten)

    @Query("SELECT * FROM TradeData WHERE handelID = :handelID LIMIT 1")
    suspend fun getById(handelID: Int): HandelsDaten?

    @Query("""
        SELECT * FROM TradeData
        WHERE spielID = :spielID
        ORDER BY runde ASC, handelID ASC
    """)
    suspend fun getBySpiel(spielID: Int): List<HandelsDaten>

    @Query("""
        SELECT * FROM TradeData
        WHERE spielID = :spielID
        ORDER BY runde ASC, handelID ASC
    """)
    fun observeBySpiel(spielID: Long): Flow<List<HandelsDaten>>

    @Query("""
        SELECT * FROM TradeData
        WHERE spielID = :spielID AND runde = :runde
        ORDER BY handelID ASC
    """)
    suspend fun getByRunde(spielID: Int, runde: String): List<HandelsDaten>

    @Query("""
        SELECT h.* FROM TradeData AS h
        INNER JOIN RoundData AS r
            ON r.rundeID = :rundeID
            AND r.spielID = :spielID
        WHERE h.spielID = :spielID
        AND h.runde = r.`index`
        ORDER BY h.handelID ASC
    """)
    suspend fun getByRunde(spielID: Int, rundeID: Int): List<HandelsDaten>

    @Query("""
        SELECT * FROM TradeData
        WHERE spielID = :spielID
        AND (besitzer = :spieler OR erwerber = :spieler)
        ORDER BY runde ASC, handelID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spieler: String): List<HandelsDaten>

    @Query("""
        SELECT h.* FROM TradeData AS h
        INNER JOIN PlayerData AS p
            ON p.spielerID = :spielerID
            AND p.spielID = :spielID
        WHERE h.spielID = :spielID
        AND (
            h.besitzer = p.spielerName
            OR h.erwerber = p.spielerName
        )
        ORDER BY h.runde ASC, h.handelID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spielerID: Int): List<HandelsDaten>

    @Query("""
        SELECT * FROM TradeData
        WHERE spielID = :spielID AND rohstoff = :rohstoff
        ORDER BY runde ASC, handelID ASC
    """)
    suspend fun getByRohstoff(spielID: Int, rohstoff: String): List<HandelsDaten>

    @Query("DELETE FROM TradeData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM TradeData WHERE handelID = :handelID")
    suspend fun deleteById(handelID: Int)
}

@Dao
interface CreditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anleihe: AnleiheDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(anleihen: List<AnleiheDaten>): List<Long>

    suspend fun insertAllBySpiel(anleihen: List<AnleiheDaten>, spielID: Long): List<Long> = insertAll(anleihen.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(anleihe: AnleiheDaten)

    @Delete
    suspend fun delete(anleihe: AnleiheDaten)

    @Query("SELECT * FROM CreditData WHERE anleiheID = :anleiheID LIMIT 1")
    suspend fun getById(anleiheID: Int): AnleiheDaten?

    @Query("""
        SELECT * FROM CreditData
        WHERE spielID = :spielID
        ORDER BY anleiheID ASC
    """)
    suspend fun getBySpiel(spielID: Int): List<AnleiheDaten>

    @Query("""
        SELECT * FROM CreditData
        WHERE spielID = :spielID
        ORDER BY anleiheID ASC
    """)
    fun observeBySpiel(spielID: Long): Flow<List<AnleiheDaten>>

    @Query("""
        SELECT * FROM CreditData
        WHERE spielID = :spielID AND emittiert = :spieler
        ORDER BY anleiheID ASC
    """)
    suspend fun getByEmittiertSpieler(spielID: Int, spieler: String): List<AnleiheDaten>

    @Query("""
        SELECT c.* FROM CreditData AS c
        INNER JOIN PlayerData AS p
            ON p.spielerID = :spielerID
            AND p.spielID = :spielID
        WHERE c.spielID = :spielID
        AND c.emittiert = p.spielerName
        ORDER BY c.anleiheID ASC
    """)
    suspend fun getByEmittiertSpieler(spielID: Int, spielerID: Int): List<AnleiheDaten>

    @Query("""
        SELECT * FROM CreditData
        WHERE spielID = :spielID AND emittent = :emittent
        ORDER BY anleiheID ASC
    """)
    suspend fun getByEmittentId(spielID: Int, emittent: String): List<AnleiheDaten>

    @Query("""
        SELECT c.* FROM CreditData AS c
        INNER JOIN PlayerData AS p
            ON p.spielerID = :emittentID
            AND p.spielID = :spielID
        WHERE c.spielID = :spielID
        AND c.emittent = p.spielerName
        ORDER BY c.anleiheID ASC
    """)
    suspend fun getByEmittentId(spielID: Int, emittentID: Int): List<AnleiheDaten>

    @Query("""
        UPDATE CreditData
        SET handel = :handel
        WHERE anleiheID = :anleiheID
    """)
    suspend fun updateHandel(anleiheID: Int, handel: String)

    @Query("DELETE FROM CreditData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM CreditData WHERE anleiheID = :anleiheID")
    suspend fun deleteById(anleiheID: Int)
}

@Dao
interface ContractDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vertrag: VertragsDaten): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vertraege: List<VertragsDaten>): List<Long>

    suspend fun insertAllBySpiel(vertraege: List<VertragsDaten>, spielID: Long): List<Long> = insertAll(vertraege.map { it.copy(spielID = spielID) })

    @Update
    suspend fun update(vertrag: VertragsDaten)

    @Delete
    suspend fun delete(vertrag: VertragsDaten)

    @Query("SELECT * FROM ContractData WHERE vertragID = :vertragID LIMIT 1")
    suspend fun getById(vertragID: Int): VertragsDaten?

    @Query("""
        SELECT * FROM ContractData
        WHERE spielID = :spielID
        ORDER BY runde ASC, vertragID ASC
    """)
    suspend fun getBySpiel(spielID: Int): List<VertragsDaten>

    @Query("""
        SELECT * FROM ContractData
        WHERE spielID = :spielID
        ORDER BY runde ASC, vertragID ASC
    """)
    fun observeBySpiel(spielID: Long): Flow<List<VertragsDaten>>

    @Query("""
        SELECT * FROM ContractData
        WHERE spielID = :spielID AND runde = :rundeID
        ORDER BY vertragID ASC
    """)
    suspend fun getByRunde(spielID: Int, rundeID: Int): List<VertragsDaten>

    @Query("""
        SELECT * FROM ContractData
        WHERE spielID = :spielID
        AND (vertragsannehmer = :spieler OR vertragsanbieter = :spieler)
        ORDER BY runde ASC, vertragID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spieler: String): List<VertragsDaten>

    @Query("""
        SELECT c.* FROM ContractData AS c
        INNER JOIN PlayerData AS p
            ON p.spielerID = :spielerID
            AND p.spielID = :spielID
        WHERE c.spielID = :spielID
        AND (
            c.vertragsannehmer = p.spielerName
            OR c.vertragsanbieter = p.spielerName
        )
        ORDER BY c.runde ASC, c.vertragID ASC
    """)
    suspend fun getBySpieler(spielID: Int, spielerID: Int): List<VertragsDaten>

    @Query("""
        SELECT * FROM ContractData
        WHERE spielID = :spielID AND vertragsart = :vertragsart
        ORDER BY runde ASC, vertragID ASC
    """)
    suspend fun getByVertragsart(spielID: Int, vertragsart: String): List<VertragsDaten>

    @Query("DELETE FROM ContractData WHERE spielID = :spielID")
    suspend fun deleteBySpiel(spielID: Int)

    @Query("DELETE FROM ContractData WHERE vertragID = :vertragID")
    suspend fun deleteById(vertragID: Int)
}
