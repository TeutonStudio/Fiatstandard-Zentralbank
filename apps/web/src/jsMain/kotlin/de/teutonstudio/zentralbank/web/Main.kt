package de.teutonstudio.zentralbank.web

import de.teutonstudio.zentralbank.anwendung.GespeichertesSpiel
import de.teutonstudio.zentralbank.anwendung.SpielSitzung
import de.teutonstudio.zentralbank.fachlogik.aktion.SpielAktion
import de.teutonstudio.zentralbank.fachlogik.start.erstelleStandardSpiel
import kotlinx.browser.document
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement

private val json = Json {
    classDiscriminator = "art"
    encodeDefaults = true
    prettyPrint = true
    ignoreUnknownKeys = false
}

private val speicher = BrowserSpielSpeicher(json)
private var sitzung: SpielSitzung? = null
private var aktuelleId: Long? = null
private var aktuellerSeed: Long? = null
private var erlaubteAktionen: List<SpielAktion> = emptyList()

fun main() {
    button("new-game").onclick = { neuesSpiel(); null }
    button("save-game").onclick = { spielSpeichern(); null }
    button("load-game").onclick = { ausgewaehltesSpielLaden(); null }
    button("delete-game").onclick = { ausgewaehltesSpielLoeschen(); null }
    button("execute-action").onclick = { aktionAusfuehren(); null }
    button("undo").onclick = { rueckgaengig(); null }
    button("redo").onclick = { wiederholen(); null }
    button("export-game").onclick = { exportieren(); null }
    button("import-game").onclick = { importieren(); null }

    gespeicherteSpieleRendern()
    speicher.alle().maxByOrNull(GespeichertesSpiel::id)?.let(::spielLaden)
        ?: rendern()
}

private fun neuesSpiel() {
    val namen = input("player-names").value
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
    val seed = input("seed").value.toLongOrNull() ?: 0L
    runCatching {
        SpielSitzung(erstelleStandardSpiel(namen, seed))
    }.onSuccess { neueSitzung ->
        sitzung = neueSitzung
        aktuelleId = null
        aktuellerSeed = seed
        meldung("Neue Partie mit Seed $seed erstellt.")
        spielSpeichern()
        rendern()
    }.onFailure(::fehler)
}

private fun spielSpeichern() {
    val aktuelleSitzung = sitzung ?: return fehler(IllegalStateException("Keine Partie geladen."))
    val id = aktuelleId ?: speicher.naechsteId().also { aktuelleId = it }
    speicher.speichern(aktuelleSitzung.alsGespeichertesSpiel(id, aktuellerSeed))
    gespeicherteSpieleRendern(id)
    meldung("Spiel $id lokal im Browser gespeichert.")
}

private fun ausgewaehltesSpielLaden() {
    val id = select("saved-games").value.toLongOrNull()
        ?: return fehler(IllegalArgumentException("Kein gespeichertes Spiel ausgewählt."))
    speicher.laden(id)?.let(::spielLaden)
        ?: fehler(IllegalStateException("Spiel $id konnte nicht geladen werden."))
}

private fun spielLaden(spiel: GespeichertesSpiel) {
    runCatching { SpielSitzung(spiel.startzustand, spiel.ereignisse) }
        .onSuccess { geladeneSitzung ->
            sitzung = geladeneSitzung
            aktuelleId = spiel.id
            aktuellerSeed = spiel.seed
            input("seed").value = (spiel.seed ?: 0L).toString()
            input("player-names").value = spiel.startzustand.spieler.joinToString(", ") { it.name }
            gespeicherteSpieleRendern(spiel.id)
            meldung("Spiel ${spiel.id} geladen.")
            rendern()
        }
        .onFailure(::fehler)
}

private fun ausgewaehltesSpielLoeschen() {
    val id = select("saved-games").value.toLongOrNull()
        ?: return fehler(IllegalArgumentException("Kein gespeichertes Spiel ausgewählt."))
    speicher.loeschen(id)
    if (aktuelleId == id) {
        sitzung = null
        aktuelleId = null
        aktuellerSeed = null
    }
    gespeicherteSpieleRendern()
    meldung("Spiel $id gelöscht.")
    rendern()
}

private fun aktionAusfuehren() {
    val aktuelleSitzung = sitzung ?: return fehler(IllegalStateException("Keine Partie geladen."))
    val index = select("actions").value.toIntOrNull()
        ?: return fehler(IllegalArgumentException("Keine Aktion ausgewählt."))
    val aktion = erlaubteAktionen.getOrNull(index)
        ?: return fehler(IllegalArgumentException("Die ausgewählte Aktion ist nicht mehr verfügbar."))
    aktuelleSitzung.aktionAnwenden(aktion)
        .onSuccess {
            spielSpeichern()
            meldung("Aktion ausgeführt: ${aktion.anzeigeName()}")
            rendern()
        }
        .onFailure(::fehler)
}

