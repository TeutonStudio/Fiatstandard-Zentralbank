package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.KartenAenderungsGrund
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.AnlagenZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauwerkZustand
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.EckBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.FeldAnlage
import de.teutonstudio.zentralbank.fachlogik.modell.FeldBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KantenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenOrt
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.KriegsEinheitTyp
import de.teutonstudio.zentralbank.fachlogik.modell.SeewegBelegung
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.angrenzendeFelder
import de.teutonstudio.zentralbank.fachlogik.modell.enthaeltFeld
import de.teutonstudio.zentralbank.fachlogik.modell.kantenAbstand

internal object KartenRegelwerk {
    fun hauptbahnhofPlatzieren(
        zustand: SpielZustand,
        ereignis: SpielEreignis.HauptbahnhofPlatziert,
    ): SpielZustand {
        val karte = requireNotNull(zustand.karte) { "Der Spielstand besitzt keine Spielkarte." }
        require(zustand.spielabschnitt == Spielabschnitt.RUNDE_NULL) {
            "Hauptbahnhöfe werden nur in Runde 0 platziert."
        }
        require(ereignis.spieler == zustand.aktiverSpieler) {
            "Nur der aktive Spieler darf seinen Hauptbahnhof platzieren."
        }
        require(ereignis.spieler in zustand.spieler.map { it.id }) {
            "Unbekannter Spieler: ${ereignis.spieler.wert}."
        }
        require(karte.belegung.ecken.none { it.position == ereignis.ecke }) {
            "Die gewählte Ecke ist bereits belegt."
        }
        require(karte.belegung.ecken.none {
            it.typ == EckGebaeudeTyp.HAUPTBAHNHOF && it.besitzer == ereignis.spieler
        }) {
            "${ereignis.spieler.wert} hat bereits einen Hauptbahnhof."
        }
        karte.belegung.ecken
            .filter { it.typ == EckGebaeudeTyp.HAUPTBAHNHOF }
            .forEach { bestehend ->
                val abstand = kantenAbstand(ereignis.ecke, bestehend.position, maximal = 2)
                require(abstand == null || abstand >= 3) {
                    "Hauptbahnhöfe müssen mindestens drei Kanten Abstand halten."
                }
            }

        val neueEcken = karte.belegung.ecken + EckBelegung(
            position = ereignis.ecke,
            typ = EckGebaeudeTyp.HAUPTBAHNHOF,
            besitzer = ereignis.spieler,
        )
        val neueKarte = karte.copy(
            belegung = karte.belegung.copy(ecken = neueEcken.eckenSortiert()),
        )
        val ohneHauptbahnhof = zustand.spieler.filter { spieler ->
            neueEcken.none { belegung ->
                belegung.typ == EckGebaeudeTyp.HAUPTBAHNHOF && belegung.besitzer == spieler.id
            }
        }
        val fertig = ohneHauptbahnhof.isEmpty()
        val naechster = if (fertig) zustand.spieler.firstOrNull()?.id else {
            val index = zustand.spieler.indexOfFirst { it.id == ereignis.spieler }
            (1..zustand.spieler.size)
                .asSequence()
                .map { versatz -> zustand.spieler[(index + versatz) % zustand.spieler.size].id }
                .first { kandidat -> kandidat in ohneHauptbahnhof.map { it.id } }
        }
        return zustand.copy(
            karte = neueKarte,
            spielabschnitt = if (fertig) Spielabschnitt.REGULAER else Spielabschnitt.RUNDE_NULL,
            aktiverSpieler = naechster,
            zugStatus = zustand.zugStatus?.copy(spieler = naechster ?: zustand.zugStatus.spieler),
        )
    }

