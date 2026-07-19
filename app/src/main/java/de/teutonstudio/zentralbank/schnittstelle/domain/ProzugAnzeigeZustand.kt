package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.fachlogik.auswertung.ProzugAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId

data class ProzugAnzeigeZustand(
    val spielerName: String,
    val runde: Int,
    val zugId: Long,
    val geld: String,
    val rohstoffbestand: String,
    val fortschritt: String,
    val abbauErtraege: List<String>,
    val produktion: List<ProduktionsAnzeige>,
    val verwaltung: List<VerwaltungsAnzeige>,
    val verbindlichkeiten: List<VerbindlichkeitAnzeige>,
    val defizite: List<String>,
    val sperrgruende: List<String>,
    val kannAbschliessen: Boolean,
)

data class ProduktionsAnzeige(
    val feld: KartenFeld,
    val titel: String,
    val rezept: String,
    val bereitsVerwendet: Boolean,
    val kannVerarbeiten: Boolean,
)

data class VerwaltungsAnzeige(
    val ecke: KartenEcke,
    val text: String,
    val versorgt: Boolean,
    val deckbar: Boolean,
)

data class VerbindlichkeitAnzeige(
    val id: VerbindlichkeitId,
    val text: String,
    val bezahlt: Boolean,
    val deckbar: Boolean,
)

fun SpielZustand.zuProzugAnzeigeZustand(): ProzugAnzeigeZustand? {
    val plan = ProzugAuswertung.plan(this) ?: return null
    val zug = requireNotNull(zugStatus)
    val prozug = zug.prozug
    val aktiver = spieler.first { it.id == plan.spieler }
    val verwaltung = plan.verwaltungsVerpflichtungen.map { verpflichtung ->
        VerwaltungsAnzeige(
            ecke = verpflichtung.id.ecke,
            text = "${verpflichtung.typ.name.lowercase()} ${verpflichtung.id.ecke}: " +
                verpflichtung.bedarf.mengenText(),
            versorgt = verpflichtung.id in prozug.versorgteStandorte,
            deckbar = verpflichtung.bedarf.all { (rohstoff, menge) ->
                aktiver.rohstoffe.getOrDefault(rohstoff, 0) >= menge
            },
        )
    }
    val verbindlichkeiten = plan.verbindlichkeiten.map { verbindlichkeit ->
        val empfaengerName = when (val empfaenger = verbindlichkeit.empfaenger) {
            KontoId.Bank -> "Geschäftsbank"
            KontoId.Ausland -> "Ausland"
            is KontoId.Spieler -> spieler.firstOrNull { it.id == empfaenger.id }?.name
                ?: empfaenger.id.wert
        }
        VerbindlichkeitAnzeige(
            id = verbindlichkeit.id,
            text = "${verbindlichkeit.id.art.name.lowercase()} · " +
                "${verbindlichkeit.id.anleihe.wert} · $empfaengerName · " +
                verbindlichkeit.betrag.zuMarkString(),
            bezahlt = verbindlichkeit.id in prozug.beglicheneVerbindlichkeiten,
            deckbar = aktiver.geldkonto >= verbindlichkeit.betrag,
        )
    }
    val versorgt = verwaltung.count { it.versorgt }
    val bezahlt = verbindlichkeiten.count { it.bezahlt }
    return ProzugAnzeigeZustand(
        spielerName = aktiver.name,
        runde = rundenzähler,
        zugId = plan.zugId,
        geld = aktiver.geldkonto.zuMarkString(),
        rohstoffbestand = aktiver.rohstoffe.mengenText().ifBlank { "keine Rohstoffe" },
        fortschritt = "$versorgt von ${verwaltung.size} Standorten versorgt, " +
            "$bezahlt von ${verbindlichkeiten.size} Zahlungen beglichen",
        abbauErtraege = plan.abbauErtraege.entries.map { (rohstoff, menge) ->
            "+$menge ${rohstoff.name.lowercase()} · bereits gutgeschrieben"
        },
        produktion = plan.produktionsStandorte.map { standort ->
            ProduktionsAnzeige(
                feld = standort.standort.feld,
                titel = "${standort.typ.text} · ${standort.standort.feld}",
                rezept = "${standort.einsatzJeLauf.mengenText()} → " +
                    standort.ertragJeLauf.mengenText(),
                bereitsVerwendet = standort.gebuchteLaeufe > 0,
                kannVerarbeiten = standort.mitBestandMoeglicheLaeufe > 0,
            )
        },
        verwaltung = verwaltung,
        verbindlichkeiten = verbindlichkeiten,
        defizite = buildList {
            if (plan.fehlendeRohstoffe.isNotEmpty()) {
                add("Fehlende Rohstoffe: ${plan.fehlendeRohstoffe.mengenText()}")
            }
            if (!plan.fehlendesGeld.istNull()) add("Fehlendes Geld: ${plan.fehlendesGeld.zuMarkString()}")
        },
        sperrgruende = plan.sperrgruende,
        kannAbschliessen = plan.kannErfolgreichAbschliessen,
    )
}

private fun Map<Rohstoff, Int>.mengenText(): String = entries
    .filter { it.value != 0 }
    .joinToString { (rohstoff, menge) -> "$menge ${rohstoff.name.lowercase()}" }
