package de.teutonstudio.zentralbank.fachlogik.auswertung

import de.teutonstudio.zentralbank.fachlogik.modell.EckGebaeudeTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Geld
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.SpielerId
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtung
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase

data class ZahlungsfaehigkeitsPlan(
    val spieler: SpielerId,
    val hauptbahnhof: VerwaltungsVerpflichtung?,
    val direktVersorgbar: Boolean,
    val nachMarkthandelVersorgbar: Boolean,
    val anleiheMoeglich: Boolean,
    val aufstockungMoeglich: Boolean,
    val schuldenstrichMoeglich: Boolean,
    val kapitulationNoetig: Boolean,
    val ausscheidenNoetig: Boolean,
)

/** Dieselbe Zahlungsfähigkeitsberechnung wird von Aktionsraum, Watchdog und UI verwendet. */
object ZahlungsfaehigkeitsAuswertung {
    fun plan(zustand: SpielZustand, spielerId: SpielerId): ZahlungsfaehigkeitsPlan {
        val spieler = zustand.spieler.single { it.id == spielerId }
        val zug = zustand.zugStatus
        val prozugPlan = ProzugAuswertung.plan(zustand)
        val hauptbahnhof = zug?.takeIf { it.spieler == spielerId && it.phase == ZugPhase.Prozug }
            ?.prozug?.verwaltungsVerpflichtungen?.singleOrNull {
                it.typ == EckGebaeudeTyp.HAUPTBAHNHOF &&
                    it.id !in zug.prozug.versorgteStandorte
            }
        val rohstoffeDirekt = hauptbahnhof == null || hauptbahnhof.bedarf.all { (rohstoff, menge) ->
            spieler.rohstoffe.getOrDefault(rohstoff, 0) >= menge
        }
        val offeneVerbindlichkeiten = if (zug?.spieler == spielerId) {
            zug.prozug.verbindlichkeiten.filter { it.id !in zug.prozug.beglicheneVerbindlichkeiten }
        } else emptyList()
        val verbindlichkeitenBetrag = offeneVerbindlichkeiten.fold(Geld.NULL) { summe, posten ->
            summe + posten.betrag
        }
        val direkt = rohstoffeDirekt && spieler.geldkonto >= verbindlichkeitenBetrag
        val karte = zustand.karte
        val marktZugang = karte != null && KartenAuswertung.kannAussenhandelBetreiben(
            karte,
            spielerId,
            zustand.konflikte,
        )
        val fehlkosten = hauptbahnhof?.bedarf.orEmpty().entries.fold(Geld.NULL) {
                summe, (rohstoff, menge) ->
            val fehlt = (menge - spieler.rohstoffe.getOrDefault(rohstoff, 0)).coerceAtLeast(0)
            summe + zustand.marktpreise.getOrDefault(rohstoff, Geld.NULL) * fehlt
        }
        val verkaufswert = spieler.rohstoffe.entries.fold(Geld.NULL) { summe, (rohstoff, menge) ->
            val pflicht = hauptbahnhof?.bedarf?.getOrDefault(rohstoff, 0) ?: 0
            val ueberschuss = (menge - pflicht).coerceAtLeast(0)
            summe + zustand.marktpreise.getOrDefault(rohstoff, Geld.NULL) * ueberschuss
        }
        val gesamtGeldbedarf = verbindlichkeitenBetrag + fehlkosten
        val nachMarkt = direkt || marktZugang &&
            spieler.geldkonto + verkaufswert >= gesamtGeldbedarf
        // Geldfinanzierung hilft bei einem Rohstoffdefizit nur, wenn der Markt erreichbar ist.
        // So kann eine endlose Aufstockung ohne Möglichkeit zum Rohstoffeinkauf das reguläre
        // Ausscheiden nicht verdecken.
        val finanzierungHilft = hauptbahnhof == null || rohstoffeDirekt || marktZugang
        val anleihe = finanzierungHilft &&
            AnleihenAuswertung.freieGeschaeftsbankPlaetze(zustand, spielerId) > 0 &&
            zustand.bankkonto > Geld.NULL
        val aufstockung = finanzierungHilft && zustand.anleihen.values.any { anleihe ->
            anleihe.emittent == spielerId &&
                AnleihenAuswertung.besitzer(zustand, anleihe.id) != KontoId.Spieler(spielerId)
        }
        val imKrieg = zustand.konflikte.any { spielerId in it.teilnehmer }
        val herabstufbar = karte?.belegung?.ecken?.any {
            it.besitzer == spielerId && it.typ != EckGebaeudeTyp.HAUPTBAHNHOF
        } == true
        val schuldenstrich = !imKrieg && herabstufbar
        val verpflichtungenOffen = hauptbahnhof != null ||
            offeneVerbindlichkeiten.isNotEmpty() ||
            prozugPlan?.kannErfolgreichAbschliessen == false
        val rettbar = direkt || nachMarkt || anleihe || aufstockung || schuldenstrich || imKrieg
        return ZahlungsfaehigkeitsPlan(
            spieler = spielerId,
            hauptbahnhof = hauptbahnhof,
            direktVersorgbar = direkt,
            nachMarkthandelVersorgbar = nachMarkt,
            anleiheMoeglich = anleihe,
            aufstockungMoeglich = aufstockung,
            schuldenstrichMoeglich = schuldenstrich,
            kapitulationNoetig = imKrieg && !directOrFinancial(direkt, nachMarkt, anleihe, aufstockung),
            ausscheidenNoetig = verpflichtungenOffen && !rettbar,
        )
    }

    private fun directOrFinancial(vararg werte: Boolean): Boolean = werte.any { it }
}
