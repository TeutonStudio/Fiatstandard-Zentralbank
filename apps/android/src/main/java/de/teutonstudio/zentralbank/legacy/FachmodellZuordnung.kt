/* LEGACY-KONVERTER: Gezielter Abbauplan in docs/legacy-migration.md. */
package de.teutonstudio.zentralbank.daten.zuordnung

import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Basispunkte
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.istInRundeNullPlatzierbar
import kotlin.math.roundToInt
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe as FachAnleihe
import de.teutonstudio.zentralbank.fachlogik.modell.Spieler as FachSpieler

fun Zahlungsmittel.zuGeld(): Geld = Geld.mark(toIntOderNull().toLong())

fun Float.zuBasispunkte(): Basispunkte = Basispunkte((this * Basispunkte.BASISPUNKTE_PRO_PROZENT).roundToInt())

fun Rohstoffe.zuRohstoff(): Rohstoff = when (this) {
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

fun Rohstoff.zuRohstoffe(): Rohstoffe = when (this) {
    Rohstoff.NAHRUNG -> Rohstoffe.NAHRUNG
    Rohstoff.LEHM -> Rohstoffe.LEHM
    Rohstoff.ZIEGEL -> Rohstoffe.ZIEGEL
    Rohstoff.HOLZ -> Rohstoffe.HOLZ
    Rohstoff.ROHOEL -> Rohstoffe.ROHÖL
    Rohstoff.SCHWEROEL -> Rohstoffe.SCHWERÖL
    Rohstoff.DIESEL -> Rohstoffe.DIESEL
    Rohstoff.KOHLE -> Rohstoffe.KOHLE
    Rohstoff.STAHL -> Rohstoffe.STAHL
    Rohstoff.EISEN -> Rohstoffe.EISEN
}

fun Bauteil.zuBauteilTyp(): BauteilTyp = when (this) {
    Handelslinie.LAND -> BauteilTyp.EISENBAHNLINIE
    Handelslinie.SEE -> BauteilTyp.FRACHTSCHIFF
    Verwaltungsstandort.HAUPTBAHNHOF -> BauteilTyp.HAUPTBAHNHOF
    Verwaltungsstandort.BAHNHOF -> BauteilTyp.BAHNHOF
    Verwaltungsstandort.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
    Verwaltungsstandort.HAFEN -> BauteilTyp.HAFEN
    Verwaltungsstandort.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
    Wirtschaftsregionen.GESCHÄFTSBANK -> BauteilTyp.GESCHAEFTSBANK
    Wirtschaftsregionen.VIEHHOF -> BauteilTyp.VIEHHOF
    Wirtschaftsregionen.ANGLER -> BauteilTyp.ANGLER
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

fun Spiel.zuSpielZustand(): SpielZustand {
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
    val fachSpieler = spielerListe.map { spieler ->
        FachSpieler(
            id = spielerIds.getValue(spieler),
            name = spieler.name,
            passwortHash = spieler.passwortHash,
            rohstoffe = startRohstoffe[spieler.name]
                .orEmpty()
                .mapKeys { (rohstoff, _) -> rohstoff.zuRohstoff() }
                .filterValues { menge -> menge > 0 },
            geldkonto = spielerSaldo.lastOrNull()?.get(spieler)?.zuGeld() ?: Geld.NULL,
            anleihen = anleihenNachBesitzer[spieler.name].orEmpty(),
            bauteile = spieler.erhalteBauSaldoZurRunde()
                .mapKeys { (bauteil, _) -> bauteil.zuBauteilTyp() }
                .filterValues { it != 0 },
        )
    }
    val rundeNullRestbestand = if (karte != null && aktuelleRunde <= 1) {
        fachSpieler.associate { spieler ->
            val platzierteEcktypen = karte.belegung.ecken
                .filter { belegung -> belegung.besitzer == spieler.id }
                .map { belegung ->
                    when (belegung.typ) {
                        EckGebaeudeTyp.HAUPTBAHNHOF -> BauteilTyp.HAUPTBAHNHOF
                        EckGebaeudeTyp.BAHNHOF -> BauteilTyp.BAHNHOF
                        EckGebaeudeTyp.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
                        EckGebaeudeTyp.HAFEN -> BauteilTyp.HAFEN
                        EckGebaeudeTyp.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
                    }
                }
                .groupingBy { it }
                .eachCount()
            spieler.id to spieler.bauteile
                .filterKeys(BauteilTyp::istInRundeNullPlatzierbar)
                .mapValues { (bauteil, menge) ->
                    (menge - platzierteEcktypen.getOrDefault(bauteil, 0)).coerceAtLeast(0)
                }
                .filterValues { menge -> menge > 0 }
        }.filterValues { rest -> rest.isNotEmpty() }
    } else {
        emptyMap()
    }
    val istRundeNull = karte != null && aktuelleRunde <= 1 && rundeNullRestbestand.isNotEmpty()
    val aktiverFachSpieler = if (istRundeNull) {
        fachSpieler.firstOrNull { it.id in rundeNullRestbestand }?.id
    } else {
        spielerListe.firstOrNull()?.let { spielerIds.getValue(it) }
    }

    return SpielZustand(
        karte = karte,
        spielabschnitt = if (istRundeNull) {
            Spielabschnitt.RUNDE_NULL
        } else {
            Spielabschnitt.REGULAER
        },
        rundeNullRestbestand = if (karte != null && aktuelleRunde <= 1) {
            rundeNullRestbestand
        } else {
            null
        },
        spieler = fachSpieler,
        bankkonto = Geld.NULL,
        bankAnleihen = anleihenNachBesitzer
            .filterKeys { besitzer -> besitzer !in spielerNamen }
            .values
            .flatten(),
        warenkorb = warenkorb
            .mapKeys { (rohstoff, _) -> rohstoff.zuRohstoff() }
            .filterValues { it != 0 },
        anleihen = anleihen.mapNotNull { anzeige ->
            val id = anleiheIds[anzeige] ?: return@mapNotNull null
            val emittentId = spielerIds.entries
                .firstOrNull { (spieler, _) -> spieler.name == anzeige.schuldiger.name }
                ?.value
                ?: return@mapNotNull null
            id to FachAnleihe(
                id = id,
                emittent = emittentId,
                nennwert = anzeige.sondervermoegen.zuGeld(),
                zinsBasispunkte = anzeige.anleihe.erhalteZinssatz() * 100,
                laufzeitRunden = anzeige.laufzeit,
                zinsbetrag = anzeige.unvermoegen.zuGeld(),
                emissionsRunde = anzeige.emittiert,
                faelligkeitsRunde = anzeige.faelligkeit,
            )
        }.toMap(),
        marktpreise = aktuelleMarktpreise
            .mapKeys { (rohstoff, _) -> rohstoff.zuRohstoff() }
            .mapValues { (_, preis) -> preis.zuGeld() },
        leitzins = aktuellerLeitzinssatz.zuBasispunkte(),
        rundenzähler = (aktuelleRunde - 1).coerceAtLeast(0),
        aktiverSpieler = aktiverFachSpieler,
    )
}
