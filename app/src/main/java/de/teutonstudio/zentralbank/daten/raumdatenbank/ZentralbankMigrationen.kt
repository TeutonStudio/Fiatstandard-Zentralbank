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

    val VON_2_NACH_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "ContractData",
                "CreditData",
                "TradeData",
                "RoundData",
                "ControlData",
                "BuildData",
                "PlayerData",
                "GameData",
            ).forEach { tabelle ->
                db.execSQL("DELETE FROM `$tabelle` WHERE `spielID` = -1")
            }
            db.execSQL("DELETE FROM `FachSpielstand` WHERE `spielId` = -1")
        }
    }

    val VON_3_NACH_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE `FachSpielstand_neu` (
                    `spielId` INTEGER NOT NULL,
                    `formatVersion` INTEGER NOT NULL,
                    `startzustandJson` TEXT NOT NULL,
                    `ereignisseJson` TEXT NOT NULL,
                    PRIMARY KEY(`spielId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `FachSpielstand_neu`
                    (`spielId`, `formatVersion`, `startzustandJson`, `ereignisseJson`)
                SELECT `spielId`, `formatVersion`, `startzustandJson`, `ereignisseJson`
                FROM `FachSpielstand`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `FachSpielstand`")
            db.execSQL("ALTER TABLE `FachSpielstand_neu` RENAME TO `FachSpielstand`")
        }
    }
}
