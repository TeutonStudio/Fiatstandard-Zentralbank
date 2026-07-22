# Python-Trainingsworker

Der dauerhaft laufende Kotlin-Prozess spricht genau ein JSON-Objekt pro Zeile auf
stdin/stdout. Python verwaltet Transport und Tensoren, aber keine Spielregel.

## Start und Protokoll

```bash
./gradlew -q --console=plain :tools:simulation:worker
```

Jede Anfrage besitzt `command`, optional `id` und bei umgebungsbezogenen Befehlen
`environmentId`. Unterstützt sind:

- `reset`: Seed, `scenarioId`, optional `watchdogDecisions`,
- `observe`, `legal_actions`,
- `step`: `actionCanonical` oder strukturiertes `action`,
- `batch_reset`, `batch_step`,
- `export_episode`: serverseitiger JSONL-Pfad,
- `close`: eine Umgebung oder ohne ID den gesamten Worker.

Erfolg enthält `ok:true`; Fehler enthalten `ok:false` und ein Objekt mit `code`,
`message` und gegebenenfalls `environmentId`. Reset/Observe liefern alle drei
Schemaversionen, aktiven Spieler, Beobachtung und legale Aktionen.

Beispiel:

```json
{"command":"reset","environmentId":"env-1","seed":42,"scenarioId":"generiert-landkrieg-3"}
{"command":"legal_actions","environmentId":"env-1"}
{"command":"step","environmentId":"env-1","actionCanonical":"{...}"}
{"command":"close"}
```

## Python-Wrapper

`fiat_ai.environment.KotlinWorker` lässt Gradle einmal die `installDist`-Distribution
bauen und startet danach die Worker-Mainklasse direkt auf deren JVM-Classpath.
Dadurch puffert kein Gradle-`JavaExec` den langlebigen NDJSON-Eingang. Der Wrapper
stellt beliebig viele `NdjsonEnvironment`-Objekte bereit. `batch_reset` und
`batch_step` reduzieren Roundtrips; Kontextmanager beziehungsweise `close()`
beendet den Prozess mit begrenztem Terminate-/Kill-Fallback sauber.

## Training und Export

```bash
cd tools/ai-python
python -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m fiat_ai.train build/data/episoden.jsonl \
  --checkpoint build/model/model.pt --epochs 1 --seed 42
.venv/bin/python -m fiat_ai.evaluate build/data/episoden.jsonl build/model/model.pt
.venv/bin/python -m fiat_ai.export_onnx build/model/model.pt \
  --output build/model/spieler-ki-v1.onnx \
  --manifest build/model/manifest.json
```

Das Manifest führt `modelVersion`, alle vier Schemaversionen, Stile,
`maximumPlayers`, `trainingCommit` und `createdAt`. Der Export verwendet dynamische
Kandidatenachsen und validiert das resultierende Modell mit ONNX. Der Kotlin-
Smoke-Test lautet `./gradlew :tools:simulation:onnxSmoke`.
