package de.teutonstudio.zentralbank.fachlogik.generierung

import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.VorkommensArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KartenGeneratorTest {
    private val generator = KartenGenerator()

    @Test
    fun gleicherSeedErzeugtIdentischeKarte() {
        val konfiguration = KartenGeneratorKonfiguration(radius = 8)

        val a = generator.generiere(42L, konfiguration)
        val b = generator.generiere(42L, konfiguration)

        assertEquals(a.vorlage, b.vorlage)
        assertEquals(a.qualitaet, b.qualitaet)
        assertEquals(AKTUELLE_KARTEN_GENERATOR_VERSION, a.generatorVersion)
    }

    @Test
    fun verschiedeneSeedsErzeugenVerschiedeneKarten() {
        val konfiguration = KartenGeneratorKonfiguration(radius = 8)

        val a = generator.generiere(11L, konfiguration)
        val b = generator.generiere(12L, konfiguration)

        assertNotEquals(a.vorlage.gelaendefelder, b.vorlage.gelaendefelder)
    }

    @Test
    fun standardkarteEnthaeltAlleProduktionsgrundlagen() {
        val ergebnis = generator.generiere(
            seed = 1_234_567L,
            konfiguration = KartenGeneratorKonfiguration(radius = 10),
        )
        val karte = ergebnis.vorlage

        assertTrue(ergebnis.qualitaet.spielbar)
        assertTrue(karte.gelaendefelder.any { it.gelaende == GelaendeTyp.EBENE })
        assertTrue(karte.gelaendefelder.any { it.gelaende == GelaendeTyp.WALD })
        assertTrue(karte.gelaendefelder.any { it.gelaende == GelaendeTyp.GEBIRGE })
        VorkommensArt.entries.forEach { art ->
            assertTrue("Vorkommen $art fehlt", karte.vorkommen.any { it.art == art })
        }
    }

    @Test
    fun bergbauvorkommenLiegenAusschliesslichImGebirge() {
        val karte = generator.generiere(
            seed = 987_654L,
            konfiguration = KartenGeneratorKonfiguration(
                radius = 9,
                profil = KartenGeneratorProfil.ROHSTOFFREICH,
                vorkommensDichte = VorkommensDichte.REICH,
            ),
        ).vorlage

        karte.vorkommen
            .filter { it.art != VorkommensArt.ROHOEL }
            .forEach { vorkommen ->
                assertEquals(GelaendeTyp.GEBIRGE, karte.landNachPosition[vorkommen.position])
            }
        karte.vorkommen.forEach { vorkommen ->
            assertTrue(vorkommen.position in karte.landNachPosition)
        }
    }

    @Test
    fun vorkommenKoennenAufBestehenderKarteNeuVerteiltWerden() {
        val basis = generator.generiere(100L, KartenGeneratorKonfiguration(radius = 8)).vorlage
            .copy(vorkommen = emptyList())

        val neu = generator.verteileVorkommenNeu(basis, seed = 200L)

        assertEquals(basis.gelaendefelder, neu.gelaendefelder)
        assertEquals(basis.spezialfelder, neu.spezialfelder)
        assertTrue(neu.vorkommen.isNotEmpty())
        assertNotEquals(basis.vorkommen, neu.vorkommen)
    }
}
