package de.teutonstudio.zentralbank.daten

import android.content.Context
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.datenbank.AppDatabase
import de.teutonstudio.zentralbank.datenbank.ZentralbankSpeicher

/** Kompositionswurzel des Android-Persistenzadapters; hält Room aus ViewModels heraus. */
class RoomSpielPersistenz private constructor(
    val spielAblage: SpielAblage,
    val legacyTabellenSpeicher: ZentralbankSpeicher,
) {
    companion object {
        fun oeffnen(context: Context): RoomSpielPersistenz {
            val datenbank = AppDatabase.oeffnen(context)
            return RoomSpielPersistenz(
                spielAblage = RaumSpielAblage(datenbank),
                legacyTabellenSpeicher = ZentralbankSpeicher(datenbank),
            )
        }
    }
}
