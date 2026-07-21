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
python fiat_ai/episode_parser.py ../simulation/build/simulation/episodes.jsonl
```

Oder als Python-API:

```python
from pathlib import Path
from fiat_ai.episode_parser import lade_episode

for schritt in lade_episode(Path("../simulation/build/simulation/episodes.jsonl")):
    print(schritt.episode_id, schritt.step, schritt.chosen_action)
```

`NdjsonEnvironment` bereitet die Befehle `reset`, `observe`, `legal_actions`, `step` und `close`
über zeilengetrenntes JSON auf stdin/stdout vor. Ein späterer Kotlin-Worker kann dieses Protokoll
implementieren; das Python-Paket wertet Spielregeln nicht selbst aus.
