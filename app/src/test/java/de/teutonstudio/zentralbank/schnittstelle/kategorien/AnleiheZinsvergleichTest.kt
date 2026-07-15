package de.teutonstudio.zentralbank.schnittstelle.kategorien

import de.teutonstudio.zentralbank.datenbank.Anleihe
import de.teutonstudio.zentralbank.datenbank.Spieler
import de.teutonstudio.zentralbank.datenbank.toZahlungsmittel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnleiheZinsvergleichTest {
    private val spieler = Spieler("Anna", emptyMap())

    @Test
    fun berechnetRelativeAbweichungDesAnleihenzinsesZumLeitzins() {
        val vergleich = berechneAnleiheZinsvergleich(
            anleihe = Anleihe(
                schuldiger = spieler,
                sondervermögen = 80.toZahlungsmittel(),
                unvermögen = 6.toZahlungsmittel(),
                laufzeit = 2,
            ),
            leitzins = 2f,
        )

        assertEquals(2.0, vergleich.leitzins, 0.001)
        assertEquals(7.5, vergleich.anleihenzins, 0.001)
        assertEquals(275.0, vergleich.relativeAbweichung ?: Double.NaN, 0.001)
        assertEquals("7,5 % bei 2,0 %: +275,0 %", formatiereAnleiheZinsvergleich(vergleich))
    }

    @Test
    fun beiLeitzinsNullIstDieRelativeAbweichungNichtDefiniert() {
        val vergleich = berechneAnleiheZinsvergleich(
            anleihe = Anleihe(
                schuldiger = spieler,
                sondervermögen = 100.toZahlungsmittel(),
                unvermögen = 5.toZahlungsmittel(),
                laufzeit = 2,
            ),
            leitzins = 0f,
        )

        assertNull(vergleich.relativeAbweichung)
    }

    @Test
    fun niedrigererAnleihenzinsErzeugtEineNegativeAbweichung() {
        val vergleich = berechneAnleiheZinsvergleich(
            anleihe = Anleihe(
                schuldiger = spieler,
                sondervermögen = 100.toZahlungsmittel(),
                unvermögen = 1.toZahlungsmittel(),
                laufzeit = 2,
            ),
            leitzins = 2f,
        )

        assertEquals(-50.0, vergleich.relativeAbweichung ?: Double.NaN, 0.001)
    }
}
