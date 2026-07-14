package de.teutonstudio.zentralbank.datenbank

import org.junit.Assert.assertEquals
import org.junit.Test

class SpielFinanzdatenTest {
    private val anna = Spieler("Anna", emptyMap())
    private val bernd = Spieler("Bernd", emptyMap())

    @Test
    fun barvermoegenUndSchuldenFolgenDemZahlungsplanNachRunde() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 50.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 80.toZahlungsmittel(),
            unvermögen = 6.toZahlungsmittel(),
            laufzeit = 2,
        )

        assertFinanzdaten(
            spiel = spiel,
            runde = 0,
            barvermögen = 100,
            schulden = 0,
            zinsschulden = 0,
            kombinierteSchulden = 0,
            globalesBarvermögen = 150,
        )

        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = anna,
                    erwerber = bernd,
                    anleihe = anleihe,
                    preis = 80.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )
        assertFinanzdaten(spiel, 1, 180, 80, 12, 92, 150)

        spiel.fügeLeereRundeHinzu()
        assertFinanzdaten(spiel, 2, 174, 80, 6, 86, 150)

        spiel.fügeLeereRundeHinzu()
        assertFinanzdaten(spiel, 3, 88, 0, 0, 0, 150)

        assertEquals(
            listOf(0, 80, 80, 0),
            spiel.globaleSchulden.map { it.toIntOderNull() },
        )
        assertEquals(
            listOf(0, 12, 6, 0),
            spiel.globaleZinsschulden.map { it.toIntOderNull() },
        )
        assertEquals(
            listOf(0, 92, 86, 0),
            spiel.globaleKombinierteSchulden.map { it.toIntOderNull() },
        )
    }

    @Test
    fun marktwertNutztBauwerksbestandUndMarktpreiseDerselbenRunde() {
        val annaMitBahn = Spieler("Anna", mapOf(Handelslinie.LAND to 1))
        val spiel = neuesSpiel(
            annaMitBahn to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
        )

        assertEquals(0, spiel.spielerMarktwert[0].getValue(annaMitBahn).toIntOderNull())

        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = bernd,
                    erwerber = annaMitBahn,
                    betrag = 10.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.HOLZ,
                ),
                RohstoffHandel(
                    besitzer = bernd,
                    erwerber = annaMitBahn,
                    betrag = 20.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.STAHL,
                ),
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(30, spiel.spielerMarktwert[1].getValue(annaMitBahn).toIntOderNull())
    }

    private fun neuesSpiel(
        vararg spieler: Pair<Spieler, Zahlungsmittel>,
    ): Spiel = Spiel(
        leitzinssatz = 2f,
        spieler = spieler.toMap(),
        warenkorb = emptyMap(),
        inflationsziel = 2f,
        normaleAbweichung = 1f,
        starkeAbweichung = 3f,
    )

    private fun Spiel.fügeLeereRundeHinzu() {
        neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = emptySet(),
            konfliktDaten = emptySet(),
        )
    }

    private fun assertFinanzdaten(
        spiel: Spiel,
        runde: Int,
        barvermögen: Int,
        schulden: Int,
        zinsschulden: Int,
        kombinierteSchulden: Int,
        globalesBarvermögen: Int,
    ) {
        assertEquals(barvermögen, spiel.spielerSaldo[runde].getValue(anna).toIntOderNull())
        assertEquals(schulden, spiel.spielerSchulden[runde].getValue(anna).toIntOderNull())
        assertEquals(
            zinsschulden,
            spiel.spielerZinsschulden[runde].getValue(anna).toIntOderNull(),
        )
        assertEquals(
            kombinierteSchulden,
            spiel.spielerKombinierteSchulden[runde].getValue(anna).toIntOderNull(),
        )
        assertEquals(globalesBarvermögen, spiel.globalesBarvermögen[runde].toIntOderNull())
    }
}
