# Umbau-Log

## Etappe 0 - Bestandsaufnahme & Absicherung

- Architektur-Ist dokumentiert: Screens/Composables, Zustandshaltung, Persistenz, Geld-/Rohstofftypen, Rundenwechsel und Abhängigkeiten.
- Charakterisierungstests für zentrale Geldoperationen und Testspiel-Basisdaten ergänzt.
- Versionskatalog für die kommenden Module um Kotlin/JVM, Serialization und Coroutines vorbereitet.
- `./gradlew build` wird als Abschlussprüfung der Etappe verwendet.

Offene Punkte:

- Kein Regelfragen-Blocker für Etappe 0.
- Für Etappe 1 muss `Rohstoffe` von Compose-`Color` entkoppelt werden, bevor der Typ in ein reines Kotlin-Modul wandern kann.
- `Zahlungsmittel` muss in späteren Etappen auf `Long` in kleinster Einheit migriert werden; die aktuellen Tests halten nur das bestehende Verhalten fest.

## Etappe 1 - Domain-Modul und Geldbasis

- Neues reines Kotlin/JVM-Modul `:domain` angelegt.
- `:app` hängt von `:domain` ab; `:domain` hat keine Android- oder Compose-Abhängigkeit.
- Serialisierbaren Geldtyp `Geld` eingeführt: kleinste gespeicherte und berechnete Einheit ist Cent, also `100L == 1 Mark`.
- Erste serialisierbare Domain-Typen ergänzt: `SpielerId`, `KontoId`, `Rohstoff`, `RohstoffMenge`, `Spieler`, `Anleihe`, `GameState`.
- Domain-Tests für Cent-basierte Geldrechnung ergänzt.
- Android-freie Bauteiltypen (`BauteilTyp`, `BauteilArt`) mit Kosten, Ertrag und Verbrauch ergänzt.
- App-seitigen Adapter vom bestehenden `Spiel` in den neuen `GameState` ergänzt, damit die neue Domain-Struktur gegen reale Bestandsdaten getestet werden kann.
- Adaptertests für Geld-, Marktpreis-, Spieler- und Bauteilabbildung ergänzt.
- `GameState` um Warenkorb und Leitzins in Basispunkten erweitert.
- JSON-Rundlauf-Test für `GameState` ergänzt, damit das Domain-Modell als spätere Event-Log-/Savegame-Basis serialisierbar bleibt.
- `GameViewModel` exponiert zusätzlich `domainState: StateFlow<GameState?>`; bestehendes `aktuellesSpiel` und neuer Domain-State werden beim Erstellen/Laden gemeinsam aktualisiert.

Offene Punkte:

- Bestehende App-Modelle verwenden noch `Zahlungsmittel`; die Migration auf `Geld` erfolgt schrittweise.
- Bestehende App-UI nutzt weiterhin `Rohstoffe` mit Compose-`Color`; die Domain nutzt bereits Android-freie `Rohstoff`- und `BauteilTyp`-Typen.

## Etappe 2 - Events, Reducer und Engine

- `GameEvent` als serialisierbares sealed Interface angelegt, inklusive Rohstoffbuchungen, Geldtransaktion, Rohstoffhandel, Anleihe-, Expansion-, Kriegs- und Zugereignissen.
- Erste Reducer-Scheibe implementiert:
  - `RohstoffEinnahme`
  - `RohstoffAusgabe`
  - `Transaktion`
  - `RohstoffHandel`
- Reducer lehnt negative Rohstoffbestände, Unterdeckung und nicht-positive Beträge/Mengen ab.
- Summenneutralität von `Transaktion` und `RohstoffHandel` durch Tests verankert.
- `GameEngine` mit Event-Log, `apply`, `undo` und `redo` ergänzt.
- `GameState` um `bankAnleihen` erweitert, damit Anleihenbesitz bei Bank/Nicht-Spieler-Konten abbildbar ist.
- Reducer-Logik für `AnleiheGekauft`, `AnleiheVerkauft` und `AnleiheFaellig` ergänzt.
- Tests verankern summenneutralen Anleihenverkauf und Entfernung fälliger Anleihen aus Portfolios und offenem Bestand.
- `GameState` um einfache `Konflikt`-Menge erweitert.
- Reducer-Logik für `Expansion`, `KriegErklaert` und `KriegBeendet` ergänzt.
- Tests verankern Rohstoffverbrauch bei Expansion und Kriegserklärung/-ende.

Offene Punkte:

- Zugphasen-Events sind definiert, aber noch nicht reducer-seitig implementiert.
- Aktive-Spieler-/Phasenprüfung folgt mit dem Zustandsautomaten in Etappe 3.

