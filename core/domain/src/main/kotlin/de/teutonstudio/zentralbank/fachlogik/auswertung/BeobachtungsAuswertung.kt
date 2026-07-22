package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.beobachtung.*
import de.teutonstudio.zentralbank.fachlogik.modell.*

object BeobachtungsAuswertung {
    fun fuerSpieler(zustand: SpielZustand, spieler: SpielerId): SpielBeobachtung {
        require(zustand.spieler.any { it.id == spieler }) { "Unbekannter Spieler: ${spieler.wert}." }
        val karte = zustand.karte
        val spielerBeobachtungen = zustand.spieler.mapIndexed { index, eintrag ->
            spielerBeobachtung(zustand, eintrag, index)
        }
        return SpielBeobachtung(
            betrachtenderSpieler = spieler,
            runde = zustand.rundenzähler,
            zug = zustand.zugStatus?.let { zug ->
                ZugBeobachtung(
                    zugId = zug.zugId,
                    aktiverSpieler = zug.spieler,
                    phase = zug.phase,
                    prozugBegonnen = zug.prozug.begonnen,
                    prozugAbgeschlossen = zug.prozug.erfolgreichAbgeschlossen,
                )
            },
            spieler = spielerBeobachtungen,
            markt = MarktBeobachtung(
                preise = Rohstoff.entries.map { rohstoff ->
                    RohstoffPreisBeobachtung(rohstoff, zustand.marktpreise[rohstoff] ?: Geld.NULL)
                },
                leitzinsBasispunkte = zustand.leitzins.wert,
                bankkonto = zustand.bankkonto,
                auslandskonto = zustand.auslandskonto,
            ),
            karte = karte?.let { kartenBeobachtung(zustand, it) },
            angebote = alleAngebote(zustand),
            kriege = zustand.konflikte.sortedBy { it.id.wert },
            friedensvertraege = zustand.friedensvertraege.sortedBy { it.id.wert },
            belagerungen = zustand.belagerungen.sortedBy { it.standort },
            schuldenstriche = zustand.schuldenstriche,
            zentralbankGeldschoepfungen = zustand.zentralbankGeldschoepfungen,
            ergebnis = zustand.ergebnis,
        )
    }

