# Abschluss des KI-Trainingsumbaus

Stand: 21. Juli 2026

## Umgesetzte Architektur

Der autoritative fachliche Pfad liegt in Android-freiem Kotlin/JVM-Code:

```text
SpielAktion
  → StandardSpielEngine
  → SpielEreignis-Liste
  → SpielRegelwerk
  → SpielZustand
  → SpielBeobachtung und AktionsRaum
```

`core/domain` enthält Fachmodell, Aktion, Ereignis, Regelwerk, Rundenfolge,
Partieende, Aktions-/Beobachtungsauswertung, Invarianten und deterministische
Zufallsabstraktion. `core/application` kapselt Spielsitzung, atomare Folgen,
Undo/Redo und Persistenzports. `tools/simulation` ist ein eigenständiges JVM-Modul
mit ausschließlicher Projektabhängigkeit auf `core/domain`. Android, Server und
Simulation verwenden `StandardSpielEngine`.

## Öffentliche Schnittstellen

- `SpielEngine.pruefe`, `anwenden` und `erlaubteAktionen`
- `AktionsAuswertung.erlaubteAktionen`
- `BeobachtungsAuswertung.fuerSpieler`
- `ZustandsInvarianten.pruefe`
- `TrainingsUmgebung.reset` und `step`
- `SpielAgent.waehleAktion`
- `BelohnungsModell.berechne`
- `EpisodenJsonl.exportieren` und `importieren`
- `SpielEpisode.replay`

Alle Engine-Schritte prüfen Eingang und Ausgang auf fachübergreifende Invarianten.
Ein ungültiger Schritt schreibt kein Ereignis und verändert den unveränderlichen
Eingangszustand nicht.

## Aktionsmodell

`SpielAktion` ist von `SpielEreignis` getrennt. Abgedeckt sind Prozug und Zugende,
Runde 0, Kartenbau/-aufwertung/-abriss, Schienen, Seewege, Einheiten, Konflikt,
Anleihen, Schuldenstrich, Außenhandel, Handels- und Anleihenangebote sowie Aufgabe.
Eine Aktion darf mehrere Ereignisse erzeugen. Rundenwechsel, Angebotsablauf,
Markt-/Leitzinsfortschritt und neuer Prozug sind interne Ereignisfolgen.

IDs für Anleihe, Seeweg, Einheit und Angebot stammen aus serialisierten Zählern im
`SpielZustand`. Der Aktionsgenerator sortiert deterministisch, begrenzt
Kartenoptionen und prüft jede Option mit der Engine. Bewegungen werden als Einheit
plus einzelne nächste Kante angeboten.

## Partieende

`SpielZustand.ergebnis` enthält `SpielErgebnis`. Aufgabe ist eine ausdrückliche
Aktion. Der letzte spielfähige Spieler erhält den Sieg; ein zweiter Platz erhält
keine Siegprämie. Nach `PartieBeendet` sind aktiver Spieler und Zugstatus leer und
normale Aktionen werden abgelehnt. `truncated` bleibt ausschließlich ein technischer
Umgebungsstatus.

Eine reguläre, von Aufgabe/Elimination unabhängige Siegbedingung ist im vorhandenen
Regelhandbuch nicht festgelegt. `REGULAERER_SIEG` bleibt daher mit präziser
Dokumentation reserviert, statt Vermögens- oder Punktregeln zu erfinden.

## Beobachtungsmodell

`SpielBeobachtung` Version 1 enthält eigenen vollständigen Wirtschaftszustand,
öffentliche Gegnerinformationen, Markt, Karte, sichtbare Angebote, Zug und
Ergebnis. Gegnerische Lager, Geldkonten und Passwörter sind nicht sichtbar.
Gerichtete Angebote sehen nur Beteiligte. Objektlisten und Kartenpositionen sind
stabil sortiert; IDs bleiben innerhalb der Episode stabil.

`BeobachtungsKodierung` Version 1 projiziert auf normalisierte feste Arrays mit
Padding. Der Fachzustand behält ganzzahliges `Geld`; Float entsteht nur in dieser
Modellprojektion. Es besteht keine PyTorch-, TensorFlow- oder ONNX-Abhängigkeit.

## Belohnungsmodell und Agenten

`PotentialBelohnungsModell` Version 1 verwendet:

- Terminal: Gewinner `+1`, Verlierer `-1`, echtes Unentschieden `0`.
- Shaping: `beta * (gamma * potential(nachher) - potential(vorher))`.
- Gewichte: Liquidität 0,35; Nettovermögen 0,20; produktive Kapazität 0,20;
  aktive Infrastruktur 0,15; Schuldendienstfähigkeit 0,10; `beta=0,02`,
  `gamma=0,99`.

Das Modell belohnt weder Rundendauer noch bloßes Überleben, Bauen, Kreditaufnahme,
Umsatz oder unbereinigten Geldbestand direkt. Die Gewichte sind eine versionierte
Baseline, kein abschließend kalibriertes KI-Ziel.

`ZufallsAgent` wählt gleichverteilt aus dem Aktionsraum und benutzt nur injizierten
Seed-Zufall. `SicherheitsAgent` priorisiert Verpflichtungen, Versorgung und einen
sauberen Prozug. `WirtschaftsAgent` priorisiert Verpflichtungen, Produktion,
finanzierbare Infrastruktur und Handel. Beide Heuristiken enthalten eine späte
Aufgaberegel, damit Baseline-Partien ohne erfundene reguläre Siegbedingung enden.

## Simulationsablauf

