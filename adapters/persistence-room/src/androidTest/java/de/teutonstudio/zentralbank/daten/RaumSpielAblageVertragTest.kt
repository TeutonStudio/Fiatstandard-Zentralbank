package de.teutonstudio.zentralbank.daten

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielAblage
import de.teutonstudio.zentralbank.datenbank.AppDatabase
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RaumSpielAblageVertragTest {
    private lateinit var datenbank: AppDatabase
    private lateinit var ablage: SpielAblage

    @Before
    fun oeffnen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        datenbank = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        ablage = RaumSpielAblage(datenbank)
    }

    @After
    fun schliessen() = datenbank.close()

    @Test
    fun speichernLadenBeobachtenUndLoeschenFolgenDemApplicationPort() = runBlocking {
        val gespeichert = GespeichertesSpiel(
            id = 5,
            startzustand = SpielZustand(
                spieler = listOf(Spieler(SpielerId("Anna"), "Anna")),
            ),
            ereignisse = listOf(SpielEreignis.ProzugBegonnen(1L)),
        )

        ablage.spielSpeichern(gespeichert)
        assertEquals(gespeichert, ablage.spielLaden(5))
        assertEquals(listOf(5L), ablage.spielstaendeBeobachten().first().map { it.id })

        ablage.spielLoeschen(5)
        assertNull(ablage.spielLaden(5))
    }
}
