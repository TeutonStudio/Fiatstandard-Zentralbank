package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RohstoffVorkommenTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun oelDarfAufWuesteLiegenUndWirdSerialisiert() {
        val feld = KartenFeld(0, 0, DreieckHaelfte.UNTEN)
        val karte = KartenVorlage(
            id = "oelwueste",
            name = "Ölwüste",
            hexagon = KartenHexagon(radius = 2),
            gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.WUESTE)),
            vorkommen = listOf(RohstoffVorkommen(feld, VorkommensArt.ROHOEL)),
        )

        val geladen = json.decodeFromString<KartenVorlage>(json.encodeToString(karte))

        assertEquals(VorkommensArt.ROHOEL, geladen.vorkommenAn(feld))
        assertEquals(4, geladen.formatVersion)
    }

    @Test
    fun erzAusserhalbDesGebirgesWirdAbgelehnt() {
        val feld = KartenFeld(0, 0, DreieckHaelfte.UNTEN)

        val fehler = assertThrows(IllegalArgumentException::class.java) {
            KartenVorlage(
                id = "falsches-erz",
                name = "Falsches Erz",
                hexagon = KartenHexagon(radius = 2),
                gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.EBENE)),
                vorkommen = listOf(RohstoffVorkommen(feld, VorkommensArt.EISENERZ)),
            )
        }

        assertTrue(fehler.message.orEmpty().contains("nur im Gebirge"))
    }

    @Test
    fun mehrereVorkommenAufEinemFeldWerdenAbgelehnt() {
        val feld = KartenFeld(0, 0, DreieckHaelfte.UNTEN)

        assertThrows(IllegalArgumentException::class.java) {
            KartenVorlage(
                id = "doppelt",
                name = "Doppeltes Vorkommen",
                hexagon = KartenHexagon(radius = 2),
                gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.GEBIRGE)),
                vorkommen = listOf(
                    RohstoffVorkommen(feld, VorkommensArt.EISENERZ),
                    RohstoffVorkommen(feld, VorkommensArt.KOHLE),
                ),
            )
        }
    }

    @Test
    fun eisenmineBrauchtGebirgeUndEisenerz() {
        val feld = KartenFeld(0, 0, DreieckHaelfte.UNTEN)
        val basis = Spielkarte(
            id = "mine",
            name = "Mine",
            hexagon = KartenHexagon(radius = 2),
            gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.GEBIRGE)),
            vorkommen = listOf(RohstoffVorkommen(feld, VorkommensArt.EISENERZ)),
        )

        val belegt = basis.copy(
            belegung = KartenBelegung(
                felder = listOf(
                    FeldBelegung(feld, FeldAnlage.Wirtschaftsregion(BauteilTyp.EISENMINE)),
                ),
            ),
        )

        assertEquals(BauteilTyp.EISENMINE, (belegt.belegung.felder.single().anlage as FeldAnlage.Wirtschaftsregion).bauteil)

        assertThrows(IllegalArgumentException::class.java) {
            basis.copy(
                vorkommen = listOf(RohstoffVorkommen(feld, VorkommensArt.KOHLE)),
                belegung = KartenBelegung(
                    felder = listOf(
                        FeldBelegung(feld, FeldAnlage.Wirtschaftsregion(BauteilTyp.EISENMINE)),
                    ),
                ),
            )
        }
    }

    @Test
    fun bohrturmBrauchtOelAberKeinBestimmtesGelaende() {
        val feld = KartenFeld(0, 0, DreieckHaelfte.UNTEN)
        val karte = Spielkarte(
            id = "bohrturm",
            name = "Bohrturm",
            hexagon = KartenHexagon(radius = 2),
            gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.SUMPF)),
            vorkommen = listOf(RohstoffVorkommen(feld, VorkommensArt.ROHOEL)),
            belegung = KartenBelegung(
                felder = listOf(
                    FeldBelegung(feld, FeldAnlage.Wirtschaftsregion(BauteilTyp.BOHRTURM)),
                ),
            ),
        )

        assertEquals(VorkommensArt.ROHOEL, karte.vorkommenAn(feld))
    }

    @Test
    fun legacyV3BleibtLesbarUndHatKeineNeueGeologiePflicht() {
        val feld = KartenFeld(0, 0, DreieckHaelfte.UNTEN)
        val karte = Spielkarte(
            formatVersion = 3,
            id = "legacy",
            name = "Legacy",
            hexagon = KartenHexagon(radius = 2),
            gelaendefelder = listOf(GelaendeFeld(feld, GelaendeTyp.GEBIRGE)),
            belegung = KartenBelegung(
                felder = listOf(
                    FeldBelegung(feld, FeldAnlage.Wirtschaftsregion(BauteilTyp.EISENMINE)),
                ),
            ),
        )

        assertEquals(3, karte.formatVersion)
        assertTrue(karte.vorkommen.isEmpty())
    }
}
