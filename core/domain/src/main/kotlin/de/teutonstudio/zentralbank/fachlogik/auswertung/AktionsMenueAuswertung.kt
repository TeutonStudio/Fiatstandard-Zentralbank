package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.modell.KartenKante
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsSeite
import de.teutonstudio.zentralbank.fachlogik.modell.KriegId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.enthaeltFeld
import de.teutonstudio.zentralbank.fachlogik.modell.istBefahrbar
import de.teutonstudio.zentralbank.fachlogik.modell.kanten

/**
 * Leichtgewichtige, bereichsweise Auswertung für die normale Android-Oberfläche.
 *
 * Sie materialisiert bewusst weder Kartenbauaktionen noch den globalen Aktionsraum. Die
 * autoritative Prüfung einer konkret bestätigten Aktion bleibt bei der Spiel-Engine.
 */
object AktionsMenueAuswertung {
    fun uebersicht(zustand: SpielZustand): AktionsMenueUebersicht? {
        val spieler = zustand.aktiverSpieler ?: return null
        if (spieler in zustand.ausgeschiedeneSpieler || zustand.ergebnis != null) return null
        val zug = zustand.zugStatus ?: return null
        if (zug.spieler != spieler) return null
        val epizug = zustand.spielabschnitt == Spielabschnitt.REGULAER && zug.phase == ZugPhase.Epizug
        val andere = zustand.spieler.count { it.id != spieler && it.id !in zustand.ausgeschiedeneSpieler }
        val eigeneEinheiten = zustand.karte?.belegung?.kriegseinheiten?.count { it.besitzer == spieler } ?: 0
        val eigeneAnleihen = zustand.spieler.firstOrNull { it.id == spieler }?.anleihen?.size ?: 0
        return AktionsMenueUebersicht(
            spieler = spieler,
            phase = zug.phase,
            bereiche = listOf(
                AktionsMenueBereichStatus(AktionsMenueBereich.KONFLIKT, epizug && andere > 0 || epizug && zustand.konflikte.any { spieler in it.teilnehmer }, zustand.konflikte.count { spieler in it.teilnehmer }),
                AktionsMenueBereichStatus(AktionsMenueBereich.DIPLOMATIE, epizug && (andere > 0 || zustand.konflikte.isNotEmpty()), null),
                AktionsMenueBereichStatus(AktionsMenueBereich.TRUPPEN, epizug && eigeneEinheiten > 0, eigeneEinheiten),
                AktionsMenueBereichStatus(AktionsMenueBereich.HANDEL, true, null),
                AktionsMenueBereichStatus(AktionsMenueBereich.ANLEIHEN, true, eigeneAnleihen),
                AktionsMenueBereichStatus(AktionsMenueBereich.ZUG, true, null),
                AktionsMenueBereichStatus(AktionsMenueBereich.KI, true, null),
            ),
        )
    }

    fun eintraege(zustand: SpielZustand, bereich: AktionsMenueBereich): List<AktionsMenueEintrag> {
        val spieler = zustand.aktiverSpieler ?: return emptyList()
        val zug = zustand.zugStatus ?: return emptyList()
        if (zug.spieler != spieler || spieler in zustand.ausgeschiedeneSpieler) return emptyList()
        return when (bereich) {
            AktionsMenueBereich.KONFLIKT -> konfliktEintraege(zustand, spieler, zug.phase)
            AktionsMenueBereich.DIPLOMATIE -> diplomatieEintraege(zustand, spieler, zug.phase)
            AktionsMenueBereich.TRUPPEN -> truppenEintraege(zustand, spieler, zug.phase)
            AktionsMenueBereich.ZUG -> zugEintraege(zustand, spieler)
            AktionsMenueBereich.HANDEL, AktionsMenueBereich.ANLEIHEN, AktionsMenueBereich.KI -> emptyList()
        }
    }

