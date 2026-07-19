package de.teutonstudio.zentralbank.datenbank

object TestSpiel : Spiel(
    leitzinssatz = 2.0f,
    spieler = testSpieler.associateWith { 100.toZahlungsmittel() },
    warenkorb = startWarenkorb,
    inflationsziel = 2.0f,
    normaleAbweichung = 1.0f,
    starkeAbweichung = 3.0f,
    karte = testSpielKarte,
) {
    init {
        neueRundenDatenDefinieren( // Runde 1
            spielerDaten = runde(
                änderung(anna, bau(Handelslinie.LAND to 1)),
                änderung(clara, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(anna, bernd, 8, 2, Rohstoffe.HOLZ),
                rohstoff(eva, anna, 9, 3, Rohstoffe.NAHRUNG),
                rohstoff(clara, david, 4, 1, Rohstoffe.LEHM),
                rohstoff(Ausland, anna, 12, 2, Rohstoffe.HOLZ),
            ),
            konfliktDaten = emptySet(),
        )

        neueRundenDatenDefinieren( // Runde 2
            spielerDaten = runde(
                änderung(bernd, bau(Handelslinie.LAND to 2)),
                änderung(eva, bau(Handelslinie.LAND to 1)),
                änderung(anna, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(bernd, clara, 7, 1, Rohstoffe.KOHLE),
                rohstoff(david, eva, 6, 2, Rohstoffe.ZIEGEL),
                rohstoff(bernd, Ausland, 10, 1, Rohstoffe.KOHLE),
                Anleihenhandel(bernd, clara, anleiheBernd1, 60.toZahlungsmittel()),
            ),
            konfliktDaten = emptySet(),
        )

        neueRundenDatenDefinieren( // Runde 3
            spielerDaten = runde(
                änderung(clara, bau(Handelslinie.LAND to 1)),
                änderung(david, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(clara, anna, 12, 1, Rohstoffe.STAHL),
                rohstoff(david, bernd, 15, 1, Rohstoffe.ROHÖL),
                rohstoff(eva, clara, 10, 2, Rohstoffe.NAHRUNG),
            ),
            konfliktDaten = setOf(
                vertrag(anna, david, Vertragsart.KRIEGSERKLÄRUNG),
            ),
        )

        neueRundenDatenDefinieren( // Runde 4
            spielerDaten = runde(
                änderung(david, bau(Handelslinie.LAND to 1)),
                änderung(anna, bau(Handelslinie.LAND to -1)),
                änderung(david, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(anna, eva, 9, 2, Rohstoffe.HOLZ),
                rohstoff(bernd, david, 13, 1, Rohstoffe.DIESEL),
                rohstoff(Ausland, eva, 15, 3, Rohstoffe.NAHRUNG),
                Anleihenhandel(anna, eva, anleiheAnna1, 80.toZahlungsmittel()),
            ),
            konfliktDaten = emptySet(),
        )

        neueRundenDatenDefinieren( // Runde 5
            spielerDaten = runde(
                änderung(eva, bau(Handelslinie.LAND to 2)),
                änderung(clara, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(eva, bernd, 5, 1, Rohstoffe.LEHM),
                rohstoff(clara, david, 14, 1, Rohstoffe.STAHL),
                rohstoff(david, anna, 18, 1, Rohstoffe.ROHÖL),
            ),
            konfliktDaten = setOf(
                vertrag(david, anna, Vertragsart.WAFFENSTILLSTAND),
            ),
        )

        neueRundenDatenDefinieren( // Runde 6
            spielerDaten = runde(
                änderung(anna, bau(Handelslinie.LAND to 1)),
                änderung(bernd, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(anna, clara, 6, 2, Rohstoffe.ZIEGEL),
                rohstoff(bernd, eva, 11, 2, Rohstoffe.KOHLE),
                rohstoff(clara, Ausland, 18, 2, Rohstoffe.ZIEGEL),
                Anleihenhandel(clara, bernd, anleiheClara1, 90.toZahlungsmittel()),
            ),
            konfliktDaten = emptySet(),
        )

        neueRundenDatenDefinieren( // Runde 7
            spielerDaten = runde(
                änderung(clara, bau(Handelslinie.LAND to 2)),
                änderung(eva, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(clara, eva, 16, 2, Rohstoffe.STAHL),
                rohstoff(david, bernd, 10, 1, Rohstoffe.DIESEL),
                rohstoff(bernd, anna, 7, 1, Rohstoffe.KOHLE),
                Anleihenhandel(clara, david, anleiheBernd1, 64.toZahlungsmittel()),
                Anleihenhandel(eva, clara, anleiheAnna1, 83.toZahlungsmittel()),
            ),
            konfliktDaten = emptySet(),
        )

        neueRundenDatenDefinieren( // Runde 8
            spielerDaten = runde(
                änderung(david, bau(Handelslinie.LAND to 1)),
                änderung(anna, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to -1)),
            ),
            handelDaten = setOf(
                rohstoff(eva, david, 8, 2, Rohstoffe.NAHRUNG),
                rohstoff(anna, bernd, 10, 2, Rohstoffe.HOLZ),
                rohstoff(Ausland, david, 14, 1, Rohstoffe.DIESEL),
                Anleihenhandel(david, anna, anleiheDavid1, 70.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(anna, david, Vertragsart.FRIEDENSERKLÄRUNG),
            ),
        )

        neueRundenDatenDefinieren( // Runde 9
            spielerDaten = runde(
                änderung(bernd, bau(Handelslinie.LAND to -1)),
                änderung(eva, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(bernd, clara, 9, 3, Rohstoffe.LEHM),
                rohstoff(clara, anna, 18, 1, Rohstoffe.SCHWERÖL),
                Anleihenhandel(anna, bernd, anleiheDavid1, 74.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(bernd, eva, Vertragsart.KRIEGSERKLÄRUNG),
            ),
        )

        neueRundenDatenDefinieren( // Runde 10
            spielerDaten = runde(
                änderung(david, bau(Handelslinie.LAND to 2)),
                änderung(clara, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(david, eva, 20, 1, Rohstoffe.ROHÖL),
                rohstoff(eva, clara, 12, 3, Rohstoffe.NAHRUNG),
                rohstoff(anna, david, 8, 1, Rohstoffe.EISEN),
                rohstoff(anna, Ausland, 16, 2, Rohstoffe.EISEN),
            ),
            konfliktDaten = emptySet(),
        )

        neueRundenDatenDefinieren( // Runde 11
            spielerDaten = runde(
                änderung(anna, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
                änderung(eva, bau(Handelslinie.LAND to -1)),
            ),
            handelDaten = setOf(
                rohstoff(anna, clara, 15, 1, Rohstoffe.STAHL),
                rohstoff(bernd, david, 9, 1, Rohstoffe.KOHLE),
                Anleihenhandel(eva, david, anleiheEva1, 100.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(eva, bernd, Vertragsart.WAFFENSTILLSTAND),
            ),
        )

        neueRundenDatenDefinieren( // Runde 12
            spielerDaten = runde(
                änderung(bernd, bau(Handelslinie.LAND to 1)),
                änderung(eva, bau(Handelslinie.LAND to 1)),
            ),
            handelDaten = setOf(
                rohstoff(clara, bernd, 11, 2, Rohstoffe.ZIEGEL),
                rohstoff(david, anna, 17, 1, Rohstoffe.DIESEL),
                Anleihenhandel(franz, georg, anleiheFranzFaellig, 50.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(bernd, eva, Vertragsart.FRIEDENSERKLÄRUNG),
            ),
        )

        neueRundenDatenDefinieren( // Runde 13
            spielerDaten = runde(
                änderung(clara, bau(Handelslinie.LAND to 1)),
                änderung(anna, bau(Handelslinie.LAND to -1)),
                änderung(clara, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(anna, eva, 12, 3, Rohstoffe.HOLZ),
                rohstoff(bernd, clara, 14, 1, Rohstoffe.EISEN),
                rohstoff(eva, david, 10, 2, Rohstoffe.NAHRUNG),
                rohstoff(Ausland, clara, 20, 2, Rohstoffe.ROHÖL),
                Anleihenhandel(bernd, eva, anleiheDavid1, 74.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(clara, anna, Vertragsart.KRIEGSERKLÄRUNG),
            ),
        )

        neueRundenDatenDefinieren( // Runde 14
            spielerDaten = runde(
                änderung(eva, bau(Handelslinie.LAND to 1)),
                änderung(david, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(clara, david, 22, 1, Rohstoffe.ROHÖL),
                rohstoff(david, bernd, 13, 1, Rohstoffe.DIESEL),
                Anleihenhandel(georg, bernd, anleiheGeorgOffen, 65.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(anna, clara, Vertragsart.WAFFENSTILLSTAND),
            ),
        )

        neueRundenDatenDefinieren( // Runde 15
            spielerDaten = runde(
                änderung(anna, bau(Handelslinie.LAND to 2)),
                änderung(bernd, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to -1)),
            ),
            handelDaten = setOf(
                rohstoff(eva, anna, 9, 3, Rohstoffe.NAHRUNG),
                rohstoff(bernd, clara, 15, 2, Rohstoffe.KOHLE),
                rohstoff(david, eva, 16, 2, Rohstoffe.EISEN),
                rohstoff(eva, Ausland, 24, 3, Rohstoffe.NAHRUNG),
                Anleihenhandel(david, bernd, anleiheEva1, 104.toZahlungsmittel()),
            ),
            konfliktDaten = setOf(
                vertrag(clara, anna, Vertragsart.FRIEDENSERKLÄRUNG),
            ),
        )

        neueRundenDatenDefinieren( // Runde 16
            spielerDaten = runde(
                änderung(david, bau(Handelslinie.LAND to 1)),
                änderung(eva, bau(Handelslinie.LAND to 1)),
                änderung(anna, kontrolle = kontroll(Wirtschaftsregionen.GESCHÄFTSBANK to 1)),
            ),
            handelDaten = setOf(
                rohstoff(anna, david, 18, 2, Rohstoffe.STAHL),
                rohstoff(clara, eva, 20, 1, Rohstoffe.SCHWERÖL),
            ),
            konfliktDaten = emptySet(),
        )
    }
}

private val startWarenkorb = Rohstoffe.anzahl(
    nahrung = 3,
    lehm = 2,
    ziegel = 2,
    holz = 3,
    rohöl = 1,
    diesel = 1,
    kohle = 2,
    stahl = 2,
    eisen = 2,
)

private val startBau: Map<Bauteil, Int> = mapOf(
    Handelslinie.LAND to 6,
)

private val startKontrolle: Map<Wirtschaftsregionen, Int> = mapOf(
    Wirtschaftsregionen.GESCHÄFTSBANK to 1,
)

internal val testSpielerNamen = listOf(
    "Anna",
    "Bernd",
    "Clara",
    "David",
    "Eva",
    "Franz",
    "Georg",
)

private val testSpieler = testSpielerNamen.map { name ->
    Spieler(name, startBau, startKontrolle)
}

private val anna = testSpieler[0]
private val bernd = testSpieler[1]
private val clara = testSpieler[2]
private val david = testSpieler[3]
private val eva = testSpieler[4]
private val franz = testSpieler[5]
private val georg = testSpieler[6]

private val anleiheAnna1 = Anleihe(
    schuldiger = anna,
    sondervermögen = 80.toZahlungsmittel(),
    unvermögen = 6.toZahlungsmittel(),
    laufzeit = 4,
)

private val anleiheBernd1 = Anleihe(
    schuldiger = bernd,
    sondervermögen = 60.toZahlungsmittel(),
    unvermögen = 4.toZahlungsmittel(),
    laufzeit = 5,
)

private val anleiheClara1 = Anleihe(
    schuldiger = clara,
    sondervermögen = 90.toZahlungsmittel(),
    unvermögen = 7.toZahlungsmittel(),
    laufzeit = 6,
)

private val anleiheDavid1 = Anleihe(
    schuldiger = david,
    sondervermögen = 70.toZahlungsmittel(),
    unvermögen = 5.toZahlungsmittel(),
    laufzeit = 5,
)

private val anleiheEva1 = Anleihe(
    schuldiger = eva,
    sondervermögen = 100.toZahlungsmittel(),
    unvermögen = 8.toZahlungsmittel(),
    laufzeit = 4,
)

private val anleiheFranzFaellig = Anleihe(
    schuldiger = franz,
    sondervermögen = 50.toZahlungsmittel(),
    unvermögen = 3.toZahlungsmittel(),
    laufzeit = 4,
)

private val anleiheGeorgOffen = Anleihe(
    schuldiger = georg,
    sondervermögen = 65.toZahlungsmittel(),
    unvermögen = 4.toZahlungsmittel(),
    laufzeit = 5,
)

private fun runde(
    vararg änderungen: Pair<Spieler, Pair<Map<Bauteil, Int>, Map<Wirtschaftsregionen, Int>>>,
): Map<Spieler, Pair<Map<Bauteil, Int>, Map<Wirtschaftsregionen, Int>>> {
    return testSpieler.associateWith {
        emptyMap<Bauteil, Int>() to emptyMap<Wirtschaftsregionen, Int>()
    } + änderungen
}

private fun änderung(
    spieler: Spieler,
    bau: Map<Bauteil, Int> = emptyMap(),
    kontrolle: Map<Wirtschaftsregionen, Int> = emptyMap(),
): Pair<Spieler, Pair<Map<Bauteil, Int>, Map<Wirtschaftsregionen, Int>>> {
    return spieler to (bau to kontrolle)
}

private fun bau(
    vararg werte: Pair<Bauteil, Int>,
): Map<Bauteil, Int> {
    return mapOf(*werte)
}

private fun kontroll(
    vararg werte: Pair<Wirtschaftsregionen, Int>,
): Map<Wirtschaftsregionen, Int> {
    return mapOf(*werte)
}

private fun rohstoff(
    besitzer: JuristischePerson,
    erwerber: JuristischePerson,
    betrag: Int,
    anzahl: Int,
    rohstoff: Rohstoffe,
): RohstoffHandel {
    return RohstoffHandel(
        besitzer = besitzer,
        erwerber = erwerber,
        betrag = betrag.toZahlungsmittel(),
        anzahl = anzahl,
        rohstoff = rohstoff,
    )
}

private fun vertrag(
    anbieter: Spieler,
    annehmer: Spieler,
    art: Vertragsart,
): Vertrag {
    return Vertrag(
        vertragsanbieter = listOf(anbieter.name),
        vertragsannehmer = listOf(annehmer.name),
        vertragsart = art,
    )
}
