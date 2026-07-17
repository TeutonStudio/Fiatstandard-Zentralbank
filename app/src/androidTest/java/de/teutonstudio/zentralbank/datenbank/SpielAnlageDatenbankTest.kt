package de.teutonstudio.zentralbank.datenbank

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.teutonstudio.zentralbank.daten.raumdatenbank.ZentralbankMigrationen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun migrationEntferntFehlgeschlagenenSpielstandMitReservierterId() = runBlocking {
        val (spiel, daten) = neuesSpiel().zuSpeicherDaten()
        val id = ZentralbankSpeicher(datenbank).insertSpielSatz(
            spiel.copy(spielID = -1) to daten,
        )
        assertEquals(-1L, id)

        ZentralbankMigrationen.VON_2_NACH_3.migrate(datenbank.openHelper.writableDatabase)

        assertNull(datenbank.gameDao().getById(-1))
        assertTrue(datenbank.playerDao().getBySpiel(-1).isEmpty())
        assertTrue(datenbank.roundDao().getBySpiel(-1).isEmpty())
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
