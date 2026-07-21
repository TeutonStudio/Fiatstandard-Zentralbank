# ADR 003: Headless-Simulation und KI-Protokoll

- Status: angenommen
- Datum: 2026-07-21

## Kontext

Trainingsläufe müssen ohne Android funktionieren und exakt dieselben erlaubten
Aktionen und Zustandsübergänge wie Produktiv-Clients verwenden. Die
Belohnungsdefinition wird sich voraussichtlich häufiger ändern als die Regeln.

## Entscheidung

`tools/simulation` ist ein Kotlin/JVM-CLI über `SpielEngine`. Eine
`SpielStrategie` wählt aus `erlaubteAktionen`; die Zufallsstrategie erhält einen
deterministischen Seed. `Bewertungsfunktion` ist außerhalb der Engine injiziert
und liefert benannte Komponenten für Überleben, Liquidität, Produktion,
Kontrolle, Schuldenrisiko und Endergebnis.

Jeder Schritt wird als versionierte JSONL-Zeile mit Episode, Engine-Version,
Seed, Akteur, Beobachtungen, legalen/gewählten Aktionen, Komponenten,
Ereignissen und Terminierungsdaten exportiert. Die gelieferte neutrale
Bewertung ist eine technische Baseline, keine fachlich endgültige
KI-Belohnungsfunktion.

Python liest diese Episoden nur. Es dupliziert keine Kotlin-Regeln. Für spätere
interaktive Läufe definiert die Python-Schnittstelle die NDJSON-Kommandos
`reset`, `observe`, `legal_actions`, `step` und `close` über stdin/stdout.

## Folgen

- Offline-Datensätze sind reproduzierbar und unabhängig von ML-Frameworks.
- Reward Shaping kann ersetzt werden, ohne die Engine-Version zu verändern.
- Das Schrittlimit ist eine gültige Terminierung, solange das Domain-Modell
  noch keinen allgemeinen Spielendestatus bereitstellt.
- Ein späterer interaktiver Kotlin-Host kann das vorbereitete NDJSON-Protokoll
  implementieren, ohne die Python-API neu zu entwerfen.
