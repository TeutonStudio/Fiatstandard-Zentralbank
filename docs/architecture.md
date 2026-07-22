# Architektur

Stand: 21. Juli 2026. Maßgeblich ist der Code auf dem aktuellen Branch; ältere
Umbaupläne unter `docs/` sind historische Arbeitsunterlagen.

## Module und Abhängigkeitsrichtung

| Modul | Verantwortung | Direkte Projektabhängigkeiten |
| --- | --- | --- |
| `core/domain` | Fachzustand, Aktionen, Ereignisse, Regeln, Engine, Aktionsraum, Beobachtung, Partieende, Invarianten und deterministischer Zufall | keine |
| `core/application` | `SpielSitzung`, atomare Aktionsfolgen, Undo/Redo, Ablageport, Read Models und Zustandshash | `core/domain` |
| `adapters/persistence-json` | In-Memory- und atomare JSON-Dateiablage | Core |
| `adapters/persistence-room` | Room-Datenbank, Migrationen, DAOs und `RaumSpielAblage` | Core |
| `adapters/protocol-json` | versionierte HTTP-DTOs und Domain-Zuordnung | Core |
| `apps/android` | Compose, Navigation, Lifecycle, Darstellung, Room-Verdrahtung und Legacy-Kompatibilität | Core und Room-Adapter |
| `apps/server` | autoritativer HTTP-Transport und strukturierte Fehlerabbildung | Core, JSON-Persistenz und Protokoll |
| `apps/web` | nicht autoritativer TypeScript-Client | HTTP-API |
| `tools/simulation` | Trainingsumgebung, Szenarien, Agenten, Kodierung, Belohnung, Episode und CLI | ausschließlich `core/domain` |
| `tools/ai-python` | Parser des Episodenformats und vorbereitete NDJSON-Transportschnittstelle | keine Kotlin-Regeln |

Die erlaubte Richtung lautet:

```text
apps ───────┐
            ├──> adapters ──> core/application ──> core/domain
tools ──────┘          └──────────────────────────> core/domain
```

`tools/simulation` benötigt für den In-Process-Trainingslauf keinen Adapter und
greift deshalb direkt auf `core/domain` zu. `architekturPruefen` verhindert
Android-/Compose-Importe in Core, Room-Importe außerhalb des Room-Adapters sowie
rückwärts gerichtete Core-Abhängigkeiten.

## Maßgebliche fachliche Kette

```text
SpielAktion
    ↓ StandardSpielEngine: prüfen und auflösen
ein oder mehrere SpielEreignisse
    ↓ SpielRegelwerk
SpielZustand
    ↓ AktionsAuswertung / BeobachtungsAuswertung
AktionsRaum und SpielBeobachtung
```

- `SpielAktion` ist eine serialisierbare Absicht eines Spielers oder Agenten.
  Erzeugungsaktionen enthalten keine frei wählbaren Objekt-IDs.
- `StandardSpielEngine` prüft den Eingangszustand, erzeugt Ereignisse, faltet sie
  über `SpielRegelwerk` und prüft den Folgezustand mit `ZustandsInvarianten`.
- `SpielEreignis` beschreibt nur akzeptierte Zustandsänderungen. Automatische
  Buchungen und Rundenfolgen sind Ereignisse, keine Agentenoptionen.
- `SpielZustand` ist die einzige fachlich maßgebliche Wahrheit. Ein Zustand kann
  vollständig aus Startzustand und geordnetem Ereignisverlauf rekonstruiert werden.
- `AktionsAuswertung` erzeugt einen deterministisch sortierten, endlichen
  `AktionsRaum` und lässt jede Kandidatenaktion durch dieselbe Engine prüfen.
- `BeobachtungsAuswertung` projiziert eine spielerbezogene `SpielBeobachtung`.

## Zug, Runde und Partieende

Ein regulärer Zug beginnt im Prozug. `ProzugBegonnen` friert die in diesem Zug
fälligen Versorgungs- und Anleihenverpflichtungen als Snapshot ein. Ein
Verwaltungsstandort erhält Ertrag nur nach Versorgung; eigene passende Produktion
deckt zuerst den Eigenbedarf. Der Spieler liefert nur den verbleibenden Bedarf.

