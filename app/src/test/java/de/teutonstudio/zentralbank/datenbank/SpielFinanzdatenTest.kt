package de.teutonstudio.zentralbank.datenbank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun marktwertNutztBauwerksbestandUndRohstoffpreiseDerVorherigenRunde() {
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

        assertEquals(0, spiel.spielerMarktwert[1].getValue(annaMitBahn).toIntOderNull())
        assertEquals(
            0,
            spiel.bauwerkMarktpreise[1].getValue(Handelslinie.LAND).toIntOderNull(),
        )

        spiel.fügeLeereRundeHinzu()

        assertEquals(0, spiel.spielerMarktwert[2].getValue(annaMitBahn).toIntOderNull())
        assertEquals(
            0,
            spiel.bauwerkMarktpreise[2].getValue(Handelslinie.LAND).toIntOderNull(),
        )

        spiel.fügeLeereRundeHinzu()

        assertEquals(30, spiel.spielerMarktwert[3].getValue(annaMitBahn).toIntOderNull())
        assertEquals(
            30,
            spiel.bauwerkMarktpreise[3].getValue(Handelslinie.LAND).toIntOderNull(),
        )
    }

    @Test
    fun nachtraeglichErfassterHandelWirdSofortBezahltAberErstFolgerundeZumMarktpreis() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 50.toZahlungsmittel(),
        )
        spiel.fügeLeereRundeHinzu()

        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = anna,
                erwerber = bernd,
                betrag = 20.toZahlungsmittel(),
                anzahl = 2,
                rohstoff = Rohstoffe.HOLZ,
            )
        )

        assertEquals(120, spiel.spielerSaldo[1].getValue(anna).toIntOderNull())
        assertEquals(30, spiel.spielerSaldo[1].getValue(bernd).toIntOderNull())
        assertEquals(0, spiel.marktpreise[1].getValue(Rohstoffe.HOLZ).toIntOderNull())

        spiel.fügeLeereRundeHinzu()

        assertEquals(10, spiel.marktpreise[2].getValue(Rohstoffe.HOLZ).toIntOderNull())
    }

    @Test
    fun spielerHandelsverlaufEnthaeltRohstoffUndAnleihenhandelChronologisch() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 50.toZahlungsmittel(),
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = anna,
                    erwerber = bernd,
                    betrag = 12.toZahlungsmittel(),
                    anzahl = 3,
                    rohstoff = Rohstoffe.LEHM,
                )
            ),
            konfliktDaten = emptySet(),
        )
        val anleihe = Anleihe(
            schuldiger = bernd,
            sondervermögen = 40.toZahlungsmittel(),
            unvermögen = 2.toZahlungsmittel(),
            laufzeit = 2,
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = bernd,
                    erwerber = anna,
                    anleihe = anleihe,
                    preis = 38.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )

        val verlauf = spiel.erhalteHandelsverlauf(anna)

        assertEquals(listOf(1, 2), verlauf.map { it.runde })
        assertTrue(verlauf[0].handel is RohstoffHandel)
        assertTrue(verlauf[0].istEinnahme)
        assertEquals(12, verlauf[0].saldo.toIntOderNull())
        assertTrue(verlauf[1].handel is Anleihenhandel)
        assertTrue(verlauf[1].istAusgabe)
        assertEquals(-38, verlauf[1].saldo.toIntOderNull())
        assertFalse(verlauf.any { it.saldo == Zahlungsmittel() })
    }

    @Test
    fun anleiheAblaufOrdnetHandelZinsUndRueckkaufDenJeweiligenBesitzernZu() {
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 80.toZahlungsmittel(),
            unvermögen = 6.toZahlungsmittel(),
            laufzeit = 2,
        )
        val emission = Anleihenhandel(
            besitzer = anna,
            erwerber = bernd,
            anleihe = anleihe,
            preis = 75.toZahlungsmittel(),
        )
        val weiterverkauf = Anleihenhandel(
            besitzer = bernd,
            erwerber = Geschäftsbank,
            anleihe = anleihe,
            preis = 77.toZahlungsmittel(),
        )
        val anzeige = AnleiheAnzeige(
            emittiert = 1,
            emittent = anna,
            aktuellerBesitzer = Geschäftsbank,
            anleihe = anleihe,
            handelsverlauf = mapOf(1 to emission, 2 to weiterverkauf),
        )

        val ablauf = anzeige.erhalteAblauf()

        assertEquals(
            listOf(
                AnleiheAblaufArt.EMISSION,
                AnleiheAblaufArt.HANDEL,
                AnleiheAblaufArt.ZINS,
                AnleiheAblaufArt.ZINS,
                AnleiheAblaufArt.RUECKKAUF,
            ),
            ablauf.map { it.art },
        )
        assertEquals(listOf(1, 2, 2, 3, 3), ablauf.map { it.runde })
        assertEquals(Geschäftsbank, ablauf[2].an)
        assertEquals(Geschäftsbank, ablauf[3].an)
        assertEquals(Geschäftsbank, ablauf[4].an)
    }

    @Test
    fun aussenhandelVeraendertM2UndWirdNachRohstoffBilanziert() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 50.toZahlungsmittel(),
        )

        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = anna,
                    erwerber = Ausland,
                    betrag = 20.toZahlungsmittel(),
                    anzahl = 2,
                    rohstoff = Rohstoffe.HOLZ,
                )
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(170, spiel.globalesBarvermögen[1].toIntOderNull())
        assertEquals(
            20,
            spiel.aussenhandelsbilanzNachRohstoff[1]
                .getValue(Rohstoffe.HOLZ)
                .toIntOderNull(),
        )
        assertEquals(20, spiel.aussenhandelsbilanzGesamt[1].toIntOderNull())
        assertEquals(
            2,
            spiel.aussenhandelsbilanzStueckNachRohstoff[1]
                .getValue(Rohstoffe.HOLZ),
        )
        assertEquals(2, spiel.aussenhandelsbilanzStueckGesamt[1])

        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = Ausland,
                    erwerber = bernd,
                    betrag = 7.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.STAHL,
                )
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(163, spiel.globalesBarvermögen[2].toIntOderNull())
        assertEquals(
            20,
            spiel.aussenhandelsbilanzNachRohstoff[2]
                .getValue(Rohstoffe.HOLZ)
                .toIntOderNull(),
        )
        assertEquals(
            -7,
            spiel.aussenhandelsbilanzNachRohstoff[2]
                .getValue(Rohstoffe.STAHL)
                .toIntOderNull(),
        )
        assertEquals(13, spiel.aussenhandelsbilanzGesamt[2].toIntOderNull())
        assertEquals(
            2,
            spiel.aussenhandelsbilanzStueckNachRohstoff[2]
                .getValue(Rohstoffe.HOLZ),
        )
        assertEquals(
            -1,
            spiel.aussenhandelsbilanzStueckNachRohstoff[2]
                .getValue(Rohstoffe.STAHL),
        )
        assertEquals(1, spiel.aussenhandelsbilanzStueckGesamt[2])
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
