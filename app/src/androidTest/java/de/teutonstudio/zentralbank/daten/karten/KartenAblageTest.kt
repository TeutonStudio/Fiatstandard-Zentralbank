package de.teutonstudio.zentralbank.daten.karten

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KartenAblageTest {
    @Test
    fun VorlageWirdGeladenUndEigeneKarteBleibtErhalten() = runBlocking {
        val kontext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ablage = KartenAblage(kontext)
        val vorlagen = ablage.alleKartenLaden()

        assertTrue(vorlagen.any { eintrag ->
            eintrag.quelle == KartenQuelle.VORLAGE && eintrag.karte.id == "vorlage-inselreich"
        })

        val gespeichert = ablage.eigeneKarteSpeichern(
            Spielkarte(
                id = "instrumentierter-entwurf",
                name = "Instrumentierte Karte",
                zeilen = 3,
                spalten = 4,
            ),
        )
        try {
            val neuGeladen = ablage.alleKartenLaden()
                .single { eintrag -> eintrag.karte.id == gespeichert.id }

            assertEquals(KartenQuelle.EIGENE_KARTE, neuGeladen.quelle)
            assertEquals(gespeichert, neuGeladen.karte)
        } finally {
            File(kontext.filesDir, "karten/eigene/${gespeichert.id}.json").delete()
        }
    }
}
