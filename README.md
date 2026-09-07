# Fiatstandard Zentralbank

Das Repository enthält eine gemeinsame, ereignisbasierte Spiel-Engine für Android,
einen autoritativen JVM-Server und Headless-Simulationen. Die maßgeblichen
Fachtypen liegen in `core/domain`; Android-, HTTP- und Persistenzdetails liegen
außerhalb des Kerns.

Voraussetzungen sind JDK 17 und für den Android-Build ein eingerichtetes Android
SDK. Gradle richtet die Kotlin/JS- und Node.js-Werkzeuge für den Web-Build ein.

## Bauen und testen

```bash
# alle JVM- und Android-Unit-Tests
./gradlew test

# Android-Debug-APK
./gradlew :apps:android:assembleDebug

# APK und direkt hochladbare itch.io-ZIP gemeinsam erzeugen
./gradlew releaseArtifacts

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
./gradlew :apps:web:jsBrowserDevelopmentRun
```

Der Browserclient führt die gemeinsame Kotlin-Spielengine lokal aus. Er benötigt
keinen Server, speichert Partien im Browser und unterstützt JSON-Import und
-Export. Die direkt auf itch.io hochladbare ZIP entsteht mit:

```bash
./gradlew :apps:web:itchZip
```

Details stehen in [`docs/ITCH_RELEASE.md`](docs/ITCH_RELEASE.md).

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
