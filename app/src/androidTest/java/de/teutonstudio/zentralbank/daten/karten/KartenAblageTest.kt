package de.teutonstudio.zentralbank.daten.karten

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.teutonstudio.zentralbank.datenbank.TestSpiel
import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenVorlage
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KartenAblageTest {
    @Test
    fun EuropaVorlagenWerdenInFuenfSkalierungenGeladen() = runBlocking {
        val kontext = ApplicationProvider.getApplicationContext<android.content.Context>()

        val europaVorlagen = KartenAblage(kontext).alleKartenLaden()
            .filter { eintrag ->
                eintrag.quelle == KartenQuelle.VORLAGE &&
                    eintrag.vorlage.id.startsWith("vorlage-europa-")
            }
            .map(KartenEintrag::vorlage)
            .sortedBy { vorlage -> vorlage.hexagon.radius }

        assertEquals(
            listOf(
                "vorlage-europa-1-kompakt",
                "vorlage-europa-2-klein",
                "vorlage-europa-3-mittel",
                "vorlage-europa-4-gross",
                "vorlage-europa-5-kontinental",
            ),
            europaVorlagen.map(KartenVorlage::id),
        )
        assertEquals(listOf(11, 13, 15, 17, 19), europaVorlagen.map { it.hexagon.radius })
        assertEquals(
            listOf(420, 594, 796, 1019, 1281),
            europaVorlagen.map { vorlage -> vorlage.gelaendefelder.size },
        )
        assertTrue(
            europaVorlagen.zipWithNext().all { (kleiner, groesser) ->
                kleiner.gelaendefelder.size < groesser.gelaendefelder.size
            },
        )

        val testspielKarte = requireNotNull(TestSpiel.karte)
        val groessteVorlage = europaVorlagen.last()
        assertEquals("testspiel-europa-5-kontinental", testspielKarte.id)
        assertEquals(groessteVorlage.hexagon, testspielKarte.hexagon)
        assertEquals(groessteVorlage.gelaendefelder, testspielKarte.gelaendefelder)
    }

    @Test
    fun KompakteEuropaTopologieTrenntBritischeInselnUndErschliesstDenOsten() = runBlocking {
        val kontext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val kompakt = KartenAblage(kontext).alleKartenLaden()
            .single { eintrag -> eintrag.vorlage.id == "vorlage-europa-1-kompakt" }
            .vorlage
        val land = kompakt.gelaendefelder.mapTo(mutableSetOf()) { feld -> feld.position }
        val irland = KartenFeld(-1, -9, DreieckHaelfte.OBEN)
        val grossbritannien = KartenFeld(-1, -7, DreieckHaelfte.UNTEN)
        val festland = KartenFeld(1, -5, DreieckHaelfte.OBEN)
        val russland = KartenFeld(-3, 9, DreieckHaelfte.UNTEN)
        val kaukasus = KartenFeld(6, 2, DreieckHaelfte.UNTEN)
        val anatolien = KartenFeld(7, -1, DreieckHaelfte.UNTEN)

        assertTrue(
            listOf(irland, grossbritannien, festland, russland, kaukasus, anatolien)
                .all { position -> position in land },
        )
        assertFalse(grossbritannien in erreichbareLandfelder(irland, land))
        assertFalse(festland in erreichbareLandfelder(grossbritannien, land))

        val festlandKomponente = erreichbareLandfelder(festland, land)
        assertTrue(russland in festlandKomponente)
        assertTrue(kaukasus in festlandKomponente)
        assertTrue(anatolien in festlandKomponente)
    }

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

    private fun erreichbareLandfelder(
        start: KartenFeld,
        land: Set<KartenFeld>,
    ): Set<KartenFeld> {
        require(start in land)
        val felderNachKante = buildMap {
            land.forEach { feld ->
                feld.kanten().forEach { kante ->
                    getOrPut(kante) { mutableListOf() }.add(feld)
                }
            }
        }
        val besucht = mutableSetOf(start)
        val offen = ArrayDeque<KartenFeld>().apply { add(start) }
        while (offen.isNotEmpty()) {
            val aktuell = offen.removeFirst()
            aktuell.kanten().forEach { kante ->
                felderNachKante[kante].orEmpty().forEach { nachbar ->
                    if (besucht.add(nachbar)) offen.add(nachbar)
                }
            }
        }
        return besucht
    }
}