`ZugBeenden` kann als eine Aktion mehrere Ereignisse erzeugen:

1. `ZugBeendet` wechselt zum nächsten spielbaren Spieler.
2. Beim Umlauf zum ersten Spieler verfallen überalterte offene Angebote.
3. `RundeBegonnen` schreibt neue Marktpreise, Leitzins und Inflationswert in den
   Fachzustand.
4. `ProzugBegonnen` erzeugt den Verpflichtungssnapshot des neuen Zuges.

Marktpreise werden aus den beobachteten Einzelpreisen der beendeten Runde
ganzzahlig gemittelt; ohne Beobachtung bleibt der bisherige Preis erhalten. Die
Leitzinsregel arbeitet ausschließlich in ganzzahligen Basispunkten. Replay führt
daher zu denselben Rundenwerten.

`SpielErgebnis` ist der terminale Fachstatus. Eine Aufgabe-Aktion existiert nicht.
Nach jedem Ausscheiden gewinnt der einzige verbleibende Spieler sofort, noch bevor
sein nächster Prozug beginnt; scheiden alle aus, gibt es keinen Gewinner. Nach
`PartieBeendet` existiert kein aktiver Zug und die Engine lehnt normale Aktionen
ab. Nur die Simulation setzt nach 10.000 aufeinanderfolgenden Entscheidungen ohne
Marktwertänderung `truncated`; sie erzeugt dabei kein Fachereignis und keinen Sieger.

## Angebote

Rohstoff- und Anleihenangebote sind Bestandteil von `SpielZustand`. Erstellen,
annehmen, ablehnen und zurückziehen sind Spieleraktionen. Beim Erstellen wird kein
Bestand reserviert. Erst die Annahme prüft den aktuellen Bestand und erzeugt die
endgültige Übertragung. Angebots-IDs stammen aus dem deterministischen Zähler des
Fachzustands. Offene alte Angebote laufen beim Rundenwechsel ab.

## Beobachtung und Modellkodierung

Beobachtung 2 enthält für alle Spieler die vollständige bereits eingetretene
öffentliche Wirtschaft, Karte, Einheiten, Anleihen, Kriege, Belagerungen und
Friedensverträge. Nur Passwörter und noch nicht gewählte zukünftige Entscheidungen
fehlen. Alle Listen werden stabil sortiert.

Aktionsschema 2 ist eine variable, vollständig legale Kandidatenliste. Jede Aktion
wird kanonisch als strukturiertes JSON serialisiert; Kartenpositionen besitzen
keine globalen Nummern und es gibt weder Hashmaske noch Kandidatenkappung. Das
Modell bewertet `policy(state_embedding, action_embedding, style_embedding)` nur
über diese Liste.

## Android-Aktion

1. Compose gibt eine UI-Absicht an das 98-zeilige `GameViewModel`.
2. Das ViewModel delegiert an den `LegacySpielKoordinator` und dessen
   `SpielSitzung`.
3. Prozug, Zugende, Konflikt und Kartenbau laufen als `SpielAktion` durch
   `StandardSpielEngine`. Noch ereignisbasierte Karten-Callbacks werden vor jeder
   Zustandsänderung ausdrücklich in eine Spieleraktion übersetzt.
4. Die erzeugten Ereignisse aktualisieren den StateFlow des `SpielZustand` und
   werden als Startzustand plus Verlauf gespeichert.
5. Das ViewModel greift weder auf DAO noch `AppDatabase` zu.

Das alte veränderliche `Spiel` wird noch von Finanz-, Handels- und
Darstellungsbildschirmen gelesen und vom Koordinator für Legacy-Tabellen gepflegt.
Es ist kein Teil der Engine. Die verbleibenden direkten Android-Ereignispfade sind
in `legacy-migration.md` aufgelistet.

## Web-Aktion

1. Der Browser sendet ein versioniertes `SpielAktionDto` an `/api/v1`.
2. Der Server ordnet das DTO einer Domain-Aktion zu. Neue Aktionstypen können im
   Protokoll v1 vorübergehend über den strikt serialisierten Umschlag
   `ErweiterteAktion` transportiert werden.