    fun eckGebaeudeBauen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.EckGebaeudeGebaut,
    ): SpielZustand {
        require(ereignis.typ != EckGebaeudeTyp.HAUPTBAHNHOF) {
            "Ein Hauptbahnhof braucht die Runde-0-Platzierung."
        }
        val karte = regulaereKarte(zustand)
        require(karte.belegung.ecken.none { it.position == ereignis.ecke }) {
            "Die gewählte Ecke ist bereits belegt."
        }
        if (ereignis.typ == EckGebaeudeTyp.HAFEN || ereignis.typ == EckGebaeudeTyp.GROSSHAFEN) {
            require(KartenAuswertung.istHafenstandort(karte, ereignis.ecke)) {
                "Ein Hafen braucht mindestens zwei Wasser- und zwei angrenzende Geländefelder."
            }
        }
        val nachKosten = bucheBauteil(zustand, ereignis.spieler, ereignis.typ.bauteilTyp(), 1)
        val aktuelleKarte = requireNotNull(nachKosten.karte)
        return nachKosten.copy(
            karte = aktuelleKarte.copy(
                belegung = aktuelleKarte.belegung.copy(
                    ecken = (aktuelleKarte.belegung.ecken + EckBelegung(
                        position = ereignis.ecke,
                        typ = ereignis.typ,
                        besitzer = ereignis.spieler,
                    )).eckenSortiert(),
                ),
            ),
        )
    }

    fun eckGebaeudeAufwerten(
        zustand: SpielZustand,
        ereignis: SpielEreignis.EckGebaeudeAufgewertet,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        val bisher = karte.belegung.eckenNachPosition[ereignis.ecke]
            ?: error("Auf der gewählten Ecke steht kein Gebäude.")
        require(bisher.besitzer == ereignis.spieler) { "Nur der Besitzer darf aufwerten." }
        require(bisher.zustand == BauwerkZustand.INTAKT) { "Nur intakte Gebäude können aufwerten." }
        require(
            bisher.typ == EckGebaeudeTyp.BAHNHOF && ereignis.zu == EckGebaeudeTyp.GROSSBAHNHOF ||
                bisher.typ == EckGebaeudeTyp.HAFEN && ereignis.zu == EckGebaeudeTyp.GROSSHAFEN,
        ) { "Diese Gebäudeaufwertung ist nicht erlaubt." }
        val nachKosten = bucheBauteil(zustand, ereignis.spieler, ereignis.zu.bauteilTyp(), 1)
        val nachTausch = bisher.typ.bauteilTyp()?.let { alt ->
            bucheBauteil(nachKosten, ereignis.spieler, alt, -1, kostenBuchen = false)
        } ?: nachKosten
        val aktuelleKarte = requireNotNull(nachTausch.karte)
        return nachTausch.copy(
            karte = aktuelleKarte.copy(
                belegung = aktuelleKarte.belegung.copy(
                    ecken = aktuelleKarte.belegung.ecken.map { belegung ->
                        if (belegung.position == ereignis.ecke) belegung.copy(typ = ereignis.zu)
                        else belegung
                    }.eckenSortiert(),
                ),
            ),
        )
    }

    fun schieneBauen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.SchieneGebaut,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        require(karte.belegung.kanten.none { it.position == ereignis.kante }) {
            "Die gewählte Kante ist bereits belegt."
        }
        val nachbarn = angrenzendeFelder(ereignis.kante)
        require(nachbarn.size == 2 && nachbarn.all { it in karte.landNachPosition }) {
            "Eine Handelslinie ist nur zwischen zwei Geländefeldern erlaubt."
        }
        val probeKarte = karte.copy(
            belegung = karte.belegung.copy(
                kanten = karte.belegung.kanten + KantenBelegung(ereignis.kante),
            ),
        )
        require(ereignis.spieler in KartenAuswertung.verbundeneSpieler(probeKarte, ereignis.kante)) {
            "Eine Handelslinie muss mit dem eigenen Hauptbahnhof oder Liniennetz verbunden sein."
        }
        val nachKosten = bucheKosten(zustand, ereignis.spieler, BauteilTyp.EISENBAHNLINIE)
        val aktuelleKarte = requireNotNull(nachKosten.karte)
        return nachKosten.copy(
            karte = aktuelleKarte.copy(
                belegung = aktuelleKarte.belegung.copy(
                    kanten = (aktuelleKarte.belegung.kanten +
                        KantenBelegung(position = ereignis.kante)).kantenSortiert(),
                ),
            ),
        )
    }

    fun neutraleAnlageErrichten(
        zustand: SpielZustand,
        ereignis: SpielEreignis.NeutraleAnlageErrichtet,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        require(ereignis.feld in karte.landNachPosition) {
            "Eine neutrale Anlage darf nur auf einem Geländefeld stehen."
        }
        require(karte.belegung.felder.none { it.position == ereignis.feld }) {
            "Das gewählte Feld ist bereits belegt."
        }
        val aktuelleKarte = requireNotNull(zustand.karte)
        return zustand.copy(
            karte = aktuelleKarte.copy(
                belegung = aktuelleKarte.belegung.copy(
                    felder = (aktuelleKarte.belegung.felder + FeldBelegung(
                        position = ereignis.feld,
                        anlage = ereignis.anlage,
                    )).felderSortiert(),
                ),
            ),
        )
    }

    fun belegungEntfernen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KartenBelegungEntfernt,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        return when (val ort = ereignis.ort) {
            is KartenOrt.Ecke -> {
                val bisher = karte.belegung.eckenNachPosition[ort.position]
                    ?: error("Die gewählte Ecke ist nicht belegt.")
                pruefeEntfernung(ereignis.spieler, bisher.besitzer, ereignis.grund)
                var nachBestand = bisher.besitzer?.let { besitzer ->
                    bisher.typ.bauteilTyp()?.let { typ ->
                        bucheBauteil(zustand, besitzer, typ, -1, kostenBuchen = false)
                    }
                } ?: zustand
                val entfernteSeewege = karte.belegung.seewege.count {
                    it.hafenA == ort.position || it.hafenB == ort.position
                }
                if (entfernteSeewege > 0 && bisher.besitzer != null) {
                    nachBestand = bucheBauteil(
                        nachBestand,
                        bisher.besitzer,
                        BauteilTyp.FRACHTSCHIFF,
                        -entfernteSeewege,
                        kostenBuchen = false,
                    )
                }
                nachBestand.mitBelegung { belegung ->
                    belegung.copy(
                        ecken = belegung.ecken.filterNot { it.position == ort.position },
                        seewege = belegung.seewege.filterNot {
                            it.hafenA == ort.position || it.hafenB == ort.position
                        },
                    )
                }.entferneUnverbundeneSchienen()
            }
            is KartenOrt.Kante -> {
                val bisher = karte.belegung.kantenNachPosition[ort.position]
                    ?: error("Die gewählte Kante ist nicht belegt.")
                if (ereignis.grund == KartenAenderungsGrund.SPIELERAKTION) {
                    require(
                        KartenAuswertung.gewalthaberBeiIntakterLinie(karte, ort.position) ==
                            ereignis.spieler,
                    ) {
                        "Nur der alleinige Gewalthaber darf diese Handelslinie abbauen."
                    }
                }
                zustand.mitBelegung { belegung ->
                    belegung.copy(kanten = belegung.kanten.filterNot { it.position == ort.position })
                }
            }
            is KartenOrt.Feld -> {
                val bisher = karte.belegung.felderNachPosition[ort.position]
                    ?: error("Das gewählte Feld ist nicht belegt.")
                zustand.mitBelegung { belegung ->
                    belegung.copy(felder = belegung.felder.filterNot { it.position == ort.position })
                }
            }
        }
    }

    fun bauwerkZustandAendern(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KartenBauwerkZustandGeaendert,
    ): SpielZustand {
        regulaereKarte(zustand)
        return when (val ort = ereignis.ort) {
            is KartenOrt.Feld -> error("Feldanlagen verwenden einen Anlagenzustand.")
            is KartenOrt.Ecke -> {
                val bisher = requireNotNull(zustand.karte)
                    .belegung.eckenNachPosition[ort.position]
                    ?: error("Die gewählte Ecke ist nicht belegt.")
                pruefeZustandsAenderung(
                    zustand,
                    ereignis.spieler,
                    bisher.besitzer,
                    ereignis.grund,
                )
                if (bisher.zustand == BauwerkZustand.ZERSTOERT) {
                    require(ereignis.zustand == BauwerkZustand.ZERSTOERT) {
                        "Zerstörte Gebäude müssen vor einem Neubau entfernt werden."
                    }
                }
                val entfernteSeewege = if (ereignis.zustand == BauwerkZustand.ZERSTOERT) {
                    zustand.karte.belegung.seewege.count {
                        it.hafenA == ort.position || it.hafenB == ort.position
                    }
                } else {
                    0
                }
                var danach = zustand.mitBelegung { belegung ->
                    belegung.copy(
                        ecken = belegung.ecken.map { eintrag ->
                            if (eintrag.position == ort.position) {
                                eintrag.copy(
                                    zustand = ereignis.zustand,
                                    besitzer = if (ereignis.zustand == BauwerkZustand.ZERSTOERT) null
                                    else eintrag.besitzer,
                                )
                            } else eintrag
                        },
                        seewege = if (ereignis.zustand == BauwerkZustand.ZERSTOERT) {
                            belegung.seewege.filterNot {
                                it.hafenA == ort.position || it.hafenB == ort.position
                            }
                        } else {
                            belegung.seewege
                        },
                    )
                }.passeBestandAnZustand(
                    besitzer = bisher.besitzer,
                    bauteil = bisher.typ.bauteilTyp(),
                    vorher = bisher.zustand,
                    nachher = ereignis.zustand,
                )
                if (entfernteSeewege > 0 && bisher.besitzer != null) {
                    danach = bucheBauteil(
                        danach,
                        bisher.besitzer,
                        BauteilTyp.FRACHTSCHIFF,
                        -entfernteSeewege,
                        kostenBuchen = false,
                    )
                }
                if (ereignis.zustand == BauwerkZustand.ZERSTOERT) {
                    danach.entferneUnverbundeneSchienen()
                } else {
                    danach
                }
            }
            is KartenOrt.Kante -> zustand.mitBelegung { belegung ->
                val bisher = belegung.kantenNachPosition[ort.position]
                    ?: error("Die gewählte Kante ist nicht belegt.")
                if (ereignis.grund == KartenAenderungsGrund.SPIELERAKTION) {
                    require(
                        KartenAuswertung.gewalthaberBeiIntakterLinie(
                            requireNotNull(zustand.karte),
                            ort.position,
                        ) == ereignis.spieler,
                    ) { "Nur der alleinige Gewalthaber darf diese Handelslinie verändern." }
                }
                require(ereignis.zustand != BauwerkZustand.BELAGERT) {
                    "Eine Handelslinie kann nicht belagert sein."
                }
                belegung.copy(
                    kanten = belegung.kanten.map { eintrag ->
                        if (eintrag.position == ort.position) eintrag.copy(zustand = ereignis.zustand)
                        else eintrag
                    },
                )
            }
        }
    }

    fun anlagenZustandAendern(
        zustand: SpielZustand,
        ereignis: SpielEreignis.FeldAnlagenZustandGeaendert,
    ): SpielZustand {
        regulaereKarte(zustand)
        return zustand.mitBelegung { belegung ->
            require(ereignis.feld in belegung.felderNachPosition) {
                "Das gewählte Feld ist nicht belegt."
            }
            belegung.copy(
                felder = belegung.felder.map { eintrag ->
                    if (eintrag.position == ereignis.feld) eintrag.copy(zustand = ereignis.zustand)
                    else eintrag
                },
            )
        }
    }

    fun seewegEinrichten(
        zustand: SpielZustand,
        ereignis: SpielEreignis.SeewegEingerichtet,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        require(ereignis.id.isNotBlank()) { "Eine Seeweg-ID darf nicht leer sein." }
        require(karte.belegung.seewege.none { it.id == ereignis.id }) {
            "Die Seeweg-ID ist bereits vergeben."
        }
        val nachKosten = bucheBauteil(zustand, ereignis.spieler, BauteilTyp.FRACHTSCHIFF, 1)
        val aktuelleKarte = requireNotNull(nachKosten.karte)
        return nachKosten.copy(
            karte = aktuelleKarte.copy(
                belegung = aktuelleKarte.belegung.copy(
                    seewege = (aktuelleKarte.belegung.seewege + SeewegBelegung(
                        id = ereignis.id,
                        hafenA = ereignis.hafenA,
                        hafenB = ereignis.hafenB,
                        besitzer = ereignis.spieler,
                        richtung = ereignis.richtung,
                    )).sortedBy(SeewegBelegung::id),
                ),
            ),
        )
    }

    fun seewegEntfernen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.SeewegEntfernt,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        val bisher = karte.belegung.seewege.firstOrNull { it.id == ereignis.id }
            ?: error("Der Seeweg wurde nicht gefunden.")
        require(bisher.besitzer == ereignis.spieler) { "Nur der Besitzer darf den Seeweg entfernen." }
        return bucheBauteil(
            zustand,
            ereignis.spieler,
            BauteilTyp.FRACHTSCHIFF,
            -1,
            kostenBuchen = false,
        ).mitBelegung { belegung ->
            belegung.copy(seewege = belegung.seewege.filterNot { it.id == ereignis.id })
        }
    }

    fun kriegsEinheitEinsetzen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegsEinheitEingesetzt,
    ): SpielZustand {
        val karte = regulaereKarte(zustand)
        require(zustand.konflikte.any { it.betrifft(ereignis.spieler, ereignis.gegner) }) {
            "Kriegseinheiten dürfen nur in einem bestehenden Krieg eingesetzt werden."
        }
        require(karte.belegung.kriegseinheiten.none { it.id == ereignis.id }) {
            "Die Kriegseinheiten-ID ist bereits vergeben."
        }
        val feld = (ereignis.ort as? KartenOrt.Feld)?.position
            ?: error("Kriegseinheiten werden auf einem Dreiecksfeld eingesetzt.")
        when (ereignis.typ) {
            KriegsEinheitTyp.PANZER -> require(feld in karte.landNachPosition) {
                "Ein Panzer muss auf einem Geländefeld stehen."
            }
            KriegsEinheitTyp.KRIEGSSCHIFF -> require(
                karte.enthaeltFeld(feld) && feld !in karte.landNachPosition,
            ) { "Ein Kriegsschiff muss auf einem Wasserfeld stehen." }
        }
        return zustand.mitBelegung { belegung ->
            belegung.copy(
                kriegseinheiten = (belegung.kriegseinheiten + KriegsEinheitBelegung(
                    id = ereignis.id,
                    typ = ereignis.typ,
                    besitzer = ereignis.spieler,
                    gegner = ereignis.gegner,
                    ort = ereignis.ort,
                )).sortedBy(KriegsEinheitBelegung::id),
            )
        }
    }

    fun kriegsEinheitEntfernen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.KriegsEinheitEntfernt,
    ): SpielZustand {
        regulaereKarte(zustand)
        val bisher = zustand.karte?.belegung?.kriegseinheiten?.firstOrNull { it.id == ereignis.id }
            ?: error("Die Kriegseinheit wurde nicht gefunden.")
        require(bisher.besitzer == ereignis.spieler) {
            "Nur der Besitzer darf die Kriegseinheit entfernen."
        }
        return zustand.mitBelegung { belegung ->
            belegung.copy(
                kriegseinheiten = belegung.kriegseinheiten.filterNot { it.id == ereignis.id },
            )
        }
    }

    private fun regulaereKarte(zustand: SpielZustand) =
        requireNotNull(zustand.karte) { "Der Spielstand besitzt keine Spielkarte." }.also {
            require(zustand.spielabschnitt == Spielabschnitt.REGULAER) {
                "Zuerst müssen alle Hauptbahnhöfe in Runde 0 platziert werden."
            }
        }

    private fun SpielZustand.mitBelegung(
        aenderung: (KartenBelegung) -> KartenBelegung,
    ): SpielZustand {
        val karte = requireNotNull(karte)
        return copy(karte = karte.copy(belegung = aenderung(karte.belegung)))
    }

    private fun bucheBauteil(
        zustand: SpielZustand,
        spieler: SpielerId,
        bauteil: BauteilTyp?,
        delta: Int,
        kostenBuchen: Boolean = true,
    ): SpielZustand {
        if (bauteil == null) return zustand
        val nachKosten = if (kostenBuchen && delta > 0 && bauteil.kosten.isNotEmpty()) {
            RohstoffRegelwerk.rohstoffeBuchen(zustand, spieler, bauteil.kosten, -1)
        } else {
            zustand
        }
        return SpielerRegelwerk.aendereSpieler(nachKosten, spieler) { bestand ->
            val bisher = bestand.bauteile.getOrDefault(bauteil, 0)
            val neu = bisher + delta
            require(neu >= 0) { "Der Bauteilbestand ${bauteil.text} darf nicht negativ werden." }
            val bauteile = bestand.bauteile.toMutableMap()
            if (neu == 0) bauteile.remove(bauteil) else bauteile[bauteil] = neu
            bestand.copy(bauteile = bauteile.toMap())
        }
    }

    private fun bucheKosten(
        zustand: SpielZustand,
        spieler: SpielerId,
        bauteil: BauteilTyp,
    ): SpielZustand = if (bauteil.kosten.isEmpty()) {
        zustand
    } else {
        RohstoffRegelwerk.rohstoffeBuchen(zustand, spieler, bauteil.kosten, -1)
    }

    private fun SpielZustand.passeBestandAnZustand(
        besitzer: SpielerId?,
        bauteil: BauteilTyp?,
        vorher: BauwerkZustand?,
        nachher: BauwerkZustand,
    ): SpielZustand {
        if (besitzer == null || bauteil == null || vorher == null) return this
        val vorherZaehlt = vorher != BauwerkZustand.ZERSTOERT
        val nachherZaehlt = nachher != BauwerkZustand.ZERSTOERT
        val delta = when {
            vorherZaehlt == nachherZaehlt -> 0
            nachherZaehlt -> 1
            else -> -1
        }
        return if (delta == 0) this else {
            bucheBauteil(this, besitzer, bauteil, delta, kostenBuchen = false)
        }
    }

    private fun SpielZustand.entferneUnverbundeneSchienen(): SpielZustand {
        val karte = karte ?: return this
        val zuEntfernen = karte.belegung.kanten.filter {
            it.zustand == BauwerkZustand.INTAKT &&
                KartenAuswertung.verbundeneSpieler(karte, it.position).isEmpty()
        }
        if (zuEntfernen.isEmpty()) return this
        val positionen = zuEntfernen.mapTo(mutableSetOf()) { it.position }
        return mitBelegung { belegung ->
            belegung.copy(kanten = belegung.kanten.filterNot { it.position in positionen })
        }
    }

    private fun pruefeEntfernung(
        spieler: SpielerId,
        besitzer: SpielerId?,
        grund: KartenAenderungsGrund,
    ) {
        require(grund != KartenAenderungsGrund.SPIELERAKTION || besitzer == null || besitzer == spieler) {
            "Nur der Besitzer darf diese Belegung entfernen."
        }
    }

    private fun pruefeZustandsAenderung(
        zustand: SpielZustand,
        spieler: SpielerId,
        besitzer: SpielerId?,
        grund: KartenAenderungsGrund,
    ) {
        require(grund != KartenAenderungsGrund.SPIELERAKTION || besitzer == spieler) {
            "Nur der Besitzer darf den Zustand dieses Bauwerks ändern."
        }
        if (grund == KartenAenderungsGrund.BELAGERUNG && besitzer != null) {
            require(zustand.konflikte.any { it.betrifft(spieler, besitzer) }) {
                "Eine Belagerung ist nur zwischen bestehenden Kriegsparteien erlaubt."
            }
        }
    }

    private fun EckGebaeudeTyp.bauteilTyp(): BauteilTyp? = when (this) {
        EckGebaeudeTyp.HAUPTBAHNHOF -> null
        EckGebaeudeTyp.BAHNHOF -> BauteilTyp.BAHNHOF
        EckGebaeudeTyp.GROSSBAHNHOF -> BauteilTyp.GROSSBAHNHOF
        EckGebaeudeTyp.HAFEN -> BauteilTyp.HAFEN
        EckGebaeudeTyp.GROSSHAFEN -> BauteilTyp.GROSSHAFEN
    }

    private fun List<EckBelegung>.eckenSortiert(): List<EckBelegung> =
        sortedWith(compareBy({ it.position.y }, { it.position.x }))

    private fun List<KantenBelegung>.kantenSortiert(): List<KantenBelegung> =
        sortedWith(
            compareBy(
                { it.position.anfang.y },
                { it.position.anfang.x },
                { it.position.ende.y },
                { it.position.ende.x },
            ),
        )

    private fun List<FeldBelegung>.felderSortiert(): List<FeldBelegung> =
        sortedWith(
            compareBy<FeldBelegung>({ it.position.zeile }, { it.position.spalte })
                .thenBy { it.position.haelfte.ordinal },
        )
}
