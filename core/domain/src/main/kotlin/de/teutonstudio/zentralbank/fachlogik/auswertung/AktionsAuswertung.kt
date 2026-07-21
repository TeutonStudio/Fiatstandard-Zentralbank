package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.engine.StandardSpielEngine
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilArt
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FrachtRichtung
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.HandelsAngebotStatus
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
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
)

object AktionsAuswertung {
    private const val MAX_KARTENOPTIONEN_PRO_TYP = 128
    private val sortierJson = Json { encodeDefaults = true }

    fun erlaubteAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): AktionsRaum {
        if (
            zustand.ergebnis != null ||
            spieler in zustand.ausgeschiedeneSpieler ||
            zustand.aktiverSpieler != spieler
        ) return AktionsRaum(spieler, emptyList())

        val kandidaten = if (zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
            rundeNullAktionen(zustand, spieler)
        } else {
            regulaereAktionen(zustand, spieler)
        } + SpielAktion.Aufgeben(spieler)
        val pruefEngine = StandardSpielEngine()
        val erlaubt = kandidaten
            .distinct()
            .sortedBy(::aktionsSchluessel)
            .filter { aktion -> pruefEngine.pruefe(zustand, aktion).isSuccess }
        return AktionsRaum(spieler, erlaubt)
    }

    fun aktionsSchluessel(aktion: SpielAktion): String =
        (aktion::class.qualifiedName ?: aktion::class.simpleName.orEmpty()) + ":" +
            sortierJson.encodeToString(SpielAktion.serializer(), aktion)

    private fun regulaereAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val zug = zustand.zugStatus ?: return emptyList()
        if (zug.spieler != spieler) return emptyList()
        if (!zug.prozug.begonnen) return listOf(SpielAktion.ProzugBeginnen(zug.zugId))
        val gemeinsameAktionen = angebotsAktionen(zustand, spieler)
        return when (zug.phase) {
            ZugPhase.Prozug -> {
                val plan = ProzugAuswertung.plan(zustand)
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
                }
            }
            ZugPhase.Epizug -> buildList {
                addAll(gemeinsameAktionen)
                addAll(anleihenAktionen(zustand, spieler))
                addAll(kartenAktionen(zustand, spieler))
                addAll(konfliktAktionen(zustand, spieler))
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

    private fun anleihenAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> = buildList {
        listOf(10L, 50L, 100L).forEach { nennwert ->
            add(
                SpielAktion.AnleiheEmittieren(
                    spieler = spieler,
                    nennwert = Geld.mark(nennwert),
                    zinsBasispunkte = zustand.leitzins.wert,
                    laufzeitRunden = 3,
                ),
            )
        }
        val bestand = zustand.spieler.first { it.id == spieler }
        val andere = zustand.spieler.map { it.id }
            .filter { it != spieler && it !in zustand.ausgeschiedeneSpieler }
        bestand.anleihen.sortedBy { it.wert }.forEach { anleiheId ->
            val nennwert = zustand.anleihen[anleiheId]?.nennwert ?: return@forEach
            andere.forEach { ziel ->
                add(SpielAktion.AnleihenangebotErstellen(spieler, ziel, anleiheId, nennwert))
            }
            val anleihe = zustand.anleihen[anleiheId]
            if (anleihe?.emittent == spieler) {
                add(SpielAktion.AnleiheFreiwilligZurueckkaufen(spieler, anleiheId, nennwert))
            }
        }
    }

    private fun kartenAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val karte = zustand.karte ?: return emptyList()
        val felder = karte.gelaendefelder.map { it.position }.sortedMitKartenfeldern()
        val ecken = felder.flatMap { it.ecken() }.distinct().sorted().take(MAX_KARTENOPTIONEN_PRO_TYP)
        val kanten = felder.flatMap { it.kanten() }.distinct().sortedMitKanten()
            .take(MAX_KARTENOPTIONEN_PRO_TYP)
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
                .take(4)
            felder.take(MAX_KARTENOPTIONEN_PRO_TYP).forEach { feld ->
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
            karte.belegung.kriegseinheiten.filter { it.besitzer == spieler }.forEach { einheit ->
                kanten.forEach { naechsteKante ->
                    add(SpielAktion.KriegsEinheitBewegen(spieler, einheit.id, naechsteKante))
                }
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
        zustand.konflikte.filter { konflikt ->
            konflikt.spielerA == spieler || konflikt.spielerB == spieler
        }.forEach { konflikt ->
            val gegner = if (konflikt.spielerA == spieler) konflikt.spielerB else konflikt.spielerA
            add(SpielAktion.FriedenSchliessen(spieler, gegner))
        }
    }

    private fun rundeNullAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        val karte = zustand.karte ?: return emptyList()
        val felder = karte.gelaendefelder.map { it.position }.sortedMitKartenfeldern()
        val ecken = felder.flatMap { it.ecken() }.distinct().sorted().take(MAX_KARTENOPTIONEN_PRO_TYP)
        val kanten = felder.flatMap { it.kanten() }.distinct().sortedMitKanten()
            .take(MAX_KARTENOPTIONEN_PRO_TYP)
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
                felder.take(MAX_KARTENOPTIONEN_PRO_TYP).forEach { feld ->
                    add(SpielAktion.AnlageErrichten(spieler, feld, FeldAnlage.Wirtschaftsregion(typ)))
                }
            }
        }
    }

    private fun List<de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld>.sortedMitKartenfeldern() =
        sortedWith(compareBy({ it.zeile }, { it.spalte }, { it.haelfte.name }))

    private fun List<de.teutonstudio.zentralbank.fachlogik.modell.KartenKante>.sortedMitKanten() =
        sortedWith(compareBy({ it.anfang.y }, { it.anfang.x }, { it.ende.y }, { it.ende.x }))
}
