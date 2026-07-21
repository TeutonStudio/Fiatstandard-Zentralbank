package de.teutonstudio.zentralbank.schnittstelle

import androidx.compose.ui.graphics.Color
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen

val Bauteil.farbe: Color get() = when (this) {
    Handelslinie.LAND -> Color(0xFF527681)
    Handelslinie.SEE -> Color(0xFF8F6D56)
    Verwaltungsstandort.HAUPTBAHNHOF -> Color(0xFF536D55)
    Verwaltungsstandort.BAHNHOF -> Color(0xFF687B55)
    Verwaltungsstandort.GROSSBAHNHOF -> Color(0xFF946570)
    Verwaltungsstandort.HAFEN -> Color(0xFF5F7392)
    Verwaltungsstandort.GROSSHAFEN -> Color(0xFF88764F)
    Wirtschaftsregionen.GESCHÄFTSBANK -> Color(0xFF6B6288)
    Wirtschaftsregionen.VIEHHOF -> Color(0xFF47756C)
    Wirtschaftsregionen.ANGLER -> Color(0xFF3F728F)
    Wirtschaftsregionen.ZIEGELBRENNER -> Color(0xFF8A604D)
    Wirtschaftsregionen.LEHMINE -> Color(0xFF5F784F)
    Wirtschaftsregionen.FÖRSTER -> Color(0xFF786183)
    Wirtschaftsregionen.BOHRTURM -> Color(0xFF466D87)
    Wirtschaftsregionen.RAFFINERIE -> Color(0xFF8B7943)
    Wirtschaftsregionen.SRAFINNERIE -> Color(0xFF4D7164)
    Wirtschaftsregionen.KOHLEMINE -> Color(0xFF865B63)
    Wirtschaftsregionen.STAHLFABRIK -> Color(0xFF666A7B)
    Wirtschaftsregionen.EISENMINE -> Color(0xFF7B634B)
}

val Rohstoffe.farbe: Color get() = when (this) {
    Rohstoffe.NAHRUNG -> Color(0xFF8A6D8F)
    Rohstoffe.LEHM -> Color(0xFFB58B5A)
    Rohstoffe.ZIEGEL -> Color(0xFFA75D5D)
    Rohstoffe.HOLZ -> Color(0xFFC9826B)
    Rohstoffe.ROHÖL -> Color(0xFF6F8061)
    Rohstoffe.SCHWERÖL -> Color(0xFF795F7C)
    Rohstoffe.DIESEL -> Color(0xFF8A7A45)
    Rohstoffe.KOHLE -> Color(0xFF697078)
    Rohstoffe.STAHL -> Color(0xFF718096)
    Rohstoffe.EISEN -> Color(0xFF858585)
}
