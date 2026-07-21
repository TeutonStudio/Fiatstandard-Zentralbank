package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
enum class BauteilArt {
    HANDELSLINIE,
    VERWALTUNGSSTANDORT,
    WIRTSCHAFTSREGION,
}

@Serializable
enum class ProduktionsArt {
    KEINE,
    ABBAU,
    VERARBEITUNG,
}

@Serializable
enum class BauteilTyp(
    val art: BauteilArt,
    val kosten: Map<Rohstoff, Int>,
    val ertrag: Map<Rohstoff, Int> = emptyMap(),
    val verbrauch: Map<Rohstoff, Int> = emptyMap(),
    val produktionsArt: ProduktionsArt = ProduktionsArt.KEINE,
) {
    HAUPTBAHNHOF(
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = emptyMap(),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 3, Rohstoff.KOHLE to 3),
    ),
    EISENBAHNLINIE(
        art = BauteilArt.HANDELSLINIE,
        kosten = rohstoffe(Rohstoff.HOLZ to 1, Rohstoff.STAHL to 1),
    ),
    FRACHTSCHIFF(
        art = BauteilArt.HANDELSLINIE,
        kosten = rohstoffe(Rohstoff.STAHL to 2),
    ),
    BAHNHOF(
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 2, Rohstoff.ZIEGEL to 2, Rohstoff.STAHL to 1),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
    ),
    GROSSBAHNHOF(
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 4, Rohstoff.ZIEGEL to 3, Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 2, Rohstoff.KOHLE to 2),
    ),
    HAFEN(
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 1, Rohstoff.ZIEGEL to 2, Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 1, Rohstoff.SCHWEROEL to 1),
    ),
    GROSSHAFEN(
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 2, Rohstoff.ZIEGEL to 3, Rohstoff.STAHL to 4),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 2, Rohstoff.SCHWEROEL to 2),
    ),
    GESCHAEFTSBANK(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
    ),
    VIEHHOF(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.NAHRUNG to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    ANGLER(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.NAHRUNG to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    ZIEGELBRENNER(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.ZIEGEL to 1),
        verbrauch = rohstoffe(Rohstoff.LEHM to 1),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    LEHMINE(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.LEHM to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    FOERSTER(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.HOLZ to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    BOHRTURM(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.ROHOEL to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    RAFFINERIE(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.DIESEL to 2, Rohstoff.SCHWEROEL to 1),
        verbrauch = rohstoffe(Rohstoff.ROHOEL to 2),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    SYNTHETIK_RAFFINERIE(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.DIESEL to 2, Rohstoff.SCHWEROEL to 1),
        verbrauch = rohstoffe(Rohstoff.KOHLE to 3),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    KOHLEMINE(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.KOHLE to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    STAHLFABRIK(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.EISEN to 1, Rohstoff.KOHLE to 1),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    EISENMINE(
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.EISEN to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
}

/** Bauteile, die in Runde 0 genau einer Ecke, Kante oder einem Dreieck zugeordnet werden. */
val BauteilTyp.istInRundeNullPlatzierbar: Boolean
    get() = this != BauteilTyp.FRACHTSCHIFF &&
        this != BauteilTyp.GROSSBAHNHOF &&
        this != BauteilTyp.GROSSHAFEN

private fun wirtschaftsstandortBaukosten(): Map<Rohstoff, Int> =
    rohstoffe(Rohstoff.HOLZ to 3, Rohstoff.ZIEGEL to 2)

fun rohstoffe(vararg mengen: Pair<Rohstoff, Int>): Map<Rohstoff, Int> {
    return mengen.toMap().filterValues { it != 0 }
}
