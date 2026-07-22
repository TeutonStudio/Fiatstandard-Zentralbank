# Fiatstandard Zentralbank

Das Repository enthält eine gemeinsame, ereignisbasierte Spiel-Engine für Android,
einen autoritativen JVM-Server und Headless-Simulationen. Die maßgeblichen
Fachtypen liegen in `core/domain`; Android-, HTTP- und Persistenzdetails liegen
außerhalb des Kerns.

Voraussetzungen sind JDK 17 und für den Android-Build ein eingerichtetes Android
SDK. Der Web-Client benötigt eine aktuelle Node.js-/npm-Installation.

## Bauen und testen

```bash
# alle JVM- und Android-Unit-Tests
./gradlew test

# Android-Debug-APK
./gradlew :apps:android:assembleDebug

# einzelne Server- und Simulationstests
./gradlew :apps:server:test
./gradlew :tools:simulation:test

# Modulgrenzen ohne vollständigen Build prüfen
./gradlew architekturPruefen
```

## Server starten

Der Server speichert standardmäßig versionierte JSON-Dateien unter
`.data/games`. Für kurzlebige Entwicklungsläufe kann `--memory` verwendet werden.

```bash
./gradlew :apps:server:run --args="--port 8080 --data .data/games"
```

Anschließend antwortet `GET http://127.0.0.1:8080/health`. Die Spiel-API liegt
unter `/api/v1/games`.

## Web-Client starten

```bash
cd apps/web
npm ci
npm run build
npm start
```

Die API-Basis-URL kann im Browserformular geändert werden und wird lokal im
Browser gespeichert.

## Simulation und Trainingsdatenexport

```bash
./gradlew :tools:simulation:run --args="--spiele 2 --seed 42 --max-entscheidungen 500 --spieler sicherheit --karte kleine-wirtschaft-v1 --ausgabe build/simulation"

# separater Massentest mit 1.000 deterministischen Partien
./gradlew :tools:simulation:massentest

# vollständige Zielkonfigurationen (je 10.000 Partien)
./gradlew :tools:simulation:massentestFriedlich10000
./gradlew :tools:simulation:massentestKrieg10000

# Agentenliga und Kotlin-ONNX-Smoke-Test
./gradlew :tools:simulation:liga
./gradlew :tools:simulation:onnxSmoke
```

Die CLI schreibt `episoden.jsonl` und `statistik.json` in den Ausgabeordner.
`--episodes`, `--max-steps` und `--output` bleiben als kompatible englische
Aliase verfügbar.

Die Ausgabe ist Episodenformat 2 als JSONL und kann ohne duplizierte Kotlin-Regeln
mit der Python-Brücke gelesen und trainiert werden. Für einen dauerhaft laufenden
Mehrumgebungsworker steht `./gradlew :tools:simulation:worker` bereit.

```bash
cd tools/ai-python
python -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m fiat_ai.train build/data/episoden.jsonl \
  --checkpoint build/model/model.pt --epochs 1 --seed 42
.venv/bin/python -m fiat_ai.export_onnx build/model/model.pt \
  --output build/model/spieler-ki-v1.onnx \
  --manifest build/model/manifest.json
```

Die v1-Architektur ist in [`docs/SPIELER_KI_MODELL_V1.md`](docs/SPIELER_KI_MODELL_V1.md)
zusammengefasst. Beobachtung, Aktionsraum, Episoden, Worker und Krieg besitzen
eigene Dokumente unter `docs/`.
