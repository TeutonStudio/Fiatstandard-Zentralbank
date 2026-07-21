/* LEGACY-ROOM-KONVERTER: Nur für Datenbankkompatibilität während der Migration. */
@file:Suppress("FunctionName")

package de.teutonstudio.zentralbank.datenbank

/** Übergangsfabriken zwischen dem alten Android-Fachmodell und den Room-Entitäten. */
fun AnleiheDaten(rundeDaten: RundeDaten, anleihe: Map<Int, Anleihenhandel>): AnleiheDaten =
    AnleiheDaten(
        spielID = -1,
        emittiert = rundeDaten.index,
        emittent = anleihe.erhalteErste().anleihe.schuldiger.name,
        sondervermogen = anleihe.erhalteErste().anleihe.sondervermögen.speichereString(),
        unvermogen = anleihe.erhalteErste().anleihe.unvermögen.speichereString(),
        laufzeit = anleihe.erhalteErste().anleihe.laufzeit,
        handel = anleihe.toSortedMap().map { (runde, handel) ->
            "${handel.besitzer.name}#${handel.erwerber.name}#${handel.preis.speichereString()}#$runde"
        }.joinToString("|"),
    )

fun BauteilDaten(
    spieler: SpielerDaten,
    runde: RundeDaten,
    bauteil: Bauteil,
    aenderung: Int,
): BauteilDaten = BauteilDaten(
    spielID = -1,
    erbauer = spieler.spielerName,
    runde = runde.index,
    bauteil = bauteil.str,
    delta = aenderung,
)

fun HandelsDaten(rundeDaten: RundeDaten, handel: RohstoffHandel): HandelsDaten = HandelsDaten(
    spielID = -1,
    runde = rundeDaten.index,
    besitzer = handel.besitzer.name,
    erwerber = handel.erwerber.name,
    menge = handel.anzahl,
    rohstoff = handel.rohstoff.str,
    preis = handel.betrag.speichereString(),
)

fun KontrolleDaten(
    spieler: SpielerDaten,
    runde: RundeDaten,
    region: Wirtschaftsregionen,
    aenderung: Int,
): KontrolleDaten = KontrolleDaten(
    spielID = -1,
    besatzer = spieler.spielerName,
    runde = runde.index,
    region = region.str,
    delta = aenderung,
)

fun RundeDaten(index: Int, zinsatz: Float): RundeDaten = RundeDaten(
    spielID = -1,
    index = index,
    leitzinssatz = zinsatz,
)

fun RundeDaten(runde: Runde): RundeDaten = RundeDaten(runde.index, runde.leitzinssatz)

fun SpielDaten(
    warenkorb: Map<Rohstoffe, Int>,
    spieler: List<Spieler>,
    inflationswerte: Triple<Float, Float, Float>,
): SpielDaten = SpielDaten(
    spieler = spieler.joinToString("/") { it.name },
    warenkorb = warenkorb.zuSpeicherWarenkorb(),
    inflationsziel = inflationswerte.first,
    nAbweichung = inflationswerte.second,
    sAbweichung = inflationswerte.third,
)

fun SpielerDaten(name: String): SpielerDaten = SpielerDaten(
    spielID = -1,
    spielerName = name,
)

fun VertragsDaten(
    rundeDaten: RundeDaten,
    annehmer: String,
    anbieter: String,
    vertragsart: Vertragsart,
): VertragsDaten = VertragsDaten(
    spielID = -1,
    runde = rundeDaten.index,
    vertragsannehmer = annehmer,
    vertragsanbieter = anbieter,
    vertragsart = vertragsart.str,
)

fun Map<Rohstoffe, Int>.zuSpeicherWarenkorb(): String {
    require(values.all { menge -> menge >= 0 }) {
        "Warenkorbmengen dürfen nicht negativ sein."
    }
    return entries
        .filter { (_, menge) -> menge > 0 }
        .sortedBy { (rohstoff, _) -> rohstoff.ordinal }
        .joinToString("/") { (rohstoff, menge) -> "${rohstoff.name}#$menge" }
}

fun AnleiheAnzeige.speichereHandelsverlauf(): String = handelsverlauf
    .toSortedMap()
    .map { (runde, handel) ->
        "${handel.besitzer.name}#${handel.erwerber.name}#${handel.preis.speichereString()}#$runde"
    }
    .joinToString("|")

fun AnleiheDaten.passtZu(anzeige: AnleiheAnzeige): Boolean =
    emittiert == anzeige.emittiert &&
        emittent == anzeige.schuldiger.name &&
        sondervermogen == anzeige.sondervermoegen.speichereString() &&
        unvermogen == anzeige.unvermoegen.speichereString() &&
        laufzeit == anzeige.laufzeit &&
        (handel.istNeuesHandelsformat().not() || handel == anzeige.speichereHandelsverlauf())

private fun String.istNeuesHandelsformat(): Boolean {
    val eintraege = split("|").map(String::trim).filter(String::isNotBlank)
    return eintraege.isNotEmpty() && eintraege.all { eintrag -> eintrag.split("#").size == 4 }
}
