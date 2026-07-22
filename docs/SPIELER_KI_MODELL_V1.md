# SpielerKIModel v1

Stand: 22. Juli 2026. SpielerKIModel v1 ist ein durchgängiger technischer
Trainingspfad. Seine Spielstärke ist bewusst keine Abnahmebedingung.

## Architektur

Für menschliche Clients, Agenten, Simulation und Training gilt genau derselbe
autoritative Fluss:

```text
SpielAktion → StandardSpielEngine → SpielEreignis → SpielRegelwerk → SpielZustand
```

`AktionsAuswertung` fragt diesen Pfad für jeden Kandidaten ab. Android, Server,
Web und Python enthalten keine zweite Spielregel. Python sendet nur eine der vom
Kotlin-Worker gelieferten kanonischen Aktionen zurück.

Die Implementierung verteilt sich auf:

- `core/domain`: Spielregeln, Zustand, Beobachtung 2 und Aktionsraum 2,
- `tools/simulation`: Szenarien, Episode 2, Rewards, Worker, Agentenliga und ONNX,
- `tools/ai-python`: Dataset, Encoder, PyTorch-Modell, Training, Evaluation, Export,
- `apps/android`, `apps/server`, `apps/web`: nicht autoritative Entwickleroberflächen.

## Zahlungsfähigkeit und reguläres Spielende

Im Prozug ist ausschließlich die Versorgung des Hauptbahnhofs zwingend;
unversorgte Nebenstandorte liefern in dieser Runde keinen Ertrag. Die zentrale
`ZahlungsfaehigkeitsAuswertung` prüft offene Rückkäufe, Zinsen, weitere
Verbindlichkeiten und den Rohstoffbedarf des Hauptbahnhofs. Reguläre Aktionen
bleiben in der Reihenfolge Eigenmittel/Rohstoffe, Verkauf, Einkauf, Emission und
Aufstockung verfügbar. Im Krieg folgt bei ausgeschöpften Mitteln zuerst
Kapitulation und Friedensabwicklung.

`ZahlungsunfaehigkeitFeststellen` ist die explizite, replaybare automatische
Abwicklung: Ohne verbleibenden regulären Rettungsweg löst die Engine zunächst
einen möglichen Schuldenstrich aus; nur wenn kein Verwaltungsstandort mehr
herabstufbar ist, scheidet der Spieler aus. Der Schuldenstrich beendet den Zug,
ist wiederholbar, aber im Krieg verboten. Bei nur noch einem aktiven Spieler
wird unmittelbar vor dem nächsten Prozug der Sieg gebucht.

## Gemeinsame Policy und Stile

Die öffentliche Konditionierung unterstützt
`VORSICHTIG`, `PRODUKTIONSORIENTIERT`, `SCHULDENFINANZIERT`,
`HANDELSORIENTIERT`, `AGGRESSIV`, `OPPORTUNISTISCH`,
`EXPANSIONISTISCH` und `DEFENSIVE_DOMINANZ`. Der Stil steht im Spielerzustand,
in Beobachtung und Episode sowie im Modellmanifest. Er ändert keine Regel.

Die Baseline berechnet konzeptionell:

```text
state_embedding  = StateEncoder(observation)
action_embedding = ActionEncoder(candidate)
style_embedding  = Embedding(style)
score             = PolicyMLP(state, action, style)
value             = ValueHead(state, style)
```

Die Aktionsdimension ist dynamisch; Softmax beziehungsweise Argmax sieht nur die
aktuell legalen Kandidaten. Das technische Modell nutzt einfache gepoolte
Merkmale statt eines GNN.

## Agenten und Fallback

Die Liga enthält Zufalls-, Sicherheits-, Wirtschafts-, aggressiven, defensiven
und ONNX-Agenten. Keiner kennt eine Aufgabe-Aktion. Der ONNX-Agent prüft Manifest,
Beobachtungs-, Aktions- und Episodenversion, alle acht Stile und die maximale
Spielerzahl. Fehlende Dateien, Ladefehler, inkompatible Versionen oder nicht
endliche Scores führen automatisch zum Sicherheitsagenten. Auch der Fallback
wählt ausschließlich aus `AktionsRaum.aktionen`.

## Szenarien und Curriculum

`SzenarioKatalog` unterstützt sechs echte Kartenvorlagen und generierte Karten,
3–7 Spieler sowie die Kategorien Wirtschaft, Handel/Schulden, Schuldenkrise,
Landkrieg, Seekrieg, Blockade, Belagerung, Friedensverhandlung und vollständig.
Die Bereiche für Training, Validierung und Test beginnen bei `0`, `1_000_000_000`
und `2_000_000_000`. Empfohlene Curriculum-Reihenfolge:

1. Wirtschaft und Versorgung,
2. Handel und Schulden,
3. Landkrieg,
4. Seekrieg und vollständige Mehrspielerlagen.

## Entwickleroberflächen

