package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotStatus
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId

/**
 * Prüft Beziehungen, die nicht sinnvoll in den Konstruktoren einzelner Wertobjekte liegen.
 * Die Engine führt diese Prüfung vor und nach jeder Spieleraktion aus. Dadurch benutzen
 * Android, Server, Replay und Simulation denselben Invariantenpfad.
 */
object ZustandsInvarianten {
    fun pruefe(zustand: SpielZustand): Result<Unit> = runCatching {
        val spielerIds = zustand.spieler.map { spieler -> spieler.id }
        require(spielerIds.size == spielerIds.toSet().size) {
            "Spieler-IDs müssen innerhalb einer Partie eindeutig sein."
        }
        require(zustand.spieler.map { spieler -> spieler.name }.distinct().size == spielerIds.size) {
            "Spielernamen müssen innerhalb einer Partie eindeutig sein."
        }
        val bekannteSpieler = spielerIds.toSet()
        require(zustand.spieler.all { spieler ->
            spieler.rohstoffe.values.all { menge -> menge >= 0 } &&
                spieler.bauteile.values.all { menge -> menge >= 0 }
        }) {
            "Rohstoff- und Bauteilbestände dürfen nicht negativ sein."
        }
        require(zustand.warenkorb.values.all { menge -> menge >= 0 }) {
            "Der Warenkorb darf keine negativen Mengen enthalten."
        }
        require(zustand.rundenzähler >= 0) { "Der Rundenzähler darf nicht negativ sein." }
        require(
            zustand.naechsteAnleiheNummer > 0L &&
                zustand.naechsteSeewegNummer > 0L &&
                zustand.naechsteEinheitenNummer > 0L &&
                zustand.naechsteAngebotsNummer > 0L &&
                zustand.naechsteKriegNummer > 0L &&
                zustand.naechsteFriedensvertragNummer > 0L,
        ) {
            "Deterministische Objektzähler müssen positiv sein."
        }

        pruefeAktivenZug(zustand, bekannteSpieler)
        pruefeAusscheidenUndErgebnis(zustand, bekannteSpieler)
        pruefeAnleihen(zustand, bekannteSpieler)
        pruefeAngebote(zustand, bekannteSpieler)
        pruefeKartenReferenzen(zustand, bekannteSpieler)
        require(zustand.konflikte.all { konflikt ->
            konflikt.teilnehmer.all { it in bekannteSpieler } &&
                konflikt.teilnehmer.none { it in zustand.ausgeschiedeneSpieler } &&
                konflikt.waffenstillstaende.all { paar ->
                    paar.erster in konflikt.teilnehmer && paar.zweiter in konflikt.teilnehmer &&
                        konflikt.aufVerschiedenenSeiten(paar.erster, paar.zweiter)
                }
        }) { "Ein Krieg enthält unbekannte, ausgeschiedene oder widersprüchliche Teilnehmer." }
        require(zustand.konflikte.map { it.id }.distinct().size == zustand.konflikte.size) {
            "Krieg-IDs müssen eindeutig sein."
        }
        require(zustand.friedensvertraege.map { it.id }.distinct().size ==
            zustand.friedensvertraege.size
        ) { "Friedensvertrag-IDs müssen eindeutig sein." }
        require(zustand.belagerungen.map { it.standort }.distinct().size ==
            zustand.belagerungen.size
        ) { "Je Verwaltungsstandort darf höchstens eine Belagerung laufen." }
    }

    private fun pruefeAktivenZug(zustand: SpielZustand, bekannteSpieler: Set<SpielerId>) {
        if (zustand.ergebnis != null) {
            require(zustand.aktiverSpieler == null && zustand.zugStatus == null) {
                "Eine beendete Partie darf keinen aktiven Zug besitzen."
            }
            return
        }
        val aktiverSpieler = zustand.aktiverSpieler
        val zug = zustand.zugStatus
        require((aktiverSpieler == null) == (zug == null)) {
            "Aktiver Spieler und Zugstatus müssen gemeinsam gesetzt oder leer sein."
        }
        if (aktiverSpieler != null && zug != null) {
            require(aktiverSpieler in bekannteSpieler) { "Der aktive Spieler ist unbekannt." }
            require(aktiverSpieler !in zustand.ausgeschiedeneSpieler) {
                "Ein ausgeschiedener Spieler darf keinen aktiven Zug besitzen."
            }
            require(zug.spieler == aktiverSpieler) {
                "Aktive Spieler-ID und Spieler-ID des Zugstatus stimmen nicht überein."
            }
            require(zug.zugId > 0L) { "Eine Zug-ID muss positiv sein." }
        }
    }

