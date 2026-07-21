# Architektur

## Ziel und Modulübersicht

Die Regeln werden einmalig im Kotlin/JVM-Kern ausgeführt. Ein Browser sendet
nur Absichten an den Server; er berechnet keinen autoritativen Zustand. Android
kann dieselbe Engine lokal verwenden und über Room speichern. Simulationen
laufen ohne Android-Laufzeit.

| Modul | Verantwortung | Darf abhängen von |
| --- | --- | --- |
| `core/domain` | Fachmodelle, Regeln, Ereignisse, Aktionen, Engine, seed-basierte Zufallsquelle | Kotlin/JVM-Bibliotheken |
| `core/application` | Spielsitzung, Atomizität, Undo/Redo, Laden/Speichern, Ports, Read Models, Zustandshash | `core/domain` |
| `adapters/persistence-room` | Room-Datenbank v4, Migrationen, DAOs, Room-Mapping, `RaumSpielAblage` | Core |
| `adapters/persistence-json` | In-Memory- und atomare JSON-Dateiablage | Core |
| `adapters/protocol-json` | versionierte, vom Domain-Modell getrennte API-DTOs | Core |
| `apps/android` | Compose, Navigation, Lifecycle, UI-Zustände, 3D, PDF, Diagramme | Adapter und Core |
| `apps/server` | HTTP-Transport, serverseitige Orchestrierung und Fehlerabbildung | Adapter und Core |
| `apps/web` | nicht autoritativer TypeScript-Browser-Client | Server-HTTP-API |
| `tools/simulation` | Headless-Episoden, Strategie, externe Bewertung, JSONL-Export | Protokoll und Core |
| `tools/ai-python` | JSONL-Parser und NDJSON-Umgebungsschnittstelle | keine Kotlin-Regelimplementierung |

Die erlaubte Richtung ist:

```text
apps ───────┐
            ├──> adapters ──> core/application ──> core/domain
tools ──────┘          └──────────────────────────> core/domain
```

`core` kennt weder Apps noch konkrete Adapter. Der Gradle-Task
`architekturPruefen` kontrolliert Android-/Compose-Importe in Core,
Room-Importe außerhalb des Room-Adapters und rückwärts gerichtete
Core-Projektabhängigkeiten. Er wird von den `test`- und `check`-Tasks ausgeführt.

## Aktion, Ereignis und Zustand

- `SpielAktion` ist eine angeforderte Entscheidung, etwa
  `VerarbeitungAusfuehren` oder `KriegErklaeren`. Sie kann abgelehnt werden.
- `SpielEreignis` ist eine bereits akzeptierte, serialisierbare Änderung. Nur
  Ereignisse werden dem Verlauf hinzugefügt.
- `SpielZustand` ist die vollständig ableitbare Sicht nach dem Falten von
  Startzustand und Ereignisfolge durch `SpielRegelwerk`.
- `SpielEngine` validiert Aktionen, erzeugt ein oder mehrere Ereignisse und
  liefert `SpielSchrittErgebnis` mit Folgezustand und Ereignissen. Außerdem
  liefert sie erlaubte Aktionen für einen Spieler.

Neue Clients rufen nicht direkt `SpielRegelwerk.wendeAn` auf. Die noch nicht in
`SpielAktion` überführten Android-Funktionen verwenden vorübergehend die
explizit benannte Ereignisbrücke in `SpielSitzung`; sie ist in der
Legacy-Migrationsliste erfasst.

## Ablauf einer Android-Aktion

1. Compose sendet eine UI-Absicht an das 95-zeilige `GameViewModel`.
2. Das ViewModel delegiert an den `LegacySpielKoordinator`; für den bereits
   migrierten vertikalen Schnitt wird daraus eine `SpielAktion`.
3. `SpielSitzung` ruft die gemeinsame `SpielEngine` auf und übernimmt alle
   erzeugten Ereignisse atomar.
4. `SpielRegelwerk` erzeugt den neuen `SpielZustand`; StateFlows aktualisieren
   die Anzeige.
5. Der Room-Adapter speichert Startzustand und Ereignisverlauf. Das ViewModel
   greift weder auf `AppDatabase` noch auf DAOs zu.

Der große Übergangskoordinator hält noch Android-spezifische Ablaufanschlüsse
und Legacy-Konvertierungen. Er ist nicht Teil von Core und sein kontrollierter
Abbau ist in `legacy-migration.md` festgehalten.

## Ablauf einer Web-Aktion

1. Der Browser lädt Zustand und erlaubte Aktionen über `/api/v1` und sendet ein
   versioniertes `SpielAktionDto`.
