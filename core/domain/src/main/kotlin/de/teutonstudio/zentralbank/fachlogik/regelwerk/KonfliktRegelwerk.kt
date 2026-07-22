package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.MarktAuswertung
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.Anleihe
import de.teutonstudio.zentralbank.fachlogik.modell.AnleiheId
import de.teutonstudio.zentralbank.fachlogik.modell.Friedensvertrag
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.Konflikt
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegId
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsSeite
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsStatus
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SchuldUebertragung
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.WaffenstillstandsAngebot

internal object KonfliktRegelwerk {
    fun kampfAufloesen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KampfAufgeloest,
    ): SpielZustand {
        require(zustand.konflikte.any { it.betrifft(ereignis.angreifer, ereignis.verteidiger) }) {
            "Eine Schlacht setzt einen Krieg zwischen Angreifer und Verteidiger voraus."
        }
        val krieg = zustand.konflikte.first { it.betrifft(ereignis.angreifer, ereignis.verteidiger) }
        require(!krieg.hatWaffenstillstand(ereignis.angreifer, ereignis.verteidiger)) {
            "Während eines Waffenstillstands ist kein Angriff zulässig."
        }
        val karte = requireNotNull(zustand.karte) { "Eine Schlacht benötigt eine Karte." }
        val angreifer = karte.belegung.kriegseinheiten.filter {
            it.besitzer == ereignis.angreifer && it.typ == ereignis.typ && it.position == ereignis.kante
        }.sortedBy { it.id }
        val verteidiger = karte.belegung.kriegseinheiten.filter {
            it.besitzer == ereignis.verteidiger && it.typ == ereignis.typ && it.position == ereignis.kante
        }.sortedBy { it.id }
        require(angreifer.size == ereignis.angreiferVorher &&
            verteidiger.size == ereignis.verteidigerVorher
        ) { "Die gemeldeten Kampfteilnehmer stimmen nicht mit der Karte überein." }
        val erwartet = ueberlebendeTruppen(angreifer.size, verteidiger.size)
        require(erwartet == (ereignis.angreiferNachher to ereignis.verteidigerNachher)) {
            "Das Kampfergebnis verletzt die deterministische Verlustregel."
        }
        val behalten = angreifer.take(ereignis.angreiferNachher).mapTo(mutableSetOf()) { it.id }
        verteidiger.take(ereignis.verteidigerNachher).mapTo(behalten) { it.id }
        val beteiligt = (angreifer + verteidiger).mapTo(mutableSetOf()) { it.id }
        return zustand.copy(
            karte = karte.copy(
                belegung = karte.belegung.copy(
                    kriegseinheiten = karte.belegung.kriegseinheiten.filter {
                        it.id !in beteiligt || it.id in behalten
                    },
                ),
            ),
        )
    }

    fun kriegErklaeren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegErklaert,
    ): SpielZustand {
        require(ereignis.aggressor != ereignis.verteidiger) {
            "Ein Spieler kann sich nicht selbst Krieg erklären."
        }
        val aktive = zustand.spieler.mapTo(mutableSetOf()) { it.id } - zustand.ausgeschiedeneSpieler
        require(ereignis.aggressor in aktive && ereignis.verteidiger in aktive) {
            "Krieg kann nur zwischen aktiven Spielern erklärt werden."
        }
        require(zustand.konflikte.none { konflikt ->
            konflikt.betrifft(ereignis.aggressor, ereignis.verteidiger)
        }) { "Zwischen diesen Spielern besteht bereits Krieg." }

        val gemeinsamerKrieg = zustand.konflikte.firstOrNull { krieg ->
            krieg.status != KriegsStatus.BEENDET &&
                ereignis.verteidiger in krieg.verteidiger &&
                ereignis.aggressor !in krieg.teilnehmer
        }
        return if (gemeinsamerKrieg != null) {
            zustand.copy(
                konflikte = zustand.konflikte - gemeinsamerKrieg + gemeinsamerKrieg.copy(
                    aggressoren = gemeinsamerKrieg.aggressoren + ereignis.aggressor,
                ),
            )
        } else {
            require(zustand.konflikte.none { it.id == ereignis.krieg }) {
                "Die Krieg-ID ist bereits vergeben."
            }
            zustand.copy(
                konflikte = zustand.konflikte + Konflikt(
                    spielerA = ereignis.aggressor,
                    spielerB = ereignis.verteidiger,
                    id = ereignis.krieg,
                    begonnenInRunde = zustand.rundenzähler,
                ),
                naechsteKriegNummer = zustand.naechsteKriegNummer + 1L,
            )
        }
    }

    fun allianzBeitreten(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegsAllianzBeigetreten,
    ): SpielZustand = zustand.aendereKrieg(ereignis.krieg) { krieg ->
        require(krieg.status != KriegsStatus.BEENDET) { "Der Krieg ist bereits beendet." }
        require(ereignis.spieler !in krieg.teilnehmer) { "Der Spieler nimmt bereits teil." }
        require(ereignis.spieler !in zustand.ausgeschiedeneSpieler) {
            "Ein ausgeschiedener Spieler kann keiner Kriegsallianz beitreten."
        }
        when (ereignis.seite) {
            KriegsSeite.AGGRESSOREN -> krieg.copy(aggressoren = krieg.aggressoren + ereignis.spieler)
            KriegsSeite.VERTEIDIGER -> krieg.copy(verteidiger = krieg.verteidiger + ereignis.spieler)
        }
    }

    fun waffenstillstandAnbieten(
        zustand: SpielZustand,
        ereignis: SpielEreignis.WaffenstillstandAngeboten,
    ): SpielZustand = zustand.aendereKrieg(ereignis.krieg) { krieg ->
        require(krieg.betrifft(ereignis.von, ereignis.an)) {
            "Waffenstillstand ist nur zwischen gegnerischen Kriegsteilnehmern möglich."
        }
        require(!krieg.hatWaffenstillstand(ereignis.von, ereignis.an)) {
            "Zwischen den Spielern gilt bereits ein Waffenstillstand."
        }
        require(krieg.waffenstillstandsAngebote.none {
            it.von == ereignis.von && it.an == ereignis.an
        }) { "Dieses Waffenstillstandsangebot ist bereits offen." }
        krieg.copy(
            waffenstillstandsAngebote = krieg.waffenstillstandsAngebote +
                WaffenstillstandsAngebot(ereignis.von, ereignis.an, zustand.rundenzähler),
        )
    }

    fun waffenstillstandSchliessen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.WaffenstillstandGeschlossen,
    ): SpielZustand {
        val krieg = zustand.krieg(ereignis.krieg)
        require(krieg.betrifft(ereignis.paar.erster, ereignis.paar.zweiter)) {
            "Waffenstillstand ist nur zwischen Gegnern möglich."
        }
        require(ereignis.paar.enthaelt(ereignis.angenommenVon)) {
            "Nur ein beteiligter Spieler darf den Waffenstillstand annehmen."
        }
        require(krieg.waffenstillstandsAngebote.any { angebot ->
            angebot.an == ereignis.angenommenVon && ereignis.paar.enthaelt(angebot.von)
        }) { "Es liegt kein gegenseitig annehmbares Waffenstillstandsangebot vor." }
        val aktualisiert = krieg.copy(
            waffenstillstaende = krieg.waffenstillstaende + ereignis.paar,
            waffenstillstandsAngebote = krieg.waffenstillstandsAngebote.filterNot {
                it.von in setOf(ereignis.paar.erster, ereignis.paar.zweiter) &&
                    it.an in setOf(ereignis.paar.erster, ereignis.paar.zweiter)
            },
        )
        if (!aktualisiert.vollstaendigImWaffenstillstand()) {
            return zustand.copy(konflikte = zustand.konflikte - krieg + aktualisiert)
        }
        val vertrag = Friedensvertrag(
            id = de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId(
                "frieden-${zustand.naechsteFriedensvertragNummer}",
            ),
            krieg = krieg.id,
            beteiligteSpieler = krieg.teilnehmer,
            unentschiedeneTeilnehmer = krieg.teilnehmer,
            angenommenVon = krieg.teilnehmer,
            abgeschlossenInRunde = zustand.rundenzähler,
        )
        return zustand.copy(
            konflikte = zustand.konflikte - krieg,
            friedensvertraege = zustand.friedensvertraege + vertrag,
            naechsteFriedensvertragNummer = zustand.naechsteFriedensvertragNummer + 1L,
        )
    }

    fun kapitulieren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegKapituliert,
    ): SpielZustand {
        val krieg = zustand.krieg(ereignis.krieg)
        require(ereignis.spieler in krieg.teilnehmer) { "Der Spieler nimmt nicht am Krieg teil." }
        val gewinner = if (ereignis.spieler in krieg.aggressoren) krieg.verteidiger else krieg.aggressoren
        val vertrag = Friedensvertrag(
            id = de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId(
                "frieden-${zustand.naechsteFriedensvertragNummer}",
            ),
            krieg = krieg.id,
            beteiligteSpieler = gewinner + ereignis.spieler,
            gewinner = gewinner,
            verlierer = setOf(ereignis.spieler),
            ausscheidendeTeilnehmer = setOf(ereignis.spieler),
            kapitulationen = setOf(ereignis.spieler),
            angenommenVon = gewinner + ereignis.spieler,
            abgeschlossenInRunde = zustand.rundenzähler,
        )
        return friedenAbschliessen(
            zustand.copy(
                konflikte = zustand.konflikte - krieg + krieg.copy(
                    kapitulationen = krieg.kapitulationen + ereignis.spieler,
                ),
            ),
            vertrag,
        )
    }

    fun friedenVorschlagen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.FriedensvertragVorgeschlagen,
    ): SpielZustand {
        val krieg = zustand.krieg(ereignis.vertrag.krieg)
        require(ereignis.vertrag.beteiligteSpieler == krieg.teilnehmer) {
            "Ein allgemeiner Friedensvertrag muss alle verbleibenden Kriegsteilnehmer enthalten."
        }
        require(ereignis.vertrag.angenommenVon.size == 1 &&
            ereignis.vertrag.angenommenVon.single() in ereignis.vertrag.beteiligteSpieler
        ) {
            "Ein neuer Friedensvertrag muss genau vom vorschlagenden Teilnehmer angenommen sein."
        }
        require(zustand.friedensvertraege.none { it.id == ereignis.vertrag.id }) {
            "Die Friedensvertrag-ID ist bereits vergeben."
        }
        require(zustand.friedensvertraege.none {
            it.krieg == ereignis.vertrag.krieg && it.abgeschlossenInRunde == null
        }) {
            "Für diesen Krieg ist bereits ein Friedensvertrag offen."
        }
        return zustand.copy(
            friedensvertraege = zustand.friedensvertraege + ereignis.vertrag,
            konflikte = zustand.konflikte - krieg + krieg.copy(
                status = KriegsStatus.FRIEDEN_ANGEBOTEN,
            ),
            naechsteFriedensvertragNummer = zustand.naechsteFriedensvertragNummer + 1L,
        )
    }

    fun friedenAnnehmen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.FriedensvertragAngenommen,
    ): SpielZustand {
        val vertrag = zustand.friedensvertraege.singleOrNull { it.id == ereignis.vertrag }
            ?: error("Unbekannter Friedensvertrag: ${ereignis.vertrag.wert}")
        require(ereignis.spieler in vertrag.beteiligteSpieler) {
            "Nur Beteiligte dürfen den Friedensvertrag annehmen."
        }
        require(ereignis.spieler !in vertrag.angenommenVon) {
            "Der Spieler hat den Vertrag bereits angenommen."
        }
        return zustand.copy(
            friedensvertraege = zustand.friedensvertraege.map {
                if (it.id == vertrag.id) it.copy(angenommenVon = it.angenommenVon + ereignis.spieler)
                else it
            },
        )
    }

    fun friedenAbschliessen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.FriedensvertragAbgeschlossen,
    ): SpielZustand = friedenAbschliessen(zustand, ereignis.vertrag)

    private fun friedenAbschliessen(
        zustand: SpielZustand,
        vertrag: Friedensvertrag,
    ): SpielZustand {
        val krieg = zustand.krieg(vertrag.krieg)
        require(vertrag.angenommenVon.containsAll(vertrag.beteiligteSpieler)) {
            "Der Friedensvertrag wurde noch nicht von allen Beteiligten angenommen."
        }
        val mitVerteilung = if (vertrag.gewinner.isNotEmpty() && vertrag.verlierer.isNotEmpty()) {
            val zentral = schuldUebertragungen(zustand, vertrag.gewinner, vertrag.verlierer)
            require(vertrag.schuldUebertragungen.isEmpty() || vertrag.schuldUebertragungen == zentral) {
                "Die Schuldübertragung weicht von der zentralen Marktwertberechnung ab."
            }
            vertrag.copy(schuldUebertragungen = zentral)
        } else {
            vertrag.copy(schuldUebertragungen = emptyList())
        }
        val austretende = mitVerteilung.ausscheidendeTeilnehmer
            .ifEmpty { mitVerteilung.beteiligteSpieler }
        val verbleibendeAggressoren = krieg.aggressoren - austretende
        val verbleibendeVerteidiger = krieg.verteidiger - austretende
        val kriege = if (verbleibendeAggressoren.isEmpty() || verbleibendeVerteidiger.isEmpty()) {
            zustand.konflikte - krieg
        } else {
            zustand.konflikte - krieg + krieg.copy(
                aggressoren = verbleibendeAggressoren,
                verteidiger = verbleibendeVerteidiger,
                status = KriegsStatus.AKTIV,
            )
        }
        val nachKrieg = zustand.copy(
            konflikte = kriege,
            naechsteFriedensvertragNummer = maxOf(
                zustand.naechsteFriedensvertragNummer,
                zustand.naechsteFriedensvertragNummer +
                    if (zustand.friedensvertraege.none { it.id == mitVerteilung.id }) 1L else 0L,
            ),
        )
        val (nachSchuldnovation, vollzogenerVertrag) =
            schuldenUmschichten(nachKrieg, mitVerteilung)
        return nachSchuldnovation.copy(
            friedensvertraege = zustand.friedensvertraege.filterNot {
                it.id == vollzogenerVertrag.id
            } + vollzogenerVertrag,
        )
    }

    /**
     * Löst die Siegeranleihen atomar aus und ersetzt sie beim jeweils selben Gläubiger
     * durch gleich hohe Forderungen gegen die Verlierer. Wirtschaftlich entspricht das
     * dem vorgeschriebenen Rückkauf plus Neuemission, ohne vorübergehende Doppelbuchung.
     */
    private fun schuldenUmschichten(
        zustand: SpielZustand,
        vertrag: Friedensvertrag,
    ): Pair<SpielZustand, Friedensvertrag> {
        if (vertrag.schuldUebertragungen.none { it.betrag > Geld.NULL }) {
            return zustand to vertrag.copy(
                entstehendeAnleihen = emptyList(),
                schuldenstrichDanach = emptySet(),
            )
        }
        val alteAnleihen = zustand.anleihen.values
            .filter { it.emittent in vertrag.gewinner }
            .sortedBy { it.id.wert }
        if (alteAnleihen.isEmpty()) return zustand to vertrag

        data class AlteForderung(
            val anleihe: Anleihe,
            val glaeubiger: KontoId,
            var restCent: Long,
        )

        val forderungen = alteAnleihen.map { anleihe ->
            AlteForderung(
                anleihe = anleihe,
                glaeubiger = AnleihenAuswertung.besitzer(zustand, anleihe.id)
                    ?: error("Anleihe ${anleihe.id.wert} besitzt keinen Gläubiger."),
                restCent = anleihe.nennwert.cent,
            )
        }
        val neue = mutableListOf<Pair<Anleihe, KontoId>>()
        var forderungsIndex = 0
        vertrag.schuldUebertragungen.sortedBy { it.verlierer.wert }.forEach { uebertragung ->
            var restAnteil = uebertragung.betrag.cent
            while (restAnteil > 0L) {
                while (forderungen[forderungsIndex].restCent == 0L) forderungsIndex += 1
                val alt = forderungen[forderungsIndex]
                val teil = minOf(restAnteil, alt.restCent)
                val id = AnleiheId("friedensschuld-${vertrag.id.wert}-${neue.size + 1}")
                require(id !in zustand.anleihen && neue.none { it.first.id == id }) {
                    "Die Friedensschuld-ID ${id.wert} ist bereits vergeben."
                }
                neue += Anleihe(
                    id = id,
                    emittent = uebertragung.verlierer,
                    nennwert = Geld.cent(teil),
                    zinsBasispunkte = maxOf(alt.anleihe.zinsBasispunkte, zustand.leitzins.wert),
                    laufzeitRunden = maxOf(
                        1,
                        alt.anleihe.faelligkeitsRunde - zustand.rundenzähler - 1,
                    ),
                    emissionsRunde = zustand.rundenzähler,
                ) to alt.glaeubiger
                alt.restCent -= teil
                restAnteil -= teil
            }
        }
        require(forderungen.all { it.restCent == 0L }) {
            "Die Friedensschuld deckt nicht alle Siegeranleihen."
        }

        var neuZustand = zustand
        alteAnleihen.forEach { alt ->
            neuZustand = AnleihenRegelwerk.anleiheAusloesen(neuZustand, alt.id)
        }
        neuZustand = neuZustand.copy(
            anleihen = neuZustand.anleihen + neue.associate { it.first.id to it.first },
            bankAnleihen = neuZustand.bankAnleihen + neue
                .filter { it.second == KontoId.Bank }
                .map { it.first.id },
            spieler = neuZustand.spieler.map { spieler ->
                val neueForderungen = neue.filter {
                    it.second == KontoId.Spieler(spieler.id)
                }.map { it.first.id }
                spieler.copy(anleihen = spieler.anleihen + neueForderungen)
            },
            naechsteAnleiheNummer = neuZustand.naechsteAnleiheNummer + neue.size,
        )

        val externeNeueJeVerlierer = neue
            .filter { (anleihe, glaeubiger) -> glaeubiger != KontoId.Spieler(anleihe.emittent) }
            .groupingBy { it.first.emittent }
            .eachCount()
        val schuldenstrichDanach = vertrag.verlierer.filterTo(mutableSetOf()) { verlierer ->
            externeNeueJeVerlierer.getOrDefault(verlierer, 0) >
                AnleihenAuswertung.freieGeschaeftsbankPlaetze(zustand, verlierer)
        }
        return neuZustand to vertrag.copy(
            entstehendeAnleihen = neue.map { it.first.id },
            schuldenstrichDanach = schuldenstrichDanach,
        )
    }

    internal fun schuldUebertragungen(
        zustand: SpielZustand,
        gewinner: Set<SpielerId>,
        verlierer: Set<SpielerId>,
    ): List<SchuldUebertragung> {
        val schuldCent = zustand.anleihen.values
            .filter { it.emittent in gewinner }
            .sumOf { it.nennwert.cent }
        if (schuldCent == 0L) return verlierer.sortedBy { it.wert }.map {
            SchuldUebertragung(it, de.teutonstudio.zentralbank.fachlogik.modell.Geld.NULL,
                MarktAuswertung.spielerMarktwert(zustand, it))
        }
        val marktwerte = verlierer.sortedBy { it.wert }.associateWith {
            MarktAuswertung.spielerMarktwert(zustand, it)
        }
        val summe = marktwerte.values.sumOf { maxOf(0L, it.cent) }
        val basis = marktwerte.mapValues { (_, wert) ->
            if (summe == 0L) schuldCent / verlierer.size
            else Math.floorDiv(Math.multiplyExact(schuldCent, maxOf(0L, wert.cent)), summe)
        }.toMutableMap()
        var rest = schuldCent - basis.values.sum()
        val rundungsReste = marktwerte.keys.associateWith { 0L }.toMutableMap()
        marktwerte.keys.sortedBy { it.wert }.forEach { spieler ->
            if (rest > 0L) {
                basis[spieler] = basis.getValue(spieler) + 1L
                rundungsReste[spieler] = 1L
                rest -= 1L
            }
        }
        return marktwerte.keys.sortedBy { it.wert }.map { spieler ->
            SchuldUebertragung(
                verlierer = spieler,
                betrag = de.teutonstudio.zentralbank.fachlogik.modell.Geld.cent(basis.getValue(spieler)),
                marktwertVorFrieden = marktwerte.getValue(spieler),
                rundungsRestCent = rundungsReste.getValue(spieler),
            )
        }
    }

    internal fun ueberlebendeTruppen(anzahlA: Int, anzahlB: Int): Pair<Int, Int> {
        require(anzahlA >= 0 && anzahlB >= 0) { "Truppenzahlen dürfen nicht negativ sein." }
        if (anzahlA == anzahlB) return 0 to 0
        val aIstStaerker = anzahlA > anzahlB
        val staerker = maxOf(anzahlA, anzahlB)
        val unterschied = kotlin.math.abs(anzahlA - anzahlB)
        val ueberlebendeStaerkere = when (unterschied) {
            1 -> 1
            2 -> minOf(3, staerker)
            else -> staerker
        }
        return if (aIstStaerker) ueberlebendeStaerkere to 0 else 0 to ueberlebendeStaerkere
    }

    private fun SpielZustand.krieg(id: KriegId): Konflikt =
        konflikte.singleOrNull { it.id == id } ?: error("Unbekannter Krieg: ${id.wert}")

    private fun SpielZustand.aendereKrieg(
        id: KriegId,
        aenderung: (Konflikt) -> Konflikt,
    ): SpielZustand {
        val bisher = krieg(id)
        return copy(konflikte = konflikte - bisher + aenderung(bisher))
    }
}
