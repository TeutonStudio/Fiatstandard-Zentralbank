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
    val text: String,
    val art: BauteilArt,
    val kosten: Map<Rohstoff, Int>,
    val ertrag: Map<Rohstoff, Int> = emptyMap(),
    val verbrauch: Map<Rohstoff, Int> = emptyMap(),
    val produktionsArt: ProduktionsArt = ProduktionsArt.KEINE,
) {
    HAUPTBAHNHOF(
        text = "hauptbahnhof",
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = emptyMap(),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 3, Rohstoff.KOHLE to 3),
    ),
    EISENBAHNLINIE(
        text = "eisenbahnlinie",
        art = BauteilArt.HANDELSLINIE,
        kosten = rohstoffe(Rohstoff.HOLZ to 1, Rohstoff.STAHL to 1),
    ),
    FRACHTSCHIFF(
        text = "frachtschiff",
        art = BauteilArt.HANDELSLINIE,
        kosten = rohstoffe(Rohstoff.STAHL to 2),
    ),
    BAHNHOF(
        text = "bahnhof",
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 2, Rohstoff.ZIEGEL to 2, Rohstoff.STAHL to 1),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 1, Rohstoff.KOHLE to 1),
    ),
    GROSSBAHNHOF(
        text = "grossbahnhof",
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 4, Rohstoff.ZIEGEL to 3, Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 2, Rohstoff.KOHLE to 2),
    ),
    HAFEN(
        text = "hafen",
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 1, Rohstoff.ZIEGEL to 2, Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 1, Rohstoff.SCHWEROEL to 1),
    ),
    GROSSHAFEN(
        text = "grosshafen",
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 2, Rohstoff.ZIEGEL to 3, Rohstoff.STAHL to 4),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 2, Rohstoff.SCHWEROEL to 2),
    ),
    GESCHAEFTSBANK(
        text = "geschaeftsbank",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
    ),
    VIEHHOF(
        text = "viehhof",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.NAHRUNG to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    ZIEGELBRENNER(
        text = "ziegelbrenner",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.ZIEGEL to 1),
        verbrauch = rohstoffe(Rohstoff.LEHM to 1),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    LEHMINE(
        text = "lehmmine",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.LEHM to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    FOERSTER(
        text = "foerster",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.HOLZ to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    BOHRTURM(
        text = "bohrturm",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.ROHOEL to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    RAFFINERIE(
        text = "raffinerie",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.DIESEL to 2, Rohstoff.SCHWEROEL to 1),
        verbrauch = rohstoffe(Rohstoff.ROHOEL to 2),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    SYNTHETIK_RAFFINERIE(
        text = "synthetik raffinerie",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.DIESEL to 2, Rohstoff.SCHWEROEL to 1),
        verbrauch = rohstoffe(Rohstoff.KOHLE to 3),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    KOHLEMINE(
        text = "kohlemine",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.KOHLE to 1),
        produktionsArt = ProduktionsArt.ABBAU,
    ),
    STAHLFABRIK(
        text = "stahlfabrik",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = wirtschaftsstandortBaukosten(),
        ertrag = rohstoffe(Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.EISEN to 1, Rohstoff.KOHLE to 1),
        produktionsArt = ProduktionsArt.VERARBEITUNG,
    ),
    EISENMINE(
        text = "eisenmine",
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
