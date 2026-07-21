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
        val id = ZentralbankSpeicher(datenbank).insertSpielSatz(neuePersistenzDaten())

        assertTrue(id > 0)
        assertEquals(id, datenbank.gameDao().getById(id.toInt())?.spielID)
    }

    private fun neuePersistenzDaten(): Pair<SpielDaten, List<SpeicherDaten>> = SpielDaten(
        spieler = "Anna",
        warenkorb = "",
        inflationsziel = 2f,
        nAbweichung = 1f,
        sAbweichung = 3f,
    ) to listOf(
        SpielerDaten(spielID = -1, spielerName = "Anna"),
        RundeDaten(spielID = -1, index = 0, leitzinssatz = 2f),
    )
}
