package de.teutonstudio.zentralbank.daten.raumdatenbank

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ZentralbankMigrationen {
    val VON_1_NACH_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `FachSpielstand` (
                    `spielId` INTEGER NOT NULL,
                    `formatVersion` INTEGER NOT NULL,
                    `startzustandJson` TEXT NOT NULL,
                    `ereignisseJson` TEXT NOT NULL,
                    `ausLegacyDatenImportiert` INTEGER NOT NULL,
                    PRIMARY KEY(`spielId`)
                )
                """.trimIndent(),
            )
        }
    }
}
