package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.FaelligeVerbindlichkeit
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsStandortId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtung
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtungId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase

data class ProduktionsStandortPlan(
    val standort: ProduktionsStandortId,
    val typ: de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp,
    val verwaltungsstandort: KartenEcke,
    val verwaltungsstandortVersorgt: Boolean,
    val maximaleLaeufe: Int,
    val gebuchteLaeufe: Int,
    val verbleibendeLaeufe: Int,
    val mitBestandMoeglicheLaeufe: Int,
    val einsatzJeLauf: Map<Rohstoff, Int>,
    val ertragJeLauf: Map<Rohstoff, Int>,
)

data class ProzugPlan(
    val spieler: SpielerId,
    val zugId: Long,
    val begonnen: Boolean,
    val abbauErtraege: Map<Rohstoff, Int>,
    val produktionsStandorte: List<ProduktionsStandortPlan>,
    val verwaltungsVerpflichtungen: List<VerwaltungsVerpflichtung>,
    val verbindlichkeiten: List<FaelligeVerbindlichkeit>,
    val fehlendeRohstoffe: Map<Rohstoff, Int>,
    val fehlendesGeld: Geld,
    val kannErfolgreichAbschliessen: Boolean,
    val sperrgruende: List<String>,
)

object ProzugAuswertung {
    fun plan(zustand: SpielZustand): ProzugPlan? {
        val zug = zustand.zugStatus ?: return null
        if (zug.phase != ZugPhase.Prozug) return null
        val spieler = zustand.spieler.firstOrNull { it.id == zug.spieler } ?: return null
        val prozug = zug.prozug
        val offeneVerwaltung = prozug.verwaltungsVerpflichtungen.filter {
            it.typ == de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp.HAUPTBAHNHOF &&
                it.id !in prozug.versorgteStandorte
        }
        val offeneVerbindlichkeiten = prozug.verbindlichkeiten.filter {
            it.id !in prozug.beglicheneVerbindlichkeiten
        }
        val gesamtBedarf = offeneVerwaltung
            .flatMap { verpflichtung -> verpflichtung.bedarf.entries }
            .groupBy(Map.Entry<Rohstoff, Int>::key, Map.Entry<Rohstoff, Int>::value)
            .mapValues { (_, mengen) -> mengen.sum() }
        val fehlendeRohstoffe = gesamtBedarf.mapNotNull { (rohstoff, bedarf) ->
            val fehlt = bedarf - spieler.rohstoffe.getOrDefault(rohstoff, 0)
            if (fehlt > 0) rohstoff to fehlt else null
        }.toMap()
        val offenesGeld = offeneVerbindlichkeiten.fold(Geld.NULL) { summe, posten ->
            summe + posten.betrag
        }
        val fehlendesGeld = if (offenesGeld > spieler.geldkonto) {
            offenesGeld - spieler.geldkonto
        } else {
            Geld.NULL
        }
        val produktionsStandorte = zustand.karte
            ?.let { karte ->
                KartenAuswertung.verarbeitungsStandorte(
                    karte,
                    zug.spieler,
                    zustand.konflikte,
                )
            }
            .orEmpty()
            .map { standort ->
                val id = ProduktionsStandortId(standort.feld)
                val gebucht = prozug.produktionsBuchungen
                    .filter { it.standort == id }
                    .sumOf { it.laeufe }
                val verbleibend = (standort.maximaleLaeufe - gebucht).coerceAtLeast(0)
                val verwaltungsstandortVersorgt = VerwaltungsVerpflichtungId(
                    zugId = zug.zugId,
                    ecke = standort.verwaltungsstandort,
                ) in prozug.versorgteStandorte
                val mitBestand = if (verwaltungsstandortVersorgt) {
                    standort.einsatzJeLauf.entries.minOfOrNull { (rohstoff, menge) ->
                        spieler.rohstoffe.getOrDefault(rohstoff, 0) / menge
                    } ?: verbleibend
                } else {
                    0
                }
                ProduktionsStandortPlan(
                    standort = id,
                    typ = standort.typ,
                    verwaltungsstandort = standort.verwaltungsstandort,
                    verwaltungsstandortVersorgt = verwaltungsstandortVersorgt,
                    maximaleLaeufe = standort.maximaleLaeufe,
                    gebuchteLaeufe = gebucht,
                    verbleibendeLaeufe = verbleibend,
                    mitBestandMoeglicheLaeufe = minOf(verbleibend, mitBestand),
                    einsatzJeLauf = standort.einsatzJeLauf,
                    ertragJeLauf = standort.ertragJeLauf,
                )
            }
        val kannAbschliessen = prozug.begonnen &&
            offeneVerwaltung.isEmpty() && offeneVerbindlichkeiten.isEmpty()
        val sperrgruende = buildList {
            if (!prozug.begonnen) add("Der Prozug wurde noch nicht begonnen.")
            if (offeneVerwaltung.isNotEmpty()) {
                add("Der Hauptbahnhof ist noch nicht versorgt.")
            }
            if (offeneVerbindlichkeiten.isNotEmpty()) {
                add("${offeneVerbindlichkeiten.size} Verbindlichkeit(en) sind noch offen.")
            }
        }
        return ProzugPlan(
            spieler = zug.spieler,
            zugId = zug.zugId,
            begonnen = prozug.begonnen,
            abbauErtraege = prozug.abbauErtraege,
            produktionsStandorte = produktionsStandorte,
            verwaltungsVerpflichtungen = prozug.verwaltungsVerpflichtungen,
            verbindlichkeiten = prozug.verbindlichkeiten,
            fehlendeRohstoffe = fehlendeRohstoffe,
            fehlendesGeld = fehlendesGeld,
            kannErfolgreichAbschliessen = kannAbschliessen,
            sperrgruende = sperrgruende,
        )
    }
}
