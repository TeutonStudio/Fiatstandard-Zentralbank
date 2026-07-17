package de.teutonstudio.zentralbank.datenbank

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpielAnlageDatenbankTest {
    private lateinit var datenbank: AppDatabase

    @Before
    fun datenbankOeffnen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        datenbank = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun datenbankSchliessen() {
        datenbank.close()
    }

    @Test
    fun neuesSpielErhaeltPositiveDatenbankId() = runBlocking {
        val id = ZentralbankSpeicher(datenbank).insertSpielSatz(neuesSpiel().zuSpeicherDaten())

        assertTrue(id > 0)
        assertEquals(id, datenbank.gameDao().getById(id.toInt())?.spielID)
    }

    private fun neuesSpiel(): Spiel = Spiel(
        leitzinssatz = 2f,
        spieler = mapOf(Spieler("Anna", emptyMap()) to 100.toZahlungsmittel()),
        warenkorb = emptyMap(),
        inflationsziel = 2f,
        normaleAbweichung = 1f,
        starkeAbweichung = 3f,
    )
}