    /** Wird erst nach Auswahl eines Stapels aufgerufen; die Nachbarschaft ist lokal begrenzt. */
    fun bewegungsZiele(zustand: SpielZustand, ids: Set<String>): List<KartenKante> {
        val spieler = zustand.aktiverSpieler ?: return emptyList()
        val karte = zustand.karte ?: return emptyList()
        if (zustand.zugStatus?.phase != ZugPhase.Epizug || ids.isEmpty()) return emptyList()
        val einheiten = karte.belegung.kriegseinheiten.filter { it.id in ids }
        if (einheiten.size != ids.size || einheiten.any { it.besitzer != spieler }) return emptyList()
        val start = einheiten.map { it.position }.distinct().singleOrNull() ?: return emptyList()
        val typ = einheiten.map { it.typ }.distinct().singleOrNull() ?: return emptyList()
        return sequenceOf(start.anfang, start.ende)
            .flatMap { ecke -> angrenzendeFelder(ecke).asSequence() }
            .filter(karte::enthaeltFeld)
            .flatMap { feld -> feld.kanten().asSequence() }
            .filter { it != start && (it.anfang == start.anfang || it.anfang == start.ende || it.ende == start.anfang || it.ende == start.ende) }
            .distinct()
            .filter { ziel -> karte.istBefahrbar(typ, ziel) }
            .sortedWith(compareBy({ it.anfang.y }, { it.anfang.x }, { it.ende.y }, { it.ende.x }))
            .toList()
    }

    private fun konfliktEintraege(zustand: SpielZustand, spieler: SpielerId, phase: ZugPhase) = buildList {
        if (phase != ZugPhase.Epizug) return@buildList
        zustand.spieler.asSequence().filter { it.id != spieler && it.id !in zustand.ausgeschiedeneSpieler }
            .filterNot { ziel -> zustand.konflikte.any { it.betrifft(spieler, ziel.id) } }
            .sortedBy { it.name }.forEach { ziel ->
                add(AktionsMenueEintrag("krieg:${ziel.id.wert}", AktionsMenueAktion.KRIEG_ERKLAEREN, ziel.name, zielSpieler = ziel.id))
            }
        zustand.konflikte.asSequence().filter { spieler in it.teilnehmer }.sortedBy { it.id.wert }.forEach { krieg ->
            add(AktionsMenueEintrag("kapitulation:${krieg.id.wert}", AktionsMenueAktion.KAPITULIEREN, "In Krieg ${krieg.id.wert} kapitulieren", konflikt = krieg.id))
        }
    }

    private fun diplomatieEintraege(zustand: SpielZustand, spieler: SpielerId, phase: ZugPhase) = buildList {
        if (phase != ZugPhase.Epizug) return@buildList
        zustand.friedensvertraege.asSequence()
            .filter { it.abgeschlossenInRunde == null && spieler in it.beteiligteSpieler && spieler !in it.angenommenVon }
            .sortedBy { it.id.wert }.forEach { vertrag ->
                add(AktionsMenueEintrag("frieden-annahme:${vertrag.id.wert}", AktionsMenueAktion.FRIEDENSVERTRAG_ANNEHMEN, "Friedensvertrag annehmen", vertrag = vertrag.id.wert))
            }
        zustand.konflikte.asSequence().sortedBy { it.id.wert }.forEach { krieg ->
            if (spieler !in krieg.teilnehmer) {
                KriegsSeite.entries.forEach { seite ->
                    add(AktionsMenueEintrag("allianz:${krieg.id.wert}:$seite", AktionsMenueAktion.ALLIANZ_BEITRETEN, "Allianz beitreten: ${krieg.id.wert} ($seite)", konflikt = krieg.id, seite = seite))
                }
            } else {
                krieg.teilnehmer.asSequence().filter { krieg.betrifft(spieler, it) }.sortedBy { it.wert }.forEach { gegner ->
                    add(AktionsMenueEintrag("waffenstillstand:${krieg.id.wert}:${gegner.wert}", AktionsMenueAktion.WAFFENSTILLSTAND_ANBIETEN, "Waffenstillstand anbieten: ${gegner.wert}", konflikt = krieg.id, zielSpieler = gegner))
                    if (krieg.waffenstillstandsAngebote.any { it.von == gegner && it.an == spieler }) {
                        add(AktionsMenueEintrag("waffenstillstand-annahme:${krieg.id.wert}:${gegner.wert}", AktionsMenueAktion.WAFFENSTILLSTAND_ANNEHMEN, "Waffenstillstand annehmen: ${gegner.wert}", konflikt = krieg.id, zielSpieler = gegner))
                    }
                    add(AktionsMenueEintrag("unabhaengiger-frieden:${krieg.id.wert}:${gegner.wert}", AktionsMenueAktion.UNABHAENGIGER_FRIEDEN, "Unabhängigen Frieden schließen: ${gegner.wert}", konflikt = krieg.id, zielSpieler = gegner))
                }
            }
        }
    }

