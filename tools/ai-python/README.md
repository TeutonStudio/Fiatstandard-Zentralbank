# Python-Brücke für KI-Werkzeuge

Dieses Verzeichnis liest die von `:tools:simulation` exportierten JSONL-Episoden. Es enthält
absichtlich keine Kopie der Spielregeln: Autoritative Zustandsübergänge bleiben in der
Kotlin-Engine.

Smoke-Test:

```bash
cd tools/ai-python
python -m unittest discover -s tests
```

Episoden lesen:

```bash
python fiat_ai/episode_parser.py ../simulation/build/simulation/episoden.jsonl
```

Oder als Python-API:

```python
from pathlib import Path
from fiat_ai.episode_parser import lade_episoden

for episode in lade_episoden(Path("../simulation/build/simulation/episoden.jsonl")):
    for entscheidung in episode.entscheidungen:
        print(episode.spiel_id, entscheidung.spieler, entscheidung.gewaehlte_aktion)
```

Der Parser akzeptiert Episodenformat 2, gleicht Regel-, Beobachtungs- und Aktionsversionen
der Entscheidungen mit dem Episodenkopf ab und weist Datensätze mit Passwortfeldern zurück.

`KotlinWorker` startet den implementierten dauerhaften Kotlin-Prozess.
`NdjsonEnvironment` unterstützt `reset`, `observe`, `legal_actions`, `step`,
`export_episode` und `close`; der Prozess unterstützt zusätzlich Batch-Reset und
Batch-Step. Das Python-Paket wertet Spielregeln nicht selbst aus.

Technischer Trainingslauf:

```bash
python -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m fiat_ai.train build/data/episoden.jsonl \
  --checkpoint build/model/model.pt --epochs 1 --seed 42
.venv/bin/python -m fiat_ai.evaluate build/data/episoden.jsonl build/model/model.pt
.venv/bin/python -m fiat_ai.export_onnx build/model/model.pt \
  --output build/model/spieler-ki-v1.onnx \
  --manifest build/model/manifest.json
```
