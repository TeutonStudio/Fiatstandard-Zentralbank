package de.teutonstudio.zentralbank.fachlogik.regelwerk

import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.auswertung.AnleihenAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.KartenAuswertung
import de.teutonstudio.zentralbank.fachlogik.auswertung.ProzugAuswertung
import de.teutonstudio.zentralbank.fachlogik.modell.KontoId
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsBuchung
import de.teutonstudio.zentralbank.fachlogik.modell.ProduktionsStandortId
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitArt
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtung
import de.teutonstudio.zentralbank.fachlogik.modell.VerwaltungsVerpflichtungId
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase

internal object ProzugRegelwerk {
    fun beginnen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.ProzugBegonnen,
    ): SpielZustand {
        val zug = pruefeProzug(zustand, ereignis.zugId, begonnen = false)
        require(zustand.spielabschnitt == Spielabschnitt.REGULAER) {
            "Prozug und Epizug beginnen erst nach Runde 0."
        }
        val karte = zustand.karte
        val abbauErtraege = karte?.let {
            KartenAuswertung.abbauErtrag(it, zug.spieler, zustand.konflikte)
        }.orEmpty()
        val verwaltung = karte?.let {
            KartenAuswertung.verwaltungsStandorte(it, zug.spieler)
        }.orEmpty().map { standort ->
            VerwaltungsVerpflichtung(
                id = VerwaltungsVerpflichtungId(zug.zugId, standort.ecke),
                typ = standort.typ,
                bedarf = standort.bedarf,
            )
        }
        val verbindlichkeiten = AnleihenAuswertung.faelligeVerbindlichkeiten(
            zustand = zustand,
            schuldner = zug.spieler,
            zugId = zug.zugId,
        )
        val mitSnapshot = zustand.copy(
            zugStatus = zug.copy(
                prozug = zug.prozug.copy(
                    begonnen = true,
                    abbauErtraege = abbauErtraege,
                    verwaltungsVerpflichtungen = verwaltung,
                    verbindlichkeiten = verbindlichkeiten,
                ),
            ),
        )
        return if (abbauErtraege.isEmpty()) {
            mitSnapshot
        } else {
            RohstoffRegelwerk.rohstoffeBuchen(
                zustand = mitSnapshot,
                spieler = zug.spieler,
                mengen = abbauErtraege,
                faktor = 1,
            )
        }
    }

    fun verarbeiten(
        zustand: SpielZustand,
        ereignis: SpielEreignis.VerarbeitungAusgefuehrt,
    ): SpielZustand {
        val zug = pruefeProzug(zustand, ereignis.zugId)
        require(ereignis.laeufe > 0) { "Die Zahl der Verarbeitungsläufe muss positiv sein." }
        val karte = requireNotNull(zustand.karte) { "Der Spielstand besitzt keine Spielkarte." }
        val standort = KartenAuswertung.verarbeitungsStandorte(
            karte,
            zug.spieler,
            zustand.konflikte,
        )
            .firstOrNull { it.feld == ereignis.feld }
            ?: error("Der Verarbeitungsstandort ist für den aktiven Spieler nicht nutzbar.")
        val id = ProduktionsStandortId(ereignis.feld)
        val bisher = zug.prozug.produktionsBuchungen
            .filter { it.standort == id }
            .sumOf { it.laeufe }
        require(bisher + ereignis.laeufe <= standort.maximaleLaeufe) {
            "Die Verarbeitungskapazität des Standortes ist überschritten."
        }
        val einsatz = standort.einsatzJeLauf.mapValues { (_, menge) -> menge * ereignis.laeufe }
        val ertrag = standort.ertragJeLauf.mapValues { (_, menge) -> menge * ereignis.laeufe }
        val nachEinsatz = RohstoffRegelwerk.rohstoffeBuchen(
            zustand = zustand,
            spieler = zug.spieler,
            mengen = einsatz,
            faktor = -1,
        )
        val nachErtrag = RohstoffRegelwerk.rohstoffeBuchen(
            zustand = nachEinsatz,
            spieler = zug.spieler,
            mengen = ertrag,
            faktor = 1,
        )
        return nachErtrag.copy(
            zugStatus = zug.copy(
                prozug = zug.prozug.copy(
                    produktionsBuchungen = zug.prozug.produktionsBuchungen +
                        ProduktionsBuchung(id, ereignis.laeufe),
                ),
            ),
        )
    }

    fun verwaltungsstandortVersorgen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.VerwaltungsstandortVersorgt,
    ): SpielZustand {
        val zug = pruefeProzug(zustand, ereignis.zugId)
        val id = VerwaltungsVerpflichtungId(zug.zugId, ereignis.ecke)
        val verpflichtung = zug.prozug.verwaltungsVerpflichtungen.firstOrNull { it.id == id }
            ?: error("Für diesen Zug besteht an der Ecke keine Verwaltungsverpflichtung.")
        require(id !in zug.prozug.versorgteStandorte) {
            "Der Verwaltungsstandort wurde bereits versorgt."
        }
        val nachVerbrauch = RohstoffRegelwerk.rohstoffeBuchen(
            zustand = zustand,
            spieler = zug.spieler,
            mengen = verpflichtung.bedarf,
            faktor = -1,
        )
        return nachVerbrauch.copy(
            zugStatus = zug.copy(
                prozug = zug.prozug.copy(
                    versorgteStandorte = zug.prozug.versorgteStandorte + id,
                ),
            ),
        )
    }

    fun verbindlichkeitBegleichen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.VerbindlichkeitBeglichen,
    ): SpielZustand {
        val zug = pruefeProzug(zustand, ereignis.zugId)
        val verbindlichkeit = zug.prozug.verbindlichkeiten
            .firstOrNull { it.id == ereignis.verbindlichkeit }
            ?: error("Die Verbindlichkeit gehört nicht zu diesem Prozug.")
        require(verbindlichkeit.id !in zug.prozug.beglicheneVerbindlichkeiten) {
            "Die Verbindlichkeit wurde bereits beglichen."
        }
        val nachZahlung = FinanzRegelwerk.geldUebertragen(
            zustand = zustand,
            von = KontoId.Spieler(zug.spieler),
            an = verbindlichkeit.empfaenger,
            betrag = verbindlichkeit.betrag,
        )
        val nachEinloesung = if (verbindlichkeit.id.art == VerbindlichkeitArt.RUECKKAUF) {
            AnleihenRegelwerk.anleiheAusloesen(nachZahlung, verbindlichkeit.id.anleihe)
        } else {
            nachZahlung
        }
        return nachEinloesung.copy(
            zugStatus = zug.copy(
                prozug = zug.prozug.copy(
                    beglicheneVerbindlichkeiten =
                        zug.prozug.beglicheneVerbindlichkeiten + verbindlichkeit.id,
                ),
            ),
        )
    }

    fun erfolgreichAbschliessen(
        zustand: SpielZustand,
        ereignis: SpielEreignis.ProzugErfolgreichAbgeschlossen,
    ): SpielZustand {
        val zug = pruefeProzug(zustand, ereignis.zugId)
        val plan = requireNotNull(ProzugAuswertung.plan(zustand))
        require(plan.kannErfolgreichAbschliessen) {
            plan.sperrgruende.joinToString(" ").ifBlank { "Der Prozug ist noch nicht erfüllt." }
        }
        return zustand.copy(
            zugStatus = zug.copy(
                phase = ZugPhase.Epizug,
                prozug = zug.prozug.copy(erfolgreichAbgeschlossen = true),
            ),
        )
    }

    private fun pruefeProzug(
        zustand: SpielZustand,
        zugId: Long,
        begonnen: Boolean = true,
    ) = requireNotNull(zustand.zugStatus) { "Es ist kein Zug aktiv." }.also { zug ->
        require(zug.phase == ZugPhase.Prozug) { "Diese Aktion ist nur im Prozug erlaubt." }
        require(zug.zugId == zugId) { "Die Zugkennung ist veraltet." }
        require(zug.spieler == zustand.aktiverSpieler) { "Der aktive Spieler stimmt nicht überein." }
        require(zug.prozug.begonnen == begonnen) {
            if (begonnen) "Der Prozug wurde noch nicht begonnen." else "Der Prozug wurde bereits begonnen."
        }
    }
}