    private fun truppenEintraege(zustand: SpielZustand, spieler: SpielerId, phase: ZugPhase): List<AktionsMenueEintrag> {
        if (phase != ZugPhase.Epizug) return emptyList()
        return zustand.karte?.belegung?.kriegseinheiten.orEmpty().asSequence()
            .filter { it.besitzer == spieler }.groupBy { it.typ to it.position }.entries
            .sortedWith(compareBy({ it.key.first.name }, { it.key.second.anfang.y }, { it.key.second.anfang.x }, { it.key.second.ende.y }, { it.key.second.ende.x }))
            .map { (schluessel, einheiten) ->
                val ids = einheiten.map { it.id }.sorted()
                AktionsMenueEintrag("stapel:${schluessel.first}:${ids.joinToString(",")}", AktionsMenueAktion.TRUPPENSTAPEL_AUSWAEHLEN, "${schluessel.first}: ${ids.size} Einheit(en)", einheitenIds = ids)
            }.toList()
    }

    private fun zugEintraege(zustand: SpielZustand, spieler: SpielerId): List<AktionsMenueEintrag> {
        val zug = zustand.zugStatus ?: return emptyList()
        if (zug.spieler != spieler) return emptyList()
        if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) return emptyList()
        if (zug.phase == ZugPhase.Epizug) return listOf(AktionsMenueEintrag("zug-ende", AktionsMenueAktion.ZUG_BEENDEN, "Zug beenden"))
        if (!zug.prozug.begonnen) return listOf(AktionsMenueEintrag("prozug-beginn", AktionsMenueAktion.PROZUG_BEGINNEN, "Prozug beginnen"))
        return buildList {
            ProzugAuswertung.plan(zustand)?.produktionsStandorte.orEmpty()
                .filter { it.verbleibendeLaeufe > 0 && it.mitBestandMoeglicheLaeufe > 0 }.forEach { standort ->
                    add(AktionsMenueEintrag("verarbeitung:${standort.standort.feld}", AktionsMenueAktion.VERARBEITUNG_AUSFUEHREN, "Verarbeitung ausführen", feld = standort.standort.feld))
                }
            zug.prozug.verwaltungsVerpflichtungen.filter { it.id !in zug.prozug.versorgteStandorte }.forEach { verpflichtung ->
                add(AktionsMenueEintrag("versorgung:${verpflichtung.id.ecke}", AktionsMenueAktion.VERWALTUNGSSTANDORT_VERSORGEN, "Verwaltungsstandort versorgen", ecke = verpflichtung.id.ecke))
            }
            zug.prozug.verbindlichkeiten.filter { it.id !in zug.prozug.beglicheneVerbindlichkeiten }.forEach { verpflichtung ->
                add(AktionsMenueEintrag("verbindlichkeit:${verpflichtung.id}", AktionsMenueAktion.VERBINDLICHKEIT_BEGLLEICHEN, "Verbindlichkeit begleichen", verbindlichkeit = verpflichtung.id))
            }
            if (ProzugAuswertung.plan(zustand)?.kannErfolgreichAbschliessen == true) add(AktionsMenueEintrag("prozug-abschluss", AktionsMenueAktion.PROZUG_ABSCHLIESSEN, "Prozug abschließen"))
            if (ZahlungsfaehigkeitsAuswertung.plan(zustand, spieler).automatischeAbwicklungNoetig) add(AktionsMenueEintrag("insolvenz", AktionsMenueAktion.ZAHLUNGSUNFAEHIGKEIT_FESTSTELLEN, "Zahlungsunfähigkeit behandeln"))
        }
    }

    fun konkreteAktion(zustand: SpielZustand, eintrag: AktionsMenueEintrag, truppenZiel: KartenKante? = null): SpielAktion? {
        val spieler = zustand.aktiverSpieler ?: return null
        val zug = zustand.zugStatus ?: return null
        return when (eintrag.aktion) {
            AktionsMenueAktion.KRIEG_ERKLAEREN -> eintrag.zielSpieler?.let { SpielAktion.KriegErklaeren(spieler, it) }
            AktionsMenueAktion.KAPITULIEREN -> eintrag.konflikt?.let { SpielAktion.KriegKapitulieren(spieler, it) }
            AktionsMenueAktion.WAFFENSTILLSTAND_ANBIETEN -> if (eintrag.konflikt != null && eintrag.zielSpieler != null) SpielAktion.WaffenstillstandAnbieten(spieler, eintrag.konflikt, eintrag.zielSpieler) else null
            AktionsMenueAktion.WAFFENSTILLSTAND_ANNEHMEN -> if (eintrag.konflikt != null && eintrag.zielSpieler != null) SpielAktion.WaffenstillstandAnnehmen(spieler, eintrag.konflikt, eintrag.zielSpieler) else null
            AktionsMenueAktion.UNABHAENGIGER_FRIEDEN -> if (eintrag.konflikt != null && eintrag.zielSpieler != null) SpielAktion.UnabhaengigenFriedenSchliessen(spieler, eintrag.konflikt, eintrag.zielSpieler) else null
            AktionsMenueAktion.ALLIANZ_BEITRETEN -> if (eintrag.konflikt != null && eintrag.seite != null) SpielAktion.KriegsAllianzBeitreten(spieler, eintrag.konflikt, eintrag.seite) else null
            AktionsMenueAktion.FRIEDENSVERTRAG_ANNEHMEN -> eintrag.vertrag?.let { SpielAktion.FriedensvertragAnnehmen(spieler, de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId(it)) }
            AktionsMenueAktion.PROZUG_BEGINNEN -> SpielAktion.ProzugBeginnen(zug.zugId)
            AktionsMenueAktion.VERARBEITUNG_AUSFUEHREN -> eintrag.feld?.let { SpielAktion.VerarbeitungAusfuehren(zug.zugId, it) }
            AktionsMenueAktion.VERWALTUNGSSTANDORT_VERSORGEN -> eintrag.ecke?.let { SpielAktion.VerwaltungsstandortVersorgen(zug.zugId, it) }
            AktionsMenueAktion.VERBINDLICHKEIT_BEGLLEICHEN -> eintrag.verbindlichkeit?.let { SpielAktion.VerbindlichkeitBegleichen(zug.zugId, it) }
            AktionsMenueAktion.PROZUG_ABSCHLIESSEN -> SpielAktion.ProzugAbschliessen(zug.zugId)
            AktionsMenueAktion.ZAHLUNGSUNFAEHIGKEIT_FESTSTELLEN -> SpielAktion.ZahlungsunfaehigkeitFeststellen(spieler, zug.zugId)
            AktionsMenueAktion.ZUG_BEENDEN -> SpielAktion.ZugBeenden
            AktionsMenueAktion.TRUPPEN_BEWEGEN -> truppenZiel?.let { SpielAktion.KriegsEinheitenBewegen(spieler, eintrag.einheitenIds, it) }
            AktionsMenueAktion.TRUPPENSTAPEL_AUSWAEHLEN -> null
        }
    }
}