## Etappe 3 - Zugautomat

- Domain-Typen für `SchrittTyp`, `Phase`, `ZugStatus`, `SchrittZustand` und `SchrittInfo` ergänzt.
- `GameState` trägt nun optionalen `zugStatus`, standardmäßig für den aktiven Spieler in der Einnahmen-Phase.
- `ZugAutomat.schritte`, `kannPhaseAbschliessen`, `kannZugBeenden` und Phasenübergang ergänzt.
- Reducer verarbeitet `SchrittAbgeschlossen`, `PhaseAbgeschlossen` und `ZugBeendet`.
- Tests verankern verfügbare/gesperrte Schritte, Pflichtschritt-Gates und Spielerwechsel beim Zugende.
- Fachliche Events werden gegen `ZugAutomat.schritte()` gegated und bei phasenfremder Nutzung abgelehnt.
- Reducer prüft fuer gegatete Events den aktiven Spieler anhand des primaeren Event-Spielers.
- Situativ entfallende Pflichtschritte umgesetzt:
  - `ROHSTOFF_EINNAHMEN` bleibt immer manuell, da der Ertrag vom physischen Spielfeld kommt.
  - `ROHSTOFF_AUSGABEN` ist automatisch erledigt, wenn der aktive Spieler keine verbrauchenden Bauteile/Regionen hat.
  - `FINANZ_AUSGABEN` ist automatisch erledigt, wenn keine eigene Anleihe bei Bank oder anderen Spielern liegt.
- `Schuldenstrich` als Finanz-Ausgaben-Event ergänzt.
- Schuldenstrich baut normale Bahnhöfe/Häfen ab, stuft Großbahnhöfe/Großhäfen zu normalen Gebäuden zurück und entfernt die vom Nutzer angegebene Anzahl Eisenbahnlinien.
- Bankgehaltene eigene Anleihen werden gelöscht; von anderen Spielern gehaltene eigene Anleihen werden durch frisches Geld zum Nennwert ausgezahlt und anschließend gelöscht.
- Nach einem Schuldenstrich wird der aktuelle Zug direkt zum nächsten Spieler weitergeschaltet.
- Reducer-Tests verankern Auszahlung, Bauteilabbau, Anleihenlöschung, Zugübersprung und Ablehnung im Krieg.
- `UeberschuldungsStatus` ergänzt und am Ende des eigenen Zuges aktualisiert.
- Überschuldung zählt nur bankgehaltene eigene Anleihen, inklusive Nennwert und aller Laufzeit-Zinsen.
- Marktwert wird aus Einheiten/Bauwerken über vorhandene Marktpreise berechnet; Rohstoffe ohne Preis gehen mit 0 ein.
- Friedliche Überschuldungsserien werden bei aktiver Kriegsbeteiligung unterbrochen, ab dem dritten betroffenen Zug gewarnt und nach mehr als drei betroffenen Zügen als schuldenstrichfällig markiert.
- Reducer-Tests verankern Warn-/Fälligkeitsstatus, Bankanleihen-Filter und Kriegsunterbrechung.
- Ein automatisch fälliger Schuldenstrich hält den Spieler am Zugende in der Aktionsphase und blockiert weitere Events, bis der Schuldenstrich mit der im Dialog eingegebenen Bahnweg-Anzahl gebucht wurde.
- Reducer-Tests verankern den blockierenden Eingabezustand und die anschließende automatische Zugweitergabe nach dem Schuldenstrich.

Offene Punkte:

- Kein Regelfragen-Blocker für Etappe 3.

## Etappe 4 - ViewModel/MVI-Anbindung

- `GameViewModel` initialisiert beim Laden/Erstellen eines Spiels eine `GameEngine` aus dem gemappten `GameState`.
- `GameViewModel.onEvent(GameEvent)` als zentrale Domain-Event-Einstiegsstelle ergänzt.
- Erfolgreiche Domain-Events aktualisieren `domainState`; abgelehnte Events werden über `domainFehler` als `SharedFlow<String>` gemeldet.
- `Navigation` sammelt `domainFehler` und zeigt Reducer-/Domain-Fehler über einen globalen Material-`SnackbarHost`.
- Das Spielmenü liest den aktuellen Spieler und die Phase aus `domainState` und zeigt sie im bestehenden unteren Textfeld an.
- Bestehende `aktuellesSpiel`-UI bleibt unverändert lauffähig, während Screens schrittweise auf `domainState` und Events umgehängt werden.

Offene Punkte:

- Bestehende Composables lesen größtenteils noch aus `aktuellesSpiel`; das Umhängen auf State-Hoisting und `onEvent` folgt schrittweise.
