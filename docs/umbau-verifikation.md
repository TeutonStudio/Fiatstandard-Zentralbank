# Umbau-Verifikation

## Phase 0: Zustand vor dem Umbau

Am 21. Juli 2026 wurden vor der ersten Verschiebung auf dem unveränderten
Arbeitsbaum ausgeführt:

```text
./gradlew test                    BUILD SUCCESSFUL
./gradlew :app:assembleDebug      BUILD SUCCESSFUL
```

Damit waren keine bereits vorhandenen Unit-Test-, Kompilierungs- oder
Android-Debug-Build-Fehler festzustellen. Es wurde keine Fachregel verändert,
um einen Baseline-Fehler zu umgehen.

## Fehler während des Umbaus

Kurzzeitig auftretende Kompilierungsfehler beim Verschieben von UI-Farben und
bei fehlenden Imports wurden innerhalb desselben Schritts behoben. Sie waren
durch den Umbau verursacht und sind nicht als Altfehler klassifiziert. Die
endgültigen Abnahmebefehle und ihr Ergebnis werden nach Abschluss dieses Umbaus
in der Übergabe zusammengefasst.

## Abschlussprüfung

Am selben Tag liefen erfolgreich:

```text
./gradlew test
./gradlew :apps:android:assembleDebug
./gradlew :apps:server:test
./gradlew :tools:simulation:test
./gradlew :tools:simulation:run --args="--episodes 2 --seed 42 --max-steps 500"
cd apps/web && npm ci && npm run build
cd tools/ai-python && python -m unittest discover -s tests
./gradlew :adapters:persistence-room:compileDebugAndroidTestKotlin
```

Der Simulationslauf erzeugte 1.000 JSONL-Zeilen für zwei Episoden; der
Python-Parser las sie vollständig. Die beiden Room-Porttests sind als
Android-Instrumentierungstests vorhanden und wurden kompiliert. Ihre Ausführung
auf Gerät/Emulator war nicht Teil der lokalen JVM-Abnahme.