enum class AktionsMenueBereich { KONFLIKT, DIPLOMATIE, TRUPPEN, HANDEL, ANLEIHEN, ZUG, KI }
data class AktionsMenueUebersicht(val spieler: SpielerId, val phase: ZugPhase, val bereiche: List<AktionsMenueBereichStatus>)
data class AktionsMenueBereichStatus(val bereich: AktionsMenueBereich, val verfuegbar: Boolean, val anzahl: Int?)
enum class AktionsMenueAktion { KRIEG_ERKLAEREN, KAPITULIEREN, WAFFENSTILLSTAND_ANBIETEN, WAFFENSTILLSTAND_ANNEHMEN, UNABHAENGIGER_FRIEDEN, ALLIANZ_BEITRETEN, FRIEDENSVERTRAG_ANNEHMEN, TRUPPENSTAPEL_AUSWAEHLEN, TRUPPEN_BEWEGEN, PROZUG_BEGINNEN, VERARBEITUNG_AUSFUEHREN, VERWALTUNGSSTANDORT_VERSORGEN, VERBINDLICHKEIT_BEGLLEICHEN, PROZUG_ABSCHLIESSEN, ZAHLUNGSUNFAEHIGKEIT_FESTSTELLEN, ZUG_BEENDEN }
data class AktionsMenueEintrag(val id: String, val aktion: AktionsMenueAktion, val titel: String, val zielSpieler: SpielerId? = null, val konflikt: KriegId? = null, val vertrag: String? = null, val seite: KriegsSeite? = null, val einheitenIds: List<String> = emptyList(), val feld: de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld? = null, val ecke: de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke? = null, val verbindlichkeit: de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId? = null)
