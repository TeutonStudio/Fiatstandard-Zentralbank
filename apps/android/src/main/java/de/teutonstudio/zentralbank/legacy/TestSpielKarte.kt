/* LEGACY-TESTDATEN: Nicht als Grundlage neuer Domain-Logik verwenden. */
package de.teutonstudio.zentralbank.datenbank

import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeFeld
import de.teutonstudio.zentralbank.fachlogik.modell.GelaendeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenHexagon
import de.teutonstudio.zentralbank.fachlogik.modell.Spielkarte
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.felder

/** Kompakte, quellcodefähige Spiegelung der größten gebündelten Europa-Vorlage. */
private val europaKontinentalRaster = """
    .............................................................................................W.WWWWWW.............
    ....................G.WGWWWWWWWWWW...............................G.GGWWWWWWWWWWWWWW.............................GG
    GGGGWWWWWWWWWWWWWW................W............GGGGGGWGWWWWWWWWWWWWWW............W.WWWW...........GGGGGGGGWW.WWWWW
    WWWWWW.W........W.WWWWWWWW...........GWGGGGGGWG.W..WWWWWWWW.W......W.WWWWWWWWWWWW...........W.GGGGGGGGWW....WWWWWW
    WW......W.WWWWWWWWWWWWWW...........W.GWGGGGGGWG.W..W.WWWWWW.W....WWWWWWWWWWWWWWWWWW.............WWGGGGGGGG.W....W.
    WWWW.W..W.WWWWWWWWWWWWWWWWWWWW.............WWGWGGGGGGWG......WWWW.WW.WWWWWWWWWWWWWWWWWWWWWWWW...............WWGWGG
    GGGG.W.......WWWWWWWWWWWWWWWWWSWSSSSWWWWWWGW.........W..W..W..W..GGGGWW......W.WWWWWWWWWWWWWWWWSSSSSSSSWWWWWWGG...
    ....W.WWWW.W....W.GGGGWG......WWWWWWWWWWWWWWWWWWSSSSSSSSSSWWWWGWGG.........EEEEEE......EE.G.G....W.WWWWWWWWWWWWWWW
    WWWSWSSSSSSSSSSWWWWGGGG...E.EE.EE.EEEE.E....E.EE......E.WWWWSWSSWWWWWWWWWWWWSSSSSSSSSSWSWWGWGG.G.....EEEEE.EEEE.E.
    .S.E.WE.E..E.EEWESWSSSSSSWSWWWWWWWWWWSSSSSSSSWSWWWWGG.G......E.EE.EEEEEEE..S.SSWWWWWWEEEEEEWWSSSSSSSSWSWWWWWWWWWWS
    SSSSSWSWWWWGWGG.......EEEEE.EEEEEES.SSWSWWWWEWEEEEWESWSSSSSSSSWWWWWWWWWWWWWWWSWWWWWWWWGG.G.........EEEEEE.ESSWSWWW
    WWWEEEEEEWWSSSSSSSSWWWWWWWWWWWWWWWWWWWWWWWWGWGG...........E.E.EEWEWWWWWWEWEEGEGGGGGGWSWSWWWWWWWWWWWWWWWWWWWWWWWWWW
    GGG........EEEEEEEEEEGWGWGWEEGEGGGGGGGGEGEEEEEEEEEEEEEEEEEEEEEEEEEE.E.........EEEEEEGEGGGGGGGGGGGGGGGGGGGGEEEEEEEE
    EEEEEEEEEEEEEEEEEE.E.......E.EEEEGEGGGGGGGGGGGGGGGGGGGGEGEEEEEEEEEEEEEEEEEEEE.E.........E.EEEEEEEEGGGGGGGGEGGEGGGG
    GGEG.E....EEEEEEEEEEEEEE.E.......G.GGGGGGGGEEEEE.GG.G..GGGGGGEGEE........EEGEGGGGGGEE.....GEGGGGGGGG.G.E..GEGG.G..
    GGGGGGEG..........G.GGGGGGGG.....EEGGGG.G....EE..GGGG....G.GGGG....G.G.GGGGGGGGGGGG.G...EEEE.E.......E..GGEG..G.GG
    .GEEGGGGGGGGGGGGGGGGGG.G...EEEE............GGEE..GG.GE.GGGGGGGGGGGGGGEGEGEG...EE............G.EG....EE..E.GGGGGGGG
    GGGGEGEEEE...............G.EG............GGGGGGGGGGEGEEEE...............EE..............EEEEEGEE.E.E..............
    ...........E.EE................................................E..................................................
""".trimIndent().filterNot { zeichen -> zeichen.isWhitespace() }

private val testSpielerHauptbahnhofPositionen = listOf(
    KartenEcke(0, 0),
    KartenEcke(34, -4),
    KartenEcke(-24, 24),
    KartenEcke(18, 28),
    KartenEcke(-6, -32),
    KartenEcke(-25, -6),
    KartenEcke(16, -20),
)

private fun baueTestSpielEuropaKarte(): Spielkarte {
    val hexagon = KartenHexagon(radius = 19)
    require(europaKontinentalRaster.length.toLong() == hexagon.anzahlFelder) {
        "Das Europa-Raster passt nicht zum Kartenhexagon."
    }
    val gelaendefelder = hexagon.felder().mapIndexedNotNull { index, position ->
        val gelaende = when (val code = europaKontinentalRaster[index]) {
            '.' -> null
            'E' -> GelaendeTyp.EBENE
            'W' -> GelaendeTyp.WALD
            'G' -> GelaendeTyp.GEBIRGE
            'S' -> GelaendeTyp.SUMPF
            'D' -> GelaendeTyp.WUESTE
            else -> error("Unbekannter Geländecode im Europa-Raster: $code")
        }
        gelaende?.let { typ -> GelaendeFeld(position, typ) }
    }
    require(testSpielerHauptbahnhofPositionen.size == testSpielerNamen.size) {
        "Jeder Testspieler braucht genau eine Hauptbahnhofposition."
    }
    val hauptbahnhoefe = testSpielerNamen.zip(
        testSpielerHauptbahnhofPositionen,
    ) { spielerName, position ->
        EckBelegung(
            position = position,
            typ = EckGebaeudeTyp.HAUPTBAHNHOF,
            besitzer = SpielerId(spielerName),
            gebautInRunde = 0,
        )
    }
    return Spielkarte(
        id = "testspiel-europa-5-kontinental",
        name = "Testspiel – Europa 5 – Kontinental",
        hexagon = hexagon,
        gelaendefelder = gelaendefelder,
        belegung = KartenBelegung(ecken = hauptbahnhoefe),
    )
}

internal val testSpielKarte = baueTestSpielEuropaKarte()