2. Der HTTP-Layer dekodiert und prüft Transportdaten, enthält aber keine
   Fachberechnung.
3. `SpielServerDienst` lädt die Sitzung über `SpielDienst` und ruft dieselbe
   injizierte `SpielEngine` wie Android und Simulation auf.
4. Nur eine akzeptierte Aktion wird samt Ereignissen gespeichert. Ungültige
   Aktionen liefern eine strukturierte Fehlerantwort und verändern nichts.
5. Die Antwort enthält einen für Web-Clients bereinigten Zustands-DTO;
   Passwort-Hashes werden nicht ausgegeben.

Der Server verwendet bewusst den JDK-HTTP-Server als kleinen JVM-Transport. Die
Application- und Domain-Grenzen erlauben einen späteren Austausch gegen Ktor,
ohne Regeln oder Persistenz umzubauen.

## Ablauf eines Simulationsschritts (Ist-Stand vor Trainingsumbau)

1. Die Simulation fragt `SpielEngine.erlaubteAktionen` ab.
2. Eine austauschbare `SpielStrategie` wählt mit einer lokalen,
   episode-spezifischen `SeedZufallsquelle` eine Aktion.
3. `SpielSitzung` führt die Aktion mit derselben Engine aus.
4. Eine separate `Bewertungsfunktion` vergleicht vorher/nachher. Die neutrale
   Beispielbewertung ist ausdrücklich nur eine Baseline.
5. Beobachtung, erlaubte und gewählte Aktion, Belohnungskomponenten,
   Folgezustand und Ereignisse werden als eine JSONL-Zeile exportiert.

Der am 21. Juli 2026 geprüfte Ausgangsstand besitzt noch keinen fachlichen
Endzustand: Episoden enden deshalb technisch am Schrittlimit. Auch
`RundenwerteAktualisiert` wird beim vollen Rundenwechsel noch durch den
Android-`LegacySpielKoordinator` erzeugt. Diese beiden Grenzen sind keine
Zielarchitektur; der laufende KI-Trainingsumbau ersetzt sie durch Domain-Regeln,
spielerbezogene Beobachtungen und eine echte `reset`/`step`-Umgebung. Der
detaillierte Codebefund steht in `KI_TRAININGSUMBAU_ISTSTAND.md`.

## Persistenz

Der Port `SpielAblage` gehört zu `core/application`. Beide Adapter erfüllen
denselben Vertrags-Test.

- JSON speichert einen Umschlag mit `schemaVersion`, `engineVersion`,
  `spielId`, `startzustand`, `ereignisse` und optionalem `seed`. Der
  Datei-Adapter schreibt erst eine temporäre Datei und ersetzt dann atomar,
  soweit das Dateisystem dies unterstützt.
- Room speichert den maßgeblichen Fachverlauf in `FachSpielstand`. Die
  normalisierten Tabellen `GameData`, `PlayerData`, `BuildData`, `ControlData`,
  `RoundData`, `TradeData`, `CreditData` und `ContractData` bleiben für
  Legacy-Kompatibilität erhalten. Keine Migration wurde entfernt; vorhanden
  sind weiterhin 1→2, 2→3 und 3→4.
- Die In-Memory-Ablage dient Tests, Server-Smoke-Tests und kurzlebigen Läufen.

## Versionierung und deterministische Wiederholung

Speicher- und Protokollumschläge besitzen eine Schema-/Protokollversion; die
Regelsemantik besitzt `engineVersion`. Unbekannte JSON-Schemaversionen werden
abgelehnt, statt still falsch gelesen zu werden. DTOs und Domain-Typen bleiben
getrennt, damit spätere Protokollmigrationen die Engine nicht verändern.

Der Zustand wird ausschließlich aus `startzustand` und der geordneten Liste von
`SpielEreignis` rekonstruiert. Die Engine verwendet keine Systemzeit, kein
Dateisystem, Netzwerk, Android, Room oder globale Zufallsquelle. Benötigte
Zufälligkeit wird per `Zufallsquelle` und Seed injiziert. Der kanonische
`ZustandsHash` serialisiert sortiert und berechnet SHA-256; gleiche Eingaben
ergeben damit denselben Hash.

## Bewusst offene Migrationsgrenzen

Die zentralen Prozug-, Zugende-, Warenkorb-, Rohstoffhandels- und
Konfliktaktionen verwenden bereits `SpielAktion`. Bauen, Anleihen und Teile des
Rundenwechsels laufen noch über die atomare Ereignisbrücke und bestehende
Legacy-Auswertungen. Diese Grenze bleibt kompilierend und getestet, ist aber
kein Endzustand; konkrete Löschkriterien stehen in `legacy-migration.md`.
