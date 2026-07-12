package de.teutonstudio.zentralbank.datenbank

import de.teutonstudio.zentralbank.domain.AnleiheId
import de.teutonstudio.zentralbank.domain.Basispunkte
import de.teutonstudio.zentralbank.domain.BauteilTyp
import de.teutonstudio.zentralbank.domain.GameState
import de.teutonstudio.zentralbank.domain.Geld
import de.teutonstudio.zentralbank.domain.Rohstoff
import de.teutonstudio.zentralbank.domain.SpielerId
import kotlin.math.roundToInt
import de.teutonstudio.zentralbank.domain.Anleihe as DomainAnleihe
import de.teutonstudio.zentralbank.domain.Spieler as DomainSpieler

fun Zahlungsmittel.zuDomainGeld(): Geld = Geld.mark(toIntOderNull().toLong())

fun Float.zuBasispunkte(): Basispunkte = Basispunkte((this * Basispunkte.BASISPUNKTE_PRO_PROZENT).roundToInt())

fun Rohstoffe.zuDomainRohstoff(): Rohstoff = when (this) {
    Rohstoffe.NAHRUNG -> Rohstoff.NAHRUNG
    Rohstoffe.LEHM -> Rohstoff.LEHM
    Rohstoffe.ZIEGEL -> Rohstoff.ZIEGEL
    Rohstoffe.HOLZ -> Rohstoff.HOLZ
    Rohstoffe.ROHÖL -> Rohstoff.ROHOEL
    Rohstoffe.SCHWERÖL -> Rohstoff.SCHWEROEL
    Rohstoffe.DIESEL -> Rohstoff.DIESEL
    Rohstoffe.KOHLE -> Rohstoff.KOHLE
    Rohstoffe.STAHL -> Rohstoff.STAHL
    Rohstoffe.EISEN -> Rohstoff.EISEN
}

fun Bauteil.zuDomainBauteilTyp(): BauteilTyp = when (this) {
    Handelslinie.LAND -> BauteilTyp.EISENBAHNLINIE
    Handelslinie.SEE -> BauteilTyp.FRACHTSCHIFF
    Verwaltungsstandort.BAHNHOF -> BauteilTyp.BAHNHOF
    Verwaltungsstandort.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
    Verwaltungsstandort.HAFEN -> BauteilTyp.HAFEN
    Verwaltungsstandort.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
    Wirtschaftsregionen.GESCHÄFTSBANK -> BauteilTyp.GESCHAEFTSBANK
    Wirtschaftsregionen.VIEHHOF -> BauteilTyp.VIEHHOF
    Wirtschaftsregionen.ZIEGELBRENNER -> BauteilTyp.ZIEGELBRENNER
    Wirtschaftsregionen.LEHMINE -> BauteilTyp.LEHMINE
    Wirtschaftsregionen.FÖRSTER -> BauteilTyp.FOERSTER
    Wirtschaftsregionen.BOHRTURM -> BauteilTyp.BOHRTURM
    Wirtschaftsregionen.RAFFINERIE -> BauteilTyp.RAFFINERIE
    Wirtschaftsregionen.SRAFINNERIE -> BauteilTyp.SYNTHETIK_RAFFINERIE
    Wirtschaftsregionen.KOHLEMINE -> BauteilTyp.KOHLEMINE
    Wirtschaftsregionen.STAHLFABRIK -> BauteilTyp.STAHLFABRIK
    Wirtschaftsregionen.EISENMINE -> BauteilTyp.EISENMINE
}

fun Spiel.zuDomainGameState(): GameState {
    val spielerIds = spielerListe.associateWith { SpielerId(it.name) }
    val anleiheIds = anleihen.associateWith { anzeige ->
        AnleiheId(
            listOf(
                anzeige.emittiert,
                anzeige.schuldiger.name,
                anzeige.sondervermoegen.speichereString(),
                anzeige.unvermoegen.speichereString(),
                anzeige.laufzeit,
            ).joinToString("#"),
        )
    }
    val anleihenNachBesitzer = anleihen
        .groupBy { it.aktuellerBesitzer.name }
        .mapValues { (_, werte) -> werte.mapNotNull { anleiheIds[it] } }
    val spielerNamen = spielerIds.keys.map { it.name }.toSet()

    return GameState(
        spieler = spielerListe.map { spieler ->
            DomainSpieler(
                id = spielerIds.getValue(spieler),
                name = spieler.name,
                geldkonto = spielerSaldo.lastOrNull()?.get(spieler)?.zuDomainGeld() ?: Geld.NULL,
                anleihen = anleihenNachBesitzer[spieler.name].orEmpty(),
                bauteile = spieler.erhalteBauSaldoZurRunde()
                    .mapKeys { (bauteil, _) -> bauteil.zuDomainBauteilTyp() }
                    .filterValues { it != 0 },
            )
        },
        bankkonto = Geld.NULL,
        bankAnleihen = anleihenNachBesitzer
            .filterKeys { besitzer -> besitzer !in spielerNamen }
            .values
            .flatten(),
        warenkorb = warenkorb
            .mapKeys { (rohstoff, _) -> rohstoff.zuDomainRohstoff() }
            .filterValues { it != 0 },
        anleihen = anleihen.mapNotNull { anzeige ->
            val id = anleiheIds[anzeige] ?: return@mapNotNull null
            val emittentId = spielerIds.entries
                .firstOrNull { (spieler, _) -> spieler.name == anzeige.schuldiger.name }
                ?.value
                ?: return@mapNotNull null
            id to DomainAnleihe(
                id = id,
                emittent = emittentId,
                nennwert = anzeige.sondervermoegen.zuDomainGeld(),
                zinsBasispunkte = anzeige.anleihe.erhalteZinssatz() * 100,
                laufzeitRunden = anzeige.laufzeit,
            )
        }.toMap(),
        marktpreise = aktuelleMarktpreise
            .mapKeys { (rohstoff, _) -> rohstoff.zuDomainRohstoff() }
            .mapValues { (_, preis) -> preis.zuDomainGeld() },
        leitzins = nächsterZinssatz.zuBasispunkte(),
        rundenzähler = aktuelleRunde,
        aktiverSpieler = spielerListe.firstOrNull()?.let { spielerIds.getValue(it) },
    )
}