    private fun spielerBeobachtung(
        zustand: SpielZustand,
        spieler: Spieler,
        sitz: Int,
    ): SpielerBeobachtung {
        val karte = zustand.karte
        val verwaltung = karte?.belegung?.ecken.orEmpty()
            .filter { it.besitzer == spieler.id && it.zustand != BauwerkZustand.ZERSTOERT }
            .sortedBy { it.position }
        val erreichbar = karte?.let {
            ErreichbarkeitsAuswertung.erreichbareWirtschaftsstandorte(
                it,
                spieler.id,
                zustand.konflikte,
            )
        }.orEmpty().sortedMitKartenfeldern()
        val abbauErtrag = karte?.let {
            KartenAuswertung.abbauErtrag(it, spieler.id, zustand.konflikte)
        }.orEmpty()
        val produktionsErtrag = karte?.let {
            KartenAuswertung.verarbeitungsStandorte(it, spieler.id, zustand.konflikte)
                .flatMap { standort -> standort.ertragJeLauf.entries.map { eintrag ->
                    eintrag.key to eintrag.value * standort.maximaleLaeufe
                } }
                .groupBy(Pair<Rohstoff, Int>::first, Pair<Rohstoff, Int>::second)
                .mapValues { it.value.sum() }
        }.orEmpty()
        val gesamtErtrag = (abbauErtrag.keys + produktionsErtrag.keys).associateWith { rohstoff ->
            abbauErtrag.getOrDefault(rohstoff, 0) + produktionsErtrag.getOrDefault(rohstoff, 0)
        }
        val versorgung = karte?.let {
            KartenAuswertung.verwaltungsStandorte(it, spieler.id)
                .flatMap { standort -> standort.bedarf.entries }
                .groupBy(Map.Entry<Rohstoff, Int>::key, Map.Entry<Rohstoff, Int>::value)
                .mapValues { it.value.sum() }
        }.orEmpty()
        val einheiten = karte?.belegung?.kriegseinheiten.orEmpty()
            .filter { it.besitzer == spieler.id }
            .sortedBy { it.id }
            .map { KriegsEinheitBeobachtung(it.id, it.typ, it.besitzer, it.ort) }
        val kriege = zustand.konflikte.filter { spieler.id in it.teilnehmer }.sortedBy { it.id.wert }
        val offeneAnleihen = zustand.anleihen.values.filter { it.emittent == spieler.id }
            .sortedBy { it.id.wert }
            .map { anleihe ->
                AnleiheBeobachtung(
                    id = anleihe.id,
                    emittent = anleihe.emittent,
                    glaeubiger = requireNotNull(AnleihenAuswertung.besitzer(zustand, anleihe.id)),
                    nennwert = anleihe.nennwert,
                    rueckkaufsbetrag = anleihe.nennwert,
                    zinsBasispunkte = anleihe.zinsBasispunkte,
                    laufzeitRunden = anleihe.laufzeitRunden,
                    emissionsRunde = anleihe.emissionsRunde,
                    faelligkeitsRunde = anleihe.faelligkeitsRunde,
                    geleisteteZinszahlungen = anleihe.geleisteteZinszahlungen,
                )
            }
        val handel = buildList {
            zustand.handelsAngebote.filter {
                it.status != HandelsAngebotStatus.OFFEN &&
                    (it.anbieter == spieler.id || it.empfaenger == spieler.id)
            }.forEach {
                add(AbgeschlossenesHandelsgeschaeftBeobachtung(
                    "handel-${it.id.wert}",
                    "ROHSTOFF",
                    if (it.anbieter == spieler.id) it.empfaenger else it.anbieter,
                    it.status.name,
                ))
            }
            zustand.anleihenAngebote.filter {
                it.status != HandelsAngebotStatus.OFFEN &&
                    (it.anbieter == spieler.id || it.empfaenger == spieler.id)
            }.forEach {
                add(AbgeschlossenesHandelsgeschaeftBeobachtung(
                    "anleihe-${it.id.wert}",
                    "ANLEIHE",
                    if (it.anbieter == spieler.id) it.empfaenger else it.anbieter,
                    it.status.name,
                ))
            }
        }.sortedBy { it.id }
        return SpielerBeobachtung(
            sitzPosition = sitz,
            id = spieler.id,
            name = spieler.name,
            spielstil = spieler.spielstil,
            aktiv = spieler.id !in zustand.ausgeschiedeneSpieler,
            ausgeschieden = spieler.id in zustand.ausgeschiedeneSpieler,
            amZug = zustand.aktiverSpieler == spieler.id,
            marktwert = MarktAuswertung.spielerMarktwert(zustand, spieler.id),
            rohstoffe = Rohstoff.entries.map {
                RohstoffBestandBeobachtung(it, spieler.rohstoffe.getOrDefault(it, 0))
            },
            geld = spieler.geldkonto,
            anleihenImBesitz = spieler.anleihen.sortedBy { it.wert },
            offeneEigeneAnleihen = offeneAnleihen,
            bauteile = BauteilTyp.entries.map {
                BauteilBestandBeobachtung(it, spieler.bauteile.getOrDefault(it, 0))
            },
            gesamtertragJeRunde = rohstoffListe(gesamtErtrag),
            produktionsmengenJeRohstoff = rohstoffListe(produktionsErtrag),
            gesamteVersorgungskosten = rohstoffListe(versorgung),
            abgeschlosseneHandelsgeschaefte = handel,
            kontrollierteVerwaltungsstandorte = verwaltung.map { it.position },
            erreichbareWirtschaftsstandorte = erreichbar,
            einheiten = einheiten,
            kriege = kriege.map { it.id },
            allianzen = kriege.filter { krieg ->
                krieg.aggressoren.size > 1 && spieler.id in krieg.aggressoren ||
                    krieg.verteidiger.size > 1 && spieler.id in krieg.verteidiger
            }.map { it.id },
            waffenstillstaende = kriege.flatMap { it.waffenstillstaende }
                .filter { it.enthaelt(spieler.id) }
                .distinct()
                .sortedWith(compareBy({ it.erster.wert }, { it.zweiter.wert })),
            kapitulationen = zustand.friedensvertraege.filter {
                spieler.id in it.kapitulationen
            }.sortedBy { it.id.wert }.map { it.id },
            friedensvertraege = zustand.friedensvertraege.filter {
                spieler.id in it.beteiligteSpieler
            }.sortedBy { it.id.wert }.map { it.id },
        )
    }

