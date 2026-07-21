package de.teutonstudio.zentralbank.fachlogik.engine

import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.auswertung.ProzugAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.RundenAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.SpielEndeAuswertung
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.regelwerk.SpielRegelwerk

class StandardSpielEngine : SpielEngine {
    override fun pruefe(zustand: SpielZustand, aktion: SpielAktion): Result<Unit> =
        anwenden(zustand, aktion).map { }

    override fun anwenden(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): Result<SpielSchrittErgebnis> = runCatching {
        val ereignisse = ereignisseFuer(zustand, aktion)
        val folgezustand = ereignisse.fold(zustand) { zwischenzustand, ereignis ->
            SpielRegelwerk.wendeAn(zwischenzustand, ereignis).getOrThrow()
        }
        SpielSchrittErgebnis(folgezustand, ereignisse)
    }

    override fun erlaubteAktionen(
        zustand: SpielZustand,
        spieler: SpielerId,
    ): List<SpielAktion> {
        if (zustand.ergebnis != null || spieler in zustand.ausgeschiedeneSpieler) return emptyList()
        val zug = zustand.zugStatus ?: return emptyList()
        if (zustand.aktiverSpieler != spieler || zug.spieler != spieler) return emptyList()

        val kandidaten: List<SpielAktion> = when (zug.phase) {
            ZugPhase.Prozug -> if (!zug.prozug.begonnen) {
                listOf(SpielAktion.ProzugBeginnen(zug.zugId))
            } else {
                val plan = ProzugAuswertung.plan(zustand)
                buildList<SpielAktion> {
                    plan?.produktionsStandorte
                        ?.filter { it.verbleibendeLaeufe > 0 && it.mitBestandMoeglicheLaeufe > 0 }
                        ?.forEach { standort ->
                            add(
                                SpielAktion.VerarbeitungAusfuehren(
                                    zugId = zug.zugId,
                                    feld = standort.standort.feld,
                                ),
                            )
                        }
                    zug.prozug.verwaltungsVerpflichtungen
                        .filter { it.id !in zug.prozug.versorgteStandorte }
                        .forEach { verpflichtung ->
                            add(
                                SpielAktion.VerwaltungsstandortVersorgen(
                                    zugId = zug.zugId,
                                    ecke = verpflichtung.id.ecke,
                                ),
                            )
                        }
                    zug.prozug.verbindlichkeiten
                        .filter { it.id !in zug.prozug.beglicheneVerbindlichkeiten }
                        .forEach { verbindlichkeit ->
                            add(
                                SpielAktion.VerbindlichkeitBegleichen(
                                    zugId = zug.zugId,
                                    verbindlichkeit = verbindlichkeit.id,
                                ),
                            )
                        }
                    if (plan?.kannErfolgreichAbschliessen == true) {
                        add(SpielAktion.ProzugAbschliessen(zug.zugId))
                    }
                }
            }

            ZugPhase.Epizug -> listOf(SpielAktion.ZugBeenden)
        }
        return (kandidaten + SpielAktion.Aufgeben(spieler))
            .distinct()
            .filter { aktion -> pruefe(zustand, aktion).isSuccess }
    }

