# Etappenplan: Umbau der App „Fiatstandard – Zentralbank" (CZB)

**Zielgruppe dieses Dokuments:** Ein Coding-Agent (Codex), der den Umbau eigenständig in der bestehenden Codebasis durchführt.
**Repo:** `TeutonStudio/Fiatstandard-Zentralbank` (Kotlin, Jetpack Compose, Gradle-Modul `:app`, Projektname `CZB`).

---

## 0. Kontext: Was die App ist

Begleit-App („Hilfsapp") für ein physisches Brettspiel. Die App führt die Buchhaltung der Zentralbank und der Spieler: Rohstoffe, Geld, Anleihen, Handel, Expansion, Kriege.

**Rundenablauf pro Spielerzug (fixe Reihenfolge, spielentscheidend):**

1. **Einnahmen-Phase:** Spieler erhält Rohstoffe.
2. **Ausgaben-Phase:** Spieler zahlt Rohstoff- und Finanz-Ausgaben. WICHTIG: Finanzielle Einnahmen eines Spielers sind immer finanzielle Ausgaben eines anderen Spielers (oder der Bank) — Geld entsteht/verschwindet nie einseitig.
3. **Aktions-Phase:** Freie Aktionen in beliebiger Reihenfolge: Anleihenhandel, Rohstoffhandel, Expansion, Kriege, etc. Danach „Zug beenden" → nächster Spieler.

**Kernproblem der aktuellen Fassung:** Der aktive Spieler sieht nicht, welche Schritte er noch erledigen muss, welche erledigt sind und welche (noch) gesperrt sind. Das Rundensystem muss als expliziter Zustandsautomat umgebaut werden.

---

## 1. Verbindliche Rahmenbedingungen

- **UI beibehalten:** Bestehende Screens/Composables optisch und strukturell so weit wie möglich unverändert lassen. Neue UI nur dort, wo der Plan sie explizit vorsieht (Zug-Checkliste, Hochformat-Layout). Refactoring unter der Haube, nicht Redesign.
- **Geld niemals als Float/Double.** Immer `Long` in kleinster Einheit. Bestehende Float-Verwendungen migrieren.
- **App muss nach jeder Etappe bau- und spielbar sein.** Keine Etappe hinterlässt einen kaputten Zwischenzustand.
- **Sprache:** Bestehende Benennungssprache der Codebasis beibehalten (Deutsch oder Englisch — bei der Bestandsaufnahme feststellen und konsistent fortführen).
- **Kein neues DI-Framework einführen**, außer es existiert bereits eines. Manuelle Konstruktor-Injektion genügt.
- **Ein Commit-/PR-Block pro Etappe**, mit kurzer Beschreibung, was geändert wurde und wie es getestet wurde.

---

## 2. Ziel-Architektur

```
:app        → Android, Compose-UI, ViewModels, Persistenz-Implementierung, Navigation
:domain     → reines Kotlin/JVM-Modul, KEINE Android-/Compose-Abhängigkeit
              GameState, Events, Reducer, Zustandsautomat, Regeln
:network    → (Etappe 8) Nearby-Connections-Brücke, Protokoll
```

**Muster:** MVI / Event-Sourcing.

- `GameState` ist eine immutable `data class` (nur `val`, nur immutable Collections) — Single Source of Truth.
- Jede Zustandsänderung ist ein `GameEvent` (sealed interface).
- Ein `Reducer` validiert Events und erzeugt den Folgezustand:
  `fun reduce(state: GameState, event: GameEvent): Result<GameState>` — ungültige Events werden mit begründetem Fehler abgelehnt, nie stillschweigend ignoriert oder teilweise angewandt.
- Die UI sendet nur Events an ein `GameViewModel`, das `StateFlow<GameState>` exponiert.
- Das Event-Log (Liste aller angewandten Events + Startzustand) ist die Persistenz- und spätere Netzwerk-Grundlage.

---

## Etappe 0 — Bestandsaufnahme & Absicherung

**Ziel:** Verstehen, was da ist, bevor etwas angefasst wird.

**Aufgaben:**
1. Vollständiges Inventar erstellen und als `docs/ARCHITEKTUR_IST.md` einchecken: alle Screens/Composables, wo Spielzustand gehalten wird (ViewModel? `remember`? Singletons? globale Objekte?), wie Geld/Rohstoffe typisiert sind, wie der Rundenwechsel aktuell funktioniert, welche Abhängigkeiten `build.gradle.kts` deklariert.
2. Build lauffähig machen/verifizieren (`./gradlew assembleDebug`).
3. Falls Logik existiert, die sich testen lässt: Charakterisierungstests schreiben, die das IST-Verhalten festhalten (insbesondere alle Berechnungen mit Geld/Rohstoffen).
4. Versionskatalog (`gradle/libs.versions.toml`) einführen bzw. aufräumen; `kotlinx-serialization`- und `kotlinx-coroutines`-Abhängigkeiten vorbereiten.

**Akzeptanzkriterien:** Doku existiert, Build grün, Tests grün.

---

## Etappe 1 — Domain-Modul extrahieren

**Ziel:** Spiellogik als reines Kotlin-Modul, unabhängig von Android.

**Aufgaben:**
1. Neues Gradle-Modul `:domain` (Plugin `org.jetbrains.kotlin.jvm`, dazu `kotlinx-serialization`). `:app` hängt von `:domain` ab, nie umgekehrt.
2. Fachliche Datentypen nach `:domain` verschieben bzw. dort neu modellieren und die bestehenden Verwendungen umhängen:
   - `SpielerId`, `Spieler` (Name, Rohstoffbestände, Geldkonto als `Long`, Anleihen-Portfolio, ggf. Territorien)
   - `Rohstoff` (enum/sealed), `RohstoffMenge`
   - `Anleihe` (Emittent, Nennwert, Zins, Laufzeit — an tatsächliche Spielregeln aus dem Bestandscode anpassen)
   - `GameState` (alle Spieler, Bank, Markt, Rundenzähler, aktiver Spieler, `ZugStatus` — kommt in Etappe 3)
3. Alle Typen mit `@Serializable` annotieren.
4. Geld-Typisierung: überall `Long`; falls die App bisher Float/Double nutzt, Migration inkl. Anzeige-Formatierung (Formatierung bleibt in `:app`).

**Akzeptanzkriterien:** `:domain` kompiliert ohne Android-Abhängigkeit; App verhält sich unverändert; keine Float/Double-Geldwerte mehr im Domain-Code.

---

## Etappe 2 — Events & Reducer (Event-Sourcing)

**Ziel:** Jede Zustandsänderung läuft über validierte Events.

**Aufgaben:**
1. `sealed interface GameEvent` in `:domain`, mindestens (an Bestandslogik anpassen/ergänzen):
   - `RohstoffEinnahme(spieler, mengen)`
   - `RohstoffAusgabe(spieler, mengen)`
   - `Transaktion(von: KontoId, an: KontoId, betrag: Long, grund: TransaktionsGrund)` — **eine** atomare Buchung für beide Konten; `KontoId` umfasst Spieler UND Bank
   - `AnleiheGekauft/-Verkauft/-Faellig(...)`
   - `RohstoffHandel(kaeufer, verkaeufer, rohstoff, menge, preis)`
   - `Expansion(...)`, `KriegErklaert/-Beendet(...)`
   - `SchrittAbgeschlossen(schritt)`, `PhaseAbgeschlossen(phase)`, `ZugBeendet`
2. `Reducer` mit `reduce(state, event): Result<GameState>`. Validierungen u.a.: Deckung (kein negatives Konto, außer Regeln erlauben Schulden — aus Bestandscode ableiten), nur der aktive Spieler darf zugbezogene Events auslösen, Event passt zur aktuellen Phase.
3. **Invariante als Test verankern:** Summe allen Geldes über alle Konten (inkl. Bank) ändert sich nur durch explizit als geldschöpfend/-vernichtend markierte Events (z.B. Zentralbank-Emission). Jede `Transaktion` ist summenneutral.
4. `GameEngine`-Klasse: hält Startzustand + Event-Log, wendet Events an, bietet `undo()` (Log kürzen, neu falten) und `redo()`.
5. Umfangreiche Unit-Tests für den Reducer (Happy Path + jede Ablehnungsregel).

**Akzeptanzkriterien:** Kein Code außerhalb des Reducers mutiert Spielzustand; Invarianten-Test grün; Undo/Redo funktioniert auf Engine-Ebene (UI-Anbindung erst Etappe 6).

---

## Etappe 3 — Zustandsautomat für das Rundensystem

**Ziel:** Der aktive Spieler sieht jederzeit: erledigt / offen / gesperrt.

**Aufgaben:**
1. In `:domain`:

```kotlin
enum class SchrittTyp(val pflicht: Boolean) {
    ROHSTOFF_EINNAHMEN(true),
    ROHSTOFF_AUSGABEN(true),
    FINANZ_AUSGABEN(true),
    ANLEIHEN_HANDEL(false),
    ROHSTOFF_HANDEL(false),
    EXPANSION(false),
    KRIEG(false),
    // an tatsächliche Spielregeln anpassen/ergänzen
}

sealed interface Phase {
    data object Einnahmen : Phase
    data object Ausgaben : Phase
    data object Aktionen : Phase
}

data class ZugStatus(
    val spieler: SpielerId,
    val phase: Phase,
    val erledigteSchritte: Set<SchrittTyp>,
)

enum class SchrittZustand { ERLEDIGT, VERFUEGBAR, GESPERRT }

data class SchrittInfo(
    val typ: SchrittTyp,
    val zustand: SchrittZustand,
    val begruendung: String?, // z.B. "Erst verfügbar in der Aktions-Phase"
)
```

2. `ZugAutomat` mit reinen Funktionen:
   - `schritte(state: GameState): List<SchrittInfo>` — vollständige Liste aller Schritte des Zuges mit Zustand + Begründung (Grundlage der Checklisten-UI)
   - `kannPhaseAbschliessen(state): Boolean` — alle Pflichtschritte der Phase erledigt
   - `kannZugBeenden(state): Boolean` — alle Pflichtphasen durchlaufen
   - Übergänge nur vorwärts: Einnahmen → Ausgaben → Aktionen → `ZugBeendet` (setzt `ZugStatus` für den nächsten Spieler zurück, erhöht ggf. Rundenzähler).
3. Reducer-Integration: Events, die zu einem Schritt gehören, werden abgelehnt, wenn `schritte()` diesen Schritt als `GESPERRT` ausweist. Die Regel lebt damit an genau einer Stelle.
4. Sonderfälle aus dem Bestandscode übernehmen (z.B. Schritte, die situativ entfallen — etwa keine Ausgaben fällig → Schritt gilt automatisch als erledigt).
5. Unit-Tests: kompletter Zugdurchlauf, Ablehnung von Phase-fremden Aktionen, Zug-Ende-Gate.

**Akzeptanzkriterien:** Es ist unmöglich, per Event eine Phase zu überspringen oder einen Pflichtschritt auszulassen; `schritte()` liefert für jeden Zustand eine konsistente, begründete Liste.

---

## Etappe 4 — ViewModel/MVI-Anbindung, UI umhängen

**Ziel:** Bestehende UI liest nur noch aus dem neuen State, ohne optische Änderung.

**Aufgaben:**
1. `GameViewModel` in `:app`: hält die `GameEngine`, exponiert `StateFlow<GameState>` (+ abgeleitete UI-Modelle über `map`/`stateIn`), nimmt Events über `onEvent(GameEvent)` entgegen; Reducer-Fehler als einmalige UI-Meldung (Snackbar/Dialog) via `SharedFlow`.
2. Alle Composables auf State-Hoisting umstellen: Parameter rein (State), Lambdas raus (Events). Spiellogik, die in Composables liegt, in Events/Reducer überführen.
3. `MutableList`/`var` im UI-State eliminieren; Listen-Darstellungen mit stabilen `key`s versehen; wo sinnvoll `@Immutable` auf UI-Modelle.
4. Alte State-Halter (Singletons, geteilte mutable Objekte) löschen.

**Akzeptanzkriterien:** App sieht aus und bedient sich wie vorher; ein vollständiges Spiel ist durchspielbar; keinerlei Spielzustand mehr außerhalb der Engine.

---

## Etappe 5 — Zug-Checkliste (neue UI)

**Ziel:** Sichtbarkeit der Schritte für den aktiven Spieler.

**Aufgaben:**
1. Composable `ZugChecklisteBar`, gespeist ausschließlich aus `ZugAutomat.schritte()`:
   - ERLEDIGT: Haken, ausgegraut
   - VERFUEGBAR: aktiv, tappbar → navigiert zum zugehörigen Bereich/Dialog
   - GESPERRT: deaktiviert; Tap zeigt die `begruendung`
   - Phasen-Fortschritt sichtbar (Einnahmen → Ausgaben → Aktionen)
2. „Phase abschließen"- und „Zug beenden"-Buttons nur aktiv, wenn der Automat es erlaubt; im deaktivierten Zustand Begründung anzeigen (fehlende Pflichtschritte auflisten).
3. Platzierung Tablet-Querformat: seitliche Rail oder Top-Bar-Bereich — an das bestehende Layout anpassen, bestehende Elemente nicht verdrängen.
4. Zugwechsel-Moment klar machen: kurzer „Spieler X ist am Zug"-Übergabescreen (wichtig für den Gerät-herumreichen-Modus).

**Akzeptanzkriterien:** Zu jedem Zeitpunkt ist ohne Erklärung erkennbar, was noch zu tun ist; kein Klickpfad erlaubt regelwidrige Aktionen.

---

## Etappe 6 — Persistenz, Autosave, Undo/Redo in der UI

**Ziel:** Kein Spielstandsverlust, komfortables Zurücknehmen.

**Aufgaben:**
1. Persistenz des Event-Logs + Startzustand via `kotlinx.serialization` (JSON) in DataStore oder als Datei im App-Verzeichnis; Autosave nach jedem erfolgreich angewandten Event (nebenläufig, gedrosselt).
2. App-Start: laufendes Spiel erkennen → „Fortsetzen / Neues Spiel".
3. Undo/Redo-Buttons in der UI (Engine-Funktion aus Etappe 2); Undo über Zuggrenzen hinweg nur nach Bestätigungsdialog.
4. Schema-Version im Savegame speichern; beim Laden prüfen und bei Inkompatibilität sauber ablehnen statt crashen.
5. Prozess-Tod-Test: Spiel starten, Events erzeugen, Prozess killen, App öffnen → identischer Zustand.

**Akzeptanzkriterien:** Rotation, Prozess-Tod und App-Neustart verlieren keinen Zustand; Undo/Redo konsistent inkl. `ZugStatus`.

---

## Etappe 7 — Adaptive Layouts (Hochformat-Handy)

**Ziel:** Vollwertig bedienbare, abgespeckte Hochformat-Variante; Tablet-UI unverändert.

**Aufgaben:**
1. `material3-window-size-class` (bzw. `material3-adaptive`) einbinden; Verzweigung pro Screen auf oberster Ebene: `Expanded`-Breite → bestehendes Tablet-Layout (unverändert), `Compact` → neue Hochformat-Variante. Gleiche ViewModels, gleiche Events — nur andere Layout-Composables.
2. Hochformat-Konzept: eine Spalte; Bereiche (Mein Zug / Markt & Handel / Spielerübersicht) über Bottom-Navigation oder Pager; `ZugChecklisteBar` als kompakte, fixierte Bottom-Bar oberhalb der Navigation; Detailansichten als eigene Navigationsziele statt Nebeneinander-Panels.
3. Bestehende Composables in kleinere, layout-agnostische Bausteine zerlegen, die beide Varianten teilen — dabei Tablet-Optik nicht verändern.

**Akzeptanzkriterien:** Komplettes Spiel auf einem Handy im Hochformat durchspielbar; Tablet-Querformat pixelig unverändert bzw. nur minimal berührt.

---

## Etappe 8 — Geräte-Brücke (WLAN/Bluetooth, Mehrgeräte-Betrieb)

**Ziel:** Jeder Spieler nutzt optional sein eigenes Gerät; ohne Internet/Router.

**Technologie:** Google **Nearby Connections API** (`com.google.android.gms:play-services-nearby`), Strategy `P2P_STAR`. Abstrahiert Bluetooth + WLAN automatisch.

**Architektur: Host-autoritativ.**
- Ein Gerät ist Host: hält die einzige `GameEngine`, validiert alles.
- Clients senden ausschließlich `GameEvent`-Wünsche; der Host wendet sie über den Reducer an (Ablehnung → Fehlermeldung nur an den Absender zurück) und broadcastet angewandte Events an alle.
- Clients falten empfangene Events lokal in eine Read-only-Kopie des States. Kein Client mutiert selbständig.

**Aufgaben:**
1. Neues Modul `:network`. Protokoll-Nachrichten (`@Serializable`):
   - `Hello(protokollVersion, spielerName)` / `Welcome(spielerId, vollstaendigerState, eventLogLaenge)`
   - `EventVorschlag(clientEventId, event)` / `EventAngewandt(logIndex, event)` / `EventAbgelehnt(clientEventId, grund)`
   - `SyncAnfrage(abLogIndex)` / `SyncAntwort(events)` für Reconnect
2. Rollen `Host`/`Client` hinter gemeinsamem Interface `SpielVerbindung`; Lobby-UI: „Spiel hosten" (Advertising) / „Spiel beitreten" (Discovery, Geräteliste, Verbindungsbestätigung).
3. Reconnect: Client speichert `logIndex`; nach Abbruch `SyncAnfrage`, Host liefert Delta oder Voll-State. Host-Verlust: Meldung + Spiel lokal aus letztem Autosave fortsetzbar.
4. Protokollversion in jeder `Hello`; bei Mismatch Verbindung mit verständlicher Meldung ablehnen.
5. Berechtigungen versionsabhängig behandeln (u.a. `BLUETOOTH_ADVERTISE/CONNECT/SCAN`, `NEARBY_WIFI_DEVICES`, auf älteren Versionen Location) inkl. Runtime-Abfrage mit Erklärung.
6. Einzelgerät-Modus bleibt der Default und funktioniert ohne jede Berechtigung.
7. UI pro Client: eigener Spieler im Fokus; Aktionen fremder Spieler nur lesend; Zug-Checkliste zeigt beim Nicht-aktiven „Spieler X ist am Zug".

**Akzeptanzkriterien:** 2+ Geräte spielen ein konsistentes Spiel; Ablehnungen erscheinen nur beim Verursacher; Verbindungsabbruch + Reconnect verliert nichts; Einzelgerät-Modus unverändert.

---

## Arbeitsweise für den Agenten

1. Etappen strikt in Reihenfolge; nicht mit einer neuen beginnen, bevor die Akzeptanzkriterien der vorherigen erfüllt sind.
2. Vor jeder Etappe kurz gegen den Bestandscode prüfen, ob Annahmen dieses Plans zutreffen (Schritt-Liste, Anleihe-Modell, Sonderregeln); Abweichungen in `docs/ARCHITEKTUR_IST.md` dokumentieren und das Domain-Modell entsprechend anpassen — die Spielregeln im Code sind die Wahrheit, nicht dieser Plan.
3. Nach jeder Etappe: `./gradlew build` und alle Tests grün; kurzer Eintrag in `docs/UMBAU_LOG.md` (was, warum, offene Punkte).
4. Bei Mehrdeutigkeiten in den Spielregeln: nicht raten — als `TODO(Regelfrage)` markieren, im Log auflisten und die konservativste Auslegung implementieren.