`KleineWirtschaftsBaseline` erzeugt aus dem Seed drei Spieler, eine kompakte Karte
mit 18 Geländefeldern auf drei Startinseln und je einen Hauptbahnhof. `reset` startet
den zwingenden Prozug. `step` akzeptiert ausschließlich eine Aktion aus dem zuvor
ausgegebenen `AktionsRaum`, führt alle Engine-Ereignisse aus und liefert den nächsten
echten Entscheidungspunkt. Umgebung und Zufallsquelle sind instanzlokal.

Die CLI unterstützt Spielanzahl, Seed, Agenten, Szenario-ID, Entscheidungslimit,
Ausgabeordner und `--parallel`. Ergebnisse werden unabhängig von Parallelität nach
Spielindex sortiert.

## Trainingsdatenformat

JSONL-Format 2 speichert je Zeile eine vollständige `SpielEpisode`:

- Regelversion `2.0.0`, Beobachtungsversion 1, Aktionsversion 1,
- Spiel-/Szenario-ID und Seed,
- passwortbereinigter Startzustand,
- alle Entscheidungen aller Spieler mit Beobachtung, Aktionsraum, gewählter Aktion,
  Belohnung und gegebenenfalls Ergebnis,
- vollständiger Ereignisverlauf,
- fachliches Ergebnis und getrennte Truncation.

Kotlin-Import prüft Format- und Unterversionen, lückenlose Entscheidungsnummern,
Spiel-ID und Passwortfreiheit. `replay()` rekonstruiert den Endzustand. Der
Python-Parser prüft Format 2 sowie dieselben Kopfversionen und lehnt Passwortfelder
erneut ab. Das vorbereitete NDJSON-Environment dupliziert keine Kotlin-Regel.

## Persistenz und Kompatibilität

Application-Schema und JSON-Schema stehen auf Version 2; JSON-Schema 1 kann geladen
und beim nächsten Speichern aktualisiert werden. Room bleibt Datenbankschema 4 mit
allen bisherigen Migrationen. Das Fachspielstandformat 3 akzeptiert Format 2;
Format 1 wird wegen des nicht rekonstruierbaren alten Prozug-Snapshots ausdrücklich
und mit Fehlermeldung abgelehnt. Historische Ereignisse werden nicht erfunden.

## Ausgeführte Tests und Massensimulation

Erfolgreich ausgeführt:

```text
./gradlew test assembleDebug :apps:server:test :tools:simulation:test \
  :adapters:persistence-room:compileDebugAndroidTestKotlin
./gradlew :tools:simulation:massentest
cd apps/web && npm ci && npm run build
cd tools/ai-python && python -m unittest discover -s tests
```

Massentest auf der kompakten Karte:

- 1.000/1.000 Partien fachlich beendet,
- 0 Truncations,
- 0 Regel-/Simulationsfehler,
- 44.841 Entscheidungen,
- durchschnittlich 44,841 Entscheidungen und 0,232 Runden,
- Replay jeder Episode identisch zum Umgebungsendzustand,
- Python-Parser las alle 1.000 Episoden.

Die geringe Rundenzahl zeigt, dass ein gleichverteilter Zufallsagent häufig früh
aufgibt. Der Lauf beweist Robustheit und Replay, nicht Trainingsqualität.

## Verbleibende Legacy-Abhängigkeiten

`GameViewModel` enthält keine Datenbankzugriffe und hält selbst keine zweite
Regelimplementierung, reicht aber `aktuellesSpielOderNull` an ältere Android-
Bildschirme weiter. `LegacySpielKoordinator` hält deshalb weiterhin das veränderliche
`Spiel`, pflegt normalisierte Tabellen und übersetzt Legacy-Modelle.

Der produktive Rundenwechsel und Kartenbau laufen über Aktionen. Android-Handels-
und Anleihendialoge benutzen noch die private Ereignisbrücke; `KartenSpiel.kt`
verwendet `SpielRegelwerk` weiterhin für nichtautoritative Vorschauen. Das alte
`Spiel`, `Spieler`, `Rohstoffe`, `Bauteil`, `Zahlungsmittel`, `Handelsregister`,
`Kriegsregister` und `FachmodellZuordnung.kt` bleiben deshalb erhalten. Details und
Löschkriterien stehen in `legacy-migration.md`.

## Bekannte Einschränkungen

- Keine fachlich definierte reguläre Siegbedingung außer letztem spielfähigem
  Spieler; Insolvenz-/Kriegs-Elimination ist als Modell vorbereitet, aber noch nicht
  als vollständige automatische Endkette angebunden.
- Das Trainingsszenario verwendet eine generierte kompakte Karte, nicht die
  Android-Kartenressourcen.
- Sicherheits- und Wirtschaftsagent sind unkalibrierte Baselines; ihre Spielstärke
  ist noch nicht statistisch gegen Zufall bewertet.
- Die Kodierungsmaske benutzt 512 Hashplätze und kann Kollisionen enthalten.
- Room-Instrumentierungstests wurden kompiliert, aber nicht auf einem Gerät oder
  Emulator ausgeführt.
- Teile der Android-UI lesen weiterhin das Legacy-Spielmodell.

## Nächste Schritte für das Modelltraining

1. Android-Kartenvorlagen und Szenariokonfiguration in ein gemeinsames JVM-
   Ressourcenmodul verschieben und ein repräsentatives Karten-/Startzustands-Corpus
   definieren.
2. Eine kollisionsfreie, versionierte Aktionsindex-Tabelle sowie separate Masken für
   hierarchische Ziel-/Mengen-/Preisparameter implementieren.
3. Agenten-Liga mit festen Seeds ausführen, Heuristiken statistisch bewerten und erst
   danach Potentialgewichte beziehungsweise ein erstes Policy-/Value-Modell
   kalibrieren.
