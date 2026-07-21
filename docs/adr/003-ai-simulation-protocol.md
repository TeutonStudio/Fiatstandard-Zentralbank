# ADR 003: Headless-Simulation und KI-Protokoll

- Status: angenommen und umgesetzt
- Datum: 2026-07-21

## Kontext

Trainingsläufe müssen ohne Android dieselben Aktionen, Regeln, Invarianten und
Zustandsübergänge wie Produktiv-Clients verwenden. Beobachtungs-, Kodierungs- und
Belohnungsdefinitionen ändern sich voraussichtlich häufiger als Fachregeln.

## Entscheidung

`tools/simulation` hängt ausschließlich von `core/domain` ab. Eine
`StandardTrainingsUmgebung` bietet `reset(szenario, seed)` und `step(aktion)`.
Jeder Entscheidungspunkt enthält eine spielerbezogene `SpielBeobachtung` und den
von `AktionsAuswertung` erzeugten `AktionsRaum`. `terminated` bedeutet ausschließlich
ein `SpielErgebnis`; ein technisches Limit setzt getrennt `truncated`.

`SpielAgent` wählt `SpielAktion`. `ZufallsAgent` verwendet nur eine injizierte
`SeedZufallsquelle`; Sicherheits- und Wirtschaftsagent sind nachvollziehbare
Heuristikbaselines. Belohnungen werden über `BelohnungsModell` außerhalb der Engine
berechnet. Version 1 kombiniert Terminalwerte (+1 Gewinner, -1 Verlierer, 0 echtes
Unentschieden) mit potentialbasiertem Shaping für Liquidität, Nettovermögen,
produktive Kapazität, aktive Infrastruktur und Schuldendienstfähigkeit.

Eine JSONL-Zeile enthält eine vollständige `SpielEpisode` im Format 2. Kopf und
Entscheidungen protokollieren Regel-, Beobachtungs- und Aktionsversion. Der
Startzustand ist passwortbereinigt; Export und Python-Parser lehnen Passwortfelder
zusätzlich ab. Der vollständige Ereignisverlauf ermöglicht Replay.

`BeobachtungsKodierung` Version 1 ist eine ML-frameworkfreie feste Arrayprojektion
mit Padding und Aktionsmaske. Das Python-Modul liest Episoden und bereitet nur den
Transport für `reset`, `observe`, `legal_actions`, `step` und `close` vor; es
dupliziert keine Spielregel.

## Folgen

- Offline-Datensätze sind seed-reproduzierbar und replaybar.
- Regel-, Beobachtungs-, Aktions-, Episoden- und Kodierungsversion können getrennt
  weiterentwickelt werden.
- Reward Shaping kann ausgetauscht werden, ohne die Engine zu ändern.
- Die Hash-basierte Baseline-Aktionsmaske kann kollidieren und muss vor einem
  produktiven Policy-Modell durch eine explizite versionierte Aktionstabelle ersetzt
  werden.
- Das JVM-Szenario verwendet eine kompakte generierte Karte. Android-Karten müssen
  später in ein gemeinsames JVM-Ressourcenmodul überführt werden, wenn reale Karten
  direkt für Training verwendet werden sollen.
