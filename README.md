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
./gradlew :tools:simulation:run --args="--episodes 2 --seed 42 --max-steps 500 --output build/simulation/episodes.jsonl"
```

Die Ausgabe ist JSONL und kann ohne duplizierte Kotlin-Regeln mit der
Python-Brücke gelesen werden:

```bash
cd tools/ai-python
python -m unittest discover -s tests
python fiat_ai/episode_parser.py ../simulation/build/simulation/episodes.jsonl
```

Die Architektur und die bewusst noch vorhandenen Übergangsmodelle sind in
[`docs/architecture.md`](docs/architecture.md) und
[`docs/legacy-migration.md`](docs/legacy-migration.md) beschrieben.
