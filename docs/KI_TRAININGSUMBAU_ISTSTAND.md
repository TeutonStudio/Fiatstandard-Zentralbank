# Ist-Stand vor dem KI-Trainingsumbau

Stand der Prüfung: 21. Juli 2026, Branch `master`, Commit `0c78565`.

## Tatsächliche Modulstruktur

Die im Auftrag genannten alten Projektpfade `:app` und `:domain` existieren
nicht mehr. Der Code liegt bereits in `:apps:android`, `:core:domain`,
`:core:application`, den drei Adaptermodulen, `:apps:server` und
`:tools:simulation`. Diese vorhandene, strengere Trennung bleibt maßgeblich;
der Trainingsumbau wird deshalb in `:core:domain` und `:tools:simulation`
fortgesetzt.

## Bereits im Domain-Kern

- unveränderlicher `SpielZustand`, Karten- und Finanzmodelle,
- serialisierbare `SpielEreignis`-Hierarchie und Replay über `SpielAblauf`,
- Prozug-Snapshot, Eigenversorgung, Verarbeitung und Verbindlichkeiten,
- Karten-, Handels-, Anleihen-, Konflikt-, Insolvenz- und Zugregelwerke,
- erste `SpielAktion`-Hierarchie und `StandardSpielEngine`,
- seed-basierte `Zufallsquelle` ohne Android-Abhängigkeit.

## Noch außerhalb des Domain-Kerns

- Der letzte Zug einer Runde erhöht im Domain-Modell bereits den
  Rundenzähler. Marktpreise und Leitzins werden danach jedoch noch durch
  `LegacySpielKoordinator.beginneNaechsteRunde()` aus dem veränderlichen
  Android-`Spiel` berechnet und per `RundenwerteAktualisiert` zurückkopiert.
- Bau-, Abriss-, Seeweg-, Einheiten- und Anleihenvorgänge besitzen Ereignisse
  und Regelwerk, aber noch keine vollständigen Spieleraktionen. Android nutzt
  hierfür die Legacy-Ereignisbrücke.
- Es gibt weder ein fachliches Partieergebnis noch ausgeschiedene Spieler,
  Handelsangebote, eine Agentenbeobachtung oder einen vollständigen
  Aktionsraum.
- `tools/simulation` schreibt einzelne Zustands-DTOS bis zum Schrittlimit. Das
  Modul besitzt noch keine `reset`/`step`-Umgebung und kann kein fachliches
  Spielende erreichen.

## Aktive Legacy-Schreibwege

- `LegacySpielKoordinator` hält gleichzeitig `SpielSitzung` und das alte
  `Spiel` und synchronisiert Runden-, Handels-, Bau- und Anleihendaten.
- `GameViewModel` delegiert zwar nur, exponiert aber weiterhin
  `aktuellesSpielOderNull` und eine öffentliche Ereignisbrücke.
- `KartenSpiel.kt` prüft und wendet Bauereignisse teilweise direkt über
  `SpielRegelwerk` an.
- Die normalisierten Room-Tabellen werden neben dem maßgeblichen
  `FachSpielstand` weitergeschrieben.

## Produktive Benutzer des alten `Spiel`

Direkte Benutzer sind `LegacySpielKoordinator`, `GameViewModel`, Navigation,
Spielerstellung, Marktplatz, Außenhandel, Anleihenansicht, Handelsdialoge und
mehrere ältere Diagramm-/Ausgabefunktionen. Der Typ darf daher erst nach der
bereichsweisen Umstellung dieser Aufrufer entfernt werden.

## Überholte Dokumentaussagen

- `docs/architecture.md` beschreibt korrekt die Modultrennung, behauptet aber
  noch, eine Simulation müsse mangels Domain-Endzustand ausschließlich am
  Schrittlimit enden. Das ist der in diesem Umbau zu schließende Kernmangel.
- Die dort beschriebene Rundenwertübernahme ist keine reine Domain-Folge:
  Markt und Leitzins stammen aktuell noch aus Android-Legacy-Code.
- `docs/umbau-verifikation.md` belegt nur das frühere technische CLI mit
  neutraler Belohnung. Es belegt weder vollständige Partien noch Replay von
  Trainings-Episoden.

## Baseline

Vor der ersten Änderung liefen erfolgreich:

```text
./gradlew test :apps:android:assembleDebug
BUILD SUCCESSFUL
```

Es wurden keine vorhandenen Fehler festgestellt.
