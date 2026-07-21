package de.teutonstudio.zentralbank.datenbank

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Database
import de.teutonstudio.zentralbank.daten.raumdatenbank.ZentralbankMigrationen
import de.teutonstudio.zentralbank.daten.raumdatenbank.entitaet.SpielstandEntitaet
import de.teutonstudio.zentralbank.daten.raumdatenbank.zugriff.SpielstandDao


@Database(
    entities = [
        SpielDaten::class,
        SpielerDaten::class,
        BauteilDaten::class,
        KontrolleDaten::class,
        RundeDaten::class,
        HandelsDaten::class,
        AnleiheDaten::class,
        VertragsDaten::class,
        SpielstandEntitaet::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun playerDao(): PlayerDao
    abstract fun buildDAO(): BuildDao
    abstract fun controlDao(): ControlDao
    abstract fun roundDao(): RoundDao
    abstract fun tradeDao(): TradeDao
    abstract fun creditDao(): CreditDao
    abstract fun contractDao(): ContractDao
    abstract fun spielstandDao(): SpielstandDao

    companion object {
        fun oeffnen(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "zentralbankspeicher",
        )
            .addMigrations(
                ZentralbankMigrationen.VON_1_NACH_2,
                ZentralbankMigrationen.VON_2_NACH_3,
                ZentralbankMigrationen.VON_3_NACH_4,
            )
            .build()
    }
}
