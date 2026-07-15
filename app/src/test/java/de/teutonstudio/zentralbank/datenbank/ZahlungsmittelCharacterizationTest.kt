package de.teutonstudio.zentralbank.datenbank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZahlungsmittelCharacterizationTest {
    @Test
    fun addiertSubtrahiertUndMultipliziertGanzeMarkBetraege() {
        val hundert = 100.toZahlungsmittel()
        val fuenfzig = 50.toZahlungsmittel()

        assertEquals(150, (hundert + fuenfzig).toIntOderNull())
        assertEquals(50, (hundert - fuenfzig).toIntOderNull())
        assertEquals(300, (hundert * 3).toIntOderNull())
        assertEquals(-50, (fuenfzig - hundert).toIntOderNull())
    }

    @Test
    fun speicherStringIstRundlaufFaehig() {
        val betrag = 12345.toZahlungsmittel() - 678.toZahlungsmittel()

        assertEquals(betrag, betrag.speichereString().toZahlungsmittel())
    }

    @Test
    fun handelVerschiebtLiquiditaetSummenneutral() {
        val anna = Spieler("Anna", emptyMap<Bauteil, Int>())
        val bernd = Spieler("Bernd", emptyMap<Bauteil, Int>())
        val start = mapOf(
            anna as JuristischePerson to 100.toZahlungsmittel(),
            bernd as JuristischePerson to 20.toZahlungsmittel(),
        )
        val handel = RohstoffHandel(
            besitzer = anna,
            erwerber = bernd,
            betrag = 15.toZahlungsmittel(),
            anzahl = 3,
            rohstoff = Rohstoffe.HOLZ,
        )

        val ergebnis = start.handelt(handel)

        assertEquals(115, ergebnis.getValue(anna).toIntOderNull())
        assertEquals(5, ergebnis.getValue(bernd).toIntOderNull())
        assertEquals(
            start.values.summeGeld { it },
            ergebnis.values.summeGeld { it },
        )
    }

    @Test
    fun testSpielHaeltBekannteBasisdaten() {
        assertEquals(17, TestSpiel.aktuelleRunde)
        assertEquals(
            listOf("Anna", "Bernd", "Clara", "David", "Eva", "Franz", "Georg"),
            TestSpiel.spielerStringListe,
        )
        assertTrue(TestSpiel.marktpreise.isNotEmpty())
        assertTrue(TestSpiel.spielerBarvermögen.distinct().size > 1)
        val anna = TestSpiel.spielerListe.single { spieler -> spieler.name == "Anna" }
        assertTrue(
            TestSpiel.spielerSaldo.all { salden ->
                salden.getValue(anna) >= Zahlungsmittel()
            }
        )
        assertTrue(
            TestSpiel.aussenhandelsbilanzGesamt.any { bilanz ->
                bilanz != Zahlungsmittel()
            }
        )
    }

    @Test
    fun rohstoffeUndBauwerkeHabenEindeutigeFarben() {
        assertEquals(
            Rohstoffe.entries.size,
            Rohstoffe.entries.map { rohstoff -> rohstoff.farbe }.toSet().size,
        )
        assertEquals(
            Bauteil.entries.count(),
            Bauteil.entries.map { bauteil -> bauteil.farbe }.toSet().size,
        )
    }

    @Test
    fun testSpielEnthaeltInDerAktuellenRundeAlleAnleihenZustaende() {
        val aktuelleRunde = TestSpiel.aktuelleRunde - 1

        assertTrue(TestSpiel.anleihen.any { anleihe -> anleihe.faelligkeit < aktuelleRunde })
        assertTrue(TestSpiel.anleihen.any { anleihe -> anleihe.faelligkeit == aktuelleRunde })
        assertTrue(TestSpiel.anleihen.any { anleihe -> anleihe.faelligkeit > aktuelleRunde })
    }
}
