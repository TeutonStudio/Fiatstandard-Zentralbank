package de.teutonstudio.zentralbank.schnittstelle

import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp

val BauteilTyp.anzeigeText: String get() = when (this) {
    BauteilTyp.HAUPTBAHNHOF -> "hauptbahnhof"
    BauteilTyp.EISENBAHNLINIE -> "eisenbahnlinie"
    BauteilTyp.FRACHTSCHIFF -> "frachtschiff"
    BauteilTyp.BAHNHOF -> "bahnhof"
    BauteilTyp.GROSSBAHNHOF -> "grossbahnhof"
    BauteilTyp.HAFEN -> "hafen"
    BauteilTyp.GROSSHAFEN -> "grosshafen"
    BauteilTyp.GESCHAEFTSBANK -> "geschaeftsbank"
    BauteilTyp.VIEHHOF -> "viehhof"
    BauteilTyp.ANGLER -> "angler"
    BauteilTyp.ZIEGELBRENNER -> "ziegelbrenner"
    BauteilTyp.LEHMINE -> "lehmmine"
    BauteilTyp.FOERSTER -> "foerster"
    BauteilTyp.BOHRTURM -> "bohrturm"
    BauteilTyp.RAFFINERIE -> "raffinerie"
    BauteilTyp.SYNTHETIK_RAFFINERIE -> "synthetik raffinerie"
    BauteilTyp.KOHLEMINE -> "kohlemine"
    BauteilTyp.STAHLFABRIK -> "stahlfabrik"
    BauteilTyp.EISENMINE -> "eisenmine"
}