    private fun kartenBeobachtung(zustand: SpielZustand, karte: Spielkarte): KartenBeobachtung {
        val aktiveSpieler = zustand.spieler.map { it.id }.filterNot { it in zustand.ausgeschiedeneSpieler }
        val alleFelder = karte.hexagon.felder()
        val alleKnoten = alleFelder.flatMap { it.ecken() }.distinct().sorted()
        val alleKanten = alleFelder.flatMap { it.kanten() }.distinct().sortedWith(
            compareBy({ it.anfang }, { it.ende }),
        )
        val kontrollen = karte.belegung.felder.associate { feld ->
            feld.position to ErreichbarkeitsAuswertung.kontrollierendeSpieler(
                karte,
                feld.position,
                aktiveSpieler,
                zustand.konflikte,
            ).sortedBy { it.wert }
        }
        val blockaden = aktiveSpieler.flatMap { blockiert ->
            karte.belegung.kriegseinheiten.filter { einheit ->
                einheit.besitzer != blockiert && zustand.konflikte.any {
                    it.betrifft(blockiert, einheit.besitzer)
                }
            }.map { einheit ->
                BlockadeBeobachtung(
                    blockierterSpieler = blockiert,
                    kante = einheit.position,
                    durchSpieler = listOf(einheit.besitzer),
                    art = if (einheit.typ == KriegsEinheitTyp.PANZER) "LAND" else "SEE",
                )
            }
        }.distinct().sortedWith(compareBy({ it.blockierterSpieler.wert }, { it.kante.anfang }, { it.kante.ende }))
        return KartenBeobachtung(
            id = karte.id,
            name = karte.name,
            hexagon = karte.hexagon,
            felder = alleFelder.map { feld ->
                val gelaende = karte.landNachPosition[feld]
                TopologieFeldBeobachtung(
                    position = feld,
                    gelaende = gelaende,
                    wasser = gelaende == null,
                    spezialfeld = karte.spezialfelder.firstOrNull { feld in it.positionen }?.typ,
                )
            },
            knoten = alleKnoten,
            kanten = alleKanten.map { kante ->
                val nachbarn = angrenzendeFelder(kante).filter(karte::enthaeltFeld)
                TopologieKanteBeobachtung(
                    position = kante,
                    landkante = nachbarn.any { it in karte.landNachPosition },
                    seekante = nachbarn.any { it !in karte.landNachPosition },
                )
            },
            gelaendefelder = karte.gelaendefelder.sortedWith(
                compareBy({ it.position.zeile }, { it.position.spalte }, { it.position.haelfte.name }),
            ),
            spezialfelder = karte.spezialfelder.sortedWith(
                compareBy({ it.mittelpunkt }, { it.typ.name }),
            ),
            eckBauwerke = karte.belegung.ecken.sortedBy { it.position }.map {
                EckBauwerkBeobachtung(it.position, it.typ, it.besitzer, it.zustand)
            },
            handelslinien = karte.belegung.kanten.sortedWith(
                compareBy({ it.position.anfang }, { it.position.ende }),
            ).map { HandelslinieBeobachtung(it.position, it.erbautVon, it.zustand) },
            feldAnlagen = karte.belegung.felder.sortedWith(
                compareBy({ it.position.zeile }, { it.position.spalte }, { it.position.haelfte.name }),
            ).map { FeldAnlageBeobachtung(it.position, it.anlage, it.zustand, kontrollen[it.position].orEmpty()) },
            seewege = karte.belegung.seewege.sortedBy { it.id }.map { seeweg ->
                val blockierte = ErreichbarkeitsAuswertung.blockierteHaefen(
                    karte,
                    seeweg.besitzer,
                    zustand.konflikte,
                )
                val eigeneIntakteHaefen = karte.belegung.ecken.asSequence()
                    .filter {
                        it.besitzer == seeweg.besitzer && it.zustand == BauwerkZustand.INTAKT &&
                            it.typ in setOf(EckGebaeudeTyp.HAFEN, EckGebaeudeTyp.GROSSHAFEN)
                    }
                    .mapTo(mutableSetOf()) { it.position }
                SeewegBeobachtung(
                    seeweg.id,
                    seeweg.hafenA,
                    seeweg.hafenB,
                    seeweg.besitzer,
                    seeweg.richtung,
                    aktiv = seeweg.hafenA in eigeneIntakteHaefen &&
                        seeweg.hafenB in eigeneIntakteHaefen &&
                        seeweg.hafenA !in blockierte && seeweg.hafenB !in blockierte,
                )
            },
            kriegseinheiten = karte.belegung.kriegseinheiten.sortedBy { it.id }.map {
                KriegsEinheitBeobachtung(it.id, it.typ, it.besitzer, it.ort)
            },
            erreichbarkeit = aktiveSpieler.sortedBy { it.wert }.map { id ->
                ErreichbarkeitBeobachtung(
                    id,
                    ErreichbarkeitsAuswertung.erreichbareEcken(karte, id, zustand.konflikte).sorted(),
                    ErreichbarkeitsAuswertung.erreichbareWirtschaftsstandorte(
                        karte,
                        id,
                        zustand.konflikte,
                    ).sortedMitKartenfeldern(),
                )
            },
            blockaden = blockaden,
        )
    }

