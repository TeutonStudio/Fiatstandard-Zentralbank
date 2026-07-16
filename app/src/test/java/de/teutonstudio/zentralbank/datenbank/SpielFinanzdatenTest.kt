package de.teutonstudio.zentralbank.datenbank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpielFinanzdatenTest {
    private val anna = Spieler("Anna", emptyMap())
    private val bernd = Spieler("Bernd", emptyMap())
    private val clara = Spieler("Clara", emptyMap())

    @Test
    fun auslandHandeltKeineAnleihenUndGeschaeftsbankKeineRohstoffe() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 50.toZahlungsmittel(),
            unvermögen = 5.toZahlungsmittel(),
            laufzeit = 2,
        )

        assertThrows(IllegalArgumentException::class.java) {
            spiel.fuegeHandelZurAktuellenRundeHinzu(
                Anleihenhandel(
                    besitzer = anna,
                    erwerber = Ausland,
                    anleihe = anleihe,
                    preis = 50.toZahlungsmittel(),
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            spiel.fuegeHandelZurAktuellenRundeHinzu(
                Anleihenhandel(
                    besitzer = Ausland,
                    erwerber = bernd,
                    anleihe = anleihe,
                    preis = 50.toZahlungsmittel(),
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            spiel.fuegeHandelZurAktuellenRundeHinzu(
                RohstoffHandel(
                    besitzer = Geschäftsbank,
                    erwerber = bernd,
                    betrag = 10.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.HOLZ,
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            spiel.fuegeHandelZurAktuellenRundeHinzu(
                RohstoffHandel(
                    besitzer = anna,
                    erwerber = Geschäftsbank,
                    betrag = 10.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.HOLZ,
                )
            )
        }
    }

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
        assertFinanzdaten(spiel, 3, 168, 80, 0, 80, 150)

        spiel.fügeLeereRundeHinzu()
        assertFinanzdaten(spiel, 4, 88, 0, 0, 0, 150)

        assertEquals(
            listOf(0, 80, 80, 80, 0),
            spiel.globaleSchulden.map { it.toIntOderNull() },
        )
        assertEquals(
            listOf(0, 12, 6, 0, 0),
            spiel.globaleZinsschulden.map { it.toIntOderNull() },
        )
        assertEquals(
            listOf(0, 92, 86, 80, 0),
            spiel.globaleKombinierteSchulden.map { it.toIntOderNull() },
        )
    }

    @Test
    fun schuldprojektionEndetInDerLetztenFaelligkeitsrundeBeiNull() {
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

        assertEquals(4, spiel.letzteSchuldenProjektionsrunde)
        assertEquals(listOf(0, 1, 2, 3, 4), spiel.schuldenProjektionsrunden)
        assertEquals(
            listOf(0, 92, 86, 80, 0),
            spiel.globaleKombinierteSchuldenMitProjektion.map { it.toIntOderNull() },
        )
        assertEquals(
            listOf(0, 92, 86, 80, 0),
            spiel.spielerKombinierteSchuldenMitProjektion.map { runde ->
                runde.getValue(anna).toIntOderNull()
            },
        )
    }

    @Test
    fun bankschuldenFolgenDemHistorischenUndProjiziertenAnleihebesitzer() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 80.toZahlungsmittel(),
            unvermögen = 6.toZahlungsmittel(),
            laufzeit = 2,
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
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = bernd,
                    erwerber = Geschäftsbank,
                    anleihe = anleihe,
                    preis = 80.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(
            listOf(0, 0, 86, 80, 0),
            spiel.spielerKombinierteBankschuldenMitProjektion.map { runde ->
                runde.getValue(anna).toIntOderNull()
            },
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
        assertEquals(listOf(1, 2, 2, 3, 4), ablauf.map { it.runde })
        assertEquals(Geschäftsbank, ablauf[2].an)
        assertEquals(Geschäftsbank, ablauf[3].an)
        assertEquals(Geschäftsbank, ablauf[4].an)
        assertEquals(
            listOf(anna, bernd, Geschäftsbank, Geschäftsbank, Geschäftsbank),
            ablauf.map { eintrag -> eintrag.zahlungsempfaenger },
        )
        assertEquals(
            listOf(
                "Bernd an Anna",
                "Geschäftsbank an Bernd",
                "Anna an Geschäftsbank",
                "Anna an Geschäftsbank",
                "Anna an Geschäftsbank",
            ),
            ablauf.map { eintrag -> eintrag.buchungssatz },
        )
        assertEquals(anzeige.faelligkeit, ablauf.maxOf { eintrag -> eintrag.runde })
    }

    @Test
    fun berndsFuenfRundenAnleiheWirdInRundeAchtDurchBerndGetilgt() {
        val anleihe = TestSpiel.anleihen.single { eintrag ->
            eintrag.schuldiger.name == "Bernd" && eintrag.sondervermoegen == 60.toZahlungsmittel()
        }
        val ablauf = anleihe.erhalteAblauf()

        assertEquals(8, anleihe.faelligkeit)
        assertEquals((2..8).toList(), ablauf.map { eintrag -> eintrag.runde }.distinct())
        assertFalse(ablauf.any { eintrag -> eintrag.runde == 9 })
        assertEquals(
            listOf("Bernd an David"),
            ablauf.filter { eintrag -> eintrag.runde == 8 }.map { eintrag -> eintrag.buchungssatz },
        )
    }

    @Test
    fun spielerAblaufEnthaeltRohstoffhandelAnleiheKaufVerkaufUndErhalteneZinsen() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
            clara to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 40.toZahlungsmittel(),
            unvermögen = 2.toZahlungsmittel(),
            laufzeit = 2,
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
                ),
                Anleihenhandel(
                    besitzer = anna,
                    erwerber = bernd,
                    anleihe = anleihe,
                    preis = 40.toZahlungsmittel(),
                ),
            ),
            konfliktDaten = emptySet(),
        )
        spiel.beginneNaechsteRunde()
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = bernd,
                    erwerber = clara,
                    anleihe = anleihe,
                    preis = 42.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )

        val ablauf = spiel.erhalteSpielerAblauf(bernd)

        assertEquals(
            listOf(
                SpielerAblaufArt.ROHSTOFFHANDEL,
                SpielerAblaufArt.ANLEIHE_ERWORBEN,
                SpielerAblaufArt.ZINSZAHLUNG,
                SpielerAblaufArt.ANLEIHE_VERKAUFT,
            ),
            ablauf.map { eintrag -> eintrag.art },
        )
        assertEquals(listOf(1, 1, 2, 3), ablauf.map { eintrag -> eintrag.runde })
        assertEquals(listOf(-12, -40, 2, 42), ablauf.map { eintrag -> eintrag.preis.toIntOderNull() })
        assertEquals(3, ablauf.first().anzahl)
        assertEquals("lehm", ablauf.first().rohstoffOderVorgang)
        assertEquals(
            "Zinszahlung (1 von 2)",
            ablauf.first { eintrag -> eintrag.art == SpielerAblaufArt.ZINSZAHLUNG }
                .rohstoffOderVorgang,
        )
        assertEquals(
            10f,
            ablauf.first { eintrag -> eintrag.art == SpielerAblaufArt.ANLEIHE_ERWORBEN }
                .erwarteteAnleihenRenditeProzent ?: Float.NaN,
            0.001f,
        )
        assertEquals(
            listOf(-2),
            spiel.erhalteSpielerAblauf(anna)
                .filter { eintrag -> eintrag.art == SpielerAblaufArt.ZINSZAHLUNG }
                .map { eintrag -> eintrag.preis.toIntOderNull() },
        )
        spiel.aktualisiereAktivenSpieler(bernd.name)
        val emittentenAblauf = spiel.erhalteSpielerAblauf(anna)
        val emission = emittentenAblauf.first { eintrag ->
            eintrag.art == SpielerAblaufArt.ANLEIHE_EMITTIERT
        }
        assertEquals("Anleihe emittiert", emission.rohstoffOderVorgang)
        assertEquals("2 Runden je 2 ℳ", emission.anleihenAnzeigeZusatz)
        assertNull(emission.erwarteteAnleihenRenditeProzent)
        assertEquals(
            listOf(-2, -2),
            emittentenAblauf
                .filter { eintrag -> eintrag.art == SpielerAblaufArt.ZINSZAHLUNG }
                .map { eintrag -> eintrag.preis.toIntOderNull() },
        )
        assertEquals(
            0f,
            ablauf.first { eintrag -> eintrag.art == SpielerAblaufArt.ANLEIHE_VERKAUFT }
                .erwarteteAnleihenRenditeProzent ?: Float.NaN,
            0.001f,
        )
        assertEquals(
            0f,
            spiel.erhalteSpielerAblauf(clara)
                .first { eintrag -> eintrag.art == SpielerAblaufArt.ANLEIHE_ERWORBEN }
                .erwarteteAnleihenRenditeProzent ?: Float.NaN,
            0.001f,
        )
    }

    @Test
    fun anleiheStatusFolgtDemEmittentenzugInDerFaelligkeitsrunde() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
            clara to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = bernd,
            sondervermögen = 40.toZahlungsmittel(),
            unvermögen = 2.toZahlungsmittel(),
            laufzeit = 1,
        )
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            Anleihenhandel(
                besitzer = bernd,
                erwerber = Geschäftsbank,
                anleihe = anleihe,
                preis = 40.toZahlungsmittel(),
            )
        )
        spiel.beginneNaechsteRunde()
        spiel.beginneNaechsteRunde()
        val anzeige = spiel.anleihen.single { eintrag -> eintrag.anleihe == anleihe }

        spiel.aktualisiereAktivenSpieler(anna.name)
        assertEquals(AnleiheStatus.OFFEN, spiel.erhalteAnleiheStatus(anzeige))

        spiel.aktualisiereAktivenSpieler(bernd.name)
        assertEquals(AnleiheStatus.FAELLIG, spiel.erhalteAnleiheStatus(anzeige))

        spiel.aktualisiereAktivenSpieler(clara.name)
        assertEquals(AnleiheStatus.GEZAHLT, spiel.erhalteAnleiheStatus(anzeige))
    }

    @Test
    fun spielerAblaufSortiertVorgaengeNachSpielerreihenfolge() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
            clara to 100.toZahlungsmittel(),
        )
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = clara,
                erwerber = anna,
                rohstoff = Rohstoffe.HOLZ,
                anzahl = 1,
                betrag = 5.toZahlungsmittel(),
            )
        )
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = bernd,
                erwerber = anna,
                rohstoff = Rohstoffe.LEHM,
                anzahl = 1,
                betrag = 4.toZahlungsmittel(),
            )
        )

        val ablauf = spiel.erhalteSpielerAblauf(anna)

        assertEquals(listOf(bernd.name, clara.name), ablauf.map { it.geschaeftspartner })
        assertEquals(listOf(1, 2), ablauf.map { it.zugPosition })
    }

    @Test
    fun davidsAnleihekaufEnthaeltZinsUndRueckkaufInChronologischUmgekehrterAnzeige() {
        val david = TestSpiel.spielerListe.single { spieler -> spieler.name == "David" }
        val ablauf = TestSpiel.erhalteSpielerAblauf(david)
        val rundeSieben = ablauf.filter { eintrag -> eintrag.runde == 7 }
        val anleihekauf = rundeSieben.first { eintrag ->
            eintrag.art == SpielerAblaufArt.ANLEIHE_ERWORBEN &&
                eintrag.preis == (-64).toZahlungsmittel()
        }

        assertEquals(0f, anleihekauf.erwarteteAnleihenRenditeProzent ?: Float.NaN, 0.001f)
        assertTrue(
            rundeSieben.indexOfFirst { eintrag -> eintrag.art == SpielerAblaufArt.ZINSZAHLUNG } <
                rundeSieben.indexOf(anleihekauf)
        )
        assertEquals(
            60,
            ablauf.first { eintrag ->
                eintrag.runde == 8 && eintrag.art == SpielerAblaufArt.RUECKKAUF_ERHALTEN
            }.preis.toIntOderNull(),
        )
        val bernd = TestSpiel.spielerListe.single { spieler -> spieler.name == "Bernd" }
        val ausloesung = TestSpiel.erhalteSpielerAblauf(bernd).first { eintrag ->
            eintrag.runde == 8 && eintrag.art == SpielerAblaufArt.ANLEIHE_AUSGELOEST
        }
        assertEquals("Anleihe ausgelöst (Rückkauf)", ausloesung.rohstoffOderVorgang)
        assertEquals(-60, ausloesung.preis.toIntOderNull())
    }

    @Test
    fun rohstoffHandelsstueckDifferenzWirdJeSpielerKumuliert() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = anna,
                    erwerber = bernd,
                    betrag = 12.toZahlungsmittel(),
                    anzahl = 3,
                    rohstoff = Rohstoffe.HOLZ,
                )
            ),
            konfliktDaten = emptySet(),
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = bernd,
                    erwerber = anna,
                    betrag = 5.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.HOLZ,
                )
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(
            listOf(0, 3, 2),
            spiel.erhalteRohstoffHandelsstueckDifferenz(anna)
                .map { differenz -> differenz.getValue(Rohstoffe.HOLZ) },
        )
        assertEquals(
            listOf(0, -3, -2),
            spiel.erhalteRohstoffHandelsstueckDifferenz(bernd)
                .map { differenz -> differenz.getValue(Rohstoffe.HOLZ) },
        )
    }

    @Test
    fun rohstoffHandelsdifferenzNachStueckUndMarkZeigtAuslandInvertiert() {
        val spiel = neuesSpiel(anna to 100.toZahlungsmittel())
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = anna,
                    erwerber = Ausland,
                    betrag = 12.toZahlungsmittel(),
                    anzahl = 3,
                    rohstoff = Rohstoffe.HOLZ,
                )
            ),
            konfliktDaten = emptySet(),
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                RohstoffHandel(
                    besitzer = Ausland,
                    erwerber = anna,
                    betrag = 5.toZahlungsmittel(),
                    anzahl = 1,
                    rohstoff = Rohstoffe.HOLZ,
                )
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(
            listOf(0, 3, 2),
            spiel.erhalteRohstoffHandelsstueckDifferenz(anna)
                .map { differenz -> differenz.getValue(Rohstoffe.HOLZ) },
        )
        assertEquals(
            listOf(0, -3, -2),
            spiel.erhalteRohstoffHandelsstueckDifferenz(Ausland)
                .map { differenz -> differenz.getValue(Rohstoffe.HOLZ) },
        )
        assertEquals(
            listOf(0, 12, 7),
            spiel.erhalteRohstoffHandelsmarkDifferenz(anna)
                .map { differenz -> differenz.getValue(Rohstoffe.HOLZ) },
        )
        assertEquals(
            listOf(0, -12, -7),
            spiel.erhalteRohstoffHandelsmarkDifferenz(Ausland)
                .map { differenz -> differenz.getValue(Rohstoffe.HOLZ) },
        )
    }

    @Test
    fun bearbeitbarerWarenkorbAendertPreisinflationswarenkorbNicht() {
        val spiel = neuesSpiel(anna to 100.toZahlungsmittel())
        val preisinflationswarenkorb = spiel.preisinflationswarenkorb

        spiel.aktualisiereWarenkorb(mapOf(Rohstoffe.STAHL to 4))

        assertEquals(preisinflationswarenkorb, spiel.preisinflationswarenkorb)
        assertEquals(mapOf(Rohstoffe.STAHL to 4), spiel.warenkorb)
    }

    @Test
    fun zinsgewinneSummierenNurZahlungenAnGeschaeftsbank() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 40.toZahlungsmittel(),
            unvermögen = 2.toZahlungsmittel(),
            laufzeit = 3,
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = anna,
                    erwerber = bernd,
                    anleihe = anleihe,
                    preis = 40.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )
        spiel.beginneNaechsteRunde()
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = bernd,
                    erwerber = Geschäftsbank,
                    anleihe = anleihe,
                    preis = 42.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )

        assertEquals(
            listOf(0, 0, 0, 2),
            spiel.bankZinsgewinne.map { betrag -> betrag.toIntOderNull() },
        )
        assertEquals(
            listOf(0, 0, 0, 2, 4, 4),
            spiel.bankZinsgewinneMitProjektion.map { betrag -> betrag.toIntOderNull() },
        )
    }

    @Test
    fun ausgabenplanNenntZahlungsempfaengerUndRohstoffverbrauchJeGebaeude() {
        val annaMitGebaeuden = Spieler(
            "Anna",
            mapOf(
                Verwaltungsstandort.BAHNHOF to 2,
                Wirtschaftsregionen.ZIEGELBRENNER to 1,
            ),
        )
        val spiel = neuesSpiel(
            annaMitGebaeuden to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = annaMitGebaeuden,
            sondervermögen = 40.toZahlungsmittel(),
            unvermögen = 3.toZahlungsmittel(),
            laufzeit = 2,
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = annaMitGebaeuden,
                    erwerber = bernd,
                    anleihe = anleihe,
                    preis = 40.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )
        spiel.fügeLeereRundeHinzu()

        val plan = spiel.erhalteAusgabenplan(annaMitGebaeuden.name, runde = 2)

        assertEquals(listOf(bernd), plan.zahlungen.map { zahlung -> zahlung.empfaenger })
        assertEquals(listOf(3), plan.zahlungen.map { zahlung -> zahlung.betrag.toIntOderNull() })
        assertEquals(
            setOf(
                Triple(Verwaltungsstandort.BAHNHOF, Rohstoffe.NAHRUNG, 2),
                Triple(Verwaltungsstandort.BAHNHOF, Rohstoffe.KOHLE, 2),
                Triple(Wirtschaftsregionen.ZIEGELBRENNER, Rohstoffe.LEHM, 1),
            ),
            plan.rohstoffVerwendungen.map { verwendung ->
                Triple(verwendung.bauteil, verwendung.rohstoff, verwendung.rohstoffAnzahl)
            }.toSet(),
        )
    }

    @Test
    fun bestehendeAnleiheKannVomBesitzerVerkauftUndVomEmittentenZurueckgekauftWerden() {
        val spiel = neuesSpiel(
            anna to 100.toZahlungsmittel(),
            bernd to 100.toZahlungsmittel(),
            clara to 100.toZahlungsmittel(),
        )
        val anleihe = Anleihe(
            schuldiger = anna,
            sondervermögen = 50.toZahlungsmittel(),
            unvermögen = 2.toZahlungsmittel(),
            laufzeit = 5,
        )
        spiel.neueRundenDatenDefinieren(
            spielerDaten = emptyMap(),
            handelDaten = setOf(
                Anleihenhandel(
                    besitzer = anna,
                    erwerber = bernd,
                    anleihe = anleihe,
                    preis = 50.toZahlungsmittel(),
                )
            ),
            konfliktDaten = emptySet(),
        )
        spiel.beginneNaechsteRunde()

        spiel.fuegeHandelZurAktuellenRundeHinzu(
            Anleihenhandel(
                besitzer = bernd,
                erwerber = clara,
                anleihe = anleihe,
                preis = 52.toZahlungsmittel(),
            )
        )
        assertEquals(clara, spiel.anleihen.single { it.anleihe === anleihe }.aktuellerBesitzer)

        spiel.beginneNaechsteRunde()
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            Anleihenhandel(
                besitzer = clara,
                erwerber = anna,
                anleihe = anleihe,
                preis = 51.toZahlungsmittel(),
            )
        )

        val anzeige = spiel.anleihen.single { it.anleihe === anleihe }
        assertEquals(anna, anzeige.aktuellerBesitzer)
        assertEquals(listOf(1, 2, 3), anzeige.handelsverlauf.keys.sorted())
    }

    @Test
    fun aussenhandelVeraendertSpielerBarvermoegenUndGlobalesBarvermoegenBleibtErhalten() {
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

        assertEquals(170, spiel.spielerBarvermögen[1].toIntOderNull())
        assertEquals(150, spiel.globalesBarvermögen[1].toIntOderNull())
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

        assertEquals(163, spiel.spielerBarvermögen[2].toIntOderNull())
        assertEquals(150, spiel.globalesBarvermögen[2].toIntOderNull())
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

    @Test
    fun hoheWarenkorbpreisinflationErhoehtLeitzinsBeimRundenwechsel() {
        val spiel = inflationsSpielMitReferenzpreis()
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = anna,
                erwerber = bernd,
                betrag = 110.toZahlungsmittel(),
                anzahl = 1,
                rohstoff = Rohstoffe.HOLZ,
            )
        )

        spiel.beginneNaechsteRunde()

        assertEquals(4f, spiel.aktuellerLeitzinssatz, 0.001f)
        assertEquals(4f, spiel.leitzinssatz(2) ?: Float.NaN, 0.001f)
    }

    @Test
    fun fallenderWarenkorbpreisSenktLeitzinsBeimRundenwechsel() {
        val spiel = inflationsSpielMitReferenzpreis()
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = anna,
                erwerber = bernd,
                betrag = 90.toZahlungsmittel(),
                anzahl = 1,
                rohstoff = Rohstoffe.HOLZ,
            )
        )

        spiel.beginneNaechsteRunde()

        assertEquals(0f, spiel.aktuellerLeitzinssatz, 0.001f)
    }

    @Test
    fun warenkorbinflationAmZielLaesstLeitzinsUnveraendert() {
        val spiel = inflationsSpielMitReferenzpreis()
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = anna,
                erwerber = bernd,
                betrag = 102.toZahlungsmittel(),
                anzahl = 1,
                rohstoff = Rohstoffe.HOLZ,
            )
        )

        spiel.beginneNaechsteRunde()

        assertEquals(2f, spiel.aktuellerLeitzinssatz, 0.001f)
    }

    private fun inflationsSpielMitReferenzpreis(): Spiel {
        val spiel = Spiel(
            leitzinssatz = 2f,
            spieler = mapOf(
                anna to 1_000.toZahlungsmittel(),
                bernd to 1_000.toZahlungsmittel(),
            ),
            warenkorb = mapOf(Rohstoffe.HOLZ to 1),
            inflationsziel = 2f,
            normaleAbweichung = 1f,
            starkeAbweichung = 3f,
        )
        spiel.fuegeHandelZurAktuellenRundeHinzu(
            RohstoffHandel(
                besitzer = anna,
                erwerber = bernd,
                betrag = 100.toZahlungsmittel(),
                anzahl = 1,
                rohstoff = Rohstoffe.HOLZ,
            )
        )
        spiel.beginneNaechsteRunde()
        assertEquals(2f, spiel.aktuellerLeitzinssatz, 0.001f)
        return spiel
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
        assertEquals(globalesBarvermögen, spiel.spielerBarvermögen[runde].toIntOderNull())
        assertEquals(globalesBarvermögen, spiel.globalesBarvermögen[runde].toIntOderNull())
    }
}