3. `SpielServerDienst` und `SpielDienst` laden die Sitzung und rufen die gemeinsame
   Engine auf; REST-Routen enthalten keine Spielregel.
4. Nur akzeptierte Ereignisse werden gespeichert. Fehler sind strukturiert und
   verändern den Zustand nicht.
5. API-Antworten enthalten keine Passwort-Hashes.

Der Browser ist niemals autoritativ. Android darf dieselbe Engine lokal betreiben.

## Simulationsschritt

1. `StandardTrainingsUmgebung.reset` erzeugt aus Szenario und Seed einen
   Startzustand und führt den zwingenden Prozugbeginn aus.
2. Der Entscheidungspunkt enthält aktiven Spieler, dessen Beobachtung und den
   aktuellen Aktionsraum.
3. Ein `SpielAgent` wählt ausschließlich daraus; `ZufallsAgent` verwendet nur die
   übergebene `SeedZufallsquelle`.
4. `step` validiert die Mitgliedschaft im Aktionsraum und führt die Aktion über die
   gemeinsame Engine aus.
5. Zwingende Regelfolgen laufen bis zum nächsten echten Entscheidungspunkt.
6. `PotentialBelohnungsModell` berechnet austauschbare Trainingsbelohnungen
   außerhalb der Engine.

`SzenarioKatalog` erzeugt reproduzierbare Wirtschafts-, Schulden-, Land-/Seekrieg-,
Blockade-, Belagerungs- und Friedenslagen für 3–7 Spieler und lädt alle echten
Kartenvorlagen aus gemeinsamen Core-Ressourcen. Mehrere Umgebungen halten
ausschließlich Instanzzustand und können unabhängig laufen.

## Trainingsdaten

Eine JSONL-Zeile ist eine vollständige `SpielEpisode` (Format 2) mit Startzustand,
allen Entscheidungen aller Spieler, komplettem Ereignisverlauf, Ergebnis und
Truncation. Jeder `EntscheidungsDatensatz` enthält Beobachtung, Aktionsraum,
gewählte Aktion und Belohnung. `replay()` faltet den gespeicherten Verlauf erneut.
Der Export verwirft Passwörter und bricht ab, falls ein Passwortfeld serialisiert
würde. Der Python-Parser prüft dieselbe Eigenschaft erneut.

## Persistenz und Versionen

- Application-Spielstandschema: 2, Regel-/Engine-Version: `2.0.0`.
- JSON-Ablageschema: 2; Schema 1 wird geladen und beim nächsten Speichern auf 2
  geschrieben. Dateien werden über eine temporäre Datei ersetzt.
- Room-Datenbank: Schema 4 mit erhaltenen Migrationen 1→2, 2→3 und 3→4;
  Fachspielstandformat 3 akzeptiert Format 2. Format 1 wird wegen eines nicht
  verifizierbaren historischen Prozug-Snapshots ausdrücklich abgelehnt.
- HTTP-Protokoll: v1; Domain-DTOs und Transport-DTOs bleiben getrennt.
- Episode 2, Beobachtung 2, Aktion 2 und Modellkodierung 2.

Die normalisierten Room-Tabellen bleiben als Legacy-Kompatibilität erhalten. Für
neue Fachwahrheit ist `FachSpielstand` mit Startzustand und Ereignissen maßgeblich.

## Determinismus und Invarianten

Die Engine verwendet keine Systemzeit, globale Zufallsquelle, Android-API, Room,
Dateisystem oder Netzwerk. Zufall wird über `Zufallsquelle` injiziert. IDs für
Anleihen, Seewege, Einheiten und Angebote stammen aus serialisierten Zählern im
Zustand. Kandidaten und Beobachtungen werden unabhängig von Map-Reihenfolgen
sortiert.

`ZustandsInvarianten` prüft unter anderem nichtnegative Rohstoff-/Bauteilbestände,
eindeutige Spieler- und Objekt-IDs, höchstens einen Gläubiger je Anleihe, gültige
Objektreferenzen sowie die Übereinstimmung von aktivem Spieler und Zugstatus. Der
Massentest replayt zusätzlich jede Episode und verlangt einen nichtleeren
Aktionsraum an jedem nichtterminalen Entscheidungspunkt.
