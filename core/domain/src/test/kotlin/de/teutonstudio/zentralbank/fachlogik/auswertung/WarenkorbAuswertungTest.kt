package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.DreieckHaelfte
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.Spezialfeld
import de.teutonstudio.zentralbank.fachlogik.modell.SpezialfeldTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WarenkorbAuswertungTest {
    @Test
    fun unverarbeiteteRohstoffeWerdenNachMoeglichenFeldernGewichtet() {
        val karte = Spielkarte(
            id = "warenkorb-gelaende",
            name = "Warenkorb Gelände",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = listOf(
                GelaendeFeld(KartenFeld(2, 2, DreieckHaelfte.UNTEN), GelaendeTyp.EBENE),
                GelaendeFeld(KartenFeld(2, 3, DreieckHaelfte.OBEN), GelaendeTyp.WALD),
                GelaendeFeld(KartenFeld(3, 2, DreieckHaelfte.OBEN), GelaendeTyp.GEBIRGE),
                GelaendeFeld(KartenFeld(3, 3, DreieckHaelfte.UNTEN), GelaendeTyp.WUESTE),
            ),
        )

        val warenkorb = WarenkorbAuswertung.vordefinierterWarenkorb(
            karte,
            WarenkorbPreset.UNVERARBEITETE_ROHSTOFFE,
        )

        assertEquals(1, warenkorb[Rohstoff.NAHRUNG])
        assertEquals(1, warenkorb[Rohstoff.HOLZ])
        assertEquals(4, warenkorb[Rohstoff.LEHM])
        assertEquals(4, warenkorb[Rohstoff.ROHOEL])
        assertEquals(4, warenkorb[Rohstoff.KOHLE])
        assertEquals(4, warenkorb[Rohstoff.EISEN])
        assertFalse(Rohstoff.ZIEGEL in warenkorb)
        assertFalse(Rohstoff.STAHL in warenkorb)
    }

    @Test
    fun verarbeiteteRohstoffeNutzenDasGekuerzteFeldverhaeltnis() {
        val karte = Spielkarte(
            id = "warenkorb-verarbeitung",
            name = "Warenkorb Verarbeitung",
            hexagon = KartenHexagon(radius = 8),
            gelaendefelder = listOf(
                GelaendeFeld(KartenFeld(2, 2, DreieckHaelfte.UNTEN), GelaendeTyp.EBENE),
                GelaendeFeld(KartenFeld(2, 3, DreieckHaelfte.OBEN), GelaendeTyp.WALD),
                GelaendeFeld(KartenFeld(3, 2, DreieckHaelfte.OBEN), GelaendeTyp.GEBIRGE),
                GelaendeFeld(KartenFeld(3, 3, DreieckHaelfte.UNTEN), GelaendeTyp.WUESTE),
            ),
        )

        val warenkorb = WarenkorbAuswertung.vordefinierterWarenkorb(
            karte,
            WarenkorbPreset.VERARBEITETE_ROHSTOFFE,
        )

        assertEquals(
            mapOf(
                Rohstoff.ZIEGEL to 1,
                Rohstoff.SCHWEROEL to 1,
                Rohstoff.DIESEL to 1,
                Rohstoff.STAHL to 1,
            ),
            warenkorb,
        )
    }

    @Test
    fun teichfelderZaehlenFuerNahrungNurEinmalUndNullgewichteEntfallen() {
        val mitte = KartenEcke(0, 0)
        val teichFelder = angrenzendeFelder(mitte)
        val karte = Spielkarte(
            id = "warenkorb-teich",
            name = "Warenkorb Teich",
            hexagon = KartenHexagon(radius = 2),
            gelaendefelder = teichFelder.map { feld -> GelaendeFeld(feld, GelaendeTyp.EBENE) },
            spezialfelder = listOf(Spezialfeld(SpezialfeldTyp.TEICH, mitte)),
        )

        val warenkorb = WarenkorbAuswertung.vordefinierterWarenkorb(
            karte,
            WarenkorbPreset.UNVERARBEITETE_ROHSTOFFE,
        )

        assertEquals(mapOf(Rohstoff.NAHRUNG to 1), warenkorb)
    }
}