    private fun ereignisseFuer(
        zustand: SpielZustand,
        aktion: SpielAktion,
    ): List<SpielEreignis> {
        if (aktion == SpielAktion.ZugBeenden) return zugBeendenEreignisse(zustand)
        if (aktion is SpielAktion.Aufgeben) return aufgebenEreignisse(zustand, aktion)
        return listOf(when (aktion) {
            is SpielAktion.Aufgeben -> error("Wird als mehrstufige Regelfolge aufgelöst.")
            is SpielAktion.ProzugBeginnen -> SpielEreignis.ProzugBegonnen(aktion.zugId)
            is SpielAktion.VerarbeitungAusfuehren -> SpielEreignis.VerarbeitungAusgefuehrt(
                zugId = aktion.zugId,
                feld = aktion.feld,
                laeufe = aktion.laeufe,
            )
            is SpielAktion.VerwaltungsstandortVersorgen ->
                SpielEreignis.VerwaltungsstandortVersorgt(aktion.zugId, aktion.ecke)
            is SpielAktion.VerbindlichkeitBegleichen ->
                SpielEreignis.VerbindlichkeitBeglichen(aktion.zugId, aktion.verbindlichkeit)
            is SpielAktion.ProzugAbschliessen ->
                SpielEreignis.ProzugErfolgreichAbgeschlossen(aktion.zugId)
            SpielAktion.ZugBeenden -> error("Wird als mehrstufige Regelfolge aufgelöst.")
            is SpielAktion.WarenkorbAendern -> SpielEreignis.WarenkorbGeaendert(aktion.warenkorb)
            is SpielAktion.RohstoffHandeln -> SpielEreignis.RohstoffHandel(
                kaeufer = aktion.kaeufer,
                verkaeufer = aktion.verkaeufer,
                rohstoff = aktion.rohstoff,
                menge = aktion.menge,
                preis = aktion.preis,
            )
            is SpielAktion.MitAuslandHandeln -> SpielEreignis.AuslandsHandel(
                spieler = aktion.spieler,
                rohstoff = aktion.rohstoff,
                menge = aktion.menge,
                preis = aktion.preis,
                art = aktion.art,
            )
            is SpielAktion.KriegErklaeren -> SpielEreignis.KriegErklaert(
                aggressor = aktion.aggressor,
                verteidiger = aktion.verteidiger,
            )
            is SpielAktion.FriedenSchliessen -> SpielEreignis.KriegBeendet(
                spielerA = aktion.spielerA,
                spielerB = aktion.spielerB,
            )
        })
    }

    private fun zugBeendenEreignisse(zustand: SpielZustand): List<SpielEreignis> {
        val vorherigeRunde = zustand.rundenzähler
        val vorherigeZugId = zustand.zugStatus?.zugId
        val ereignisse = mutableListOf<SpielEreignis>(SpielEreignis.ZugBeendet)
        var zwischenzustand = SpielRegelwerk.wendeAn(zustand, ereignisse.last()).getOrThrow()
        val zugHatGewechselt = zwischenzustand.zugStatus?.zugId != vorherigeZugId
        if (!zugHatGewechselt) return ereignisse

        if (zwischenzustand.rundenzähler > vorherigeRunde) {
            val werte = RundenAuswertung.naechsteRundenwerte(zwischenzustand)
            val rundenereignis = SpielEreignis.RundeBegonnen(
                runde = werte.runde,
                marktpreise = werte.marktpreise,
                leitzins = werte.leitzins,
                preisinflation = werte.preisinflation,
            )
            ereignisse += rundenereignis
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, rundenereignis).getOrThrow()
        }
        val naechsterZug = requireNotNull(zwischenzustand.zugStatus)
        ereignisse += SpielEreignis.ProzugBegonnen(naechsterZug.zugId)
        return ereignisse
    }

    private fun aufgebenEreignisse(
        zustand: SpielZustand,
        aktion: SpielAktion.Aufgeben,
    ): List<SpielEreignis> {
        val vorherigeRunde = zustand.rundenzähler
        val ereignisse = mutableListOf<SpielEreignis>(
            SpielEreignis.SpielerAusgeschieden(
                spieler = aktion.spieler,
                grund = de.teutonstudio.zentralbank.fachlogik.modell.AusscheidensGrund.AUFGABE,
            ),
        )
        var zwischenzustand = SpielRegelwerk.wendeAn(zustand, ereignisse.last()).getOrThrow()
        val ergebnis = SpielEndeAuswertung.ergebnisFallsBeendet(zwischenzustand)
        if (ergebnis != null) {
            ereignisse += SpielEreignis.PartieBeendet(ergebnis)
            return ereignisse
        }
        if (zwischenzustand.rundenzähler > vorherigeRunde) {
            val werte = RundenAuswertung.naechsteRundenwerte(zwischenzustand)
            val rundenereignis = SpielEreignis.RundeBegonnen(
                werte.runde,
                werte.marktpreise,
                werte.leitzins,
                werte.preisinflation,
            )
            ereignisse += rundenereignis
            zwischenzustand = SpielRegelwerk.wendeAn(zwischenzustand, rundenereignis).getOrThrow()
        }
        ereignisse += SpielEreignis.ProzugBegonnen(requireNotNull(zwischenzustand.zugStatus).zugId)
        return ereignisse
    }
}