    private fun pruefeAusscheidenUndErgebnis(
        zustand: SpielZustand,
        bekannteSpieler: Set<SpielerId>,
    ) {
        require(zustand.ausgeschiedeneSpieler.all { it in bekannteSpieler }) {
            "Der Ausscheidensstand enthält einen unbekannten Spieler."
        }
        require(zustand.ausscheidensReihenfolge.size == zustand.ausscheidensReihenfolge.toSet().size) {
            "Ein Spieler darf nur einmal in der Ausscheidensreihenfolge stehen."
        }
        require(zustand.ausscheidensReihenfolge.toSet() == zustand.ausgeschiedeneSpieler) {
            "Ausscheidensmenge und Ausscheidensreihenfolge müssen dieselben Spieler enthalten."
        }
        zustand.ergebnis?.let { ergebnis ->
            require(ergebnis.ausgeschiedeneSpieler == zustand.ausgeschiedeneSpieler) {
                "Das Partieergebnis enthält einen abweichenden Ausscheidensstand."
            }
            require(ergebnis.platzierungen.size == ergebnis.platzierungen.toSet().size) {
                "Ein Spieler darf in den Platzierungen höchstens einmal vorkommen."
            }
            require(ergebnis.platzierungen.all { it in bekannteSpieler }) {
                "Das Partieergebnis verweist auf einen unbekannten Spieler."
            }
            require(ergebnis.gewinner == null || ergebnis.gewinner in bekannteSpieler) {
                "Der Gewinner ist kein Spieler dieser Partie."
            }
            require(ergebnis.gewinner == null || ergebnis.gewinner !in zustand.ausgeschiedeneSpieler) {
                "Ein ausgeschiedener Spieler kann nicht Gewinner sein."
            }
            require(ergebnis.endRunde == zustand.rundenzähler) {
                "Das Partieergebnis gehört nicht zur laufenden Runde."
            }
        }
    }

    private fun pruefeAnleihen(zustand: SpielZustand, bekannteSpieler: Set<SpielerId>) {
        require(zustand.anleihen.values.all { anleihe -> anleihe.emittent in bekannteSpieler }) {
            "Eine Anleihe verweist auf einen unbekannten Emittenten."
        }
        val alleBestandsIds = buildList<AnleiheId> {
            addAll(zustand.bankAnleihen)
            zustand.spieler.forEach { spieler -> addAll(spieler.anleihen) }
        }
        require(alleBestandsIds.all { it in zustand.anleihen }) {
            "Ein Anleihebestand verweist auf eine unbekannte Anleihe."
        }
        require(alleBestandsIds.size == alleBestandsIds.toSet().size) {
            "Jede Anleihe darf höchstens einen Gläubiger besitzen."
        }
    }

    private fun pruefeAngebote(zustand: SpielZustand, bekannteSpieler: Set<SpielerId>) {
        require(zustand.handelsAngebote.map { it.id }.distinct().size == zustand.handelsAngebote.size) {
            "Handelsangebot-IDs müssen eindeutig sein."
        }
        require(zustand.anleihenAngebote.map { it.id }.distinct().size == zustand.anleihenAngebote.size) {
            "Anleihenangebot-IDs müssen eindeutig sein."
        }
        zustand.handelsAngebote.forEach { angebot ->
            require(angebot.anbieter in bekannteSpieler &&
                (angebot.empfaenger == null || angebot.empfaenger in bekannteSpieler)
            ) { "Ein Handelsangebot verweist auf einen unbekannten Spieler." }
            require(angebot.angeboteneRohstoffe.values.all { it >= 0 } &&
                angebot.geforderteRohstoffe.values.all { it >= 0 }
            ) { "Angebotsmengen dürfen nicht negativ sein." }
            require(angebot.angebotenerGeldbetrag >= Geld.NULL &&
                angebot.geforderterGeldbetrag >= Geld.NULL
            ) { "Angebotsbeträge dürfen nicht negativ sein." }
        }
        zustand.anleihenAngebote.forEach { angebot ->
            require(angebot.anbieter in bekannteSpieler &&
                (angebot.empfaenger == null || angebot.empfaenger in bekannteSpieler)
            ) { "Ein Anleihenangebot verweist auf einen unbekannten Spieler." }
            require(angebot.anleihe in zustand.anleihen) {
                "Ein Anleihenangebot verweist auf eine unbekannte Anleihe."
            }
            require(angebot.preis >= Geld.NULL) { "Ein Angebotspreis darf nicht negativ sein." }
        }
        val offeneNummern = zustand.handelsAngebote
            .filter { it.status == HandelsAngebotStatus.OFFEN }
            .map { it.id.wert } + zustand.anleihenAngebote
            .filter { it.status == HandelsAngebotStatus.OFFEN }
            .map { it.id.wert }
        require(offeneNummern.all { it < zustand.naechsteAngebotsNummer }) {
            "Eine offene Angebots-ID muss vor dem nächsten Objektzähler liegen."
        }
    }

    private fun pruefeKartenReferenzen(
        zustand: SpielZustand,
        bekannteSpieler: Set<SpielerId>,
    ) {
        val belegung = zustand.karte?.belegung ?: return
        require(belegung.ecken.mapNotNull { it.besitzer }.all { it in bekannteSpieler }) {
            "Ein Eckgebäude verweist auf einen unbekannten Besitzer."
        }
        require(belegung.kanten.mapNotNull { it.erbautVon }.all { it in bekannteSpieler }) {
            "Eine Handelslinie verweist auf einen unbekannten Erbauer."
        }
        require(belegung.seewege.all { it.besitzer in bekannteSpieler }) {
            "Ein Seeweg verweist auf einen unbekannten Besitzer."
        }
        require(belegung.kriegseinheiten.all { einheit ->
            einheit.besitzer in bekannteSpieler &&
                (einheit.gegner == null || einheit.gegner in bekannteSpieler)
        }) { "Eine Kriegseinheit verweist auf einen unbekannten Spieler." }
    }
}
