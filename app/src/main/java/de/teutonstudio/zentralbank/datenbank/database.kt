package de.teutonstudio.zentralbank.datenbank

import androidx.room.Entity
import androidx.room.PrimaryKey

fun <A> List<A>.dropFirst(n:Int) = this.reversed().dropLast(n).reversed()
fun <A> Map<Int,A>.dropLowest(): Map<Int,A> = this.toSortedMap().toList().dropFirst(1).toMap()
fun <A> Map<Int,A>.getLowest(): Map.Entry<Int,A> = this.toSortedMap().firstEntry()
fun Map<Int, Anleihenhandel>.erhalteErste(): Anleihenhandel = this.getLowest().value

sealed interface SpeicherDaten {
    val spielID: Long
}

@Entity(tableName = "GameData")
data class SpielDaten(
    @PrimaryKey(autoGenerate = true)
    override val spielID: Long = 0,

    val spieler: String,

    val warenkorb: String,
    val inflationsziel: Float,
    val nAbweichung: Float,
    val sAbweichung: Float,
): SpeicherDaten {
    constructor(warenkorb: Map<Rohstoffe,Int>, spieler:List<Spieler>, inflationswerte: Triple<Float, Float, Float>) : this(
        spielID=(-1).toLong(),
        spieler=spieler.joinToString("/") { it.name },
        warenkorb=warenkorb.map { "${it.key}#${it.value}" }.joinToString("/"),
        inflationsziel=inflationswerte.first,
        nAbweichung=inflationswerte.second,
        sAbweichung=inflationswerte.third,
    )
}

@Entity(tableName = "PlayerData")
data class SpielerDaten(
    @PrimaryKey(autoGenerate = true)
    val spielerID: Int = 0, // -1 bedeutet Ausland oder Geschäftsbank, abhängig des Kontext

    override var spielID: Long,

    val spielerName: String,
): SpeicherDaten {
    constructor(name: String): this(
        spielID = -1,
        spielerName = name,
    )
}

@Entity(tableName = "BuildData")
data class BauteilDaten(
    @PrimaryKey(autoGenerate = true)
    val bauwerkID: Int = 0,
    override val spielID: Long,

    val erbauer: String,
    val runde: Int,
    val bauteil: String,
    val delta: Int,
): SpeicherDaten {
    constructor(spieler: SpielerDaten, runde: RundeDaten, bauteil: Bauteil, änderung: Int): this(
        spielID = -1, //runde.spielID,
        erbauer = spieler.spielerName,
        runde = runde.index,
        bauteil = bauteil.str,
        delta = änderung,
    )
}

@Entity(tableName = "ControlData")
data class KontrolleDaten(
    @PrimaryKey(autoGenerate = true)
    val kontrolleID: Int = 0,
    override val spielID: Long,

    val besatzer: String,
    val runde: Int,
    val region: String,
    val delta: Int,
): SpeicherDaten {
    constructor(spieler: SpielerDaten, runde: RundeDaten, region: Wirtschaftsregionen, änderung: Int): this(
        spielID = -1, // runde.spielID,
        besatzer = spieler.spielerName,
        runde = runde.index,
        region = region.str,
        delta = änderung,
    )
}

@Entity(tableName = "RoundData")
data class RundeDaten(
    @PrimaryKey(autoGenerate = true)
    val rundeID: Int = 0,
    override var spielID: Long,

    val index: Int,
    val leitzinssatz: Float,
): SpeicherDaten {
    constructor(index: Int,zinsatz: Float): this(
        spielID = -1,
        index = index,
        leitzinssatz = zinsatz,
    )
    constructor(runde: Runde): this(runde.index,runde.leitzinssatz)
}

@Entity(tableName = "TradeData")
data class HandelsDaten(
    @PrimaryKey(autoGenerate = true)
    val handelID: Int = 0,
    override val spielID: Long,

    val runde: Int,
    val besitzer: String,
    val erwerber: String,
    val menge: Int,
    val rohstoff: String,
    val preis: String,
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,handel: RohstoffHandel): this(
        spielID=-1, // rundeDaten.spielID,
        runde=rundeDaten.index,
        besitzer=handel.besitzer.name,
        erwerber=handel.erwerber.name,
        menge=handel.anzahl,
        rohstoff=handel.rohstoff.str,
        preis=handel.betrag.speichereString(),
    )
}

@Entity(tableName = "CreditData")
data class AnleiheDaten(
    @PrimaryKey(autoGenerate = true)
    val anleiheID: Int = 0,
    override val spielID: Long = 0,

    val emittiert: Int,
    val emittent: String,
    val sondervermogen: String,
    val unvermogen: String,
    val laufzeit: Int,
    var handel: String, // spekulant1,preis1,Zeitpunkt1/spekulant2,preis2,Zeitpunkt2
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,anleihe: Map<Int,Anleihenhandel>): this(
        spielID=-1,//rundeDaten.spielID,
        emittiert=rundeDaten.index,
        emittent=anleihe.erhalteErste().besitzer.name,
        sondervermogen=anleihe.erhalteErste().anleihe.sondervermögen.speichereString(),
        unvermogen=anleihe.erhalteErste().anleihe.unvermögen.speichereString(),
        laufzeit=anleihe.erhalteErste().anleihe.laufzeit,
        handel= anleihe.dropLowest().map { "${it.value.erwerber}#${it.value.preis.toIntOderNull()}#${it.key}" }.joinToString("/")
    )
}

@Entity(tableName = "ContractData")
data class VertragsDaten(
    @PrimaryKey(autoGenerate = true)
    val vertragID: Int = 0,
    override val spielID: Long,

    val runde: Int,
    val vertragsannehmer: String,
    val vertragsanbieter: String,
    val vertragsart: String,
): SpeicherDaten {
    constructor(rundeDaten: RundeDaten,annehmer: String,anbieter: String,vertragsart: Vertragsart): this(
        spielID=-1, // rundeDaten.spielID,
        runde=rundeDaten.index,
        vertragsannehmer=annehmer,
        vertragsanbieter=anbieter,
        vertragsart=vertragsart.str,
    )
}