Androids Bereich `KI_ENTWICKLUNG` zeigt die öffentlichen Wirtschafts- und
Kriegsdaten, Stil/Agent/Fallback und erzeugt seine Schaltflächen aus dem zentralen
Aktionsraum. Der Web-Client bietet dieselben generischen Aktions-, Stil-,
Simulations- und Ligawerkzeuge. Der Server stellt zusätzlich bereit:

- `GET /api/v1/games/{id}/observation`,
- `POST /api/v1/simulations`,
- `POST /api/v1/league`,
- `GET /api/v1/league/latest`.

## Reproduzierbare Befehle

```bash
./gradlew test
./gradlew :tools:simulation:massentestFriedlich10000
./gradlew :tools:simulation:massentestKrieg500
./gradlew :tools:simulation:massentestKrieg10000
./gradlew :tools:simulation:liga --args='6 42 build/liga'

cd tools/ai-python
python -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m fiat_ai.train build/data/episoden.jsonl \
  --checkpoint build/model/model.pt --epochs 1 --seed 42
.venv/bin/python -m fiat_ai.evaluate build/data/episoden.jsonl build/model/model.pt
.venv/bin/python -m fiat_ai.export_onnx build/model/model.pt \
  --output build/model/spieler-ki-v1.onnx \
  --manifest build/model/manifest.json
cd ../..
./gradlew :tools:simulation:onnxSmoke
```

## Ausgefuehrte Abnahme am 22. Juli 2026

- `./gradlew test`: erfolgreich, 314 Tests in 59 Suites; 68 Gradle-Tasks ohne
  fehlgeschlagenen Test.
- konfliktfrei: 10.000/10.000 Episoden beendet, 0 Truncations, 0 Fehler,
  336,74 Schritte/s, Seed `2_000_000_000`.
- vollständiger Land-/Seekrieg: 500/500 Episoden beendet, 0 Truncations,
  0 Fehler, 40,26 Schritte/s, Seed `2_100_000_000`.
- Agentenliga: sechs sitzrotierte Spiele, 0 Fehler, alle sechs Agententypen mit
  je drei Einsätzen und je einem Sieg; 50,56 Schritte/s. JSON und Kurztext liegen
  unter `tools/simulation/build/liga`.
- Python: drei Tests bestanden; ein CPU-Trainingsdurchlauf verarbeitete 88
  Samples. Die technische Imitationsgenauigkeit betrug 63,64 Prozent.
- ONNX: Export und Kotlin-Laden erfolgreich; die Inferenz wählte
  `VerwaltungsstandortVersorgen` aus 22 legalen Kandidaten.

Die konfliktfreien 10.000 Episoden belegen mit vollständiger Topologie rund
24 GiB, die 500 vollständigen Kriegsepisoden rund 3,7 GiB. Der vollständige
10.000er-Kriegslauf wurde deshalb im verfügbaren Arbeitsfenster nicht
vorgetäuscht: Bei gemessenen 40,26 Schritten/s ist mit mehreren Stunden und grob
74 GiB Episodendaten zu rechnen. Der oben angegebene Gradle-Task führt das
unveränderte Ziel von 10.000 Partien aus.

## Bekannte Einschraenkungen

- Das Smoke-Modell ist absichtlich klein und mit 88 Heuristik-Samples nicht auf
  Spielstärke trainiert.
- Android und Web sind Entwickleroberflächen; sie visualisieren den öffentlichen
  Zustand und senden kanonische Aktionen, besitzen aber noch kein finales Layout.
- Eine große Liga mit vielen ONNX-Partien ist CPU- und speicherintensiv, weil für
  jede legale Kandidatenaktion ein Score berechnet und die V2-Trajektorie für die
  Statistik gehalten wird. Für CI/Smoke ist die Sechser-Rotation vorgesehen.

## Bewusste technische Entscheidungen

- Friedensschulden werden atomar noviert: alte Siegeranleihen verschwinden und
  gleich hohe neue Verliererforderungen entstehen beim selben Gläubiger. Das ist
  buchhalterisch Rückkauf plus Neuemission, vermeidet aber temporäre Doppelbuchung.
- Cent-Reste gehen in stabiler Spieler-ID-Reihenfolge an die ersten Verlierer.
- Beginnen mehrere gleich starke Belagerer im selben Ereignis, entscheidet die
  kleinste Spieler-ID; danach behält der zuerst führende Spieler den Gleichstand.
- Ein Frachtschiff samt Seeweg ist ein dauerhaftes Kartenobjekt. Fehlt ein
  intakter, vom Routeneigentümer kontrollierter Endhafen, ruht nur der Handel.
- Der vier Byte große Aktionsindex ist ausschließlich die lokale Position in der
  aktuellen kanonischen Kandidatenliste, niemals eine globale Karten- oder
  Aktionsnummer.
- Die ONNX-Baseline ist ein Kompatibilitätsnachweis, keine Zusage zur Spielstärke.
- Android und Web sind funktionale Entwickleroberflächen, kein finales Grafikdesign.
