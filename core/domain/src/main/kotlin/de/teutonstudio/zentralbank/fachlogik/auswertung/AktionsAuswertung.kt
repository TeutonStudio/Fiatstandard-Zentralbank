package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilArt
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.Friedensvertrag
import de.teutonstudio.zentralbank.fachlogik.modell.FriedensvertragId
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotStatus
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsSeite
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.ecken
import de.teutonstudio.zentralbank.fachlogik.modell.kanten
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AktionsRaum(
    val spieler: SpielerId,
    val aktionen: List<SpielAktion>,
    val aktionsSchemaVersion: Int = AKTUELLE_AKTIONS_SCHEMA_VERSION,
)

const val AKTUELLE_AKTIONS_SCHEMA_VERSION = 2

object AktionsAuswertung {
    private val sortierJson = Json {
        classDiscriminator = "art"
        encodeDefaults = true
    }

    fun erlaubteAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): AktionsRaum {
        if (
            zustand.ergebnis != null ||
            spieler in zustand.ausgeschiedeneSpieler ||
            zustand.aktiverSpieler != spieler
        ) return AktionsRaum(spieler = spieler, aktionen = emptyList())

        val kandidaten = if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
            rundeNullAktionen(zustand, spieler)
        } else {
            regulaereAktionen(zustand, spieler)
        }
        val pruefEngine = StandardSpielEngine()
        val erlaubt = kandidaten
            .distinct()
            .sortedBy(::aktionsSchluessel)
            .filter { aktion -> pruefEngine.pruefe(zustand, aktion).isSuccess }
        return AktionsRaum(spieler = spieler, aktionen = erlaubt)
    }

    fun aktionsSchluessel(aktion: SpielAktion): String =
        sortierJson.encodeToString(SpielAktion.serializer(), aktion)

    private fun regulaereAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val zug = zustand.zugStatus ?: return emptyList()
        if (zug.spieler != spieler) return emptyList()
        if (!zug.prozug.begonnen) return listOf(SpielAktion.ProzugBeginnen(zug.zugId))
        val gemeinsameAktionen = angebotsAktionen(zustand, spieler) +
            ressourcenTransferAktionen(zustand, spieler)
        return when (zug.phase) {
            ZugPhase.Prozug -> {
                val plan = ProzugAuswertung.plan(zustand)
                val zahlungsplan = ZahlungsfaehigkeitsAuswertung.plan(zustand, spieler)
                buildList {
                    plan?.produktionsStandorte
                        ?.filter { it.verbleibendeLaeufe > 0 && it.mitBestandMoeglicheLaeufe > 0 }
                        ?.forEach { standort ->
                            add(SpielAktion.VerarbeitungAusfuehren(zug.zugId, standort.standort.feld))
                        }
                    zug.prozug.verwaltungsVerpflichtungen
                        .filter { it.id !in zug.prozug.versorgteStandorte }
                        .forEach { add(SpielAktion.VerwaltungsstandortVersorgen(zug.zugId, it.id.ecke)) }
                    zug.prozug.verbindlichkeiten
                        .filter { it.id !in zug.prozug.beglicheneVerbindlichkeiten }
                        .forEach { add(SpielAktion.VerbindlichkeitBegleichen(zug.zugId, it.id)) }
                    if (plan?.kannErfolgreichAbschliessen == true) {
                        add(SpielAktion.ProzugAbschliessen(zug.zugId))
                    }
                    addAll(gemeinsameAktionen)
                    addAll(
                        anleihenAktionen(
                            zustand,
                            spieler,
                            emissionErlaubt = zahlungsplan.anleiheMoeglich,
                            aufstockungErlaubt = zahlungsplan.aufstockungMoeglich,
                        ),
                    )
                    addAll(aussenhandelsAktionen(zustand, spieler))
                    addAll(insolvenzAktionen(zustand, spieler))
                    addAll(konfliktRettungsAktionen(zustand, spieler))
                    if (zahlungsplan.automatischeAbwicklungNoetig) {
                        add(SpielAktion.ZahlungsunfaehigkeitFeststellen(spieler, zug.zugId))
                    }
                }
            }
            ZugPhase.Epizug -> buildList {
                addAll(gemeinsameAktionen)
                addAll(anleihenAktionen(zustand, spieler))
                addAll(kartenAktionen(zustand, spieler))
                addAll(konfliktAktionen(zustand, spieler))
                addAll(insolvenzAktionen(zustand, spieler))
                add(SpielAktion.ZugBeenden)
            }
        }
    }

    private fun angebotsAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> = buildList {
        zustand.handelsAngebote
            .filter { angebot -> angebot.status == HandelsAngebotStatus.OFFEN }
            .forEach { angebot ->
                if (angebot.anbieter == spieler) {
                    add(SpielAktion.HandelsangebotZurueckziehen(spieler, angebot.id))
                } else if (angebot.empfaenger == null || angebot.empfaenger == spieler) {
                    add(SpielAktion.HandelsangebotAnnehmen(spieler, angebot.id))
                    add(SpielAktion.HandelsangebotAblehnen(spieler, angebot.id))
                }
            }
        zustand.anleihenAngebote
            .filter { angebot -> angebot.status == HandelsAngebotStatus.OFFEN }
            .forEach { angebot ->
                if (angebot.anbieter == spieler) {
                    add(SpielAktion.AnleihenangebotZurueckziehen(spieler, angebot.id))
                } else if (angebot.empfaenger == null || angebot.empfaenger == spieler) {
                    add(SpielAktion.AnleihenangebotAnnehmen(spieler, angebot.id))
                    add(SpielAktion.AnleihenangebotAblehnen(spieler, angebot.id))
                }
            }
        val bestand = zustand.spieler.first { it.id == spieler }
        val empfaenger = zustand.spieler.map { it.id }
            .filter { it != spieler && it !in zustand.ausgeschiedeneSpieler }
        bestand.rohstoffe.entries
            .sortedBy { it.key.name }
            .filter { it.value > 0 }
            .forEach { (rohstoff, _) ->
                val preis = zustand.marktpreise[rohstoff]
                    ?.takeIf { it > Geld.NULL }
                    ?: Geld.mark(1)
                empfaenger.forEach { ziel ->
                    val bereitsOffen = zustand.handelsAngebote.any { angebot ->
                        angebot.status == HandelsAngebotStatus.OFFEN &&
                            angebot.anbieter == spieler && angebot.empfaenger == ziel &&
                            angebot.angeboteneRohstoffe == mapOf(rohstoff to 1) &&
                            angebot.geforderterGeldbetrag == preis
                    }
                    if (!bereitsOffen) {
                        add(
                            SpielAktion.HandelsangebotErstellen(
                                spieler = spieler,
                                empfaenger = ziel,
                                angeboteneRohstoffe = mapOf(rohstoff to 1),
                                geforderterGeldbetrag = preis,
                            ),
                        )
                    }
                }
            }
    }

    private fun anleihenAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
        emissionErlaubt: Boolean = true,
        aufstockungErlaubt: Boolean = true,
    ): List<SpielAktion> = buildList {
        val planFehlbetrag = ProzugAuswertung.plan(zustand)?.fehlendesGeld?.cent ?: 0L
        val kandidatenCent = buildSet {
            add(Geld.mark(10).cent)
            if (planFehlbetrag > 0L) {
                add(((planFehlbetrag + 99L) / 100L) * 100L)
            }
            zustand.anleihen.values.filter { it.emittent == spieler }.forEach { anleihe ->
                add(anleihe.nennwert.cent + Geld.mark(10).cent)
            }
        }.filter { it > 0L }.sorted()
        if (emissionErlaubt && AnleihenAuswertung.freieGeschaeftsbankPlaetze(zustand, spieler) > 0) {
            kandidatenCent.forEach { nennwertCent ->
                add(
                    SpielAktion.AnleiheEmittieren(
                        spieler = spieler,
                        nennwert = Geld.cent(nennwertCent),
                        zinsBasispunkte = zustand.leitzins.wert,
                        laufzeitRunden = 3,
                    ),
                )
            }
        }
        val bestand = zustand.spieler.first { it.id == spieler }
        val andere = zustand.spieler.map { it.id }
            .filter { it != spieler && it !in zustand.ausgeschiedeneSpieler }
        bestand.anleihen.sortedBy { it.wert }.forEach { anleiheId ->
            val nennwert = zustand.anleihen[anleiheId]?.nennwert ?: return@forEach
            andere.forEach { ziel ->
                add(SpielAktion.AnleihenangebotErstellen(spieler, ziel, anleiheId, nennwert))
            }
        }
        zustand.anleihen.values.filter { it.emittent == spieler }
            .sortedBy { it.id.wert }
            .forEach { anleihe ->
                add(SpielAktion.AnleiheFreiwilligZurueckkaufen(
                    spieler,
                    anleihe.id,
                    anleihe.nennwert,
                ))
                if (aufstockungErlaubt) {
                    add(SpielAktion.AnleiheAufstocken(
                        spieler = spieler,
                        alteAnleihe = anleihe.id,
                        neuerNennwert = anleihe.nennwert + Geld.mark(10),
                        zinsBasispunkte = maxOf(zustand.leitzins.wert, anleihe.zinsBasispunkte),
                        laufzeitRunden = maxOf(1, anleihe.laufzeitRunden),
                    ))
                }
            }
    }

    private fun aussenhandelsAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> = buildList {
        val karte = zustand.karte ?: return@buildList
        if (!de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
                .kannAussenhandelBetreiben(karte, spieler, zustand.konflikte)
        ) return@buildList
        val bestand = zustand.spieler.single { it.id == spieler }
        Rohstoff.entries.forEach { rohstoff ->
            val preis = zustand.marktpreise[rohstoff]?.takeIf { it > Geld.NULL } ?: return@forEach
            if (bestand.rohstoffe.getOrDefault(rohstoff, 0) > 0) {
                add(
                    SpielAktion.MitAuslandHandeln(
                        spieler,
                        rohstoff,
                        1,
                        preis,
                        de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt.EXPORT,
                    ),
                )
            }
            if (bestand.geldkonto >= preis) {
                add(
                    SpielAktion.MitAuslandHandeln(
                        spieler,
                        rohstoff,
                        1,
                        preis,
                        de.teutonstudio.zentralbank.fachlogik.ereignis.AussenhandelsArt.IMPORT,
                    ),
                )
            }
        }
    }

    private fun insolvenzAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val imKrieg = zustand.konflikte.any { spieler in it.teilnehmer }
        val herabstufbar = zustand.karte?.belegung?.ecken?.any {
            it.besitzer == spieler && it.typ != EckGebaeudeTyp.HAUPTBAHNHOF
        } == true
        return if (!imKrieg && herabstufbar) {
            listOf(SpielAktion.SchuldenstrichDurchfuehren(spieler))
        } else {
            emptyList()
        }
    }

    private fun konfliktRettungsAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> = zustand.konflikte
        .filter { spieler in it.teilnehmer }
        .sortedBy { it.id.wert }
        .map { SpielAktion.KriegKapitulieren(spieler, it.id) }

    private fun kartenAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val karte = zustand.karte ?: return emptyList()
        val felder = karte.gelaendefelder.map { it.position }.sortedMitKartenfeldern()
        val ecken = felder.flatMap { it.ecken() }.distinct().sorted()
        val kanten = felder.flatMap { it.kanten() }.distinct().sortedMitKanten()
        return buildList {
            ecken.forEach { ecke ->
                add(SpielAktion.EckGebaeudeBauen(spieler, ecke, EckGebaeudeTyp.BAHNHOF))
                add(SpielAktion.EckGebaeudeBauen(spieler, ecke, EckGebaeudeTyp.HAFEN))
            }
            karte.belegung.ecken.filter { it.besitzer == spieler }.forEach { belegung ->
                when (belegung.typ) {
                    EckGebaeudeTyp.BAHNHOF -> add(
                        SpielAktion.EckGebaeudeAufwerten(
                            spieler,
                            belegung.position,
                            EckGebaeudeTyp.GROSSBAHNHOF,
                        ),
                    )
                    EckGebaeudeTyp.HAFEN -> add(
                        SpielAktion.EckGebaeudeAufwerten(
                            spieler,
                            belegung.position,
                            EckGebaeudeTyp.GROSSHAFEN,
                        ),
                    )
                    else -> Unit
                }
                add(SpielAktion.BelegungAbreissen(spieler, KartenOrt.Ecke(belegung.position)))
            }
            kanten.forEach { kante -> add(SpielAktion.SchieneBauen(spieler, kante)) }
            karte.belegung.kanten.forEach { belegung ->
                add(SpielAktion.BelegungAbreissen(spieler, KartenOrt.Kante(belegung.position)))
            }
            val wirtschaftstypen = BauteilTyp.entries
                .filter { it.art == BauteilArt.WIRTSCHAFTSREGION }
            felder.forEach { feld ->
                wirtschaftstypen.forEach { typ ->
                    add(SpielAktion.AnlageErrichten(spieler, feld, FeldAnlage.Wirtschaftsregion(typ)))
                }
            }
            karte.belegung.felder.forEach { belegung ->
                add(SpielAktion.BelegungAbreissen(spieler, KartenOrt.Feld(belegung.position)))
            }
            val haefen = karte.belegung.ecken
                .filter { it.besitzer == spieler && it.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN) }
                .map { it.position }
                .sorted()
            haefen.forEachIndexed { index, hafenA ->
                haefen.drop(index + 1).forEach { hafenB ->
                    FrachtRichtung.entries.forEach { richtung ->
                        add(SpielAktion.SeewegEinrichten(spieler, hafenA, hafenB, richtung))
                    }
                }
            }
            karte.belegung.seewege.filter { it.besitzer == spieler }.forEach {
                add(SpielAktion.SeewegEntfernen(spieler, it.id))
            }
            kanten.forEach { kante ->
                KriegsEinheitTyp.entries.forEach { typ ->
                    add(SpielAktion.KriegsEinheitBauen(spieler, typ, kante))
                }
            }
            val eigeneEinheiten = karte.belegung.kriegseinheiten
                .filter { it.besitzer == spieler }
                .sortedBy { it.id }
            eigeneEinheiten.forEach { einheit ->
                kanten.filter { de.teutonstudio.zentralbank.fachlogik.modell.sindBenachbarteKanten(
                    einheit.position,
                    it,
                ) }.forEach { naechsteKante ->
                    add(SpielAktion.KriegsEinheitBewegen(spieler, einheit.id, naechsteKante))
                }
            }
            eigeneEinheiten.groupBy { it.typ to it.position }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<Pair<KriegsEinheitTyp, de.teutonstudio.zentralbank.fachlogik.modell.KartenKante>, *>>(
                        { it.key.first.name },
                        { it.key.second.anfang },
                        { it.key.second.ende },
                    ),
                )
                .forEach { (_, gruppe) ->
                    val ids = gruppe.map { it.id }.sorted()
                    if (ids.size < 2) return@forEach
                    val ziele = kanten.filter {
                        de.teutonstudio.zentralbank.fachlogik.modell.sindBenachbarteKanten(
                            gruppe.first().position,
                            it,
                        )
                    }
                    ids.teilmengenAbZwei().forEach { teilnehmer ->
                        ziele.forEach { ziel ->
                            add(SpielAktion.KriegsEinheitenBewegen(spieler, teilnehmer, ziel))
                        }
                    }
                }
            karte.belegung.ecken.filter { it.zustand == de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand.ZERSTOERT }
                .forEach { ruine ->
                    add(SpielAktion.VerwaltungsruineReparieren(spieler, ruine.position))
                    add(SpielAktion.VerwaltungsruineAbreissen(spieler, ruine.position))
                }
        }
    }

    private fun konfliktAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> = buildList {
        zustand.spieler.map { it.id }
            .filter { it != spieler && it !in zustand.ausgeschiedeneSpieler }
            .forEach { gegner -> add(SpielAktion.KriegErklaeren(spieler, gegner)) }
        zustand.friedensvertraege
            .filter {
                it.abgeschlossenInRunde == null && spieler in it.beteiligteSpieler &&
                    spieler !in it.angenommenVon
            }
            .sortedBy { it.id.wert }
            .forEach { add(SpielAktion.FriedensvertragAnnehmen(spieler, it.id)) }
        zustand.konflikte.sortedBy { it.id.wert }.forEach { konflikt ->
            if (spieler !in konflikt.teilnehmer) {
                KriegsSeite.entries.forEach { seite ->
                    add(SpielAktion.KriegsAllianzBeitreten(spieler, konflikt.id, seite))
                }
                return@forEach
            }
            val gegner = konflikt.teilnehmer
                .filter { konflikt.betrifft(spieler, it) }
                .sortedBy { it.wert }
            gegner.forEach { ziel ->
                add(SpielAktion.WaffenstillstandAnbieten(spieler, konflikt.id, ziel))
                if (konflikt.waffenstillstandsAngebote.any { it.von == ziel && it.an == spieler }) {
                    add(SpielAktion.WaffenstillstandAnnehmen(spieler, konflikt.id, ziel))
                }
                add(SpielAktion.UnabhaengigenFriedenSchliessen(spieler, konflikt.id, ziel))
            }
            add(SpielAktion.KriegKapitulieren(spieler, konflikt.id))
            val offenerVertrag = zustand.friedensvertraege.any {
                it.krieg == konflikt.id && it.abgeschlossenInRunde == null
            }
            if (!offenerVertrag) {
                val alle = konflikt.teilnehmer.sortedBy { it.wert }.toCollection(linkedSetOf())
                val eigeneSeite = when (konflikt.seiteVon(spieler)) {
                    KriegsSeite.AGGRESSOREN -> konflikt.aggressoren
                    KriegsSeite.VERTEIDIGER -> konflikt.verteidiger
                    null -> emptySet()
                }.sortedBy { it.wert }.toCollection(linkedSetOf())
                val andereSeite = (alle - eigeneSeite).sortedBy { it.wert }
                    .toCollection(linkedSetOf())
                val id = FriedensvertragId("frieden-${zustand.naechsteFriedensvertragNummer}")
                add(SpielAktion.FriedensvertragVorschlagen(
                    spieler,
                    Friedensvertrag(
                        id = id,
                        krieg = konflikt.id,
                        beteiligteSpieler = alle,
                        unentschiedeneTeilnehmer = alle,
                        ausscheidendeTeilnehmer = alle,
                        angenommenVon = setOf(spieler),
                    ),
                ))
                add(SpielAktion.FriedensvertragVorschlagen(
                    spieler,
                    Friedensvertrag(
                        id = id,
                        krieg = konflikt.id,
                        beteiligteSpieler = alle,
                        gewinner = eigeneSeite,
                        verlierer = andereSeite,
                        ausscheidendeTeilnehmer = alle,
                        angenommenVon = setOf(spieler),
                    ),
                ))
                add(SpielAktion.FriedensvertragVorschlagen(
                    spieler,
                    Friedensvertrag(
                        id = id,
                        krieg = konflikt.id,
                        beteiligteSpieler = alle,
                        gewinner = andereSeite,
                        verlierer = eigeneSeite,
                        ausscheidendeTeilnehmer = alle,
                        angenommenVon = setOf(spieler),
                    ),
                ))
            }
        }
    }

    private fun ressourcenTransferAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val bestand = zustand.spieler.single { it.id == spieler }
        val verbuendete = zustand.konflikte.flatMap { konflikt ->
            when (konflikt.seiteVon(spieler)) {
                KriegsSeite.AGGRESSOREN -> konflikt.aggressoren - spieler
                KriegsSeite.VERTEIDIGER -> konflikt.verteidiger - spieler
                null -> emptySet()
            }
        }.filterNot { it in zustand.ausgeschiedeneSpieler }
            .distinct()
            .sortedBy { it.wert }
        return buildList {
            verbuendete.forEach { empfaenger ->
                bestand.rohstoffe.entries.sortedBy { it.key.name }
                    .filter { it.value > 0 }
                    .forEach { (rohstoff, _) ->
                        add(SpielAktion.RessourcenUebertragen(
                            spieler,
                            empfaenger,
                            rohstoffe = mapOf(rohstoff to 1),
                        ))
                    }
                if (bestand.geldkonto >= Geld.mark(1)) {
                    add(SpielAktion.RessourcenUebertragen(
                        spieler,
                        empfaenger,
                        geld = Geld.mark(1),
                    ))
                }
            }
        }
    }

    private fun rundeNullAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val karte = zustand.karte ?: return emptyList()
        val felder = karte.gelaendefelder.map { it.position }.sortedMitKartenfeldern()
        val ecken = felder.flatMap { it.ecken() }.distinct().sorted()
        val kanten = felder.flatMap { it.kanten() }.distinct().sortedMitKanten()
        val rest = zustand.rundeNullRestbestand?.get(spieler).orEmpty()
        return buildList {
            if (rest.getOrDefault(BauteilTyp.HAUPTBAHNHOF, 0) > 0 || rest.isEmpty()) {
                ecken.forEach { add(SpielAktion.HauptbahnhofPlatzieren(spieler, it)) }
            }
            mapOf(
                BauteilTyp.BAHNHOF to EckGebaeudeTyp.BAHNHOF,
                BauteilTyp.HAFEN to EckGebaeudeTyp.HAFEN,
            ).forEach { (bauteil, typ) ->
                if (rest.getOrDefault(bauteil, 0) > 0) {
                    ecken.forEach { add(SpielAktion.EckGebaeudeBauen(spieler, it, typ)) }
                }
            }
            if (rest.getOrDefault(BauteilTyp.EISENBAHNLINIE, 0) > 0) {
                kanten.forEach { add(SpielAktion.SchieneBauen(spieler, it)) }
            }
            rest.entries.filter { (typ, menge) ->
                menge > 0 && typ.art == BauteilArt.WIRTSCHAFTSREGION
            }.forEach { (typ, _) ->
                felder.forEach { feld ->
                    add(SpielAktion.AnlageErrichten(spieler, feld, FeldAnlage.Wirtschaftsregion(typ)))
                }
            }
        }
    }

    private fun List<de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld>.sortedMitKartenfeldern() =
        sortedWith(compareBy({ it.zeile }, { it.spalte }, { it.haelfte.name }))

    private fun List<de.teutonstudio.zentralbank.fachlogik.modell.KartenKante>.sortedMitKanten() =
        sortedWith(compareBy({ it.anfang.y }, { it.anfang.x }, { it.ende.y }, { it.ende.x }))

    private fun List<String>.teilmengenAbZwei(): List<List<String>> = buildList {
        fun sammeln(index: Int, aktuell: MutableList<String>) {
            if (index == this@teilmengenAbZwei.size) {
                if (aktuell.size >= 2) add(aktuell.toList())
                return
            }
            sammeln(index + 1, aktuell)
            aktuell += this@teilmengenAbZwei[index]
            sammeln(index + 1, aktuell)
            aktuell.removeAt(aktuell.lastIndex)
        }
        sammeln(0, mutableListOf())
    }.sortedWith(compareBy<List<String>>({ it.size }, { it.joinToString("\u0000") }))
}