    private fun alleAngebote(zustand: SpielZustand): List<AngebotBeobachtung> = buildList {
        zustand.handelsAngebote.forEach { angebot ->
            add(AngebotBeobachtung.RohstoffHandel(
                angebot.id,
                angebot.anbieter,
                angebot.empfaenger,
                rohstoffListe(angebot.angeboteneRohstoffe),
                rohstoffListe(angebot.geforderteRohstoffe),
                angebot.angebotenerGeldbetrag,
                angebot.geforderterGeldbetrag,
                angebot.status.name,
            ))
        }
        zustand.anleihenAngebote.forEach { angebot ->
            add(AngebotBeobachtung.AnleihenHandel(
                angebot.id,
                angebot.anbieter,
                angebot.empfaenger,
                angebot.anleihe,
                angebot.preis,
                angebot.status.name,
            ))
        }
    }.sortedBy { angebot -> when (angebot) {
        is AngebotBeobachtung.RohstoffHandel -> "H-${angebot.id.wert}"
        is AngebotBeobachtung.AnleihenHandel -> "A-${angebot.id.wert}"
    } }

    private fun rohstoffListe(mengen: Map<Rohstoff, Int>): List<RohstoffBestandBeobachtung> =
        Rohstoff.entries.map { RohstoffBestandBeobachtung(it, mengen.getOrDefault(it, 0)) }

    private fun Collection<KartenFeld>.sortedMitKartenfeldern(): List<KartenFeld> = sortedWith(
        compareBy({ it.zeile }, { it.spalte }, { it.haelfte.name }),
    )
}