private fun rueckgaengig() {
    val aktuelleSitzung = sitzung ?: return
    aktuelleSitzung.rueckgaengig()
    spielSpeichern()
    meldung("Letzte Aktion rückgängig gemacht.")
    rendern()
}

private fun wiederholen() {
    val aktuelleSitzung = sitzung ?: return
    aktuelleSitzung.wiederholen()
        .onSuccess {
            spielSpeichern()
            meldung("Aktion wiederholt.")
            rendern()
        }
        .onFailure(::fehler)
}

private fun exportieren() {
    val aktuelleSitzung = sitzung ?: return fehler(IllegalStateException("Keine Partie geladen."))
    val id = aktuelleId ?: speicher.naechsteId()
    textarea("transfer").value = json.encodeToString(
        aktuelleSitzung.alsGespeichertesSpiel(id, aktuellerSeed),
    )
    meldung("Spielstand als JSON bereitgestellt.")
}

private fun importieren() {
    val text = textarea("transfer").value.trim()
    if (text.isEmpty()) return fehler(IllegalArgumentException("Kein JSON-Spielstand eingetragen."))
    runCatching { json.decodeFromString<GespeichertesSpiel>(text) }
        .onSuccess { spiel ->
            val importierterSpielstand = if (speicher.laden(spiel.id) == null) {
                spiel
            } else {
                spiel.copy(id = speicher.naechsteId())
            }
            speicher.speichern(importierterSpielstand)
            spielLaden(importierterSpielstand)
            meldung("Spielstand ${importierterSpielstand.id} importiert.")
        }
        .onFailure(::fehler)
}

private fun rendern() {
    val aktuelleSitzung = sitzung
    val zustand = aktuelleSitzung?.zustand
    element("game-summary").innerHTML = zustand?.zusammenfassungHtml() ?: "<h2>Spielstand</h2><p>Keine Partie geladen.</p>"
    element("players").innerHTML = zustand?.spielerHtml().orEmpty()
    element("map").innerHTML = zustand?.kartenSvg() ?: "<p>Keine Karte geladen.</p>"
    element("map-caption").textContent = zustand?.karte?.name.orEmpty()

    val aktiverSpieler = zustand?.aktiverSpieler
    erlaubteAktionen = if (aktuelleSitzung != null && aktiverSpieler != null && zustand.ergebnis == null) {
        aktuelleSitzung.erlaubteAktionen(aktiverSpieler)
    } else {
        emptyList()
    }
    val auswahl = select("actions")
    auswahl.innerHTML = ""
    erlaubteAktionen.forEachIndexed { index, aktion ->
        val option = document.createElement("option") as HTMLOptionElement
        option.value = index.toString()
        option.textContent = aktion.anzeigeName()
        auswahl.appendChild(option)
    }
    button("execute-action").disabled = erlaubteAktionen.isEmpty()
    button("save-game").disabled = aktuelleSitzung == null
    button("undo").disabled = aktuelleSitzung == null || aktuelleSitzung.ereignisVerlauf.angewandteEreignisse.isEmpty()
    button("redo").disabled = aktuelleSitzung == null || aktuelleSitzung.ereignisVerlauf.wiederholbareEreignisse.isEmpty()
}

private fun gespeicherteSpieleRendern(ausgewaehlt: Long? = aktuelleId) {
    val auswahl = select("saved-games")
    auswahl.innerHTML = ""
    speicher.alle().sortedByDescending(GespeichertesSpiel::id).forEach { spiel ->
        val uebersicht = runCatching { spiel.zuUebersicht() }.getOrNull() ?: return@forEach
        val option = document.createElement("option") as HTMLOptionElement
        option.value = spiel.id.toString()
        option.textContent = "#${spiel.id} · Runde ${uebersicht.runde} · ${uebersicht.spielerNamen.joinToString(", ")}"
        option.selected = spiel.id == ausgewaehlt
        auswahl.appendChild(option)
    }
    val leer = auswahl.length == 0
    button("load-game").disabled = leer
    button("delete-game").disabled = leer
}

private fun meldung(text: String) {
    element("message").className = "message"
    element("message").textContent = text
}

private fun fehler(fehler: Throwable) {
    element("message").className = "message error"
    element("message").textContent = fehler.message ?: "Unbekannter Fehler."
}

private fun element(id: String) = document.getElementById(id)
    ?: error("Element #$id fehlt.")
private fun button(id: String) = element(id) as HTMLButtonElement
private fun input(id: String) = element(id) as HTMLInputElement
private fun select(id: String) = element(id) as HTMLSelectElement
private fun textarea(id: String) = element(id) as HTMLTextAreaElement
