package de.teutonstudio.zentralbank.daten.karten

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
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
            eintrag.quelle == KartenQuelle.VORLAGE && eintrag.vorlage.id == "vorlage-inselreich"
        })

        val gespeichert = ablage.eigeneKarteSpeichern(
            KartenVorlage(
                id = "instrumentierter-entwurf",
                name = "Instrumentierte Karte",
            ),
            referenz = null,
        )
        try {
            val neuGeladen = ablage.alleKartenLaden()
                .single { eintrag -> eintrag.vorlage.id == gespeichert.id }

            assertEquals(KartenQuelle.EIGENE_KARTE, neuGeladen.quelle)
            assertEquals(gespeichert, neuGeladen.vorlage)
        } finally {
            File(kontext.filesDir, "karten/eigene/${gespeichert.id}.json").delete()
        }
    }

    @Test
    fun VeralteteEigeneRasterkarteWirdIgnoriert() = runBlocking {
        val kontext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val verzeichnis = File(kontext.filesDir, "karten/eigene").apply { mkdirs() }
        val alteKarte = File(verzeichnis, "veraltetes-format-test.json")
        alteKarte.writeText(
            """{
                "formatVersion": 2,
                "id": "eigene-veraltetes-format-test",
                "name": "Alte Rasterkarte",
                "zeilen": 2,
                "spalten": 3,
                "landfelder": [
                    {
                        "position": {"zeile": 1, "spalte": 2, "haelfte": "UNTEN"},
                        "gelaende": "EBENE"
                    }
                ]
            }""".trimIndent(),
        )

        try {
            val geladen = KartenAblage(kontext).alleKartenLaden()

            assertTrue(geladen.none { eintrag ->
                eintrag.vorlage.id == "eigene-veraltetes-format-test"
            })
        } finally {
            alteKarte.delete()
        }
    }

    @Test
    fun ReferenzbildWirdMitEigenerKarteGespeichertGeladenUndGeloescht() = runBlocking {
        val kontext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ablage = KartenAblage(kontext)
        val quellbild = File(kontext.cacheDir, "karten-referenz-ablage-test.bild").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val referenz = KartenReferenz(
            bildDatei = quellbild,
            metadaten = KartenReferenzMetadaten(
                zentrumX = 3.5f,
                zentrumZ = -7f,
                breiteInBrettEinheiten = 42f,
                deckkraft = 0.55f,
            ),
        )
        val gespeichert = ablage.eigeneKarteSpeichern(
            vorlage = KartenVorlage(id = "referenz-test", name = "Referenztest"),
            referenz = referenz,
        )

        try {
            val geladen = requireNotNull(ablage.referenzLaden(gespeichert.id))
            val eintrag = ablage.alleKartenLaden()
                .single { es -> es.vorlage.id == gespeichert.id }

            assertEquals(referenz.metadaten, geladen.metadaten)
            assertTrue(geladen.bildDatei.readBytes().contentEquals(quellbild.readBytes()))
            assertTrue(eintrag.hatReferenzbild)
            assertTrue(ablage.eigeneKarteLoeschen(gespeichert.id))
            assertEquals(null, ablage.referenzLaden(gespeichert.id))
        } finally {
            ablage.eigeneKarteLoeschen(gespeichert.id)
            quellbild.delete()
        }
    }
}
