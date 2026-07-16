package de.teutonstudio.zentralbank.fachlogik.modell

import kotlinx.serialization.Serializable

@Serializable
enum class BauteilArt {
    HANDELSLINIE,
    VERWALTUNGSSTANDORT,
    WIRTSCHAFTSREGION,
}

@Serializable
enum class BauteilTyp(
    val text: String,
    val art: BauteilArt,
    val kosten: Map<Rohstoff, Int>,
    val ertrag: Map<Rohstoff, Int> = emptyMap(),
    val verbrauch: Map<Rohstoff, Int> = emptyMap(),
) {
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
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 1, Rohstoff.DIESEL to 1),
    ),
    GROSSHAFEN(
        text = "grosshafen",
        art = BauteilArt.VERWALTUNGSSTANDORT,
        kosten = rohstoffe(Rohstoff.HOLZ to 2, Rohstoff.ZIEGEL to 3, Rohstoff.STAHL to 4),
        verbrauch = rohstoffe(Rohstoff.NAHRUNG to 2, Rohstoff.DIESEL to 2),
    ),
    GESCHAEFTSBANK(
        text = "geschaeftsbank",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
    ),
    VIEHHOF(
        text = "viehhof",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.NAHRUNG to 1),
    ),
    ZIEGELBRENNER(
        text = "ziegelbrenner",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.ZIEGEL to 1),
        verbrauch = rohstoffe(Rohstoff.LEHM to 1),
    ),
    LEHMINE(
        text = "lehmmine",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.LEHM to 1),
    ),
    FOERSTER(
        text = "foerster",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.HOLZ to 1),
    ),
    BOHRTURM(
        text = "bohrturm",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.ROHOEL to 1),
    ),
    RAFFINERIE(
        text = "raffinerie",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.DIESEL to 2, Rohstoff.SCHWEROEL to 1),
        verbrauch = rohstoffe(Rohstoff.ROHOEL to 2),
    ),
    SYNTHETIK_RAFFINERIE(
        text = "synthetik raffinerie",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.DIESEL to 2, Rohstoff.SCHWEROEL to 1),
        verbrauch = rohstoffe(Rohstoff.KOHLE to 3),
    ),
    KOHLEMINE(
        text = "kohlemine",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.KOHLE to 1),
    ),
    STAHLFABRIK(
        text = "stahlfabrik",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.STAHL to 2),
        verbrauch = rohstoffe(Rohstoff.EISEN to 1, Rohstoff.KOHLE to 1),
    ),
    EISENMINE(
        text = "eisenmine",
        art = BauteilArt.WIRTSCHAFTSREGION,
        kosten = emptyMap(),
        ertrag = rohstoffe(Rohstoff.EISEN to 1),
    ),
}

fun rohstoffe(vararg mengen: Pair<Rohstoff, Int>): Map<Rohstoff, Int> {
    return mengen.toMap().filterValues { it != 0 }
}
